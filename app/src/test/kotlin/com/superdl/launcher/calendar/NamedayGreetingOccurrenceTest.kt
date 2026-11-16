package com.superdl.launcher.calendar

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NamedayGreetingOccurrenceTest {
    private val first = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 26, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val later = Calendar.getInstance().apply {
        timeInMillis = first
        add(Calendar.YEAR, 1)
    }.timeInMillis
    private val definition = CalendarEvent(42, "Anna névnapja", first, first + 3_600_000,
        CalendarRecurrence.YEARLY)
    private val instance = CalendarEvent(42, "Anna névnapja", later, later + 3_600_000)

    @Test fun laterYearUsesCurrentInstanceAndCurrentRecurrence() {
        val verified = NamedayGreetingOccurrence.verified(
            definition, instance, 42, later, "Anna névnapja")!!
        assertEquals(later, verified.begin)
        assertEquals(CalendarRecurrence.YEARLY, verified.recurrence)
        val greeting = NamedayGreetingPolicy.Greeting(42, "+36301234567", "Üdv!", true)
        assertEquals(true, NamedayGreetingPolicy.ready(greeting, verified, later))
    }

    @Test fun staleChangedOrOneOffAlarmIsRejected() {
        assertNull(NamedayGreetingOccurrence.verified(definition, instance, 42,
            later - 60_000, "Anna névnapja"))
        assertNull(NamedayGreetingOccurrence.verified(definition, instance, 42,
            later, "Másik névnapja"))
        assertNull(NamedayGreetingOccurrence.verified(definition.copy(recurrence = CalendarRecurrence.NONE),
            instance, 42, later, "Anna névnapja"))
        assertNull(NamedayGreetingOccurrence.verified(null, instance, 42, later, "Anna névnapja"))
    }
}