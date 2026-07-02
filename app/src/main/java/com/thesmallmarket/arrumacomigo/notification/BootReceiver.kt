package com.thesmallmarket.arrumacomigo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.thesmallmarket.arrumacomigo.HouseholdApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Reagenda os lembretes após o aparelho reiniciar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as HouseholdApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                app.container.reminderScheduler.scheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
