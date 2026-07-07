package com.thesmallmarket.arrumacomigo.sync

import android.content.Context
import com.thesmallmarket.arrumacomigo.data.entity.PendingDelete
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.Priority
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.RoomType
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion
import com.thesmallmarket.arrumacomigo.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Sincroniza o Room local com o Supabase (PostgREST puro, sem SDK).
 *
 * Modelo: Room é a fonte local reativa; o remoto guarda o estado compartilhado.
 * Push: linhas com pendingSync=1 + fila de pending_deletes (viram tombstone `deleted=true`).
 * Pull: linhas remotas com updated_at > cursor, aplicadas com last-write-wins.
 * Falha de rede aborta silenciosamente — pendingSync garante retentativa no próximo ciclo.
 */
class SyncEngine(context: Context, private val db: AppDatabase) {

    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Dispara um ciclo completo em background; erros são engolidos (retry no próximo trigger). */
    fun requestSync() {
        if (!SyncConfig.isConfigured) return
        scope.launch { runCatching { syncOnce() } }
    }

    /** Um ciclo completo (pull + push). Usado pelo SyncWorker. */
    suspend fun syncOnce() {
        if (!SyncConfig.isConfigured) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                pullAll()
                pushAll()
            }
        }
    }

    /** Só o pull, best-effort. Retorna true se completou — gate do HouseSeeder num device novo. */
    suspend fun pullOnce(): Boolean {
        if (!SyncConfig.isConfigured) return false
        return withContext(Dispatchers.IO) {
            mutex.withLock { runCatching { pullAll() }.isSuccess }
        }
    }

    // ---------- Pull ----------

    private suspend fun pullAll() {
        // Ordem pais → filhos, para os FKs locais resolverem no mesmo passe.
        pullTable(PendingDelete.PEOPLE) { applyPerson(it) }
        pullTable(PendingDelete.ROOMS) { applyRoom(it) }
        pullTable(PendingDelete.TASKS) { applyTask(it) }
        pullTable(PendingDelete.TASK_COMPLETIONS) { applyCompletion(it) }
    }

    private suspend fun pullTable(table: String, apply: suspend (JSONObject) -> Unit) {
        var cursor = prefs.getLong("last_pull_$table", 0L)
        while (true) {
            // ponytail: slack de 10 min compensa clock skew entre devices (updated_at é relógio do cliente);
            // reaplicar linha já vista é idempotente. Upgrade: updated_at por trigger no Postgres.
            val since = (cursor - CLOCK_SLACK_MS).coerceAtLeast(0)
            val rows = JSONArray(
                httpGet("$table?updated_at=gt.$since&order=updated_at.asc&limit=$PAGE_SIZE")
            )
            val before = cursor
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                if (row.optBoolean("deleted")) {
                    deleteLocal(table, row.getString("uuid"))
                } else {
                    apply(row)
                }
                cursor = maxOf(cursor, row.getLong("updated_at"))
            }
            prefs.edit().putLong("last_pull_$table", cursor).apply()
            if (rows.length() < PAGE_SIZE || cursor == before) break
        }
    }

    private suspend fun deleteLocal(table: String, uuid: String) = when (table) {
        PendingDelete.PEOPLE -> db.personDao().deleteByUuid(uuid)
        PendingDelete.ROOMS -> db.roomDao().deleteByUuid(uuid) // CASCADE local limpa tasks/completions
        PendingDelete.TASKS -> db.taskDao().deleteByUuid(uuid)
        else -> db.taskCompletionDao().deleteByUuid(uuid)
    }

    private suspend fun applyPerson(o: JSONObject) {
        val local = db.personDao().getByUuidOnce(o.getString("uuid"))
        if (!shouldApplyRemote(local?.updatedAt, o.getLong("updated_at"))) return
        val person = personFromJson(o, localId = local?.id ?: 0)
        if (local == null) db.personDao().insert(person) else db.personDao().update(person)
    }

    private suspend fun applyRoom(o: JSONObject) {
        val local = db.roomDao().getByUuidOnce(o.getString("uuid"))
        if (!shouldApplyRemote(local?.updatedAt, o.getLong("updated_at"))) return
        val room = roomFromJson(o, localId = local?.id ?: 0)
        if (local == null) db.roomDao().insert(room) else db.roomDao().update(room)
    }

    private suspend fun applyTask(o: JSONObject) {
        val local = db.taskDao().getByUuidOnce(o.getString("uuid"))
        if (!shouldApplyRemote(local?.updatedAt, o.getLong("updated_at"))) return
        // Cômodo ainda não existe localmente (ex.: tombstonado) → pula; o próximo pull resolve ou nunca importa.
        val roomId = db.roomDao().getByUuidOnce(o.getString("room_uuid"))?.id ?: return
        val personId = o.optStringOrNull("assigned_person_uuid")
            ?.let { db.personDao().getByUuidOnce(it)?.id }
        val task = taskFromJson(o, localId = local?.id ?: 0, roomId = roomId, personId = personId)
        if (local == null) db.taskDao().insert(task) else db.taskDao().update(task)
    }

    private suspend fun applyCompletion(o: JSONObject) {
        val local = db.taskCompletionDao().getByUuidOnce(o.getString("uuid"))
        if (!shouldApplyRemote(local?.updatedAt, o.getLong("updated_at"))) return
        val taskId = db.taskDao().getByUuidOnce(o.getString("task_uuid"))?.id ?: return
        val personId = o.optStringOrNull("person_uuid")
            ?.let { db.personDao().getByUuidOnce(it)?.id }
        val completion = completionFromJson(o, localId = local?.id ?: 0, taskId = taskId, personId = personId)
        if (local == null) db.taskCompletionDao().insert(completion) else return
    }

    // ---------- Push ----------

    private suspend fun pushAll() {
        // Tombstones primeiro: HttpURLConnection não suporta PATCH, então o tombstone
        // é um upsert de {uuid, deleted, updated_at} — as demais colunas têm DEFAULT no schema.
        for (d in db.pendingDeleteDao().getAll()) {
            upsert(
                d.tableName,
                JSONObject()
                    .put("uuid", d.uuid)
                    .put("deleted", true)
                    .put("updated_at", System.currentTimeMillis()),
            )
            db.pendingDeleteDao().delete(d.uuid)
        }
        for (p in db.personDao().getPending()) {
            upsert(PendingDelete.PEOPLE, personToJson(p))
            db.personDao().clearPending(p.uuid, p.updatedAt)
        }
        for (r in db.roomDao().getPending()) {
            upsert(PendingDelete.ROOMS, roomToJson(r))
            db.roomDao().clearPending(r.uuid, r.updatedAt)
        }
        for (t in db.taskDao().getPending()) {
            val roomUuid = db.roomDao().getByIdOnce(t.roomId)?.uuid ?: continue
            val personUuid = t.assignedPersonId?.let { db.personDao().getByIdOnce(it)?.uuid }
            upsert(PendingDelete.TASKS, taskToJson(t, roomUuid, personUuid))
            db.taskDao().clearPending(t.uuid, t.updatedAt)
        }
        for (c in db.taskCompletionDao().getPending()) {
            val taskUuid = db.taskDao().getByIdOnce(c.taskId)?.uuid ?: continue
            val personUuid = c.personId?.let { db.personDao().getByIdOnce(it)?.uuid }
            upsert(PendingDelete.TASK_COMPLETIONS, completionToJson(c, taskUuid, personUuid))
            db.taskCompletionDao().clearPending(c.uuid, c.updatedAt)
        }
    }

    private fun upsert(table: String, row: JSONObject) {
        http("POST", "$table?on_conflict=uuid", JSONArray().put(row).toString())
    }

    // ---------- HTTP (PostgREST) ----------

    private fun httpGet(pathAndQuery: String): String = http("GET", pathAndQuery, null)

    private fun http(method: String, pathAndQuery: String, body: String?): String {
        val conn = URL("${SyncConfig.SUPABASE_URL}/rest/v1/$pathAndQuery")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("apikey", SyncConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${SyncConfig.SUPABASE_ANON_KEY}")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IOException("Supabase HTTP $code em $method /$pathAndQuery: $error")
            }
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val PAGE_SIZE = 1000
        private const val CLOCK_SLACK_MS = 10 * 60 * 1000L

        /** LWW: aplica o remoto se não existe local ou se é estritamente mais novo (empate → local ganha). */
        fun shouldApplyRemote(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
            localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt
    }
}

// ---------- Mapeamento JSON ↔ entidade (funções puras, testadas em SyncEngineTest) ----------

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else getString(key)

internal fun personToJson(p: Person): JSONObject = JSONObject()
    .put("uuid", p.uuid)
    .put("name", p.name)
    .put("color_hex", p.colorHex)
    .put("emoji", p.emoji)
    .put("updated_at", p.updatedAt)
    .put("deleted", false)

internal fun personFromJson(o: JSONObject, localId: Long): Person = Person(
    id = localId,
    name = o.getString("name"),
    colorHex = o.getString("color_hex"),
    emoji = o.getString("emoji"),
    uuid = o.getString("uuid"),
    updatedAt = o.getLong("updated_at"),
    pendingSync = false,
)

internal fun roomToJson(r: RoomEntity): JSONObject = JSONObject()
    .put("uuid", r.uuid)
    .put("name", r.name)
    .put("type", r.type.name)
    .put("updated_at", r.updatedAt)
    .put("deleted", false)

internal fun roomFromJson(o: JSONObject, localId: Long): RoomEntity = RoomEntity(
    id = localId,
    name = o.getString("name"),
    type = RoomType.fromName(o.getString("type")),
    uuid = o.getString("uuid"),
    updatedAt = o.getLong("updated_at"),
    pendingSync = false,
)

internal fun taskToJson(t: Task, roomUuid: String, personUuid: String?): JSONObject = JSONObject()
    .put("uuid", t.uuid)
    .put("room_uuid", roomUuid)
    .put("title", t.title)
    .put("assigned_person_uuid", personUuid ?: JSONObject.NULL)
    .put("priority", t.priority.name)
    .put("estimated_minutes", t.estimatedMinutes ?: JSONObject.NULL)
    .put("recurrence", t.recurrence.name)
    .put("recurrence_interval", t.recurrenceInterval)
    .put("days_of_week", t.daysOfWeek)
    .put("next_due_date", t.nextDueDate.toString())
    .put("reminder_time", t.reminderTime?.toString() ?: JSONObject.NULL)
    .put("reminder_enabled", t.reminderEnabled)
    .put("is_archived", t.isArchived)
    .put("updated_at", t.updatedAt)
    .put("deleted", false)

internal fun taskFromJson(o: JSONObject, localId: Long, roomId: Long, personId: Long?): Task = Task(
    id = localId,
    roomId = roomId,
    title = o.getString("title"),
    assignedPersonId = personId,
    priority = Priority.fromName(o.getString("priority")),
    estimatedMinutes = if (o.isNull("estimated_minutes")) null else o.getInt("estimated_minutes"),
    recurrence = Recurrence.fromName(o.getString("recurrence")),
    recurrenceInterval = o.getInt("recurrence_interval"),
    daysOfWeek = o.getInt("days_of_week"),
    nextDueDate = LocalDate.parse(o.getString("next_due_date")),
    reminderTime = o.optStringOrNull("reminder_time")?.let { LocalTime.parse(it) },
    reminderEnabled = o.getBoolean("reminder_enabled"),
    isArchived = o.getBoolean("is_archived"),
    uuid = o.getString("uuid"),
    updatedAt = o.getLong("updated_at"),
    pendingSync = false,
)

internal fun completionToJson(c: TaskCompletion, taskUuid: String, personUuid: String?): JSONObject =
    JSONObject()
        .put("uuid", c.uuid)
        .put("task_uuid", taskUuid)
        .put("person_uuid", personUuid ?: JSONObject.NULL)
        .put("task_title", c.taskTitle)
        .put("completed_at", c.completedAt.toString())
        .put("due_date", c.dueDate?.toString() ?: JSONObject.NULL)
        .put("updated_at", c.updatedAt)
        .put("deleted", false)

internal fun completionFromJson(o: JSONObject, localId: Long, taskId: Long, personId: Long?): TaskCompletion =
    TaskCompletion(
        id = localId,
        taskId = taskId,
        personId = personId,
        taskTitle = o.getString("task_title"),
        completedAt = LocalDateTime.parse(o.getString("completed_at")),
        dueDate = o.optStringOrNull("due_date")?.let { LocalDate.parse(it) },
        uuid = o.getString("uuid"),
        updatedAt = o.getLong("updated_at"),
        pendingSync = false,
    )
