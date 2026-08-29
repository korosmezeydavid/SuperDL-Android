package com.superdl.launcher.screenreader

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.superdl.launcher.R

/**
 * A képernyőolvasó HANGVISSZAJELZÉSEI.
 *
 * Minden művelethez tartozik egy rövid, jellegzetes hang, hogy a felhasználó
 * FÜLLEL is kövesse, mi történik — ne csak a felolvasott szövegből.
 *
 * MIÉRT SOUNDPOOL: a rövid hangokat előre betölti a memóriába, és késleltetés
 * nélkül szólaltatja meg. A MediaPlayer minden lejátszásnál újra megnyitná a
 * fájlt, ami a gyors navigálásnál érezhetően késne.
 *
 * A hangok hossza szándékosan a művelet gyakoriságához igazodik: a legtöbbször
 * használt lépkedés kapja a legrövidebb koppanást (55 ms), az állapotváltozás
 * (be- és kikapcsolás) a hosszabb, jellegzetesebb hangot.
 */
class ScreenReaderSounds(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = mutableMapOf<Sound, Int>()

    /**
     * Hány hang töltődött be eddig. A betöltés ASZINKRON: a szolgáltatás
     * indulása után pár tizedmásodperccel készül el. Enélkül az első néhány
     * hang némán elmaradna, mert még nincs a memóriában.
     */
    private var loadedCount = 0
    private val ready: Boolean get() = loadedCount > 0

    enum class Sound {
        NEXT,           // következő elem
        PREV,           // előző elem
        EDGE,           // lista széle, nincs több
        ERROR,          // nem nyomható meg, hiba
        BACK,           // vissza
        SCROLL_UP,      // görgetés felfelé
        SCROLL_DOWN,    // görgetés lefelé
        FIRST,          // első elem
        LAST,           // utolsó elem
        FIELD,          // szövegmező kiválasztva
        LONG_PRESS,     // hosszan nyomás
        ACTIVATE,       // aktiválás, gombnyomás
        NOTIFICATIONS,  // értesítések
        RECENTS,        // legutóbbi alkalmazások
        HOME,           // kezdőképernyő
        ON,             // olvasó bekapcsol
        OFF,            // olvasó kikapcsol
        FANFARE,        // siker: jeles vizsga, óra teljesítése
        FAIL,           // bukás: lekonyuló hang
        PIP,            // vékony pittyegés a százalék-jelzéshez
        STATE_ON,       // kapcsoló/jelölőnégyzet BEKAPCSOLT állapota
        STATE_OFF       // kapcsoló/jelölőnégyzet KIKAPCSOLT állapota
    }

    init {
        try {
            ids[Sound.NEXT] = pool.load(context, R.raw.snd_sr_next, 1)
            ids[Sound.PREV] = pool.load(context, R.raw.snd_sr_prev, 1)
            ids[Sound.EDGE] = pool.load(context, R.raw.snd_sr_edge, 1)
            ids[Sound.ERROR] = pool.load(context, R.raw.snd_sr_error, 1)
            ids[Sound.BACK] = pool.load(context, R.raw.snd_sr_back, 1)
            ids[Sound.SCROLL_UP] = pool.load(context, R.raw.snd_sr_scroll_up, 1)
            ids[Sound.SCROLL_DOWN] = pool.load(context, R.raw.snd_sr_scroll_down, 1)
            ids[Sound.FIRST] = pool.load(context, R.raw.snd_sr_first, 1)
            ids[Sound.LAST] = pool.load(context, R.raw.snd_sr_last, 1)
            ids[Sound.FIELD] = pool.load(context, R.raw.snd_sr_field, 1)
            ids[Sound.LONG_PRESS] = pool.load(context, R.raw.snd_sr_longpress, 1)
            ids[Sound.ACTIVATE] = pool.load(context, R.raw.snd_sr_activate, 1)
            ids[Sound.NOTIFICATIONS] = pool.load(context, R.raw.snd_sr_notifications, 1)
            ids[Sound.RECENTS] = pool.load(context, R.raw.snd_sr_recents, 1)
            ids[Sound.HOME] = pool.load(context, R.raw.snd_sr_home, 1)
            ids[Sound.ON] = pool.load(context, R.raw.snd_sr_on, 1)
            ids[Sound.OFF] = pool.load(context, R.raw.snd_sr_off, 1)
            ids[Sound.FANFARE] = pool.load(context, R.raw.snd_sr_fanfare, 1)
            ids[Sound.FAIL] = pool.load(context, R.raw.snd_sr_fail, 1)
            ids[Sound.PIP] = pool.load(context, R.raw.snd_sr_pip, 1)
            ids[Sound.STATE_ON] = pool.load(context, R.raw.snd_sr_state_on, 1)
            ids[Sound.STATE_OFF] = pool.load(context, R.raw.snd_sr_state_off, 1)
            pool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) loadedCount++
            }
        } catch (e: Exception) {
            android.util.Log.w(ScreenReaderPrefs.TAG, "Hangok betoltese sikertelen: ${e.message}")
        }
    }

    /**
     * Egy visszajelző hang megszólaltatása.
     * Ha a betöltés még tart, csendben kihagyjuk — jobb egy hiányzó koppanás,
     * mint egy akadó vagy hibás lejátszás.
     */
    fun play(sound: Sound) {
        if (!ready) return
        val id = ids[sound] ?: return
        try {
            pool.play(id, 0.7f, 0.7f, 1, 0, 1f)
        } catch (_: Exception) {
        }
    }

    /**
     * SZÁZALÉKJELZŐ PITTYEGÉS — vékony, pici hang, ami a HELYZETET mutatja.
     *
     * A hangmagasság KÉT OKTÁVOT fog át a lista eleje és vége között:
     * 0 százaléknál egy oktávval mélyebb, 100 százaléknál egy oktávval
     * magasabb az alaphangnál. Ez jóval nagyobb különbség, mint egy szűk
     * tartomány — így tényleg ÉRZED, hol tartasz, nem csak sejted.
     *
     * @param position 0.0 = a lista eleje, 1.0 = a vége
     */
    fun playPercent(position: Float) {
        if (!ready) return
        val id = ids[Sound.PIP] ?: return
        // 0.5 = egy oktávval lejjebb, 2.0 = egy oktávval feljebb.
        val rate = 0.5f + position.coerceIn(0f, 1f) * 1.5f
        try {
            pool.play(id, 0.55f, 0.55f, 1, 0, rate.coerceIn(0.5f, 2.0f))
        } catch (_: Exception) {
        }
    }

    /**
     * TÉRBELI HELYZETJELZŐ — a képernyő KÉT irányát egyetlen hanggal.
     *
     * MIÉRT EZ A LEGFONTOSABB ÚJÍTÁS:
     * Egy képernyőolvasó meg tudja mondani, MI van az ujjad alatt. Azt viszont
     * nem, hogy HOL VAGY a képernyőn. Az ember elveszik: "valahol középen
     * tapogatok, de nem tudom, a felső vagy az alsó felén".
     *
     * Ez a hang mindkettőt megmondja, egyszerre:
     *   FÜGGŐLEGES helyzet -> HANGMAGASSÁG (fent magas, lent mély)
     *   VÍZSZINTES helyzet -> BAL-JOBB FÜL (a hang oda csúszik)
     *
     * Fülhallgatóval a felhasználó két-három pásztázás után MEGJEGYZI a
     * képernyő térképét, és legközelebb egyenesen odanyúl — ahogy egy látó
     * ember ránéz és odanyúl.
     *
     * @param x 0.0 = bal szél, 1.0 = jobb szél
     * @param y 0.0 = képernyő teteje, 1.0 = alja
     * @param volume alap hangerő (halkabb, ha csak kísérő jelzés)
     */
    fun playAtScreenPosition(x: Float, y: Float, volume: Float = 0.5f) {
        if (!ready) return
        val id = ids[Sound.PIP] ?: return
        val safeX = x.coerceIn(0f, 1f)
        val safeY = y.coerceIn(0f, 1f)

        // FÜGGŐLEGES: a képernyő TETEJE a magas hang. A számítás megfordítja,
        // mert a képernyő-koordinátában a 0 van fent.
        val rate = (2.0f - safeY * 1.5f).coerceIn(0.5f, 2.0f)

        // VÍZSZINTES: bal és jobb hangerő aránya.
        // Nem nullázzuk le egyik oldalt sem — a 0.15-ös alapérték megtartja a
        // hangot hallhatónak akkor is, ha valaki csak egy fülén hall, vagy
        // hangszóróból hallgatja.
        val leftVolume = ((1f - safeX) * 0.85f + 0.15f) * volume
        val rightVolume = (safeX * 0.85f + 0.15f) * volume

        try {
            pool.play(id, leftVolume, rightVolume, 1, 0, rate)
        } catch (_: Exception) {
        }
    }

    /**
     * ÜRES TERÜLET JELZÉSE felderítés közben.
     *
     * Nagyon halk, rövid kattanás. Azért kell, mert enélkül a felhasználó nem
     * tudja, hogy üres helyen jár-e, vagy a program hallgatott el. A
     * helyzetjelzés itt is működik: tudod, HOL van az üresség.
     */
    fun playEmptySpot(x: Float, y: Float) {
        playAtScreenPosition(x, y, volume = 0.12f)
    }

    /**
     * HELYZETJELZŐ HANG — a magasság mutatja, hol tartasz a listában.
     *
     * MIÉRT HASZNOS: egy hosszú listában szavakkal lassú lenne folyton közölni a
     * pozíciót ("37 / 210"), de a HANG MAGASSÁGÁT azonnal érzed. Mély hang a
     * lista elején, egyre magasabb a vége felé — így görgetés közben is tudod,
     * mennyi van még hátra, anélkül hogy bármit meg kellene hallgatnod.
     *
     * @param position 0.0 = a lista eleje, 1.0 = a vége
     */
    fun playAtPosition(sound: Sound, position: Float) {
        if (!ready) return
        val id = ids[sound] ?: return
        // A lejátszási sebesség egyben a hangmagasság is. A 0.75–1.6 tartomány
        // jól hallható különbséget ad, de nem torzítja el a hangot.
        val rate = (0.75f + position.coerceIn(0f, 1f) * 0.85f).coerceIn(0.5f, 2.0f)
        try {
            pool.play(id, 0.7f, 0.7f, 1, 0, rate)
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            pool.release()
        } catch (_: Exception) {
        }
    }
}
