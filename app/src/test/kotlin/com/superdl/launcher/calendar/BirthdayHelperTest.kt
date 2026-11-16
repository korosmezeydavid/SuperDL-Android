package com.superdl.launcher.calendar

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BirthdayHelperTest {
    private fun date(year: Int, month: Int, day: Int) =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 12, 0)
        }

    @Test fun acceptsRealPastDateAndRejectsInvalidOrFuture() {
        val now = date(2026, 10, 26)
        assertEquals(BirthdayHelper.BirthDate(1985, 3, 2), BirthdayHelper.parse("1985-03-02", now))
        assertNull(BirthdayHelper.parse("2026-02-29", now))
        assertNull(BirthdayHelper.parse("2027-01-01", now))
        assertNull(BirthdayHelper.parse("not a date", now))
    }

    @Test fun leapDayReminderWaitsUntilNextLeapYear() {
        val next = BirthdayHelper.nextOccurrence(
            BirthdayHelper.BirthDate(2000, 2, 29), date(2027, 2, 28))!!
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = next }
        assertEquals(2028, cal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun acceptsSpokenHungarianMonthWithoutGuessingInvalidDates() {
        val now = date(2026, 10, 26)
        assertEquals(BirthdayHelper.BirthDate(1985, 3, 2),
            BirthdayHelper.parse("1985 március 2", now))
        assertEquals(BirthdayHelper.BirthDate(2000, 2, 29),
            BirthdayHelper.parse("2000. február 29.", now))
        assertNull(BirthdayHelper.parse("1985 március 32", now))
        assertNull(BirthdayHelper.parse("1985 zöld 2", now))
        assertNull(BirthdayHelper.parse("nem 1985-03-02", now))
    }
}
