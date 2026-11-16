package com.superdl.launcher.calendar

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamedayGreetingPolicyTest {
    private val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }.timeInMillis
    private val event = CalendarEvent(42, "Ágnes névnapja", today, today + 3_600_000, CalendarRecurrence.YEARLY)
    private val greeting = NamedayGreetingPolicy.Greeting(42, "+36301234567", "Boldog névnapot!", true)

    @Test fun explicitConsentAndMatchingOwnAnnualEventRequired() {
        assertTrue(NamedayGreetingPolicy.ready(greeting, event, today))
        assertFalse(NamedayGreetingPolicy.ready(greeting.copy(enabled = false), event, today))
        assertFalse(NamedayGreetingPolicy.ready(greeting.copy(eventId = 43), event, today))
        assertFalse(NamedayGreetingPolicy.ready(greeting, event.copy(title = "Találkozó"), today))
        assertFalse(NamedayGreetingPolicy.ready(greeting, event.copy(recurrence = CalendarRecurrence.NONE), today))
    }

    @Test fun yearlyGreetingRemainsDueInLaterYearsOnlyOnItsAnniversary() {
        val nextYear = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.YEAR, 1)
        }
        assertTrue(NamedayGreetingPolicy.ready(greeting, event, nextYear.timeInMillis))
        nextYear.add(Calendar.DAY_OF_MONTH, -1)
        assertFalse(NamedayGreetingPolicy.ready(greeting, event, nextYear.timeInMillis))
        val beforeFirst = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.HOUR_OF_DAY, -1)
        }
        assertFalse(NamedayGreetingPolicy.ready(greeting, event, beforeFirst.timeInMillis))
    }
    @Test fun malformedAddressAndRepeatAttemptAreRejected() {
        assertFalse(NamedayGreetingPolicy.ready(greeting.copy(phone = "123"), event, today))
        assertFalse(NamedayGreetingPolicy.ready(greeting.copy(text = " "), event, today))
        assertFalse(NamedayGreetingPolicy.ready(greeting.copy(lastAttemptDay = NamedayGreetingPolicy.dayKey(today)), event, today))
        assertFalse(NamedayGreetingPolicy.ready(greeting, event, today + 86_400_000))
    }
}
