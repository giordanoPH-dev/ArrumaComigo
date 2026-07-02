package com.thesmallmarket.arrumacomigo.data.repository

import com.thesmallmarket.arrumacomigo.data.RecurrenceCalculator
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.RoomType
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion
import com.thesmallmarket.arrumacomigo.data.local.PersonDao
import com.thesmallmarket.arrumacomigo.data.local.RoomDao
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
) : HouseholdRepository {

    // Pessoas
    override fun people(): Flow<List<Person>> = personDao.getAll()
    override fun person(id: Long): Flow<Person?> = personDao.getById(id)
    override suspend fun peopleCount(): Int = personDao.count()
    override suspend fun upsertPerson(person: Person): Long =
        if (person.id == 0L) personDao.insert(person) else { personDao.update(person); person.id }
    override suspend fun deletePerson(person: Person) = personDao.delete(person)

    // Cômodos
    override fun rooms(): Flow<List<RoomEntity>> = roomDao.getAll()
    override fun room(id: Long): Flow<RoomEntity?> = roomDao.getById(id)
    override suspend fun roomsCount(): Int = roomDao.count()
    override suspend fun upsertRoom(room: RoomEntity): Long =
        if (room.id == 0L) roomDao.insert(room) else { roomDao.update(room); room.id }
    override suspend fun deleteRoom(room: RoomEntity) = roomDao.delete(room)

    override suspend fun createRoomWithTasks(
        name: String,
        type: RoomType,
        taskTitles: List<String>,
        firstDueDate: LocalDate,
    ): Long {
        val roomId = roomDao.insert(RoomEntity(name = name, type = type))
        taskTitles.forEach { title ->
            taskDao.insert(Task(roomId = roomId, title = title, nextDueDate = firstDueDate))
        }
        return roomId
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
    override suspend fun upsertTask(task: Task): Long =
        if (task.id == 0L) taskDao.insert(task) else { taskDao.update(task); task.id }
    override suspend fun deleteTask(task: Task) = taskDao.delete(task)
    override suspend fun tasksWithReminders(): List<Task> = taskDao.getTasksWithReminders()

    override suspend fun completeTask(task: Task, completedAt: LocalDateTime): Task? {
        completionDao.insert(
            TaskCompletion(
                taskId = task.id,
                personId = task.assignedPersonId,
                taskTitle = task.title,
                completedAt = completedAt,
                dueDate = task.nextDueDate,
            )
        )
        val next = RecurrenceCalculator.next(
            from = maxOf(task.nextDueDate, completedAt.toLocalDate()),
            recurrence = task.recurrence,
            interval = task.recurrenceInterval,
            daysOfWeek = task.daysOfWeek,
        )
        return if (next == null) {
            taskDao.update(task.copy(isArchived = true))
            null
        } else {
            val updated = task.copy(nextDueDate = next)
            taskDao.update(updated)
            updated
        }
    }

    override suspend fun uncompleteTask(task: Task, completion: TaskCompletion): Task {
        completionDao.deleteById(completion.id)
        val reverted = task.copy(
            nextDueDate = completion.dueDate ?: task.nextDueDate,
            isArchived = false,
        )
        taskDao.update(reverted)
        return reverted
    }

    override suspend fun skipTask(task: Task): Task? {
        val next = RecurrenceCalculator.next(
            from = maxOf(task.nextDueDate, LocalDate.now()),
            recurrence = task.recurrence,
            interval = task.recurrenceInterval,
            daysOfWeek = task.daysOfWeek,
        )
        return if (next == null) {
            taskDao.update(task.copy(isArchived = true))
            null
        } else {
            val updated = task.copy(nextDueDate = next)
            taskDao.update(updated)
            updated
        }
    }

    override suspend fun postponeTask(task: Task): Task {
        val updated = task.copy(nextDueDate = LocalDate.now().plusDays(1))
        taskDao.update(updated)
        return updated
    }

    // Histórico
    override fun completions(): Flow<List<TaskCompletion>> = completionDao.getAll()
    override fun completionsSince(since: LocalDateTime): Flow<List<TaskCompletion>> =
        completionDao.getSince(since.toString())
    override fun completionsBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<TaskCompletion>> =
        completionDao.getBetween(start.toString(), end.toString())
    override suspend fun latestCompletionFor(taskId: Long): TaskCompletion? =
        completionDao.latestForTask(taskId)
}
