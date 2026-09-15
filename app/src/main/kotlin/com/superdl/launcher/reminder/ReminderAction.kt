package com.superdl.launcher.reminder

/**
 * MIT LEHET KEZDENI EGY FÜGGŐ EMLÉKEZTETŐVEL.
 *
 * AZ IDŐZÍTETT SMS ITT MŰVELET, NEM KÉRDÉS. Először úgy terveztük, hogy az
 * emlékeztető beállításakor rákérdez — de a huszadik kérdés után az már
 * bosszantó, és a bosszantó kérdésre az ember gépiesen nemet mond. Így
 * viszont ott van, ha kell, és nincs útban, ha nem.
 */
enum class ReminderAction(val label: String) {
    CALL_NOW("Hívás most"),
    NEW_TIME("Új időpont"),
    SCHEDULE_SMS("Időzített SMS erre az időpontra"),
    DELETE("Emlékeztető törlése"),
    BACK("Vissza");

    companion object {
        fun forEntry(entry: LaterReminder): List<ReminderAction> {
            val actions = mutableListOf<ReminderAction>()
            if (entry.number.isNotBlank()) actions.add(CALL_NOW)
            actions.add(NEW_TIME)
            if (entry.number.isNotBlank()) actions.add(SCHEDULE_SMS)
            actions.add(DELETE)
            actions.add(BACK)
            return actions
        }
    }
}
