package com.thesmallmarket.arrumacomigo.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Emite a data de hoje e re-emite ao virar o dia, para telas que ficam abertas
 * atravessando a meia-noite (o tablet da família fica sempre ligado).
 */
fun currentDateFlow(): Flow<LocalDate> = flow {
    while (true) {
        val today = LocalDate.now()
        emit(today)
        val untilMidnight = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay())
        delay(untilMidnight.toMillis().coerceAtLeast(1_000))
    }
}
