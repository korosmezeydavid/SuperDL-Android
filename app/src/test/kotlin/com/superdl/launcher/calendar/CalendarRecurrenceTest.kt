package com.superdl.launcher.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarRecurrenceTest {
    @Test fun providerMayAddRruleFieldsToYearlyEvent() {
        assertEquals(CalendarRecurrence.YEARLY,
            CalendarRecurrence.fromRRule("FREQ=YEARLY;WKST=MO"))
        assertEquals(CalendarRecurrence.YEARLY,
            CalendarRecurrence.fromRRule("RRULE:FREQ=YEARLY;COUNT=10"))
    }
}
