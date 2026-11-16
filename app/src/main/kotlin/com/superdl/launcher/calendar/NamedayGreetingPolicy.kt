package com.superdl.launcher.calendar

import java.util.Calendar

/** A névnapi automatikus SMS fail-closed feltételei. Küldést ez az osztály nem végez. */
object NamedayGreetingPolicy {
    data class Greeting(
        val eventId: Long,
        val phone: String,
        val text: String,
        val enabled: Boolean,
        val lastAttemptDay: Int = 0
    )

    private val validPhone = Regex("""^\+?\d{7,15}$""")

    fun validConfiguration(greeting: Greeting): Boolean =
        greeting.eventId > 0 &&
            validPhone.matches(greeting.phone) &&
            greeting.text.isNotBlank() && greeting.text == greeting.text.trim() &&
            greeting.text.length <= 500

    fun dayKey(timeMs: Long): Int = Calendar.getInstance().apply { timeInMillis = timeMs }
        .let { it.get(Calendar.YEAR) * 10_000 + (it.get(Calendar.MONTH) + 1) * 100 + it.get(Calendar.DAY_OF_MONTH) }

    fun ready(greeting: Greeting?, event: CalendarEvent, nowMs: Long): Boolean {
        if (greeting == null || !greeting.enabled || !validConfiguration(greeting)) return false
        if (greeting.eventId != event.eventId || event.recurrence != CalendarRecurrence.YEARLY) return false
        if (!event.title.endsWith(" névnapja")) return false
        val today = dayKey(nowMs)
        val first = Calendar.getInstance().apply { timeInMillis = event.begin }
        val current = Calendar.getInstance().apply { timeInMillis = nowMs }
        return nowMs >= event.begin &&
            first.get(Calendar.MONTH) == current.get(Calendar.MONTH) &&
            first.get(Calendar.DAY_OF_MONTH) == current.get(Calendar.DAY_OF_MONTH) &&
            greeting.lastAttemptDay != today
    }
}
