package com.superdl.launcher.calendar

import java.util.Calendar

/** A külön SMS-beállítás csak saját, változatlan névnapi naptáreseményre érvényes. */
object NamedayOwnership {
    fun matches(entry: NamedayStore.Entry?, event: CalendarEvent?): Boolean {
        if (entry == null || event == null) return false
        if (entry.eventId != event.eventId || event.recurrence != CalendarRecurrence.YEARLY) return false
        if (event.title != "${entry.name} névnapja") return false
        val date = Calendar.getInstance().apply { timeInMillis = event.begin }
        return date.get(Calendar.MONTH) + 1 == entry.month &&
            date.get(Calendar.DAY_OF_MONTH) == entry.day
    }
}
