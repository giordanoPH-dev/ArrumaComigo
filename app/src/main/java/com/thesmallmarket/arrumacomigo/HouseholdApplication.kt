package com.thesmallmarket.arrumacomigo

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.thesmallmarket.arrumacomigo.data.seed.HouseSeeder
import com.thesmallmarket.arrumacomigo.di.AppContainer
import com.thesmallmarket.arrumacomigo.notification.NotificationHelper
import com.thesmallmarket.arrumacomigo.sync.SyncConfig
import com.thesmallmarket.arrumacomigo.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class HouseholdApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)

        applicationScope.launch {
            // Pull antes do seed: num device novo os dados da casa chegam do Supabase e o
            // seeder não duplica. Device novo offline → não semeia; tenta no próximo start.
            val pulled = container.syncEngine.pullOnce()
            if (pulled || !SyncConfig.isConfigured) {
                // Popula a casa + rotina na primeira abertura (banco vazio).
                HouseSeeder.seedIfEmpty(container.repository)
            }
            container.syncEngine.requestSync()
        }

        if (SyncConfig.isConfigured) {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SyncWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build(),
            )
        }
    }
}
