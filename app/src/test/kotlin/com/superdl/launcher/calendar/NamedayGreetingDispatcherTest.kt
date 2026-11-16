package com.superdl.launcher.calendar

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class NamedayGreetingDispatcherTest {
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 26, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val event = CalendarEvent(42, "Anna névnapja", now, now + 3_600_000,
        CalendarRecurrence.YEARLY)
    private val greeting = NamedayGreetingPolicy.Greeting(42, "+36301234567", "Boldog névnapot!", true)

    @Test fun onlyOriginalSameDayAlarmMayStartAutomaticSend() {
        assertEquals(true, NamedayGreetingDispatcher.originalAlarm(now, now, now))
        assertEquals(false, NamedayGreetingDispatcher.originalAlarm(now + 3_600_000, now, now + 3_600_000))
        assertEquals(false, NamedayGreetingDispatcher.originalAlarm(now, now, now - 1))
        val nextDay = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        assertEquals(false, NamedayGreetingDispatcher.originalAlarm(now, now, nextDay))
    }
    @Test fun sendsOnlyAfterReservationAndUsesExactStoredText() {
        val order = mutableListOf<String>()
        val result = NamedayGreetingDispatcher.dispatchVerified(event, now,
            reserve = { _, _ -> order.add("reserved"); greeting },
            send = { phone, text ->
                order.add("send")
                assertEquals(greeting.phone, phone)
                assertEquals(greeting.text, text)
                true
            })
        assertEquals(NamedayGreetingDispatcher.Result.ACCEPTED_BY_PHONE, result)
        assertEquals(listOf("reserved", "send"), order)
    }

    @Test fun failedReservationOrUncertainSendNeverRetries() {
        var sends = 0
        assertEquals(NamedayGreetingDispatcher.Result.NOT_ELIGIBLE,
            NamedayGreetingDispatcher.dispatchVerified(event, now,
                reserve = { _, _ -> null }, send = { _, _ -> sends++; true }))
        assertEquals(0, sends)
        assertEquals(NamedayGreetingDispatcher.Result.NOT_ELIGIBLE,
            NamedayGreetingDispatcher.dispatchVerified(event, now,
                reserve = { _, _ -> throw IllegalStateException("storage") },
                send = { _, _ -> sends++; true }))
        assertEquals(0, sends)
        assertEquals(NamedayGreetingDispatcher.Result.SEND_FAILED,
            NamedayGreetingDispatcher.dispatchVerified(event, now,
                reserve = { _, _ -> greeting }, send = { _, _ -> sends++; throw RuntimeException("network") }))
        assertEquals(1, sends)
        assertEquals(NamedayGreetingDispatcher.Result.NOT_ELIGIBLE,
            NamedayGreetingDispatcher.dispatchVerified(null, now,
                reserve = { _, _ -> greeting }, send = { _, _ -> sends++; true }))
        assertEquals(1, sends)
    }
}