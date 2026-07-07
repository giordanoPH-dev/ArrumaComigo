package com.thesmallmarket.arrumacomigo.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thesmallmarket.arrumacomigo.HouseholdApplication

/** Sync periódico (15 min) — puxa mudanças feitas em outros devices para o tablet sempre ligado. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HouseholdApplication
        runCatching { app.container.syncEngine.syncOnce() }
        return Result.success() // falha de rede → o próximo ciclo periódico retenta
    }

    companion object {
        const val UNIQUE_NAME = "supabase_sync"
    }
}
