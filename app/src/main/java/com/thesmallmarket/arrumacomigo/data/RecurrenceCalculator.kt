package com.thesmallmarket.arrumacomigo.data

import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Calcula a próxima data de vencimento de uma tarefa recorrente.
 * Para [Recurrence.WEEKLY] usa o bitmask de dias da semana (bit 0 = segunda ... bit 6 = domingo).
 */
object RecurrenceCalculator {

    /** Retorna a próxima data após [from], ou null para tarefas não recorrentes. */
    fun next(
        from: LocalDate,
        recurrence: Recurrence,
        interval: Int = 1,
        daysOfWeek: Int = 0,
    ): LocalDate? {
        val step = interval.coerceAtLeast(1)
        return when (recurrence) {
            Recurrence.NONE -> null
            Recurrence.DAILY -> from.plusDays(step.toLong())
            Recurrence.MONTHLY -> from.plusMonths(step.toLong())
            Recurrence.WEEKLY -> nextWeekly(from, step, daysOfWeek)
        }
    }

    private fun nextWeekly(from: LocalDate, interval: Int, daysOfWeek: Int): LocalDate {
        // Sem dias marcados: simplesmente soma semanas.
        if (daysOfWeek == 0) return from.plusWeeks(interval.toLong())
        // Procura o próximo dia marcado. Se ele cair na semana seguinte (cruzou dom→seg),
        // a semana ativa terminou e o intervalo entra na conta: quinzenal pula 1 semana extra.
        for (offset in 1..7) {
            val candidate = from.plusDays(offset.toLong())
            if (isDaySelected(candidate.dayOfWeek, daysOfWeek)) {
                val crossedWeek = candidate.dayOfWeek.value <= from.dayOfWeek.value
                return if (crossedWeek) candidate.plusWeeks(interval - 1L) else candidate
            }
        }
        return from.plusWeeks(interval.toLong())
    }

    /** Retorna a ocorrência anterior a [from], ou null para tarefas não recorrentes. */
    fun previous(
        from: LocalDate,
        recurrence: Recurrence,
        interval: Int = 1,
        daysOfWeek: Int = 0,
    ): LocalDate? {
        val step = interval.coerceAtLeast(1)
        return when (recurrence) {
            Recurrence.NONE -> null
            Recurrence.DAILY -> from.minusDays(step.toLong())
            Recurrence.MONTHLY -> from.minusMonths(step.toLong())
            Recurrence.WEEKLY -> previousWeekly(from, step, daysOfWeek)
        }
    }

    private fun previousWeekly(from: LocalDate, interval: Int, daysOfWeek: Int): LocalDate {
        if (daysOfWeek == 0) return from.minusWeeks(interval.toLong())
        // Espelho de nextWeekly: procura o dia marcado anterior; se cruzou seg→dom para trás,
        // saiu da semana ativa e o intervalo entra na conta.
        for (offset in 1..7) {
            val candidate = from.minusDays(offset.toLong())
            if (isDaySelected(candidate.dayOfWeek, daysOfWeek)) {
                val crossedWeek = candidate.dayOfWeek.value >= from.dayOfWeek.value
                return if (crossedWeek) candidate.minusWeeks(interval - 1L) else candidate
            }
        }
        return from.minusWeeks(interval.toLong())
    }

    /**
     * Fração [0..1] do período atual já decorrida: 0 logo após a ocorrência anterior,
     * ~cheia no dia do vencimento e 1 depois que ele passa. Alimenta a barra de progresso do cartão.
     */
    fun progressFraction(
        nextDueDate: LocalDate,
        recurrence: Recurrence,
        interval: Int,
        daysOfWeek: Int,
        now: java.time.LocalDateTime,
    ): Float {
        // ponytail: tarefa avulsa não tem período; janela fixa de 7 dias antes do vencimento.
        val start = previous(nextDueDate, recurrence, interval, daysOfWeek) ?: nextDueDate.minusDays(7)
        val startAt = start.atStartOfDay()
        val endAt = nextDueDate.plusDays(1).atStartOfDay()
        val total = java.time.Duration.between(startAt, endAt).toMinutes().toFloat()
        if (total <= 0f) return 1f
        val elapsed = java.time.Duration.between(startAt, now).toMinutes().toFloat()
        return (elapsed / total).coerceIn(0f, 1f)
    }

    /**
     * Primeira ocorrência de uma tarefa semanal a partir de [from]: o próprio [from] se o dia
     * dele está marcado (ou sem dias marcados), senão o próximo dia marcado.
     * Evita nascer "atrasada" uma tarefa criada na quarta para repetir às terças.
     */
    fun firstWeeklyOccurrence(from: LocalDate, daysOfWeek: Int): LocalDate {
        if (daysOfWeek == 0 || isDaySelected(from.dayOfWeek, daysOfWeek)) return from
        for (offset in 1..6) {
            val candidate = from.plusDays(offset.toLong())
            if (isDaySelected(candidate.dayOfWeek, daysOfWeek)) return candidate
        }
        return from
    }

    /**
     * A tarefa tem ocorrência em [date]? Usado na projeção do calendário semanal.
     * Em [today] as atrasadas acumulam; em dias futuros caminha o padrão de recorrência
     * a partir de [nextDueDate]; dias passados não projetam (só conclusões registradas).
     */
    fun occursOn(
        nextDueDate: LocalDate,
        recurrence: Recurrence,
        interval: Int,
        daysOfWeek: Int,
        date: LocalDate,
        today: LocalDate,
    ): Boolean {
        return when {
            date < today -> false
            date == today -> nextDueDate <= today
            else -> {
                // Atrasada: a ocorrência corrente "mora" em hoje; as próximas seguem o padrão a partir dela.
                var occurrence = maxOf(nextDueDate, today)
                while (occurrence < date) {
                    occurrence = next(occurrence, recurrence, interval, daysOfWeek) ?: return false
                }
                occurrence == date
            }
        }
    }

    fun isDaySelected(day: DayOfWeek, daysOfWeek: Int): Boolean {
        val bit = 1 shl (day.value - 1) // Monday(1) -> bit 0
        return daysOfWeek and bit != 0
    }

    fun toggleDay(daysOfWeek: Int, day: DayOfWeek): Int =
        daysOfWeek xor (1 shl (day.value - 1))
}
