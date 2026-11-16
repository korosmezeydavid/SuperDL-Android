package com.superdl.launcher.calendar

import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test

class NamedayOwnershipTest {
    private val begin = Calendar.getInstance().apply {
        set(2026, Calendar.JANUARY, 21, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val entry = NamedayStore.Entry(42, "Ágnes", 1, 21)
    private val event = CalendarEvent(42, "Ágnes névnapja", begin, begin + 3_600_000L,
        CalendarRecurrence.YEARLY)

    @Test fun acceptsOnlyOwnMatchingAnnualEvent() {
        assertTrue(NamedayOwnership.matches(entry, event))
        assertFalse(NamedayOwnership.matches(null, event))
        assertFalse(NamedayOwnership.matches(entry, event.copy(eventId = 43)))
        assertFalse(NamedayOwnership.matches(entry, event.copy(title = "Más névnapja")))
        assertFalse(NamedayOwnership.matches(entry, event.copy(recurrence = CalendarRecurrence.NONE)))
        assertFalse(NamedayOwnership.matches(entry.copy(day = 22), event))
    }
}
