package com.superdl.launcher.alarm

data class AlarmEntry(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean = true
) {
    fun speakTime(): String {
        val hourWord = hour.toString().padStart(2, '0')
        val minuteWord = minute.toString().padStart(2, '0')
        return "$hourWord óra $minuteWord perc"
    }

    fun speakSummary(): String {
        val name = if (label.isBlank()) "Ébresztő" else label
        return "$name, ${speakTime()}"
    }
}