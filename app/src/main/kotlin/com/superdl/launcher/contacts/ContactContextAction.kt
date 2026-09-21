package com.superdl.launcher.contacts

enum class ContactContextAction(val label: String) {
    CALL("Hívás indítása"),
    SEND_SMS("SMS küldés"),

    /**
     * VISSZAHÍVANDÓRA TÉVE — kézzel, hívás nélkül.
     *
     * EZ VOLT A HIÁNY (Alph, 2026-09-21): a visszahívandók listájára eddig
     * CSAK a hívásnaplóból lehetett felkerülni. Márpedig a leggyakoribb eset
     * nem onnan indul: „tudom, hogy fel kell hívnom, de most kora reggel van,
     * nem akarom zavarni" — ilyenkor nincs is hívás, amire rá lehetne
     * söpörni. A névjegynél viszont ott a neve, és egy mozdulattal fel lehet
     * tenni a listára, hogy később ne felejtsd el.
     */
    REMIND_LATER("Emlékeztetés később — visszahívandókhoz"),
    RINGTONE("Egyéni csengőhang"),
    EDIT("Névjegy szerkesztése"),
    DELETE("Névjegy törlése");

    companion object {
        val browseActions: List<ContactContextAction> = entries.toList()

        /**
         * Amit EZZEL a névjeggyel lehet kezdeni. Akinek nincs száma, annak
         * nincs mit emlékeztetni — a nem működő menüpont vakon rosszabb,
         * mint a hiányzó.
         */
        fun forContact(hasPhone: Boolean): List<ContactContextAction> =
            if (hasPhone) browseActions else browseActions - REMIND_LATER
    }
}