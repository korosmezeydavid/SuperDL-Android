package com.superdl.launcher.calendar

import org.json.JSONObject

/**
 * MŰVELET EGY NAPTÁRI PROGRAMHOZ — „az emlékeztető ne csak szóljon, hanem
 * csináljon is valamit".
 *
 * Alph ötlete (2026-09-07): „beállítom, hogy este fél hat boltba menni, a
 * figyelmeztetés megerősítésével pedig meg is nyitja a bevásárlást. Vagy
 * megadott időben SMS-t küld."
 *
 * ── HÁROM ELV, AMI MEGHATÁROZZA AZ EGÉSZET ────────────────────────────────
 *
 * 1. **SOHA NEM FUT LE MAGÁTÓL.** A művelet CSAK a riasztásnál, a felhasználó
 *    kifejezett megerősítésére indul el. Egy magától SMS-t küldő naptár nem
 *    kényelmi funkció, hanem kiszámíthatatlan telefon — vakon különösen.
 *    Ezért nincs „csendes" végrehajtás, és nincs időzített automatika.
 *
 * 2. **A MŰVELET NEM A PROGRAM RÉSZE.** A naptári bejegyzés az Android
 *    naptárszolgáltatójából jön, oda nem tudunk saját mezőt tenni. Ezért a
 *    hozzárendelés MELLETTE él, a program azonosítójához kötve — ugyanúgy,
 *    ahogy a CalendarReminderStore tartja számon a teljesítetteket.
 *
 * 3. **MINDEN MŰVELET KIMONDHATÓ.** Ha egy művelet nem mondható el egy
 *    mondatban, akkor nem is szabad hozzárendelni: a felhasználónak a
 *    riasztáskor, félálomban is értenie kell, mire mond igent.
 */
sealed class CalendarAction {

    /** Ez hangzik el a riasztásnál, a megerősítés előtt. */
    abstract fun label(): String

    /** A tároláshoz. */
    abstract fun toJson(): JSONObject

    /**
     * A SuperDL egy menüpontjának megnyitása (bevásárlólista, diktafon, …).
     * A leggyakoribb eset, és a legártalmatlanabb: csak megnyit valamit.
     */
    data class OpenMenu(val actionName: String, val spoken: String) : CalendarAction() {
        override fun label(): String = "Megnyitás: $spoken"
        override fun toJson(): JSONObject = JSONObject().apply {
            put("tipus", "menu")
            put("muvelet", actionName)
            put("nev", spoken)
        }
    }

    /** Egy felvett műveletsor (makró) lejátszása. */
    data class RunTaskRoute(val routeId: String, val routeName: String) : CalendarAction() {
        override fun label(): String = "Műveletsor indítása: $routeName"
        override fun toJson(): JSONObject = JSONObject().apply {
            put("tipus", "muveletsor")
            put("id", routeId)
            put("nev", routeName)
        }
    }

    /**
     * SMS küldése előre megírt szöveggel.
     *
     * KÜLÖN MEGERŐSÍTÉS: ez az egyetlen művelet, ami KIFELÉ hat — elmegy
     * valakinek. Ezért a riasztásnál a címzettet ÉS a szöveget is kimondjuk,
     * mielőtt igent lehetne rá mondani.
     */
    data class SendSms(val number: String, val text: String, val who: String) : CalendarAction() {
        override fun label(): String =
            "SMS küldése neki: $who. A szöveg: $text"
        override fun toJson(): JSONObject = JSONObject().apply {
            put("tipus", "sms")
            put("szam", number)
            put("szoveg", text)
            put("kinek", who)
        }
    }

    /** Egy telefonra telepített alkalmazás megnyitása. */
    data class OpenApp(val packageName: String, val appName: String) : CalendarAction() {
        override fun label(): String = "Alkalmazás megnyitása: $appName"
        override fun toJson(): JSONObject = JSONObject().apply {
            put("tipus", "alkalmazas")
            put("csomag", packageName)
            put("nev", appName)
        }
    }

    companion object {
        fun fromJson(o: JSONObject): CalendarAction? = try {
            when (o.optString("tipus")) {
                "menu" -> OpenMenu(
                    actionName = o.optString("muvelet"),
                    spoken = o.optString("nev", "menüpont")
                ).takeIf { it.actionName.isNotBlank() }

                "muveletsor" -> RunTaskRoute(
                    routeId = o.optString("id"),
                    routeName = o.optString("nev", "műveletsor")
                ).takeIf { it.routeId.isNotBlank() }

                "sms" -> SendSms(
                    number = o.optString("szam"),
                    text = o.optString("szoveg"),
                    who = o.optString("kinek", o.optString("szam"))
                ).takeIf { it.number.isNotBlank() && it.text.isNotBlank() }

                "alkalmazas" -> OpenApp(
                    packageName = o.optString("csomag"),
                    appName = o.optString("nev", "alkalmazás")
                ).takeIf { it.packageName.isNotBlank() }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
