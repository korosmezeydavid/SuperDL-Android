package com.superdl.launcher.calendar

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamedayGreetingStoreTest {
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 26, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val event = CalendarEvent(42, "Anna névnapja", now, now + 3_600_000,
        CalendarRecurrence.YEARLY)
    private val greeting = NamedayGreetingPolicy.Greeting(
        42, "+36301234567", "Boldog névnapot, Anna!", true)

    @Test fun preservesExactRecipientMessageAndAttemptDay() {
        val record = greeting.copy(text = "Áldott névnapot, Anna!", lastAttemptDay = 20261026)
        assertEquals(record, NamedayGreetingStore.decode(NamedayGreetingStore.encode(record)))
    }

    @Test fun rejectsMalformedOrEnabledIncompleteRecords() {
        assertNull(NamedayGreetingStore.decode("broken"))
        assertNull(NamedayGreetingStore.decode("-1,QQ,QQ,1,0"))
        assertNull(NamedayGreetingStore.decode("42,%%%,QQ,1,0"))
        assertNull(NamedayGreetingStore.decode(NamedayGreetingStore.encode(
            greeting.copy(phone = "123"))))
    }

    @Test fun reserveMarksOnlyMatchingGreetingAndBlocksRepeat() {
        val other = greeting.copy(eventId = 99)
        val result = NamedayGreetingStore.reservedEntries(listOf(greeting, other), event, now)!!
        assertEquals(NamedayGreetingPolicy.dayKey(now), result.second.lastAttemptDay)
        assertEquals(other, result.first[1])
        assertFalse(NamedayGreetingPolicy.ready(result.second, event, now))
        assertNull(NamedayGreetingStore.reservedEntries(result.first, event, now))
        assertNull(NamedayGreetingStore.reservedEntries(listOf(greeting.copy(enabled = false)), event, now))
        assertTrue(result.second.enabled)
    }
}