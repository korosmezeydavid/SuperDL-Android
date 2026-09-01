package com.superdl.launcher.gestures

import android.content.Context

/**
 * FELÜLET ELFORGATÁSA — a négy söprés jelentésének átrendezése.
 *
 * MIÉRT KELL: aki iPhone-ról jött, a listákban balra-jobbra pöccint, nem
 * fel-le. Ez nem butaság és nem szokás kérdése csak: évek beidegződése,
 * amit vakon a legnehezebb átállítani. Egyszerűbb a programot elforgatni,
 * mint az ujjat átszoktatni.
 *
 * EZ NEM MATEMATIKAI ELFORGATÁS, ÉS EZT FONTOS TUDNI. Egy valódi 90 fokos
 * forgatás a balra söprést a "következő"-re vinné. Itt viszont MINDKÉT
 * elforgatott módban a balra az ELŐZŐ és a jobbra a KÖVETKEZŐ — mert az ujj
 * ezt várja. Csak a fel-le szerepe cserélődik a két mód között.
 * Ez szándékos leképezés-táblázat, nem geometria. Ne "javítsd ki".
 *
 * | mozdulat | ALAP     | JOBBRA forgatva | BALRA forgatva |
 * |----------|----------|-----------------|----------------|
 * | balra    | vissza   | előző elem      | előző elem     |
 * | jobbra   | belépés  | következő elem  | következő elem |
 * | fel      | előző    | belépés         | kilépés        |
 * | le       | következő| kilépés         | belépés        |
 *
 * AZ EGÉSZ PROGRAMRA ÉRVÉNYES: a SuperDL 41 képernyője ugyanazon a
 * SwipeGestureListener-en megy keresztül, a képernyőolvasó pedig a
 * ScreenReaderService.onGesture-ön. Mindkettő ITT kérdezi meg a leképezést.
 * Így nincs olyan képernyő, ami kimaradna — egy kimaradt képernyő vakon
 * pontosan olyan rossz, mintha az egész nem működne.
 */
object GestureOrientation {

    private const val PREFS = "superdl_gestures"
    private const val KEY_MODE = "orientation"

    enum class Mode {
        /** A megszokott: fel-le lépked, jobbra belép, balra vissza. */
        NORMAL,

        /** Balra-jobbra lépked, FEL belép, LE kilép. */
        ROTATED_RIGHT,

        /** Balra-jobbra lépked, LE belép, FEL kilép. */
        ROTATED_LEFT;

        fun label(): String = when (this) {
            NORMAL -> "Alap kezelés"
            ROTATED_RIGHT -> "Elforgatva jobbra"
            ROTATED_LEFT -> "Elforgatva balra"
        }

        /**
         * A teljes szabály kimondva — váltáskor ezt hallja a felhasználó.
         *
         * A `literal()` NEM DÍSZ. Ez a mondat MÁR fizikai irányokban beszél,
         * tehát a szófordítón NEM szabad átmennie. Enélkül a program
         * lefordítja a saját tanítását: elforgatva jobbra a „balra söprés az
         * előző elem" mondatból „lefelé söprés az előző elem" lett — vagyis
         * pont az a mondat hazudott, aminek a helyes kezelést kellett volna
         * megtanítania. (Tesztelői hibajelentés, 2026-09-01.)
         */
        fun speakRule(): String = GestureWords.literal(
            when (this) {
                NORMAL ->
                    "Alap kezelés. Fel-le söprés a lépkedés, jobbra a belépés, " +
                        "balra a visszalépés."
                ROTATED_RIGHT ->
                    "Elforgatva jobbra. Balra söprés az előző elem, jobbra a következő, " +
                        "felfelé söprés a belépés, lefelé a visszalépés."
                ROTATED_LEFT ->
                    "Elforgatva balra. Balra söprés az előző elem, jobbra a következő, " +
                        "lefelé söprés a belépés, felfelé a visszalépés."
            }
        )
    }

    /** A négy LOGIKAI irány — amit a program ért, nem amit az ujj csinál. */
    enum class Logical { PREVIOUS, NEXT, ENTER, BACK }

    /** A négy FIZIKAI irány — amit az ujj csinál. */
    enum class Physical { UP, DOWN, LEFT, RIGHT }

    // A gyorsítótár azért kell, mert a leképezés a gesztus-kezelés forró
    // útvonalán fut, és olyan helyen is (zárképernyő, képernyőolvasó), ahol
    // nincs kényelmes Context. Ugyanaz a minta, mint a ContactPrefs-nél.
    @Volatile
    private var cache: Mode = Mode.NORMAL

    @Volatile
    private var warmed = false

    fun warm(context: Context) {
        try {
            val raw = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, Mode.NORMAL.name) ?: Mode.NORMAL.name
            cache = runCatching { Mode.valueOf(raw) }.getOrDefault(Mode.NORMAL)
            warmed = true
        } catch (_: Exception) {
            // Direct Boot vagy más hiba: marad az alap kezelés.
        }
    }

    fun mode(context: Context): Mode {
        if (!warmed) warm(context)
        return cache
    }

    /** Context nélküli olvasás. Bemelegítés előtt: alap kezelés. */
    fun modeFast(): Mode = cache

    fun setMode(context: Context, mode: Mode) {
        cache = mode
        warmed = true
        try {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE, mode.name).apply()
        } catch (_: Exception) {
        }
    }

    /** Körbeforgatás a menüpontból: alap, jobbra, balra, alap... */
    fun cycle(context: Context): Mode {
        val next = when (mode(context)) {
            Mode.NORMAL -> Mode.ROTATED_RIGHT
            Mode.ROTATED_RIGHT -> Mode.ROTATED_LEFT
            Mode.ROTATED_LEFT -> Mode.NORMAL
        }
        setMode(context, next)
        return next
    }

    /**
     * A LEKÉPEZÉS. Ez az egyetlen hely, ahol a táblázat le van írva.
     */
    fun logicalOf(physical: Physical): Logical = when (modeFast()) {
        Mode.NORMAL -> when (physical) {
            Physical.UP -> Logical.PREVIOUS
            Physical.DOWN -> Logical.NEXT
            Physical.RIGHT -> Logical.ENTER
            Physical.LEFT -> Logical.BACK
        }
        Mode.ROTATED_RIGHT -> when (physical) {
            Physical.LEFT -> Logical.PREVIOUS
            Physical.RIGHT -> Logical.NEXT
            Physical.UP -> Logical.ENTER
            Physical.DOWN -> Logical.BACK
        }
        Mode.ROTATED_LEFT -> when (physical) {
            Physical.LEFT -> Logical.PREVIOUS
            Physical.RIGHT -> Logical.NEXT
            Physical.DOWN -> Logical.ENTER
            Physical.UP -> Logical.BACK
        }
    }

    /**
     * A FORDÍTOTT IRÁNY: melyik fizikai mozdulat jelenti ezt a logikai
     * műveletet. A képernyőolvasónak kell, ahol a gesztus-azonosítót
     * kell visszafordítani.
     */
    fun physicalOf(logical: Logical): Physical = when (modeFast()) {
        Mode.NORMAL -> when (logical) {
            Logical.PREVIOUS -> Physical.UP
            Logical.NEXT -> Physical.DOWN
            Logical.ENTER -> Physical.RIGHT
            Logical.BACK -> Physical.LEFT
        }
        Mode.ROTATED_RIGHT -> when (logical) {
            Logical.PREVIOUS -> Physical.LEFT
            Logical.NEXT -> Physical.RIGHT
            Logical.ENTER -> Physical.UP
            Logical.BACK -> Physical.DOWN
        }
        Mode.ROTATED_LEFT -> when (logical) {
            Logical.PREVIOUS -> Physical.LEFT
            Logical.NEXT -> Physical.RIGHT
            Logical.ENTER -> Physical.DOWN
            Logical.BACK -> Physical.UP
        }
    }
}
