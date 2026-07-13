package com.thesmallmarket.arrumacomigo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thesmallmarket.arrumacomigo.data.entity.PendingDelete
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.Scenario
import com.thesmallmarket.arrumacomigo.data.entity.ScenarioItem
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<Person>>

    @Query("SELECT * FROM people WHERE id = :id")
    fun getById(id: Long): Flow<Person?>

    @Query("SELECT COUNT(*) FROM people")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    @Delete
    suspend fun delete(person: Person)

    // Sync
    @Query("SELECT * FROM people WHERE pendingSync = 1")
    suspend fun getPending(): List<Person>

    @Query("SELECT * FROM people WHERE uuid = :uuid")
    suspend fun getByUuidOnce(uuid: String): Person?

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Person?

    /** Só limpa se ninguém editou durante o push (updatedAt inalterado). */
    @Query("UPDATE people SET pendingSync = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun clearPending(uuid: String, updatedAt: Long)

    @Query("DELETE FROM people WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)
}

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id")
    fun getById(id: Long): Flow<RoomEntity?>

    @Query("SELECT COUNT(*) FROM rooms")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(room: RoomEntity): Long

    @Update
    suspend fun update(room: RoomEntity)

    @Delete
    suspend fun delete(room: RoomEntity)

    // Sync
    @Query("SELECT * FROM rooms WHERE pendingSync = 1")
    suspend fun getPending(): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE uuid = :uuid")
    suspend fun getByUuidOnce(uuid: String): RoomEntity?

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun getByIdOnce(id: Long): RoomEntity?

    @Query("UPDATE rooms SET pendingSync = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun clearPending(uuid: String, updatedAt: Long)

    @Query("DELETE FROM rooms WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isArchived = 0 ORDER BY nextDueDate, priority DESC")
    fun getAllActive(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY nextDueDate, priority DESC")
    fun getAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE roomId = :roomId AND isArchived = 0 ORDER BY nextDueDate")
    fun getByRoom(roomId: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isArchived = 0 AND nextDueDate <= :date ORDER BY nextDueDate, priority DESC")
    fun getDueOnOrBefore(date: LocalDate): Flow<List<Task>>

    /** Agenda de hoje: pendentes (vencidas até hoje) + as já concluídas hoje, para permanecerem visíveis. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE (isArchived = 0 AND nextDueDate <= :date)
           OR id IN (SELECT taskId FROM task_completions WHERE completedAt >= :startOfDay)
        ORDER BY nextDueDate, priority DESC
        """
    )
    fun getTodayView(date: LocalDate, startOfDay: LocalDateTime): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getById(id: Long): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE isArchived = 0 AND reminderEnabled = 1 AND reminderTime IS NOT NULL")
    suspend fun getTasksWithReminders(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    // Sync
    @Query("SELECT * FROM tasks WHERE pendingSync = 1")
    suspend fun getPending(): List<Task>

    @Query("SELECT * FROM tasks WHERE uuid = :uuid")
    suspend fun getByUuidOnce(uuid: String): Task?

    @Query("UPDATE tasks SET pendingSync = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun clearPending(uuid: String, updatedAt: Long)

    @Query("DELETE FROM tasks WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    /** uuids das tarefas de um cômodo — para tombstonar os filhos que o CASCADE local apaga. */
    @Query("SELECT uuid FROM tasks WHERE roomId = :roomId")
    suspend fun uuidsByRoom(roomId: Long): List<String>
}

@Dao
interface TaskCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: TaskCompletion): Long

    @Query("SELECT * FROM task_completions ORDER BY completedAt DESC")
    fun getAll(): Flow<List<TaskCompletion>>

    @Query("SELECT * FROM task_completions WHERE completedAt >= :since ORDER BY completedAt DESC")
    fun getSince(since: String): Flow<List<TaskCompletion>>

    @Query("SELECT * FROM task_completions WHERE completedAt >= :start AND completedAt < :end ORDER BY completedAt DESC")
    fun getBetween(start: String, end: String): Flow<List<TaskCompletion>>

    /** Conclusão mais recente de uma tarefa (para o "Desfazer" do snackbar). */
    @Query("SELECT * FROM task_completions WHERE taskId = :taskId ORDER BY completedAt DESC LIMIT 1")
    suspend fun latestForTask(taskId: Long): TaskCompletion?

    /** Remove uma conclusão específica pela chave primária (usado ao desfazer). */
    @Query("DELETE FROM task_completions WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Sync
    @Query("SELECT * FROM task_completions WHERE pendingSync = 1")
    suspend fun getPending(): List<TaskCompletion>

    @Query("SELECT * FROM task_completions WHERE uuid = :uuid")
    suspend fun getByUuidOnce(uuid: String): TaskCompletion?

    @Query("UPDATE task_completions SET pendingSync = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun clearPending(uuid: String, updatedAt: Long)

    @Query("DELETE FROM task_completions WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT uuid FROM task_completions WHERE taskId = :taskId")
    suspend fun uuidsByTask(taskId: Long): List<String>

    @Query("SELECT uuid FROM task_completions WHERE taskId IN (SELECT id FROM tasks WHERE roomId = :roomId)")
    suspend fun uuidsByRoom(roomId: Long): List<String>
}

@Dao
interface ScenarioDao {
    @Query("SELECT * FROM scenarios ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<Scenario>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scenario: Scenario): Long

    @Update
    suspend fun update(scenario: Scenario)

    @Delete
    suspend fun delete(scenario: Scenario)

    // Sync
    @Query("SELECT * FROM scenarios WHERE pendingSync = 1")
    suspend fun getPending(): List<Scenario>

    @Query("SELECT * FROM scenarios WHERE uuid = :uuid")
    suspend fun getByUuidOnce(uuid: String): Scenario?

    @Query("SELECT * FROM scenarios WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Scenario?

    @Query("UPDATE scenarios SET pendingSync = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun clearPending(uuid: String, updatedAt: Long)

    @Query("DELETE FROM scenarios WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)
}

@Dao
interface ScenarioItemDao {
    @Query("SELECT * FROM scenario_items WHERE scenarioId = :scenarioId ORDER BY position, id")
    fun getByScenario(scenarioId: Long): Flow<List<ScenarioItem>>

    @Query("SELECT * FROM scenario_items")
    fun getAll(): Flow<List<ScenarioItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScenarioItem): Long

    @Update
    suspend fun update(item: ScenarioItem)

    @Delete
    suspend fun delete(item: ScenarioItem)

    /** Resetar: desmarca todos os itens marcados do cenário (e os marca para sync). */
    @Query("UPDATE scenario_items SET checked = 0, updatedAt = :now, pendingSync = 1 WHERE scenarioId = :scenarioId AND checked = 1")
    suspend fun reset(scenarioId: Long, now: Long)

    // Sync
    @Query("SELECT * FROM scenario_items WHERE pendingSync = 1")
    suspend fun getPending(): List<ScenarioItem>

    @Query("SELECT * FROM scenario_items WHERE uuid = :uuid")
    suspend fun getByUuidOnce(uuid: String): ScenarioItem?

    @Query("SELECT * FROM scenario_items WHERE id = :id")
    suspend fun getByIdOnce(id: Long): ScenarioItem?

    @Query("UPDATE scenario_items SET pendingSync = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun clearPending(uuid: String, updatedAt: Long)

    @Query("DELETE FROM scenario_items WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    /** uuids dos itens de um cenário — para tombstonar os filhos que o CASCADE local apaga. */
    @Query("SELECT uuid FROM scenario_items WHERE scenarioId = :scenarioId")
    suspend fun uuidsByScenario(scenarioId: Long): List<String>
}

@Dao
interface PendingDeleteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deletes: List<PendingDelete>)

    @Query("SELECT * FROM pending_deletes")
    suspend fun getAll(): List<PendingDelete>

    @Query("DELETE FROM pending_deletes WHERE uuid = :uuid")
    suspend fun delete(uuid: String)
}
