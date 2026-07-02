package com.thesmallmarket.arrumacomigo.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/** Registro histórico de uma tarefa concluída — alimenta o balanço por pessoa. */
@Entity(
    tableName = "task_completions",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId"), Index("personId"), Index("completedAt")],
)
data class TaskCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    /** Quem concluiu (cópia do responsável no momento); nulo se não atribuída. */
    val personId: Long? = null,
    /** Título da tarefa no momento da conclusão, para o histórico sobreviver a exclusões. */
    val taskTitle: String,
    val completedAt: LocalDateTime,
    /** Data de vencimento que a tarefa tinha ao ser concluída, para reverter ao desfazer. */
    val dueDate: LocalDate? = null,
)
