package com.thesmallmarket.arrumacomigo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Um cômodo da casa (cozinha, banheiro, etc.).
 * Chamado de [RoomEntity] para não conflitar com `androidx.room.Room`.
 */
@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: RoomType,
)
