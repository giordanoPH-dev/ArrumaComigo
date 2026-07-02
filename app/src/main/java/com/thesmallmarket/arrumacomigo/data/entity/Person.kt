package com.thesmallmarket.arrumacomigo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Uma pessoa do lar a quem tarefas podem ser atribuídas. */
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Cor do avatar em hex (#RRGGBB), usada nos cartões e chips. */
    val colorHex: String,
    /** Emoji simples usado como avatar. */
    val emoji: String = "🙂",
)
