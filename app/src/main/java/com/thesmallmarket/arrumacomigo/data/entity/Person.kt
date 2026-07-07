package com.thesmallmarket.arrumacomigo.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Uma pessoa do lar a quem tarefas podem ser atribuídas. */
@Entity(
    tableName = "people",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Cor do avatar em hex (#RRGGBB), usada nos cartões e chips. */
    val colorHex: String,
    /** Emoji simples usado como avatar. */
    val emoji: String = "🙂",
    /** Identidade global de sync (o id Long é só local). */
    @ColumnInfo(defaultValue = "") val uuid: String = newUuid(),
    /** Epoch millis da última mutação local — usado no LWW do sync. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    /** Aguardando push para o Supabase. */
    @ColumnInfo(defaultValue = "1") val pendingSync: Boolean = true,
)

/** UUID sem hifens, formato usado como PK remota no Supabase. */
fun newUuid(): String = UUID.randomUUID().toString().replace("-", "")
