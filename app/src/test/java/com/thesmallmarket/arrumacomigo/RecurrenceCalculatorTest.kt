package com.thesmallmarket.arrumacomigo

import com.thesmallmarket.arrumacomigo.data.RecurrenceCalculator
import com.thesmallmarket.arrumacomigo.data.entity.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceCalculatorTest {

    private val monday = LocalDate.of(2026, 6, 22) // segunda-feira

    @Test
    fun `none has no next date`() {
        assertNull(RecurrenceCalculator.next(monday, Recurrence.NONE))
    }

    @Test
    fun `daily advances by interval days`() {
        assertEquals(monday.plusDays(1), RecurrenceCalculator.next(monday, Recurrence.DAILY))
        assertEquals(monday.plusDays(3), RecurrenceCalculator.next(monday, Recurrence.DAILY, interval = 3))
    }

    @Test
    fun `monthly advances by interval months`() {
        assertEquals(monday.plusMonths(1), RecurrenceCalculator.next(monday, Recurrence.MONTHLY))
        assertEquals(monday.plusMonths(2), RecurrenceCalculator.next(monday, Recurrence.MONTHLY, interval = 2))
    }

    @Test
    fun `weekly without days adds a week`() {
        assertEquals(monday.plusWeeks(1), RecurrenceCalculator.next(monday, Recurrence.WEEKLY))
    }

    @Test
    fun `weekly finds next selected weekday`() {
        // Marca quarta-feira (bit 2).
        val wednesdayMask = RecurrenceCalculator.toggleDay(0, DayOfWeek.WEDNESDAY)
        val next = RecurrenceCalculator.next(monday, Recurrence.WEEKLY, daysOfWeek = wednesdayMask)
        assertEquals(DayOfWeek.WEDNESDAY, next?.dayOfWeek)
        assertEquals(monday.plusDays(2), next)
    }

    @Test
    fun `biweekly single day advances two weeks`() {
        // Quinzenal na segunda: concluiu na segunda, volta em 14 dias.
        val mondayMask = RecurrenceCalculator.toggleDay(0, DayOfWeek.MONDAY)
        val next = RecurrenceCalculator.next(monday, Recurrence.WEEKLY, interval = 2, daysOfWeek = mondayMask)
        assertEquals(monday.plusWeeks(2), next)
    }

    @Test
    fun `biweekly keeps remaining days of the active week`() {
        // Quinzenal em seg+qui: a partir da segunda, a quinta da MESMA semana ainda vale.
        var mask = RecurrenceCalculator.toggleDay(0, DayOfWeek.MONDAY)
        mask = RecurrenceCalculator.toggleDay(mask, DayOfWeek.THURSDAY)
        val next = RecurrenceCalculator.next(monday, Recurrence.WEEKLY, interval = 2, daysOfWeek = mask)
        assertEquals(monday.plusDays(3), next) // quinta da mesma semana
        // A partir da quinta, a próxima segunda pula 1 semana extra (quinzenal).
        val afterThursday = RecurrenceCalculator.next(monday.plusDays(3), Recurrence.WEEKLY, interval = 2, daysOfWeek = mask)
        assertEquals(monday.plusWeeks(2), afterThursday)
    }

    @Test
    fun `monthly with interval three advances a quarter`() {
        assertEquals(monday.plusMonths(3), RecurrenceCalculator.next(monday, Recurrence.MONTHLY, interval = 3))
    }

    @Test
    fun `weekly single day same weekday advances one week`() {
        // Semanal na segunda (interval 1): concluiu na segunda, volta em 7 dias.
        val mondayMask = RecurrenceCalculator.toggleDay(0, DayOfWeek.MONDAY)
        val next = RecurrenceCalculator.next(monday, Recurrence.WEEKLY, interval = 1, daysOfWeek = mondayMask)
        assertEquals(monday.plusWeeks(1), next)
    }

    @Test
    fun `first weekly occurrence keeps date when day matches or no days set`() {
        val mondayMask = RecurrenceCalculator.toggleDay(0, DayOfWeek.MONDAY)
        assertEquals(monday, RecurrenceCalculator.firstWeeklyOccurrence(monday, mondayMask))
        assertEquals(monday, RecurrenceCalculator.firstWeeklyOccurrence(monday, 0))
    }

    @Test
    fun `first weekly occurrence advances to the next selected day`() {
        // Criada na quarta (24/6) para repetir às terças: primeira = terça seguinte (30/6).
        val wednesday = monday.plusDays(2)
        val tuesdayMask = RecurrenceCalculator.toggleDay(0, DayOfWeek.TUESDAY)
        assertEquals(monday.plusDays(8), RecurrenceCalculator.firstWeeklyOccurrence(wednesday, tuesdayMask))
    }

    @Test
    fun `occursOn accumulates overdue only on today`() {
        val today = monday.plusDays(2) // quarta
        val overdue = monday // venceu segunda
        assertTrue(RecurrenceCalculator.occursOn(overdue, Recurrence.WEEKLY, 1, 0, today, today))
        assertTrue(!RecurrenceCalculator.occursOn(overdue, Recurrence.WEEKLY, 1, 0, today.minusDays(1), today))
    }

    @Test
    fun `occursOn projects future occurrences following the pattern`() {
        val today = monday
        val fridayMask = RecurrenceCalculator.toggleDay(0, DayOfWeek.FRIDAY)
        val dueFriday = monday.plusDays(4)
        // Semanal na sexta: ocorre na sexta desta semana, não na quinta.
        assertTrue(RecurrenceCalculator.occursOn(dueFriday, Recurrence.WEEKLY, 1, fridayMask, dueFriday, today))
        assertTrue(!RecurrenceCalculator.occursOn(dueFriday, Recurrence.WEEKLY, 1, fridayMask, monday.plusDays(3), today))
        // Diária vencida hoje aparece em todos os dias futuros.
        assertTrue(RecurrenceCalculator.occursOn(today, Recurrence.DAILY, 1, 0, today.plusDays(3), today))
        // Não recorrente só aparece na própria data.
        assertTrue(RecurrenceCalculator.occursOn(monday.plusDays(2), Recurrence.NONE, 1, 0, monday.plusDays(2), today))
        assertTrue(!RecurrenceCalculator.occursOn(monday.plusDays(2), Recurrence.NONE, 1, 0, monday.plusDays(3), today))
    }

    @Test
    fun `toggle and isDaySelected are consistent`() {
        var mask = 0
        mask = RecurrenceCalculator.toggleDay(mask, DayOfWeek.FRIDAY)
        assertTrue(RecurrenceCalculator.isDaySelected(DayOfWeek.FRIDAY, mask))
        mask = RecurrenceCalculator.toggleDay(mask, DayOfWeek.FRIDAY)
        assertTrue(!RecurrenceCalculator.isDaySelected(DayOfWeek.FRIDAY, mask))
    }
}
