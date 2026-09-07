package com.superdl.launcher.environment

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * HANGOS RÁVEZETÉS — a „melegebb-hidegebb" játék, hanggal.
 *
 * MIÉRT KELL: a „Mi van előttem?" LEÍRJA a jelenetet. Egy leírás tájékoztat,
 * de nem visz oda. Aki egy széket keres, annak nem az a kérdés, hogy „van-e
 * szék", hanem hogy „merre fordítsam a telefont". Erre a beszéd túl lassú:
 * mire kimondjuk, hogy „balra", a telefon már máshol jár.
 *
 * A hang NEM a hangmagasságával, hanem a SŰRŰSÉGÉVEL hordozza az
 * információt: minél közelebb a keresett dolog a kép közepéhez, annál
 * sűrűbben kattan. A fül a ritmusváltozást gyorsabban és pontosabban
 * követi, mint bármelyik kimondott szót — ez a fémdetektorok és a
 * parkolóradarok elve.
 *
 * ---
 *
 * 2026-09-07, ALPH VISSZAJELZÉSE AZ ELSŐ VÁLTOZATRÓL. Két hibát talált,
 * mindkettő a hangtervezésben volt, nem a logikában:
 *
 * 1. „nagyon pici vékony tick sound kell" — az első változat a rendszer
 *    `ToneGenerator`-át használta, ami 40 ezredmásodperces SÍPOT ad. Egy síp
 *    hangmagassága és lecsengése van; sűrűn ismételve összemosódik egy
 *    zümmögéssé, és elnyomja a beszédet. Egy KATTANÁS ezzel szemben pontszerű:
 *    akármilyen sűrűn jöhet, mindig meg lehet számolni. Ezért a hangot most
 *    magunk állítjuk elő: 7 ezredmásodperc, saját burkológörbével, hogy ne
 *    pattanjon.
 *
 * 2. „jobban reagáljon a közelségre, így ez nem informatív ahogy tekergettem"
 *    — az első változat EGYENESEN arányosan osztotta el az ütemet a távolság
 *    mentén. Ez matematikailag helyes, füllel viszont használhatatlan: a fül
 *    nem a különbséget hallja, hanem az ARÁNYT. 300 és 400 ezredmásodperc
 *    között alig van hallható eltérés, 40 és 80 között viszont kétszeres a
 *    tempó. Ezért az ütem most MÉRTANI sorozat (minden lépés ugyanakkora
 *    szorzó), a közeli tartomány pedig külön szét van húzva egy 0,7-es
 *    kitevővel. A tartomány is szélesebb lett: 35-től 750 ezredmásodpercig.
 *    Középen ez már szinte pergés — azt nem lehet nem észrevenni.
 *
 * ---
 *
 * Három állapota van:
 *
 * - **keresés** (`update(null)`): ritka, halkabb, mélyebb koppanás. Azt
 *   üzeni, hogy figyelünk, de még nincs meg. Csend helyett azért kell, mert
 *   a néma telefonról nem lehet megmondani, hogy dolgozik-e még.
 * - **rávezetés** (`update(távolság)`): a kattanások sűrűsége a középtől
 *   mért távolsággal nő.
 * - **megvan** (`success()`): kettős nyugtázás, és a kattogás elhallgat.
 *   A csend itt maga az információ.
 *
 * HA KÉSŐBB SAJÁT HANGFÁJL JÖN: a `keszitKattanas()` helyére kell egy
 * fájlból beolvasott PCM-tömb, minden más változatlan marad. A ritmust és
 * az ütemezést nem érinti.
 */
class GuidanceTone(private val volume: Float = 0.55f) {

    private val handler = Handler(Looper.getMainLooper())

    private var vezetoKattanas: AudioTrack? = null
    private var keresoKattanas: AudioTrack? = null

    // A képkocka-elemzés HÁTTÉRSZÁLON hívja az update()-et, az ütemezés
    // viszont a fő szálon fut. A két mező ezért volatile — enélkül a
    // háttérszál írása tetszőlegesen sokáig láthatatlan maradhatna, és a
    // kattogás egy régi távolságot ismételgetne.
    @Volatile
    private var running = false

    /** null = nem látjuk a célt; különben a középtől mért távolság (0..~0,7). */
    @Volatile
    private var centerDistance: Float? = null

    fun start() {
        if (running) return
        running = true
        if (vezetoKattanas == null) {
            vezetoKattanas = keszitSav(keszitKattanas(VEZETO_HZ, VEZETO_MS), volume)
            keresoKattanas = keszitSav(keszitKattanas(KERESO_HZ, KERESO_MS), volume * 0.6f)
        }
        scheduleNext()
    }

    fun update(centerDistance: Float?) {
        this.centerDistance = centerDistance
    }

    /** Megvan: kettős nyugtázás, és a kattogás abbamarad. */
    fun success() {
        running = false
        handler.removeCallbacksAndMessages(null)
        szolal(vezetoKattanas)
        handler.postDelayed({ szolal(vezetoKattanas) }, 90L)
        handler.postDelayed({ szolal(vezetoKattanas) }, 180L)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    fun release() {
        stop()
        listOf(vezetoKattanas, keresoKattanas).forEach {
            try {
                it?.stop()
                it?.release()
            } catch (_: Exception) {
            }
        }
        vezetoKattanas = null
        keresoKattanas = null
    }

    // ==================== ÜTEMEZÉS ====================

    private fun scheduleNext() {
        if (!running) return
        val tavolsag = centerDistance
        val kesleltetes = if (tavolsag == null) {
            KERESES_UTEM_MS
        } else {
            utem(tavolsag)
        }
        handler.postDelayed({
            if (!running) return@postDelayed
            szolal(if (tavolsag == null) keresoKattanas else vezetoKattanas)
            scheduleNext()
        }, kesleltetes)
    }

    /**
     * A TÁVOLSÁGBÓL ÜTEM.
     *
     * Mértani sorozat: a leggyorsabb ütemet szorozzuk fel a lassú és a gyors
     * arányával, a távolság hatványozott arányában. Így a szomszédos
     * lépések közti VÁLTOZÁS mindenütt ugyanakkora aránynak hallatszik — a
     * fül ezt tudja mérni, a különbséget nem.
     *
     * A 0,7-es kitevő a közeli tartományt húzza szét: ott, ahol a
     * felhasználó már finoman céloz, legyen a legnagyobb a felbontás.
     */
    private fun utem(tavolsag: Float): Long {
        val arany = (tavolsag / MAX_TAVOLSAG).coerceIn(0f, 1f).toDouble().pow(0.7)
        val szorzo = (LASSU_MS.toDouble() / GYORS_MS).pow(arany)
        return (GYORS_MS * szorzo).toLong().coerceIn(GYORS_MS.toLong(), LASSU_MS.toLong())
    }

    // ==================== A HANG ====================

    private fun szolal(track: AudioTrack?) {
        val t = track ?: return
        try {
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (_: Exception) {
        }
    }

    /**
     * EGY KATTANÁS ELŐÁLLÍTÁSA.
     *
     * Rövid szinuszhullám, aminek a hangereje az elejétől a végéig lecseng.
     * A lecsengés nem díszítés: kapcsoló nélkül a hullám a végén hirtelen
     * nullára ugrana, és az PATTANÁST ad — sűrűn ismételve kifejezetten
     * bántó. Az első fél ezredmásodperc pedig felfutás, ugyanezért.
     */
    private fun keszitKattanas(frekvenciaHz: Double, hosszMs: Int): ShortArray {
        val minta = (MINTAVETEL * hosszMs / 1000.0).toInt().coerceAtLeast(16)
        val felfutas = (MINTAVETEL * 0.0005).toInt().coerceAtLeast(1)
        val out = ShortArray(minta)
        for (i in 0 until minta) {
            val fazis = 2.0 * PI * frekvenciaHz * i / MINTAVETEL
            val burkolo = when {
                i < felfutas -> i.toDouble() / felfutas
                else -> {
                    val h = (i - felfutas).toDouble() / (minta - felfutas)
                    (1.0 - h).pow(2.0)
                }
            }
            out[i] = (sin(fazis) * burkolo * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
        return out
    }

    private fun keszitSav(minta: ShortArray, hangero: Float): AudioTrack? = try {
        val bajt = minta.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // SONIFICATION: a rendszer tudja, hogy ez visszajelző hang
                    // és nem zene. Így nem szakítja meg a lejátszást, és nem
                    // is versenyez a beszéddel.
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(MINTAVETEL)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bajt)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(minta, 0, minta.size)
        track.setVolume(hangero.coerceIn(0f, 1f))
        track
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val MINTAVETEL = 44100

        /** Ennél távolabb már nem lassítunk tovább — a kép sarka nagyjából 0,7. */
        private const val MAX_TAVOLSAG = 0.55f

        /** A kép közepén: szinte pergés. */
        private const val GYORS_MS = 35f

        /** A kép szélén: ritka kattanás. */
        private const val LASSU_MS = 750f

        /** Nem látjuk a célt: „itt vagyok, dolgozom" ütem. */
        private const val KERESES_UTEM_MS = 1200L

        private const val VEZETO_HZ = 2100.0
        private const val VEZETO_MS = 7

        // A kereső kattanás mélyebb és tompább: hallani, hogy MÁS állapot,
        // anélkül hogy oda kellene figyelni rá.
        private const val KERESO_HZ = 750.0
        private const val KERESO_MS = 9
    }
}
