package com.superdl.launcher.calendar

import java.util.Calendar

/** Csak tényleges születési dátumot fogad; nem tolja el a február 29-et. */
object BirthdayHelper {
    private val hungarianMonths = listOf(
        "január", "február", "március", "április", "május", "június",
        "július", "augusztus", "szeptember", "október", "november", "december"
    )

    data class BirthDate(val year: Int, val month: Int, val day: Int) {
        fun spoken(): String = "$year. $month. $day."
    }

    fun parse(text: String, now: Calendar = Calendar.getInstance()): BirthDate? {
        val digits = text.filter { it.isDigit() }
        val numeric = Regex("""^\s*\d{4}[.\-/ ]*\d{2}[.\-/ ]*\d{2}\.?\s*$""")
        val parts = if (digits.length == 8 && numeric.matches(text)) {
            Triple(digits.substring(0, 4).toIntOrNull(),
                   digits.substring(4, 6).toIntOrNull(),
                   digits.substring(6, 8).toIntOrNull())
        } else {
            val spoken = Regex("""^\s*(\d{4})\.?\s+([\p{L}]+)\s+(\d{1,2})\.?\s*$""")
                .matchEntire(text.lowercase()) ?: return null
            val month = hungarianMonths.indexOf(spoken.groupValues[2]) + 1
            Triple(spoken.groupValues[1].toIntOrNull(), month,
                   spoken.groupValues[3].toIntOrNull())
        }
        val year = parts.first ?: return null
        val month = parts.second ?: return null
        val day = parts.third ?: return null
        if (year !in 1870..now.get(Calendar.YEAR)) return null
        val date = Calendar.getInstance(now.timeZone).apply {
            isLenient = false
            clear()
            set(year, month - 1, day, 0, 0)
        }
        val timestamp = try { date.timeInMillis } catch (_: IllegalArgumentException) { return null }
        if (timestamp > now.timeInMillis) return null
        return BirthDate(year, month, day)
    }

    fun nextOccurrence(birth: BirthDate, now: Calendar = Calendar.getInstance()): Long? {
        for (year in now.get(Calendar.YEAR)..now.get(Calendar.YEAR) + 8) {
            val date = Calendar.getInstance(now.timeZone).apply {
                isLenient = false
                clear()
                set(year, birth.month - 1, birth.day, 9, 0)
            }
            val timestamp = try { date.timeInMillis } catch (_: IllegalArgumentException) { continue }
            if (timestamp >= now.timeInMillis) return timestamp
        }
        return null
    }
}
