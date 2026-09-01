package com.superdl.launcher.screenreader

import android.os.Handler

/**
 * A HANGTÉRKÉP MEGSZÓLALTATÁSA — négy hangnyelv, hogy legyen miből választani.
 *
 * MIÉRT NÉGY, ÉS MIÉRT NEM EGY:
 * Ez az egyetlen funkció a tervben, aminél előre nem tudom megmondani, hogy jó
 * lesz-e. Nem logika kérdése, hanem hallásélményé — papíron nem dől el, és
 * nem is illik úgy tenni, mintha eldőlne. Ezért nem egy megoldás épült meg,
 * hanem négy, és a fül dönt. Amelyik nyer, az marad; a többi kikerül.
 *
 * A négy változat KÉT kérdést jár körül:
 *
 *   1. Szerkezetet halljunk, vagy leltárt?
 *      A Csoportos és a Számláló a szerkezetet mutatja, a Pásztázó a teljes
 *      tartalmat. Az elmélet szerint a szerkezet a jó válasz — de az elmélet
 *      itt épp az, amit mérni akarunk.
 *
 *   2. Segít-e a beszéd, vagy csak lassít?
 *      A Beszédes ugyanazt a szerkezetet mondja is. Lehet, hogy ettől lesz
 *      érthető; és lehet, hogy pont ettől lesz lassú, és akkor semmivel nem
 *      jobb a végiglépkedésnél.
 *
 * KÖZÖS SZABÁLY MIND A NÉGYBEN: a függőleges helyzet a hangmagasság, a
 * vízszintes a bal-jobb fül. Ugyanaz, mint a felderítésnél — ha máshogy
 * szólna, kétszer kellene megtanulni ugyanazt.
 */
object ScreenMapPlayer {

    const val STYLE_GROUPS = 0
    const val STYLE_COUNTED = 1
    const val STYLE_SWEEP = 2
    const val STYLE_SPOKEN = 3

    fun styleName(style: Int): String = when (style) {
        STYLE_GROUPS -> "Csoportos"
        STYLE_COUNTED -> "Számláló"
        STYLE_SWEEP -> "Pásztázó"
        else -> "Beszédes"
    }

    fun styleDescription(style: Int): String = when (style) {
        STYLE_GROUPS -> "Csoportonként egy hang. A leggyorsabb, csak a szerkezetet mutatja."
        STYLE_COUNTED -> "Csoportonként annyi hang, ahány elem. Hallod, mennyi van."
        STYLE_SWEEP -> "Minden elem külön, fentről lefelé pásztázva. A legrészletesebb."
        else -> "Csoporthangok, és utána egy rövid mondat is elhangzik."
    }

    /**
     * A hangszín mondja meg, MI van ott.
     *
     * A meglévő hangkészletből válogatunk, nem újat gyártunk: ezeket a
     * felhasználó már ismeri a napi navigálásból, tehát nem nulláról kell
     * megtanulnia a jelentésüket. A gomb hangja az, ami aktiváláskor szól;
     * a beírómezőé az, ami szövegmezőre lépéskor.
     */
    private fun soundFor(kind: ScreenMap.Kind): ScreenReaderSounds.Sound = when (kind) {
        ScreenMap.Kind.BUTTON -> ScreenReaderSounds.Sound.ACTIVATE
        ScreenMap.Kind.FIELD -> ScreenReaderSounds.Sound.FIELD
        ScreenMap.Kind.SWITCH -> ScreenReaderSounds.Sound.STATE_ON
        ScreenMap.Kind.LIST -> ScreenReaderSounds.Sound.SCROLL_DOWN
        ScreenMap.Kind.IMAGE -> ScreenReaderSounds.Sound.LONG_PRESS
        ScreenMap.Kind.TEXT -> ScreenReaderSounds.Sound.NEXT
    }

    /**
     * A térkép lejátszása.
     *
     * @param onSpeak a "Beszédes" változat ezen mondja el az összefoglalót;
     *        a többi nem hívja meg
     * @return hozzávetőleg hány ezredmásodpercig tart — hogy a hívó tudja,
     *         mikortól szólalhat meg megint bármi
     */
    fun play(
        result: ScreenMap.Result,
        style: Int,
        tempo: Int,
        sounds: ScreenReaderSounds?,
        handler: Handler,
        onSpeak: (String) -> Unit
    ): Long {
        if (sounds == null) return 0L
        if (result.isEmpty) {
            onSpeak("Üres képernyő.")
            return 0L
        }

        val f = tempoFactor(tempo)
        return when (style) {
            STYLE_COUNTED -> playCounted(result, sounds, handler, f)
            STYLE_SWEEP -> playSweep(result, sounds, handler, f)
            STYLE_SPOKEN -> playSpoken(result, sounds, handler, f, onSpeak)
            else -> playGroups(result, sounds, handler, f)
        }
    }

    fun tempoName(tempo: Int): String = when (tempo) {
        0 -> "Nyugodt"
        1 -> "Normál"
        else -> "Gyors"
    }

    /**
     * A tempó SZORZÓJA.
     *
     * Az első próba szava az volt, hogy "ledarálja" — és ez igaz mind a négy
     * hangnyelvre, tehát nem a nyelvvel van baj, hanem a sebességgel. A
     * nyugodt tempó kétszeres idő: nem "kicsit lassabb", hanem annyival
     * lassabb, hogy legyen idő KÜLÖN HALLANI a hangokat, ne egy sodrásként.
     */
    private fun tempoFactor(tempo: Int): Float = when (tempo) {
        0 -> 2.0f
        1 -> 1.4f
        else -> 1.0f
    }

    /** Ennyi idő telik el két sáv hangja közt (gyors tempón). */
    private const val BAND_STEP_MS = 210L

    /** A pásztázás sűrűbb: itt a folyamatos sodrás adja a képet. */
    private const val SWEEP_STEP_MS = 45L

    /** Egy sávon belül a számláló koppanások közti idő. */
    private const val COUNT_STEP_MS = 85L

    // ── 1. CSOPORTOS ───────────────────────────────────────────────────────
    //
    // Sávonként EGY hang. Ez a legtisztább fordítása annak, amit a látó ember
    // egy pillantással megkap: nem tudja, hány elem van, de tudja, hogy "fent
    // valami sáv, középen a tartalom, lent gombok".

    private fun playGroups(
        result: ScreenMap.Result,
        sounds: ScreenReaderSounds,
        handler: Handler,
        f: Float
    ): Long {
        var delay = 0L
        for (band in result.bands) {
            val d = delay
            handler.postDelayed({
                sounds.playMapped(soundFor(band.kind), band.x, band.y, volume = 0.65f)
            }, d)
            delay += (BAND_STEP_MS * f).toLong()
        }
        return delay
    }

    // ── 2. SZÁMLÁLÓ ────────────────────────────────────────────────────────
    //
    // Ugyanaz, de a sáv hangja annyiszor szól, ahány elem van benne — ötnél
    // megállunk. Ötnél több elemnél úgysem a pontos szám érdekes, hanem az,
    // hogy "sok"; és a hatodik koppanás után már senki nem számol.

    private fun playCounted(
        result: ScreenMap.Result,
        sounds: ScreenReaderSounds,
        handler: Handler,
        f: Float
    ): Long {
        var delay = 0L
        val countStep = (COUNT_STEP_MS * f).toLong()
        for (band in result.bands) {
            val repeats = band.count.coerceIn(1, 5)
            for (i in 0 until repeats) {
                val d = delay + i * countStep
                handler.postDelayed({
                    sounds.playMapped(soundFor(band.kind), band.x, band.y, volume = 0.6f)
                }, d)
            }
            delay += repeats * countStep + (BAND_STEP_MS * f).toLong()
        }
        return delay
    }

    // ── 3. PÁSZTÁZÓ ────────────────────────────────────────────────────────
    //
    // Minden elem külön, fentről lefelé, gyorsan. Nem hangonként hallgatod,
    // hanem SODRÁSKÉNT: a sűrűsödés és a ritkulás rajzolja ki a képernyőt.
    // Ez a leltár-változat — épp azt próbáljuk ki vele, hogy tényleg zaj-e.

    private fun playSweep(
        result: ScreenMap.Result,
        sounds: ScreenReaderSounds,
        handler: Handler,
        f: Float
    ): Long {
        var delay = 0L
        // Sokelemes képernyőn ritkítunk, hogy ne fulladjon bele önmagába:
        // a SoundPool egyszerre négy hangot szólaltat meg.
        val step = if (result.items.size > 40) 2 else 1
        val sweepStep = (SWEEP_STEP_MS * f).toLong()
        var played = 0
        for ((i, item) in result.items.withIndex()) {
            if (i % step != 0) continue
            val d = delay
            handler.postDelayed({
                sounds.playMapped(soundFor(item.kind), item.x, item.y, volume = 0.45f, pitchRange = 1.0f)
            }, d)
            delay += sweepStep
            played++
            if (played > 60) break
        }
        return delay
    }

    // ── 4. BESZÉDES ────────────────────────────────────────────────────────
    //
    // A csoporthangok, és utánuk egy rövid mondat. A hang megadja a helyet és
    // a ritmust, a mondat a jelentést. Lehet, hogy ez a kettő együtt az igazi;
    // és lehet, hogy a mondat után a hangokra már senki nem figyel.

    private fun playSpoken(
        result: ScreenMap.Result,
        sounds: ScreenReaderSounds,
        handler: Handler,
        f: Float,
        onSpeak: (String) -> Unit
    ): Long {
        val length = playGroups(result, sounds, handler, f)
        handler.postDelayed({ onSpeak(ScreenMap.speak(result)) }, length + 120L)
        return length + 120L
    }
}
