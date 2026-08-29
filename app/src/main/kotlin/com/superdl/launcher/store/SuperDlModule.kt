package com.superdl.launcher.store

/**
 * EGY TELEPÍTETT PROGRAM-MODUL.
 *
 * A modul ÖNÁLLÓ Android alkalmazás, de a manifestjében megjelöli magát
 * SuperDL-modulként. A SuperDL megkeresi, ellenőrzi, és a SAJÁT MENÜJÉBE teszi
 * — így a felhasználónak úgy tűnik, mintha a SuperDL része lenne.
 */
data class SuperDlModule(
    val packageName: String,
    val name: String,
    val description: String,
    val category: ModuleMenuCategory,
    val versionName: String,
    /** Igaz, ha a modult a SuperDL kulcsával írták alá (megbízható). */
    val trusted: Boolean
) {
    fun speakSummary(): String {
        val trust = if (trusted) "" else " Figyelem: nem ellenőrzött forrásból."
        return "$name. $description.$trust"
    }
}

/**
 * Hova kerüljön a modul a SuperDL menüjében.
 * A menuId a MEGLÉVŐ menücsoport azonosítója — oda szúrjuk be a bővítményt.
 */
enum class ModuleMenuCategory(val key: String, val label: String, val menuId: String) {
    TOOLS("eszkozok", "Eszközök", "tools"),
    GAMES("jatekok", "Játékok", "games"),
    MEDIA("media", "Zene és média", "media"),
    HEALTH("egeszseg", "Egészség és mozgás", "tools"),
    COMMUNITY("kozlekedes", "Közlekedés", "community"),
    OTHER("egyeb", "Egyéb", "tools");

    companion object {
        fun fromKey(key: String?): ModuleMenuCategory =
            entries.firstOrNull { it.key.equals(key, true) } ?: OTHER
    }
}
