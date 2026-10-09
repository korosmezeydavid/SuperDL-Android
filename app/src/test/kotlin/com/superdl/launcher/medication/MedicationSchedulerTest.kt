package com.superdl.launcher.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MedicationSchedulerTest {
    @Test
    fun markedDailyDoseMovesOnlyThatReminderToTomorrow() {
        val now = Calendar.getInstance()
        val dose = MedicationReminder(1, "Teszt", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), MedicationCycleType.DAILY)
        val skipped = MedicationScheduler.nextTriggerMillis(dose, skipToday = true)
        val nextDay = Calendar.getInstance().apply { timeInMillis = skipped }
        val expected = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        assertEquals(expected.get(Calendar.YEAR), nextDay.get(Calendar.YEAR))
        assertEquals(expected.get(Calendar.DAY_OF_YEAR), nextDay.get(Calendar.DAY_OF_YEAR))
        assertEquals(dose.hour, nextDay.get(Calendar.HOUR_OF_DAY))
        assertEquals(dose.minute, nextDay.get(Calendar.MINUTE))
    }

    @Test
    fun markedWeeklyDoseMovesToItsNextAllowedDay() {
        val today = Calendar.getInstance()
        val dose = MedicationReminder(
            2, "Heti", 23, 59, MedicationCycleType.WEEKLY,
            setOf(today.get(Calendar.DAY_OF_WEEK))
        )
        val skipped = Calendar.getInstance().apply {
            timeInMillis = MedicationScheduler.nextTriggerMillis(dose, skipToday = true)
        }
        assertEquals(today.get(Calendar.DAY_OF_WEEK), skipped.get(Calendar.DAY_OF_WEEK))
        assertTrue(skipped.timeInMillis > today.timeInMillis + 5L * 24 * 60 * 60 * 1000)
    }
}
