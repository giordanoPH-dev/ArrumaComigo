package com.thesmallmarket.arrumacomigo.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * Uma tarefa doméstica, pertencente a um cômodo e opcionalmente atribuída a uma pessoa.
 * Suporta recorrência e lembrete por horário.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["assignedPersonId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("roomId"),
        Index("assignedPersonId"),
        Index("nextDueDate"),
        Index(value = ["uuid"], unique = true),
    ],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val title: String,
    val assignedPersonId: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val estimatedMinutes: Int? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    /** A cada quantos períodos repete (ex.: 2 = a cada 2 dias/semanas/meses). */
    val recurrenceInterval: Int = 1,
    /** Bitmask de dias da semana para recorrência semanal (bit 0 = segunda ... bit 6 = domingo). */
    val daysOfWeek: Int = 0,
    /** Próxima data em que a tarefa deve ser feita. */
    val nextDueDate: LocalDate,
    /** Horário do lembrete; nulo se não houver lembrete. */
    val reminderTime: LocalTime? = null,
    val reminderEnabled: Boolean = false,
    val isArchived: Boolean = false,
    /** Ordem manual na lista (menor = mais acima). */
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "1") val pendingSync: Boolean = true,
)
