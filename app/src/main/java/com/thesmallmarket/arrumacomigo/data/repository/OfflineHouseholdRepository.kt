package com.thesmallmarket.arrumacomigo.data.repository

import com.thesmallmarket.arrumacomigo.data.RecurrenceCalculator
import com.thesmallmarket.arrumacomigo.data.entity.PendingDelete
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.RoomType
import com.thesmallmarket.arrumacomigo.data.entity.Scenario
import com.thesmallmarket.arrumacomigo.data.entity.ScenarioItem
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion
import com.thesmallmarket.arrumacomigo.data.local.PendingDeleteDao
import com.thesmallmarket.arrumacomigo.data.local.PersonDao
import com.thesmallmarket.arrumacomigo.data.local.RoomDao
import com.thesmallmarket.arrumacomigo.data.local.ScenarioDao
import com.thesmallmarket.arrumacomigo.data.local.ScenarioItemDao
import com.thesmallmarket.arrumacomigo.data.local.TaskCompletionDao
import com.thesmallmarket.arrumacomigo.data.local.TaskDao
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

class OfflineHouseholdRepository(
    private val personDao: PersonDao,
    private val roomDao: RoomDao,
    private val taskDao: TaskDao,
    private val completionDao: TaskCompletionDao,
    private val pendingDeleteDao: PendingDeleteDao,
    private val scenarioDao: ScenarioDao,
    private val scenarioItemDao: ScenarioItemDao,
) : HouseholdRepository {

    /** Ligado pelo AppContainer ao SyncEngine — dispara um sync após cada mutação. */
    var onMutated: (() -> Unit)? = null

    private fun now() = System.currentTimeMillis()

    private inline fun <T> mutate(block: () -> T): T = block().also { onMutated?.invoke() }

    // Pessoas
    override fun people(): Flow<List<Person>> = personDao.getAll()
    override fun person(id: Long): Flow<Person?> = personDao.getById(id)
    override suspend fun peopleCount(): Int = personDao.count()
    override suspend fun upsertPerson(person: Person): Long = mutate {
        val stamped = person.copy(updatedAt = now(), pendingSync = true)
        if (stamped.id == 0L) personDao.insert(stamped) else { personDao.update(stamped); stamped.id }
    }
    override suspend fun deletePerson(person: Person) = mutate {
        pendingDeleteDao.insertAll(listOf(PendingDelete(person.uuid, PendingDelete.PEOPLE)))
        personDao.delete(person)
    }

    // Cômodos
    override fun rooms(): Flow<List<RoomEntity>> = roomDao.getAll()
    override fun room(id: Long): Flow<RoomEntity?> = roomDao.getById(id)
    override suspend fun roomsCount(): Int = roomDao.count()
    override suspend fun upsertRoom(room: RoomEntity): Long = mutate {
        val stamped = room.copy(updatedAt = now(), pendingSync = true)
        if (stamped.id == 0L) roomDao.insert(stamped) else { roomDao.update(stamped); stamped.id }
    }
    override suspend fun deleteRoom(room: RoomEntity) = mutate {
        // O CASCADE local vai apagar tasks e completions do cômodo — tombstona todo mundo no remoto.
        val taskUuids = taskDao.uuidsByRoom(room.id)
        val completionUuids = completionDao.uuidsByRoom(room.id)
        pendingDeleteDao.insertAll(
            completionUuids.map { PendingDelete(it, PendingDelete.TASK_COMPLETIONS) } +
                taskUuids.map { PendingDelete(it, PendingDelete.TASKS) } +
                PendingDelete(room.uuid, PendingDelete.ROOMS)
        )
        roomDao.delete(room)
    }

    override suspend fun createRoomWithTasks(
        name: String,
        type: RoomType,
        taskTitles: List<String>,
        firstDueDate: LocalDate,
    ): Long = mutate {
        val roomId = roomDao.insert(RoomEntity(name = name, type = type, updatedAt = now()))
        taskTitles.forEach { title ->
            taskDao.insert(Task(roomId = roomId, title = title, nextDueDate = firstDueDate, updatedAt = now()))
        }
        roomId
    }

    // Tarefas
    override fun activeTasks(): Flow<List<Task>> = taskDao.getAllActive()
    override fun allTasks(): Flow<List<Task>> = taskDao.getAll()
    override fun tasksByRoom(roomId: Long): Flow<List<Task>> = taskDao.getByRoom(roomId)
    override fun tasksDueOnOrBefore(date: LocalDate): Flow<List<Task>> = taskDao.getDueOnOrBefore(date)
    override fun todayView(date: LocalDate): Flow<List<Task>> =
        taskDao.getTodayView(date, date.atStartOfDay())
    override fun task(id: Long): Flow<Task?> = taskDao.getById(id)
    override suspend fun taskOnce(id: Long): Task? = taskDao.getByIdOnce(id)
    override suspend fun upsertTask(task: Task): Long = mutate {
        val stamped = task.copy(updatedAt = now(), pendingSync = true)
        if (stamped.id == 0L) taskDao.insert(stamped) else { taskDao.update(stamped); stamped.id }
    }
    override suspend fun deleteTask(task: Task) = mutate {
        pendingDeleteDao.insertAll(
            completionDao.uuidsByTask(task.id).map { PendingDelete(it, PendingDelete.TASK_COMPLETIONS) } +
                PendingDelete(task.uuid, PendingDelete.TASKS)
        )
        taskDao.delete(task)
    }
    override suspend fun tasksWithReminders(): List<Task> = taskDao.getTasksWithReminders()

    // ponytail: renumerar a lista visível pode reordenar globalmente tarefas fora dela
    // (a aba Hoje é um subconjunto) — aceito, app de família.
    override suspend fun moveTask(tasks: List<Task>, from: Int, to: Int) = mutate {
        val reordered = tasks.toMutableList().apply { add(to, removeAt(from)) }
        reordered.forEachIndexed { index, task ->
            if (task.position != index) {
                taskDao.update(task.copy(position = index, updatedAt = now(), pendingSync = true))
            }
        }
    }

    override suspend fun completeTask(task: Task, completedAt: LocalDateTime): Task? = mutate {
        completionDao.insert(
            TaskCompletion(
                taskId = task.id,
                personId = task.assignedPersonId,
                taskTitle = task.title,
                completedAt = completedAt,
                dueDate = task.nextDueDate,
                updatedAt = now(),
            )
        )
        val next = RecurrenceCalculator.next(
            from = maxOf(task.nextDueDate, completedAt.toLocalDate()),
            recurrence = task.recurrence,
            interval = task.recurrenceInterval,
            daysOfWeek = task.daysOfWeek,
        )
        if (next == null) {
            taskDao.update(task.copy(isArchived = true, updatedAt = now(), pendingSync = true))
            null
        } else {
            val updated = task.copy(nextDueDate = next, updatedAt = now(), pendingSync = true)
            taskDao.update(updated)
            updated
        }
    }

    override suspend fun uncompleteTask(task: Task, completion: TaskCompletion): Task = mutate {
        pendingDeleteDao.insertAll(listOf(PendingDelete(completion.uuid, PendingDelete.TASK_COMPLETIONS)))
        completionDao.deleteById(completion.id)
        val reverted = task.copy(
            nextDueDate = completion.dueDate ?: task.nextDueDate,
            isArchived = false,
            updatedAt = now(),
            pendingSync = true,
        )
        taskDao.update(reverted)
        reverted
    }

    override suspend fun skipTask(task: Task): Task? = mutate {
        val next = RecurrenceCalculator.next(
            from = maxOf(task.nextDueDate, LocalDate.now()),
            recurrence = task.recurrence,
            interval = task.recurrenceInterval,
            daysOfWeek = task.daysOfWeek,
        )
        if (next == null) {
            taskDao.update(task.copy(isArchived = true, updatedAt = now(), pendingSync = true))
            null
        } else {
            val updated = task.copy(nextDueDate = next, updatedAt = now(), pendingSync = true)
            taskDao.update(updated)
            updated
        }
    }

    override suspend fun postponeTask(task: Task): Task = mutate {
        // Semanal com dias marcados: "amanhã" vira o próximo dia do padrão (não quebra a escala).
        val tomorrow = LocalDate.now().plusDays(1)
        val target = if (task.recurrence == Recurrence.WEEKLY && task.daysOfWeek != 0) {
            RecurrenceCalculator.firstWeeklyOccurrence(tomorrow, task.daysOfWeek)
        } else tomorrow
        val updated = task.copy(nextDueDate = target, updatedAt = now(), pendingSync = true)
        taskDao.update(updated)
        updated
    }

    // Histórico
    override fun completions(): Flow<List<TaskCompletion>> = completionDao.getAll()
    override fun completionsSince(since: LocalDateTime): Flow<List<TaskCompletion>> =
        completionDao.getSince(since.toString())
    override fun completionsBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<TaskCompletion>> =
        completionDao.getBetween(start.toString(), end.toString())
    override suspend fun latestCompletionFor(taskId: Long): TaskCompletion? =
        completionDao.latestForTask(taskId)

    // Cenários
    override fun scenarios(): Flow<List<Scenario>> = scenarioDao.getAll()
    override fun scenarioItems(scenarioId: Long): Flow<List<ScenarioItem>> =
        scenarioItemDao.getByScenario(scenarioId)
    override fun allScenarioItems(): Flow<List<ScenarioItem>> = scenarioItemDao.getAll()
    override suspend fun upsertScenario(scenario: Scenario): Long = mutate {
        val stamped = scenario.copy(updatedAt = now(), pendingSync = true)
        if (stamped.id == 0L) scenarioDao.insert(stamped) else { scenarioDao.update(stamped); stamped.id }
    }
    override suspend fun upsertScenarioItem(item: ScenarioItem): Long = mutate {
        val stamped = item.copy(updatedAt = now(), pendingSync = true)
        if (stamped.id == 0L) scenarioItemDao.insert(stamped) else { scenarioItemDao.update(stamped); stamped.id }
    }
    override suspend fun deleteScenario(scenario: Scenario) = mutate {
        // O CASCADE local vai apagar os itens — tombstona todo mundo no remoto.
        pendingDeleteDao.insertAll(
            scenarioItemDao.uuidsByScenario(scenario.id).map { PendingDelete(it, PendingDelete.SCENARIO_ITEMS) } +
                PendingDelete(scenario.uuid, PendingDelete.SCENARIOS)
        )
        scenarioDao.delete(scenario)
    }
    override suspend fun deleteScenarioItem(item: ScenarioItem) = mutate {
        pendingDeleteDao.insertAll(listOf(PendingDelete(item.uuid, PendingDelete.SCENARIO_ITEMS)))
        scenarioItemDao.delete(item)
    }
    override suspend fun moveScenarioItem(items: List<ScenarioItem>, from: Int, to: Int) = mutate {
        val reordered = items.toMutableList().apply { add(to, removeAt(from)) }
        reordered.forEachIndexed { index, item ->
            if (item.position != index) {
                scenarioItemDao.update(item.copy(position = index, updatedAt = now(), pendingSync = true))
            }
        }
    }
    override suspend fun resetScenario(scenarioId: Long) = mutate {
        scenarioItemDao.reset(scenarioId, now())
    }
}
