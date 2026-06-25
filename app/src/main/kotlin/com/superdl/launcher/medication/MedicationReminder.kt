package com.superdl.launcher.medication

import java.util.Calendar

enum class MedicationCycleType(val label: String) {
    DAILY("Naponta"),
    WEEKLY("Hetente"),
    CUSTOM("Egyéni napok");

    fun speakSummary(): String = label
}

data class MedicationReminder(
    val id: Int,
    val name: String,
    val hour: Int,
    val minute: Int,
    val cycleType: MedicationCycleType,
    val weekDays: Set<Int> = emptySet(),
    val enabled: Boolean = true
) {
    fun speakTime(): String {
        val hourWord = hour.toString().padStart(2, '0')
        val minuteWord = minute.toString().padStart(2, '0')
        return "$hourWord óra $minuteWord perc"
    }

    fun speakCycle(): String = when (cycleType) {
        MedicationCycleType.DAILY -> cycleType.label
        MedicationCycleType.WEEKLY,
        MedicationCycleType.CUSTOM -> {
            val days = weekDays.map { MedicationWeekdays.labelFor(it) }.sorted()
            if (days.isEmpty()) cycleType.label else "${cycleType.label}: ${days.joinToString(", ")}"
        }
    }

    fun speakSummary(): String = "$name, ${speakTime()}, ${speakCycle()}"

    fun shouldFireOn(dayOfWeek: Int): Boolean = when (cycleType) {
        MedicationCycleType.DAILY -> true
        MedicationCycleType.WEEKLY,
        MedicationCycleType.CUSTOM -> dayOfWeek in weekDays
    }

    fun shouldFireToday(): Boolean = shouldFireOn(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
}

data class WeekdayOption(
    val dayOfWeek: Int,
    val label: String
)

object MedicationWeekdays {
    val all: List<WeekdayOption> = listOf(
        WeekdayOption(Calendar.MONDAY, "Hétfő"),
        WeekdayOption(Calendar.TUESDAY, "Kedd"),
        WeekdayOption(Calendar.WEDNESDAY, "Szerda"),
        WeekdayOption(Calendar.THURSDAY, "Csütörtök"),
        WeekdayOption(Calendar.FRIDAY, "Péntek"),
        WeekdayOption(Calendar.SATURDAY, "Szombat"),
        WeekdayOption(Calendar.SUNDAY, "Vasárnap")
    )

    fun labelFor(dayOfWeek: Int): String =
        all.firstOrNull { it.dayOfWeek == dayOfWeek }?.label ?: "Ismeretlen nap"
}

data class MedicationIngestionLog(
    val reminderId: Int,
    val medicationName: String,
    val takenAtMillis: Long
)