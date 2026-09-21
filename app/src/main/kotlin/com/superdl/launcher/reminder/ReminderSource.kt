package com.superdl.launcher.reminder

/**
 * HONNAN JÖJJÖN A SZÁM, amikor KÉZZEL veszel fel visszahívandót.
 *
 * EZ ALPH ÖTLETE (2026-09-21), és jobb, mint amit először csináltam.
 * Először csak a névjegy műveletei közé tettem be az „Emlékeztetés később"
 * pontot. Az működik — de ahhoz ELŐRE tudni kell, hogy ott van. Aki
 * visszahívandót akar felvenni, az a Visszahívandókhoz megy, mert ott jár
 * az esze. Az ajtót oda kell tenni, ahol az ember áll.
 *
 * Ezért a felvétel három helyről indul, és mindhárom ugyanide vezet:
 *  - Hívások → Visszahívandó felvétele (saját menüpont),
 *  - a Visszahívandók listájának VÉGÉN egy „Új felvétele" sor,
 *  - és ha a lista üres, egyenesen ide érkezel.
 */
enum class ReminderSource(val label: String) {
    /**
     * A névjegyzéken át. Nem külön válogatót nyitunk, hanem a MEGLÉVŐ
     * névjegyzéket — ott a betűindex, a keresés, minden megszokott. A
     * névjegy műveletei közül az „Emlékeztetés később" hozza ide vissza.
     */
    CONTACT("Névjegyek közül"),

    /** Olyan szám, ami nincs a névjegyek között: bemondod. */
    DICTATE("Telefonszám bemondása"),

    BACK("Vissza");

    companion object {
        val ALL: List<ReminderSource> = entries.toList()

        const val INTRO: String =
            "Visszahívandó felvétele. Fel-le söpréssel választasz, jobbra indítod."
    }
}
