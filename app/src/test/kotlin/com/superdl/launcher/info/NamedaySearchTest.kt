package com.superdl.launcher.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamedaySearchTest {
    @Test fun accentlessSearchFindsNameAndDate() {
        val matches = NamedayHelper.search("agnes")
        assertTrue(matches.any { it.name == "Ágnes" && it.month == 1 && it.day == 21 })
    }

    @Test fun leapDayOccursOnlyOnNextLeapYear() {
        val now = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2027, 1, 28, 12, 0)
        }
        val match = NamedayHelper.NamedayMatch(2, 29, "Antónia")
        val next = NamedayHelper.nextOccurrence(match, now)!!
        val date = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = next
        }
        assertEquals(2028, date.get(java.util.Calendar.YEAR))
        assertEquals(1, date.get(java.util.Calendar.MONTH))
        assertEquals(29, date.get(java.util.Calendar.DAY_OF_MONTH))
    }

    @Test fun exactNamePrecedesPrefixAndBlankFindsNothing() {
        val matches = NamedayHelper.search("Tamás")
        assertEquals("Tamás", matches.first().name)
        assertTrue(NamedayHelper.search(" ").isEmpty())
    }
}
