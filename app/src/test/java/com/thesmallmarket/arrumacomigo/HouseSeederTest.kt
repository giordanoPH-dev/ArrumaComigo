package com.thesmallmarket.arrumacomigo

import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import com.thesmallmarket.arrumacomigo.data.seed.HouseSeeder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HouseSeederTest {

    @Test
    fun `seeds two people the routine rooms and a full routine`() = runTest {
        val repo = FakeRepo()
        HouseSeeder.seedIfEmpty(repo)

        assertEquals(2, repo.people.size)
        assertTrue(repo.people.any { it.name == "Giordano" })
        assertTrue(repo.people.any { it.name == "Amanda" })

        // Cômodos da "Rotina da Casa"
        assertEquals(13, repo.rooms.size)

        // Rotina robusta com diárias, semanais, quinzenais e mensais
        assertTrue("esperava muitas tarefas, veio ${repo.tasks.size}", repo.tasks.size >= 30)
        assertTrue(repo.tasks.any { it.recurrence == Recurrence.DAILY })
        assertTrue(repo.tasks.any { it.recurrence == Recurrence.WEEKLY && it.recurrenceInterval == 2 }) // quinzenal
        assertTrue(repo.tasks.any { it.recurrence == Recurrence.MONTHLY })

        // Divisão do casal conforme o documento
        val amanda = repo.people.first { it.name == "Amanda" }.id
        val giordano = repo.people.first { it.name == "Giordano" }.id
        assertTrue(repo.tasks.any { it.title.contains("comida aos cães", true) && it.assignedPersonId == giordano })
        assertTrue(repo.tasks.any { it.title.contains("Arrumar a cama", true) && it.assignedPersonId == amanda })

        // Toda tarefa tem um responsável e um cômodo válido
        assertTrue(repo.tasks.all { it.assignedPersonId != null && it.roomId > 0 })
    }

    @Test
    fun `seed is idempotent`() = runTest {
        val repo = FakeRepo()
        HouseSeeder.seedIfEmpty(repo)
        val firstCount = repo.tasks.size
        HouseSeeder.seedIfEmpty(repo) // não deve duplicar
        assertEquals(firstCount, repo.tasks.size)
        assertEquals(2, repo.people.size)
    }
}

/** Repositório em memória só para testar o seeder (sem Room/dispositivo). */
private class FakeRepo : HouseholdRepository {
    val people = mutableListOf<Person>()
    val rooms = mutableListOf<RoomEntity>()
    val tasks = mutableListOf<Task>()
    private var personId = 0L
    private var roomId = 0L
    private var taskId = 0L

    override suspend fun peopleCount(): Int = people.size
    override suspend fun roomsCount(): Int = rooms.size

    override suspend fun upsertPerson(person: Person): Long {
        val id = ++personId
        people += person.copy(id = id)
        return id
    }

    override suspend fun upsertRoom(room: RoomEntity): Long {
        val id = ++roomId
        rooms += room.copy(id = id)
        return id
    }

    override suspend fun upsertTask(task: Task): Long {
        val id = ++taskId
        tasks += task.copy(id = id)
        return id
    }

    // --- Não usados pelo seeder ---
    override fun people(): Flow<List<Person>> = flowOf(people)
    override fun person(id: Long): Flow<Person?> = flowOf(people.firstOrNull { it.id == id })
    override suspend fun deletePerson(person: Person) {}
    override fun rooms(): Flow<List<RoomEntity>> = flowOf(rooms)
    override fun room(id: Long): Flow<RoomEntity?> = flowOf(rooms.firstOrNull { it.id == id })
    override suspend fun deleteRoom(room: RoomEntity) {}
    override suspend fun createRoomWithTasks(
        name: String,
        type: com.thesmallmarket.arrumacomigo.data.entity.RoomType,
        taskTitles: List<String>,
        firstDueDate: LocalDate,
    ): Long = 0
    override fun activeTasks(): Flow<List<Task>> = flowOf(tasks)
    override fun allTasks(): Flow<List<Task>> = flowOf(tasks)
    override fun tasksByRoom(roomId: Long): Flow<List<Task>> = flowOf(tasks.filter { it.roomId == roomId })
    override fun tasksDueOnOrBefore(date: LocalDate): Flow<List<Task>> = flowOf(tasks)
    override fun todayView(date: LocalDate): Flow<List<Task>> = flowOf(tasks)
    override fun task(id: Long): Flow<Task?> = flowOf(tasks.firstOrNull { it.id == id })
    override suspend fun taskOnce(id: Long): Task? = tasks.firstOrNull { it.id == id }
    override suspend fun deleteTask(task: Task) {}
    override suspend fun tasksWithReminders(): List<Task> = emptyList()
    override suspend fun completeTask(task: Task, completedAt: LocalDateTime): Task? = null
    override suspend fun uncompleteTask(task: Task, completion: TaskCompletion): Task = task
    override suspend fun skipTask(task: Task): Task? = null
    override suspend fun postponeTask(task: Task): Task = task
    override fun completions(): Flow<List<TaskCompletion>> = flowOf(emptyList())
    override fun completionsSince(since: LocalDateTime): Flow<List<TaskCompletion>> = flowOf(emptyList())
    override fun completionsBetween(start: LocalDateTime, end: LocalDateTime): Flow<List<TaskCompletion>> = flowOf(emptyList())
    override suspend fun latestCompletionFor(taskId: Long): TaskCompletion? = null
}
