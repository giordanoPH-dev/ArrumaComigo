package com.thesmallmarket.arrumacomigo.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ptBR = Locale("pt", "BR")
private val dayMonth = DateTimeFormatter.ofPattern("d 'de' MMM", ptBR)

/** Rótulo amigável da data de vencimento (Atrasada, Hoje, Amanhã ou a data). */
fun dueLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    date.isBefore(today) -> "Atrasada"
    date == today -> "Hoje"
    date == today.plusDays(1) -> "Amanhã"
    else -> date.format(dayMonth)
}

fun isOverdue(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean = date.isBefore(today)

/** Abreviações dos dias da semana (seg..dom) para o seletor de recorrência semanal. */
val weekdayShortLabels: List<String> = java.time.DayOfWeek.entries.map {
    it.getDisplayName(TextStyle.SHORT, ptBR).replaceFirstChar { c -> c.uppercase() }.take(3)
}
