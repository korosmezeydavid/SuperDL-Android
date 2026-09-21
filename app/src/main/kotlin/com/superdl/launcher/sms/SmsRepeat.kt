package com.superdl.launcher.sms

import java.util.Calendar

/**
 * MILYEN GYAKRAN MENJEN AZ IDŐZÍTETT ÜZENET.
 *
 * ALPH KÉRÉSE (2026-09-21): „időzített sms nem csak egy, hanem előre
 * beállítható akár napi heti havi ismétlődéssel".
 *
 * MIÉRT NINCS HOZZÁ KÜLÖN KÉPERNYŐ: az időpontot úgyis be kell mondani.
 * Aki azt mondja, hogy „minden nap reggel hétkor", az egyetlen mondatban
 * megmondta a gyakoriságot ÉS az időt is. Egy külön kérdés ide csak egy
 * felesleges lépés lenne — és minden felesleges lépés egy söprés, amit
 * vakon kell megtalálni.
 *
 * A HAVI ISMÉTLÉS CSAPDÁJA: ha valaki 31-ére állít be havi üzenetet,
 * februárban nincs 31-e. A naptár ilyenkor magától a hónap utolsó napjára
 * csúsztat — ez az, amit az ember is ért alatta.
 */
enum class SmsRepeat(val label: String, val speak: String) {

    NONE("Egyszeri", "egyszer"),
    DAILY("Naponta", "naponta"),
    WEEKLY("Hetente", "hetente"),
    MONTHLY("Havonta", "havonta");

    /** A következő alkalom. Egyszeri üzenetnél nincs ilyen. */
    fun next(from: Long): Long? {
        if (this == NONE) return null
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        when (this) {
            DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            MONTHLY -> {
                // A NAPOT MEGJEGYEZZÜK, mert a naptár hozzáadás után
                // levágná: január 31 plusz egy hónap február 28, és onnantól
                // minden hónap 28-a lenne. Aki 31-ére állította, az minden
                // hónap végét érti alatta.
                val day = cal.get(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, 1)
                val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(day, max))
            }
            NONE -> return null
        }
        return cal.timeInMillis
    }

    /** A következő alkalom, ami MÁR A JÖVŐBEN van. */
    fun nextAfter(from: Long, now: Long = System.currentTimeMillis()): Long? {
        var at = next(from) ?: return null
        var guard = 0
        while (at <= now && guard++ < 400) {
            at = next(at) ?: return null
        }
        return at
    }

    companion object {
        /**
         * Mit hallottunk ki a bemondott mondatból.
         *
         * Szándékosan nagyvonalú: „mindennap", „minden nap", „naponta",
         * „naponként" mind ugyanazt jelenti. Aki beszél, nem szótárból
         * válogat.
         */
        fun detect(normalized: String): SmsRepeat = when {
            normalized.contains("havonta") ||
                normalized.contains("havonkent") ||
                normalized.contains("minden honap") -> MONTHLY

            normalized.contains("hetente") ||
                normalized.contains("hetenkent") ||
                normalized.contains("minden heten") ||
                normalized.contains("minden het") -> WEEKLY

            normalized.contains("naponta") ||
                normalized.contains("naponkent") ||
                normalized.contains("mindennap") ||
                normalized.contains("minden nap") -> DAILY

            else -> NONE
        }

        fun fromName(raw: String?): SmsRepeat =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}
