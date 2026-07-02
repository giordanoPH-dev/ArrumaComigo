package com.thesmallmarket.arrumacomigo.di

import android.content.Context
import com.thesmallmarket.arrumacomigo.data.local.AppDatabase
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import com.thesmallmarket.arrumacomigo.data.repository.OfflineHouseholdRepository
import com.thesmallmarket.arrumacomigo.notification.ReminderScheduler

/** Container de dependências montado manualmente (sem Hilt), no estilo dos codelabs. */
class AppContainer(context: Context) {
    private val database = AppDatabase.getDatabase(context)

    val repository: HouseholdRepository = OfflineHouseholdRepository(
        personDao = database.personDao(),
        roomDao = database.roomDao(),
        taskDao = database.taskDao(),
        completionDao = database.taskCompletionDao(),
    )

    val reminderScheduler = ReminderScheduler(context.applicationContext, repository)
}
