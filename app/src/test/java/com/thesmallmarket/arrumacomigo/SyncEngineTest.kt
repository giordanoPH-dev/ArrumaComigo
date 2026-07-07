package com.thesmallmarket.arrumacomigo

import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.Priority
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
import com.thesmallmarket.arrumacomigo.data.entity.RoomType
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.entity.TaskCompletion
import com.thesmallmarket.arrumacomigo.sync.SyncEngine
import com.thesmallmarket.arrumacomigo.sync.completionFromJson
import com.thesmallmarket.arrumacomigo.sync.completionToJson
import com.thesmallmarket.arrumacomigo.sync.personFromJson
import com.thesmallmarket.arrumacomigo.sync.personToJson
import com.thesmallmarket.arrumacomigo.sync.roomFromJson
import com.thesmallmarket.arrumacomigo.sync.roomToJson
import com.thesmallmarket.arrumacomigo.sync.taskFromJson
import com.thesmallmarket.arrumacomigo.sync.taskToJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SyncEngineTest {

    // --- LWW ---

    @Test
    fun `remoto e aplicado quando nao existe local`() {
        assertTrue(SyncEngine.shouldApplyRemote(localUpdatedAt = null, remoteUpdatedAt = 100))
    }

    @Test
    fun `remoto mais novo vence`() {
        assertTrue(SyncEngine.shouldApplyRemote(localUpdatedAt = 100, remoteUpdatedAt = 200))
    }

    @Test
    fun `local mais novo vence`() {
        assertFalse(SyncEngine.shouldApplyRemote(localUpdatedAt = 200, remoteUpdatedAt = 100))
    }

    @Test
    fun `empate mantem o local`() {
        assertFalse(SyncEngine.shouldApplyRemote(localUpdatedAt = 100, remoteUpdatedAt = 100))
    }

    // --- Mapeamento JSON ↔ entidade (roundtrip preserva os campos) ---

    @Test
    fun `person roundtrip`() {
        val person = Person(id = 7, name = "Amanda", colorHex = "#AA88FF", emoji = "🌸", updatedAt = 123)
        val back = personFromJson(personToJson(person), localId = 7)
        assertEquals(person.copy(pendingSync = false), back)
    }

    @Test
    fun `room roundtrip`() {
        val room = RoomEntity(id = 3, name = "Cozinha", type = RoomType.KITCHEN, updatedAt = 456)
        val back = roomFromJson(roomToJson(room), localId = 3)
        assertEquals(room.copy(pendingSync = false), back)
    }

    @Test
    fun `task roundtrip com todos os campos`() {
        val task = Task(
            id = 10,
            roomId = 3,
            title = "Lavar louça",
            assignedPersonId = 7,
            priority = Priority.HIGH,
            estimatedMinutes = 20,
            recurrence = Recurrence.WEEKLY,
            recurrenceInterval = 2,
            daysOfWeek = 0b0010101,
            nextDueDate = LocalDate.of(2026, 7, 8),
            reminderTime = LocalTime.of(19, 30),
            reminderEnabled = true,
            isArchived = false,
            updatedAt = 789,
        )
        val back = taskFromJson(taskToJson(task, "room-uuid", "person-uuid"), localId = 10, roomId = 3, personId = 7)
        assertEquals(task.copy(pendingSync = false), back)
    }

    @Test
    fun `task com nulos vira JSON null e volta nulo`() {
        val task = Task(id = 1, roomId = 2, title = "Tirar o lixo", nextDueDate = LocalDate.of(2026, 7, 7))
        val json = taskToJson(task, "room-uuid", personUuid = null)
        assertTrue(json.isNull("assigned_person_uuid"))
        assertTrue(json.isNull("estimated_minutes"))
        assertTrue(json.isNull("reminder_time"))
        val back = taskFromJson(json, localId = 1, roomId = 2, personId = null)
        assertNull(back.assignedPersonId)
        assertNull(back.estimatedMinutes)
        assertNull(back.reminderTime)
    }

    @Test
    fun `completion roundtrip`() {
        val completion = TaskCompletion(
            id = 5,
            taskId = 10,
            personId = 7,
            taskTitle = "Lavar louça",
            completedAt = LocalDateTime.of(2026, 7, 7, 20, 15),
            dueDate = LocalDate.of(2026, 7, 7),
            updatedAt = 999,
        )
        val back = completionFromJson(
            completionToJson(completion, "task-uuid", "person-uuid"),
            localId = 5, taskId = 10, personId = 7,
        )
        assertEquals(completion.copy(pendingSync = false), back)
    }
}
