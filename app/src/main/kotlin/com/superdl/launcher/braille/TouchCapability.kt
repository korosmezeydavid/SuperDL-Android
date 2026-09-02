package com.superdl.launcher.braille

import android.content.Context

/**
 * HÁNY UJJAT ÉREZ EGYSZERRE EZ A TELEFON?
 *
 * MIÉRT MÉRJÜK, ÉS MIÉRT NEM KÉRDEZZÜK MEG A RENDSZERTŐL:
 *
 * Az Android képesség-jelzője (`FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND`)
 * csak annyit ígér, hogy **öt vagy több** pontot tud a kijelző. A hatpontos
 * Braille-hez viszont **hat** kell — vagyis a rendszer válasza pont abban a
 * kérdésben nem dönt, ami minket érdekel.
 *
 * Ezért nem kérdezünk, hanem MÉRÜNK: a felhasználó ráteszi a képernyőre
 * annyi ujját, amennyit tud, a program megszámolja, és a legnagyobb egyszerre
 * látott számot jegyzi meg. Ez ugyanaz az elv, amit az elem-ujjlenyomat
 * küszöbeinél is kimondtunk — mérni kell, nem tippelni.
 *
 * A MÉRÉS EREDMÉNYE AJÁNLÁS, NEM PARANCS. A felhasználó bármelyik módot
 * választhatja: lehet, hogy a telefonja tud hatot, de ő mégis a sínt szereti.
 * Az ő keze, az ő döntése.
 */
object TouchCapability {

    private const val PREFS = "superdl_braille"
    private const val KEY_MEASURED = "measured_points"
    private const val KEY_CHOSEN = "chosen_mode"

    /**
     * A HÁROM BEVITELI SZINT.
     *
     * A sorrend szándékos: a leggyorsabbtól a legkevesebb hardvert igénylőig.
     */
    enum class Mode(val label: String, val neededPoints: Int) {

        /** Hat ujj, egyszerre. A leggyorsabb, de a legritkábban elérhető. */
        FULL("Igazi Braille — hat ujj", 6),

        /**
         * Két menet, három ujjal: első koppintás a BAL oszlop (1-2-3),
         * második a JOBB (4-5-6). Három egyidejű érintést gyakorlatilag
         * minden érintőkijelző tud, a legolcsóbb hardver is.
         */
        TWO_PASS("Két menet — három ujj", 3),

        /**
         * A SÍN: egyetlen ujjal. Középen a sín, oldalra lépve ütöd le a
         * pontokat. Bármilyen telefonon megy.
         */
        RAIL("Sín — egy ujj", 1);

        fun speakDescription(): String = when (this) {
            FULL ->
                "Igazi Braille. Mind a hat pontot egyszerre ütöd le, hat ujjal. " +
                    "Ez a leggyorsabb, de hat egyidejű érintést kell tudnia a kijelzőnek."
            TWO_PASS ->
                "Két menet. Egy kéz három ujjával először a bal oszlopot ütöd le, " +
                    "aztán a jobbat. Két koppintás egy betű. " +
                    "Ehhez három egyidejű érintés is elég."
            RAIL ->
                "Sín. Egyetlen ujjal. Leteszed középre, fel-le választasz sort, " +
                    "balra vagy jobbra lépve ütöd le a pontot. " +
                    "Ez bármilyen telefonon működik."
        }
    }

    /** A mért érték, vagy 0, ha még nem mértünk. */
    fun measured(context: Context): Int =
        prefs(context).getInt(KEY_MEASURED, 0)

    fun saveMeasurement(context: Context, points: Int) {
        prefs(context).edit().putInt(KEY_MEASURED, points.coerceIn(0, 10)).apply()
    }

    fun hasMeasured(context: Context): Boolean = measured(context) > 0

    /**
     * Melyik mód AJÁNLOTT a mérés alapján.
     *
     * Mérés nélkül a SÍN az ajánlás, mert az mindenhol működik. Soha nem
     * ajánlunk olyat, amiről nem tudjuk, hogy a készülék bírja — egy nem
     * működő billentyűzet vakon nem kellemetlenség, hanem zsákutca.
     */
    fun recommended(context: Context): Mode = when (measured(context)) {
        0 -> Mode.RAIL
        in 6..10 -> Mode.FULL
        in 3..5 -> Mode.TWO_PASS
        else -> Mode.RAIL
    }

    // ── A FELHASZNÁLÓ VÁLASZTÁSA ────────────────────────────────────────────
    //
    // MIÉRT KELLETT KÜLÖN (2026-09-02, tesztelői visszajelzés):
    //
    // A kódban azt írtam, hogy „a mérés eredménye ajánlás, nem parancs, a
    // felhasználó bármelyik módot választhatja" — de a VÁLASZTÁSRA nem
    // csináltam menüpontot. A tesztelő egy ujjal próbálta a mérést, a program
    // a sínt ajánlotta, és onnantól nem volt hova nyúlni.
    //
    // Ez a fajta hiba a legalattomosabb: a szándék ott volt a kommentben, a
    // működés meg nem. Egy komment nem funkció.

    /** A felhasználó által VÁLASZTOTT mód, vagy null, ha a mérésre bízza. */
    fun chosenMode(context: Context): Mode? {
        val raw = prefs(context).getString(KEY_CHOSEN, "").orEmpty()
        if (raw.isBlank()) return null
        return runCatching { Mode.valueOf(raw) }.getOrNull()
    }

    /** Ami TÉNYLEG érvényes: a választás, vagy ha nincs, az ajánlás. */
    fun effectiveMode(context: Context): Mode = chosenMode(context) ?: recommended(context)

    /**
     * Körbeforgatás a menüpontból: automatikus, hat ujj, két menet, sín,
     * majd újra automatikus.
     *
     * AZ „AUTOMATIKUS" AZÉRT VAN BENNE, mert a legtöbb embernek az a jó —
     * de aki tudja, mit akar, az a mérést felülbírálhatja. Nem tiltjuk le a
     * telefonja képességénél többet igénylő módot sem: ha valaki a hat ujjas
     * módot választja egy olyan telefonon, ami csak hármat érez, azt inkább
     * PRÓBÁLJA KI és hallja a saját fülével, mint hogy mi mondjuk meg neki,
     * mire képes.
     */
    fun cycleMode(context: Context): Mode? {
        val next: Mode? = when (chosenMode(context)) {
            null -> Mode.FULL
            Mode.FULL -> Mode.TWO_PASS
            Mode.TWO_PASS -> Mode.RAIL
            Mode.RAIL -> null
        }
        prefs(context).edit().apply {
            if (next == null) remove(KEY_CHOSEN) else putString(KEY_CHOSEN, next.name)
        }.apply()
        return next
    }

    /** Amit a mód váltásakor kimondunk. */
    fun speakChoice(context: Context): String {
        val chosen = chosenMode(context)
        if (chosen == null) {
            val rec = recommended(context)
            return "Automatikus. A mérés szerint: ${rec.label}. ${rec.speakDescription()}"
        }
        val measured = measured(context)
        val warn = if (measured in 1 until chosen.neededPoints) {
            " FIGYELEM: ehhez ${chosen.neededPoints} ujj kellene, a telefonod viszont " +
                "$measured ujjat érzett a mérésnél. Lehet, hogy nem fog működni — " +
                "próbáld ki, és ha nem megy, válts vissza."
        } else {
            ""
        }
        return "${chosen.label}. ${chosen.speakDescription()}$warn"
    }

    /** Amit a mérés után kimond a program. */
    fun speakResult(context: Context): String {
        val points = measured(context)
        if (points <= 0) return "Még nem mértük meg, hány ujjat érez a telefon."
        val head = "A telefon $points ujjat érzett egyszerre."
        val advice = when {
            points >= 6 ->
                "Ez elég az igazi Braille-hez, mind a hat ponttal. " +
                    "A másik két mód is választható."
            points >= 3 ->
                "Ez a hatpontos Braille-hez kevés, de a két menetes módhoz elég: " +
                    "három ujjal, két koppintással írsz egy betűt."
            else ->
                "Ez csak a sínhez elég: egyetlen ujjal, középről oldalra lépve."
        }
        return "$head $advice"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
