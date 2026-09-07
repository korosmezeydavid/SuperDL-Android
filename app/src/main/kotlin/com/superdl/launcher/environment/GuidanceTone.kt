package com.superdl.launcher.environment

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * HANGOS RÁVEZETÉS — a „melegebb-hidegebb" játék, hanggal.
 *
 * MIÉRT KELL: a „Mi van előttem?" eddig LEÍRTA a jelenetet. Egy leírás
 * tájékoztat, de nem visz oda. Aki egy széket keres, annak nem az a kérdés,
 * hogy „van-e szék", hanem hogy „merre forduljak, hogy elérjem". Erre a
 * beszéd túl lassú: mire kimondjuk, hogy „balra", a telefon már máshol jár.
 *
 * A megoldás egy folyamatos sípolás, aminek NEM a hangmagassága, hanem a
 * SŰRŰSÉGE hordozza az információt: minél közelebb van a keresett tárgy a
 * kép közepéhez, annál sűrűbben szól. A fül a ritmusváltozást gyorsabban és
 * pontosabban követi, mint bármilyen kimondott szót — ez ugyanaz az elv,
 * amit a fémdetektorok és a parkolóradarok használnak.
 *
 * Három állapota van:
 *
 * - **keresés** (`update(null)`): ritka, halk koppanás. Azt üzeni, hogy
 *   figyelünk, de még nincs meg. Csend helyett azért kell, mert a néma
 *   telefonról nem lehet megmondani, hogy dolgozik-e még.
 * - **rávezetés** (`update(távolság)`): a sípok sűrűsége a középtől mért
 *   távolsággal nő.
 * - **megvan** (`success()`): kettős nyugtázó hang, és a sípolás elhallgat.
 *   A csend itt maga az információ.
 *
 * A hangerő szándékosan mérsékelt: a sípolás a beszéd ALATT is megy, és nem
 * nyomhatja el azt. Ezt a leckét a zárképernyős hangoknál tanultuk meg —
 * két egymásra vágott hang közül a fontosabbik veszít.
 */
class GuidanceTone(private val volumePercent: Int = 55) {

    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null

    // A képkocka-elemzés HÁTTÉRSZÁLON hívja az update()-et, az ütemezés
    // viszont a fő szálon fut. A két mező ezért volatile — enélkül a
    // háttérszál írása tetszőlegesen sokáig láthatatlan maradhatna, és a
    // sípolás egy régi távolságot ismételgetne.
    @Volatile
    private var running = false

    /** null = nem látjuk a célt; különben a középtől mért távolság (0..~0,7). */
    @Volatile
    private var centerDistance: Float? = null

    fun start() {
        if (running) return
        running = true
        // Csak egyszer hozzuk létre: a start() újraindításkor is meghívódik,
        // és egy el nem engedett ToneGenerator lefoglalva tartja a hangsávot.
        if (tone == null) {
            tone = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, volumePercent)
            } catch (_: Exception) {
                null
            }
        }
        scheduleNext()
    }

    fun update(centerDistance: Float?) {
        this.centerDistance = centerDistance
    }

    /** Megvan: kettős nyugtázás, és a sípolás abbamarad. */
    fun success() {
        running = false
        handler.removeCallbacksAndMessages(null)
        val t = tone ?: return
        try {
            t.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            handler.postDelayed({
                try {
                    t.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                } catch (_: Exception) {
                }
            }, 180L)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    fun release() {
        stop()
        try {
            tone?.release()
        } catch (_: Exception) {
        }
        tone = null
    }

    private fun scheduleNext() {
        if (!running) return
        val tavolsag = centerDistance
        val kesleltetes = if (tavolsag == null) {
            SEARCH_INTERVAL_MS
        } else {
            // 0 → a leggyorsabb ütem, MAX_DISTANCE fölött a leglassabb.
            val arany = (tavolsag / MAX_DISTANCE).coerceIn(0f, 1f)
            (MIN_INTERVAL_MS + arany * (MAX_INTERVAL_MS - MIN_INTERVAL_MS)).toLong()
        }
        handler.postDelayed({
            playOne(tavolsag)
            scheduleNext()
        }, kesleltetes)
    }

    private fun playOne(tavolsag: Float?) {
        if (!running) return
        val t = tone ?: return
        try {
            if (tavolsag == null) {
                // Keresés: halkabb, tompább koppanás — jelen van, de nem tolakszik.
                t.startTone(ToneGenerator.TONE_PROP_PROMPT, 45)
            } else {
                t.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        /** Ennél távolabb már nem gyorsítunk tovább — a kép sarka nagyjából 0,7. */
        private const val MAX_DISTANCE = 0.55f
        private const val MIN_INTERVAL_MS = 110f
        private const val MAX_INTERVAL_MS = 850f
        private const val SEARCH_INTERVAL_MS = 1400L
    }
}
