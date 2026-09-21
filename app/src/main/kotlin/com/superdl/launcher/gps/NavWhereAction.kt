package com.superdl.launcher.gps

/**
 * MIT LEHET KEZDENI AZZAL, HOGY MEGTUDTAD, HOL VAGY.
 *
 * ALPH DÖNTÉSE (2026-09-21): a helyzet megosztása **nem kap külön
 * menüpontot**. „Fölösleges ezért egy külön menüpontot létrehozni, ne
 * zsúfoljuk túl az alkalmazást." Igaza van: a megosztás nem önálló feladat,
 * hanem az, amit a MEGLÉVŐ eredménnyel csinálsz — ezért itt a helye, közvetlen
 * a mentés alatt.
 *
 * MIÉRT VAN BENNE A FELOLVASÁS ÉS AZ ÚJRAMÉRÉS: ezen a képernyőn eddig a
 * felfelé söprés ismételt, a lefelé söprés újramért. Most a fel-le a
 * válogatás — ha ezt a két dolgot nem tennénk a listába, csendben
 * elvesztenénk őket. Egy funkciót nem szabad úgy megszüntetni, hogy senki
 * nem mondja ki.
 */
enum class NavWhereAction(val label: String) {
    SAVE("Mentés egyéni helyként"),

    /**
     * SMS-ben, a program saját üzenetküldésével: címzettválasztás,
     * visszamondás, küldés-ellenőrzés — a megszokott úton.
     */
    SHARE_SMS("Helyzet megosztása üzenetben"),

    /**
     * Bármilyen más alkalmazással, a rendszer megosztás-ablakával.
     * Ugyanaz a szöveg megy, csak más csatornán.
     */
    SHARE_OTHER("Megosztás egyéb módon"),

    REPEAT("A hely újra felolvasása"),
    REMEASURE("Újramérés, pontosítás"),
    BACK("Vissza");

    companion object {
        val ALL: List<NavWhereAction> = entries.toList()
    }
}
