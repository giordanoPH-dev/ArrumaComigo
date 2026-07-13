package com.thesmallmarket.arrumacomigo.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Um cenário: checklist para um momento específico (ex.: "Pré-viagem").
 * Itens vivem só dentro do cenário — sem recorrência, lembretes ou aba Hoje.
 */
@Entity(
    tableName = "scenarios",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class Scenario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "1") val pendingSync: Boolean = true,
)

/** Item marcável de um cenário. */
@Entity(
    tableName = "scenario_items",
    foreignKeys = [
        ForeignKey(
            entity = Scenario::class,
            parentColumns = ["id"],
            childColumns = ["scenarioId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("scenarioId"),
        Index(value = ["uuid"], unique = true),
    ],
)
data class ScenarioItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scenarioId: Long,
    val title: String,
    @ColumnInfo(defaultValue = "0") val checked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "1") val pendingSync: Boolean = true,
)
