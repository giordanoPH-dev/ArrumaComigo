package com.thesmallmarket.arrumacomigo.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thesmallmarket.arrumacomigo.HouseholdApplication
import kotlinx.coroutines.flow.first

/**
 * Dispara a notificação de lembrete de uma tarefa. Se a tarefa for recorrente,
 * reagenda o próximo lembrete para manter o ciclo (diário/semanal/mensal).
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0) return Result.failure()

        val app = applicationContext as HouseholdApplication
        val repository = app.container.repository

        val task = repository.taskOnce(taskId) ?: return Result.success()
        if (task.isArchived || !task.reminderEnabled) return Result.success()

        val roomName = repository.room(task.roomId).first()?.name
        NotificationHelper.showTaskReminder(applicationContext, task.id, task.title, roomName)

        // Reagenda a próxima ocorrência das tarefas recorrentes.
        app.container.reminderScheduler.scheduleNextOccurrence(task)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
