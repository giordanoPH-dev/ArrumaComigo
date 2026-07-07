package com.thesmallmarket.arrumacomigo.di

import android.content.Context
import com.thesmallmarket.arrumacomigo.data.local.AppDatabase
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import com.thesmallmarket.arrumacomigo.data.repository.OfflineHouseholdRepository
import com.thesmallmarket.arrumacomigo.notification.ReminderScheduler
import com.thesmallmarket.arrumacomigo.sync.SyncEngine

/** Container de dependências montado manualmente (sem Hilt), no estilo dos codelabs. */
class AppContainer(context: Context) {
    private val database = AppDatabase.getDatabase(context)

    val repository: HouseholdRepository = OfflineHouseholdRepository(
        personDao = database.personDao(),
        roomDao = database.roomDao(),
        taskDao = database.taskDao(),
        completionDao = database.taskCompletionDao(),
        pendingDeleteDao = database.pendingDeleteDao(),
    )

    val syncEngine = SyncEngine(context.applicationContext, database)

    val reminderScheduler = ReminderScheduler(context.applicationContext, repository)

    init {
        (repository as OfflineHouseholdRepository).onMutated = { syncEngine.requestSync() }
    }
}
