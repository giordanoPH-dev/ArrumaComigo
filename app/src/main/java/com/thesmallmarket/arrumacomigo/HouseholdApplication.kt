package com.thesmallmarket.arrumacomigo

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.thesmallmarket.arrumacomigo.auth.AuthState
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
import java.util.concurrent.atomic.AtomicBoolean

class HouseholdApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataFlowStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)

        if (container.authManager.state.value is AuthState.Ready) {
            startDataFlow()
        }
        // Sair da conta OU da família rearma o fluxo: entrar noutra família refaz pull + seed.
        applicationScope.launch {
            container.authManager.state.collect {
                if (it !is AuthState.Ready) dataFlowStarted.set(false)
            }
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

    /** Pull → seed → sync. Idempotente: roda no onCreate (se já logado) ou quando a família fica pronta. */
    fun startDataFlow() {
        if (!dataFlowStarted.compareAndSet(false, true)) return
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
    }
}
