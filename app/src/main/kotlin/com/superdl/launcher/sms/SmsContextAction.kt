package com.superdl.launcher.sms

enum class SmsContextAction(val label: String) {
    READ("Üzenet felolvasása"),
    REPLY("Válasz küldése"),
    FORWARD("Üzenet továbbítása"),
    /**
     * EMLÉKEZTETÉS KÉSŐBBRE. Olyan üzenet, amivel most nem tudsz foglalkozni:
     * a „Függő üzeneteim" listára kerül, és a megadott időpontban szól.
     */
    REMIND_LATER("Emlékeztetés később"),
    DELETE("Üzenet törlése");

    companion object {
        val all: List<SmsContextAction> = entries.toList()
    }
}