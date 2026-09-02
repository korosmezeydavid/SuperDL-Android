package com.superdl.launcher.braille

import android.content.Context
import android.content.pm.ActivityInfo

/**
 * HOGYAN FEKSZENEK AZ UJJAID A KIJELZŐN.
 *
 * MIÉRT KELL EZ, ÉS MIÉRT NEM DÖNTHETEM EL ÉN:
 *
 * Az első változatban egyetlen elrendezést erőltettem — hat ujj egymás
 * mellett, álló telefonon. **Ez butaság volt: hat ujj állva egyszerűen nem
 * fér el egymás mellett.** A tesztelő azonnal észrevette.
 *
 * De a tanulság ennél nagyobb: NINCS EGYETLEN JÓ ELRENDEZÉS. Van, akinek a
 * cella alakja a természetes (két oszlop, ahogy a Braille-t olvassa), van,
 * akinek a zongora (hat ujj egy sorban, ahogy a Perkins-gépen írja). A kéz
 * mérete, a telefon mérete és a megszokás mind más-más felé húz.
 *
 * Ezért mind a kettő választható, és a tájolás is.
 */
object BrailleLayoutPrefs {

    private const val PREFS = "superdl_braille"
    private const val KEY_LAYOUT = "layout"
    private const val KEY_ORIENTATION = "orientation"

    /** Az ujjak elrendezése a kijelzőn. */
    enum class Layout(val label: String) {

        /**
         * CELLA: két oszlop, ahogy a Braille-cella néz ki.
         * Bal oldalon fentről lefelé 1, 2, 3 — jobb oldalon 4, 5, 6.
         * Ez fér el álló telefonon, és ez felel meg a cella alakjának.
         */
        CELL("Cella — két oszlop"),

        /**
         * ZONGORA: mind a hat ujj egy sorban, balról jobbra 1-től 6-ig.
         * Ez a Perkins-gép elrendezése. Fekvő telefonon kényelmes.
         */
        PIANO("Zongora — egy sorban");

        fun speakDescription(): String = when (this) {
            CELL ->
                "Cella elrendezés. A bal kezed három ujja a kijelző BAL oldalán, " +
                    "egymás alatt: fentről lefelé egyes, kettes, hármas pont. " +
                    "A jobb kezed három ujja a JOBB oldalon: négyes, ötös, hatos. " +
                    "Ez ugyanaz az alak, amit a Braille-cellából ismersz."
            PIANO ->
                "Zongora elrendezés. Mind a hat ujjad egy sorban fekszik, " +
                    "balról jobbra: bal gyűrűs, bal középső, bal mutató… " +
                    "vagyis egytől hatig. Ez a Perkins-gép elrendezése."
        }
    }

    /** A telefon tartása. */
    enum class Orientation(val label: String) {
        PORTRAIT("Álló"),
        LANDSCAPE("Fekvő");

        fun activityInfo(): Int = when (this) {
            PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        fun speakHold(): String = when (this) {
            PORTRAIT ->
                "Fogd a telefont ÁLLÓ helyzetben, a töltő csatlakozója lefelé."
            // A FEKVŐ TÁJOLÁSNÁL A TÖLTŐ JOBBRA ESIK. Az Android „landscape"
            // tájolása balra forgatja a képernyőt, vagyis a telefon alja —
            // és vele a csatlakozó — a JOBB oldalra kerül. Az első változat
            // „balra" mondott, és a tesztelő azonnal jelezte: az utasításnak
            // azt kell mondania, ami TÖRTÉNIK, nem azt, ami szépen hangzik.
            LANDSCAPE ->
                "Fordítsd a telefont FEKVŐ helyzetbe, a töltő csatlakozója JOBBRA."
        }
    }

    fun layout(context: Context): Layout {
        val raw = prefs(context).getString(KEY_LAYOUT, Layout.CELL.name) ?: Layout.CELL.name
        return runCatching { Layout.valueOf(raw) }.getOrDefault(Layout.CELL)
    }

    fun orientation(context: Context): Orientation {
        val raw = prefs(context).getString(KEY_ORIENTATION, Orientation.PORTRAIT.name)
            ?: Orientation.PORTRAIT.name
        return runCatching { Orientation.valueOf(raw) }.getOrDefault(Orientation.PORTRAIT)
    }

    /**
     * Az elrendezés beállítása A MÉRÉSBŐL — és ez SZÁNDÉKOSAN nem törli a
     * megtanult ujjhelyeket.
     *
     * A `cycleLayout` azért töröl, mert ott a felhasználó menüből vált, és a
     * régi pontok a másik elrendezésben értelmetlenek. Itt fordítva van: a
     * kalibrálás ÉPP MOST mérte meg a pontokat, és azokból ISMERTE FEL az
     * elrendezést. Ha itt törölnénk, a saját mérésünket dobnánk el.
     */
    fun setLayout(context: Context, layout: Layout) {
        prefs(context).edit().putString(KEY_LAYOUT, layout.name).apply()
    }

    /**
     * Váltás a következő elrendezésre.
     *
     * A VÁLTÁS ÉRVÉNYTELENÍTI A MEGTANULT UJJHELYEKET. Muszáj: a cella és a
     * zongora elrendezésben teljesen máshol vannak az ujjak, és a régi
     * pozíciókkal minden betű hibás lenne. Inkább kérjük meg újra a mérésre,
     * mint hogy csendben rosszat írjunk.
     */
    fun cycleLayout(context: Context): Layout {
        val next = when (layout(context)) {
            Layout.CELL -> Layout.PIANO
            Layout.PIANO -> Layout.CELL
        }
        prefs(context).edit().putString(KEY_LAYOUT, next.name).apply()
        BrailleAnchors.clear(context)
        return next
    }

    /** Ugyanígy: a tájolás váltása is új mérést kíván. */
    fun cycleOrientation(context: Context): Orientation {
        val next = when (orientation(context)) {
            Orientation.PORTRAIT -> Orientation.LANDSCAPE
            Orientation.LANDSCAPE -> Orientation.PORTRAIT
        }
        prefs(context).edit().putString(KEY_ORIENTATION, next.name).apply()
        BrailleAnchors.clear(context)
        return next
    }

    /** A javasolt párosítás, ha a felhasználó nem választott. */
    fun speakCurrent(context: Context): String =
        "${orientation(context).label} tájolás, ${layout(context).label}. " +
            "${orientation(context).speakHold()} ${layout(context).speakDescription()}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
