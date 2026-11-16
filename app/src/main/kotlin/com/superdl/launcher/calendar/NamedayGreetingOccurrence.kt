package com.superdl.launcher.calendar

/** Az alarm pillanatképét a jelenlegi naptárbejegyzéssel és valódi előfordulással egyezteti. */
object NamedayGreetingOccurrence {
    fun verified(
        definition: CalendarEvent?, instance: CalendarEvent?,
        alarmEventId: Long, alarmBeginMs: Long, alarmTitle: String
    ): CalendarEvent? {
        if (alarmEventId <= 0 || alarmBeginMs <= 0 || alarmTitle.isBlank()) return null
        if (definition == null || instance == null) return null
        if (definition.eventId != alarmEventId || instance.eventId != alarmEventId) return null
        if (definition.title != alarmTitle || instance.title != alarmTitle) return null
        if (definition.recurrence != CalendarRecurrence.YEARLY) return null
        if (instance.begin != alarmBeginMs || instance.end <= instance.begin) return null
        if (definition.begin > instance.begin) return null
        // Az Instances lekérdezés nem ad RRULE-t: azt a jelenlegi Events sorból vesszük.
        return definition.copy(begin = instance.begin, end = instance.end)
    }
}