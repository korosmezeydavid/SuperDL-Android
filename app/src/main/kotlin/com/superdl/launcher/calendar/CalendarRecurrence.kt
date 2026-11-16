package com.superdl.launcher.calendar

enum class CalendarRecurrence(
    val label: String,
    val rrule: String?
) {
    NONE("Nincs ismétlés", null),
    DAILY("Naponta", "FREQ=DAILY"),
    WEEKLY("Hetente", "FREQ=WEEKLY"),
    BIWEEKLY("Kéthetente", "FREQ=WEEKLY;INTERVAL=2"),
    MONTHLY("Havonta", "FREQ=MONTHLY"),
    YEARLY("Évente", "FREQ=YEARLY");

    fun speakSummary(): String = label

    companion object {
        val selectable: List<CalendarRecurrence> = entries.toList()

        fun fromRRule(rrule: String?): CalendarRecurrence {
            if (rrule.isNullOrBlank()) return NONE
            val fields = rrule.trim().removePrefix("RRULE:").split(';')
                .mapNotNull { field ->
                    val parts = field.trim().split('=', limit = 2)
                    if (parts.size == 2) parts[0].uppercase() to parts[1].uppercase() else null
                }.toMap()
            return when (fields["FREQ"]) {
                "YEARLY" -> YEARLY
                "MONTHLY" -> MONTHLY
                "DAILY" -> DAILY
                "WEEKLY" -> if (fields["INTERVAL"] == "2") BIWEEKLY else WEEKLY
                else -> NONE
            }
        }
    }
}
