package com.thesmallmarket.arrumacomigo.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Um cômodo da casa (cozinha, banheiro, etc.).
 * Chamado de [RoomEntity] para não conflitar com `androidx.room.Room`.
 */
@Entity(
    tableName = "rooms",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: RoomType,
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "1") val pendingSync: Boolean = true,
)
