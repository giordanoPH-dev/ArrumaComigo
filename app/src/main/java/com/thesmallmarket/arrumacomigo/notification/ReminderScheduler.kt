package com.thesmallmarket.arrumacomigo.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.thesmallmarket.arrumacomigo.data.RecurrenceCalculator
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import com.thesmallmarket.arrumacomigo.data.entity.Task
import com.thesmallmarket.arrumacomigo.data.repository.HouseholdRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Agenda/cancela lembretes de tarefas usando WorkManager. */
class ReminderScheduler(
    private val context: Context,
    private val repository: HouseholdRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** (Re)agenda o lembrete de uma tarefa para a sua próxima data de vencimento. */
    fun schedule(task: Task) {
        val reminderTime = task.reminderTime
        if (!task.reminderEnabled || task.isArchived || reminderTime == null) {
            cancel(task.id)
            return
        }
        var target = LocalDateTime.of(task.nextDueDate, reminderTime)
        val now = LocalDateTime.now()
        // Se o horário já passou, avança para a próxima ocorrência (tarefas recorrentes).
        if (target.isBefore(now)) {
            if (task.recurrence == Recurrence.NONE) {
                // Tarefa única já vencida: dispara em breve.
                enqueue(task.id, Duration.ofSeconds(5).toMillis())
                return
            }
            var date: LocalDate? = task.nextDueDate
            while (target.isBefore(now) && date != null) {
                date = RecurrenceCalculator.next(
                    date, task.recurrence, task.recurrenceInterval, task.daysOfWeek,
                )
                if (date != null) target = LocalDateTime.of(date, reminderTime)
            }
            if (date == null) return
        }
        val delay = Duration.between(now, target).toMillis().coerceAtLeast(0)
        enqueue(task.id, delay)
    }

    /** Reagenda a próxima ocorrência após disparar (chamado pelo worker). */
    fun scheduleNextOccurrence(task: Task) {
        val reminderTime = task.reminderTime ?: return
        if (task.recurrence == Recurrence.NONE) return
        val nextDate = RecurrenceCalculator.next(
            LocalDate.now(), task.recurrence, task.recurrenceInterval, task.daysOfWeek,
        ) ?: return
        val target = LocalDateTime.of(nextDate, reminderTime)
        val delay = Duration.between(LocalDateTime.now(), target).toMillis().coerceAtLeast(0)
        enqueue(task.id, delay)
    }

    fun cancel(taskId: Long) {
        workManager.cancelUniqueWork(workName(taskId))
    }

    /** Reagenda todos os lembretes ativos (ex.: após reboot). */
    suspend fun scheduleAll() {
        repository.tasksWithReminders().forEach { schedule(it) }
    }

    private fun enqueue(taskId: Long, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(ReminderWorker.KEY_TASK_ID, taskId).build())
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(taskId: Long) = "reminder_$taskId"

    companion object {
        const val TAG = "task_reminder"
    }
}
