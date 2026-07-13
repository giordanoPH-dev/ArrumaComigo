package com.thesmallmarket.arrumacomigo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Fila de deletes locais aguardando tombstone no Supabase. */
@Entity(tableName = "pending_deletes")
data class PendingDelete(
    @PrimaryKey val uuid: String,
    val tableName: String,
) {
    companion object {
        const val PEOPLE = "people"
        const val ROOMS = "rooms"
        const val TASKS = "tasks"
        const val TASK_COMPLETIONS = "task_completions"
        const val SCENARIOS = "scenarios"
        const val SCENARIO_ITEMS = "scenario_items"
    }
}
