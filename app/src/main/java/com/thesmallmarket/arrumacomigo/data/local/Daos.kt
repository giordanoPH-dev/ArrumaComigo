package com.thesmallmarket.arrumacomigo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thesmallmarket.arrumacomigo.data.entity.Person
import com.thesmallmarket.arrumacomigo.data.entity.RoomEntity
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
}
