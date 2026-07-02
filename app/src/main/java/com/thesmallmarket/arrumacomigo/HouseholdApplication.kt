package com.thesmallmarket.arrumacomigo

import android.app.Application
import com.thesmallmarket.arrumacomigo.data.seed.HouseSeeder
import com.thesmallmarket.arrumacomigo.di.AppContainer
import com.thesmallmarket.arrumacomigo.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HouseholdApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
        // Popula a casa + rotina na primeira abertura (banco vazio).
        applicationScope.launch { HouseSeeder.seedIfEmpty(container.repository) }
    }
}
