package com.superdl.launcher.tts

import android.content.Context
import org.json.JSONObject

/**
 * KIEJTÉSI SZÓTÁR — hogy a program ne mondjon félre dolgokat.
 *
 * A PROBLÉMA:
 * A magyar beszédmotorok rendszeresen félremondanak rövidítéseket,
 * márkaneveket és idegen szavakat. A "kb." például "ká bé"-nek hangzik,
 * a "Ft" pedig "ef té"-nek — pedig "körülbelül" és "forint" kellene.
 *
 * A saját szövegeinket ki tudjuk javítani (a nagytakarítás során meg is
 * tettük). DE a KÉPERNYŐN MEGJELENŐ idegen szövegen semmilyen hatalmunk
 * nincs: azt egy másik alkalmazás írta.
 *
 * A MEGOLDÁS: kicseréljük a szavakat, MIELŐTT a beszédmotorhoz érnének.
 * A felhasználó pedig maga is bővítheti a szótárat — ha hall valamit
 * rosszul, megtanítja a programnak.
 *
 * MIÉRT ÉRTÉKES EZ KÜLÖNÖSEN:
 * A szótár MEGOSZTHATÓ. Ha valaki összeállítja a magyar banki és hivatali
 * szavak kiejtését, azt mindenki használhatja — egy ember munkája
 * mindenkinek segít.
 */
object PronunciationDictionary {

    private const val PREFS = "superdl_pronunciation"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_BUILTIN_OFF = "builtin_off"

    /**
     * BEÉPÍTETT SZABÁLYOK — a leggyakoribb magyar rövidítések.
     *
     * Ezek MINDEN felhasználónál működnek, beállítás nélkül. Aki nem kéri,
     * kikapcsolhatja őket (van, akit zavar, ha a program "okoskodik").
     *
     * A kulcs KISBETŰS, az összehasonlítás kis-nagybetűt nem néz.
     */
    private val builtIn: Map<String, String> = linkedMapOf(
        // Mértékegységek és pénz
        "ft" to "forint",
        "eft" to "ezer forint",
        "km/h" to "kilométer per óra",
        "km" to "kilométer",
        "kg" to "kilogramm",
        "dkg" to "dekagramm",
        "cm" to "centiméter",
        "mm" to "milliméter",
        // "m2" -> "négyzetméter" KIVÉVE, SZÁNDÉKOSAN.
        //
        // A HIBA, AMIT EZ JAVÍT (Péter, 2026-09-06): „a járatoknál azt
        // mondja, hogy metró négyzetméter". A tömegközlekedési szöveg
        // „Metró M2 járat" volt, és ez a szabály — szóhatárra illeszkedve,
        // kis-nagybetűt nem nézve — lecsapott a metróvonal jelére.
        //
        // Egy tömegközlekedést is kezelő programban az M1–M4 sokkal
        // gyakoribb, mint a lakásméret, és a kár is nagyobb: a „metró
        // négyzetméter" értelmezhetetlen, míg egy „ötven em kettő" legfeljebb
        // suta. A szótár nem lát környezetet (nem tudja, hogy szám áll-e
        // előtte), ezért itt nem lehet jól eldönteni — a metróvonalakat
        // pedig a TransitHelper.jaratMegnevezes() amúgy is magyarul mondja.
        "db" to "darab",
        "%" to "százalék",
        // Gyakori rövidítések
        "kb." to "körülbelül",
        "stb." to "és a többi",
        "pl." to "például",
        "ill." to "illetve",
        "vö." to "vesd össze",
        "ún." to "úgynevezett",
        "kb" to "körülbelül",
        // Címek és megszólítások
        "dr." to "doktor",
        "prof." to "professzor",
        "id." to "idősebb",
        "ifj." to "ifjabb",
        // Címek
        "u." to "utca",
        "krt." to "körút",
        "tér" to "tér",
        "hrsz." to "helyrajzi szám",
        "em." to "emelet",
        "fszt." to "földszint",
        // A program neve — hogy ne betűzze
        "superdl" to "szuper dé el",
        "super dl" to "szuper dé el"
    )

    private fun prefs(context: Context) =
        com.superdl.launcher.storage.SafePrefs.get(context.applicationContext, PREFS)

    // ── A SZÓTÁR ALKALMAZÁSA ────────────────────────────────────────────────

    /**
     * A szöveg átalakítása kimondás előtt.
     *
     * FONTOS RÉSZLET: SZÓHATÁRRA illesztünk. Enélkül a "db" csere elrontaná
     * a "dbase" szót, a "km" pedig a "kmeta"-t. A pont végű rövidítéseknél
     * (kb., stb.) a pont a minta része.
     *
     * SEBESSÉG: ez MINDEN kimondás előtt lefut, ezért gyorsnak kell lennie.
     * Ha nincs egyetlen szabály sem, azonnal visszaadjuk az eredetit.
     */
    fun apply(context: Context, text: String): String {
        if (text.isBlank()) return text
        val rules = activeRules(context)
        if (rules.isEmpty()) return text

        var result = text
        rules.forEach { (from, to) ->
            result = replaceWord(result, from, to)
        }
        return result
    }

    /**
     * Egyetlen szó cseréje, szóhatárra illesztve.
     * A magyar ékezetes betűket is betűnek tekintjük.
     */
    private fun replaceWord(text: String, from: String, to: String): String {
        if (from.isBlank()) return text
        return try {
            val quoted = Regex.escape(from)
            // Szóhatár: előtte és utána NE legyen betű vagy szám.
            val pattern = Regex(
                "(?<![\\p{L}\\p{N}])$quoted(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE
            )
            pattern.replace(text, Regex.escapeReplacement(to))
        } catch (_: Exception) {
            text
        }
    }

    /** A ténylegesen érvényes szabályok: beépített + saját. */
    private fun activeRules(context: Context): Map<String, String> {
        val custom = userEntries(context)
        if (!isBuiltInEnabled(context)) return custom
        // A SAJÁT szabály ERŐSEBB: ha valaki felülírja a beépítettet,
        // az övé érvényesül.
        return builtIn + custom
    }

    // ── SAJÁT BEJEGYZÉSEK ───────────────────────────────────────────────────

    fun userEntries(context: Context): Map<String, String> = try {
        val raw = prefs(context).getString(KEY_ENTRIES, null)
        if (raw.isNullOrBlank()) {
            emptyMap()
        } else {
            val json = JSONObject(raw)
            val map = linkedMapOf<String, String>()
            json.keys().forEach { key -> map[key] = json.optString(key) }
            map
        }
    } catch (_: Exception) {
        emptyMap()
    }

    /** Új szabály felvétele vagy meglévő módosítása. */
    fun addEntry(context: Context, written: String, spoken: String): Boolean {
        val from = written.trim()
        val to = spoken.trim()
        if (from.isBlank() || to.isBlank()) return false
        return try {
            val current = userEntries(context).toMutableMap()
            current[from.lowercase()] = to
            saveEntries(context, current)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun removeEntry(context: Context, written: String): Boolean = try {
        val current = userEntries(context).toMutableMap()
        val removed = current.remove(written.lowercase()) != null
        if (removed) saveEntries(context, current)
        removed
    } catch (_: Exception) {
        false
    }

    fun clearUserEntries(context: Context) {
        prefs(context).edit().remove(KEY_ENTRIES).apply()
    }

    private fun saveEntries(context: Context, entries: Map<String, String>) {
        val json = JSONObject()
        entries.forEach { (k, v) -> json.put(k, v) }
        prefs(context).edit().putString(KEY_ENTRIES, json.toString()).apply()
    }

    // ── BEÉPÍTETT SZABÁLYOK KI-BE ───────────────────────────────────────────

    fun isBuiltInEnabled(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_BUILTIN_OFF, false)

    fun toggleBuiltIn(context: Context): Boolean {
        val next = !isBuiltInEnabled(context)
        prefs(context).edit().putBoolean(KEY_BUILTIN_OFF, !next).apply()
        return next
    }

    // ── FELOLVASHATÓ ÁLLAPOT ────────────────────────────────────────────────

    fun speakStatus(context: Context): String {
        val custom = userEntries(context)
        val builtInState = if (isBuiltInEnabled(context)) {
            "A beépített szabályok bekapcsolva: ${builtIn.size} rövidítés."
        } else {
            "A beépített szabályok kikapcsolva."
        }
        val customState = if (custom.isEmpty()) {
            "Saját szabályod még nincs."
        } else {
            "Saját szabályaid: ${custom.size} darab."
        }
        return "$builtInState $customState"
    }

    /** A saját szabályok felolvasható listája. */
    fun speakUserEntries(context: Context): String {
        val custom = userEntries(context)
        if (custom.isEmpty()) {
            return "Nincs saját kiejtési szabályod. Az Új szabály ponttal " +
                "megtaníthatod a programnak, hogyan mondjon ki egy szót."
        }
        val list = custom.entries.joinToString(". ") { (from, to) ->
            "$from, kimondva: $to"
        }
        return "${custom.size} saját szabály. $list"
    }
}
