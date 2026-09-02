package com.superdl.launcher.braille

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.superdl.launcher.tts.TtsManager

/**
 * A KÉZ MEGTANULÁSA — EGY MOZDULATTAL, ahogy az iPhone csinálja.
 *
 * MIÉRT ÍRTAM ÁT (2026-09-02, tesztelői visszajelzés):
 *
 * Az előző változat CELLA elrendezésben két menetben mért: előbb a bal
 * oszlop három ujja, aztán a jobb háromé. Ez önmagában kényelmes volt — de
 * **soha nem láthatott hat ujjat egyszerre.** A mérés így mindig hármat
 * jegyzett meg, és a program utána *minden esetben* a két menetes módot
 * ajánlotta. A tesztelő pontosan ezt jelezte:
 *
 * > „leteszem a bal kezem 3 ujját, felemelem, ráteszem a jobb kezem 3 ujját,
 * >  megint csak azt mondja, hogy kétmenetes. Hadd tegyem rá mind a 6
 * >  ujjamat, ha igazi Braille-ra akarom kalibrálni."
 *
 * Teljesen igaza van, és a hiba az enyém volt: a mérés módja tette
 * elérhetetlenné azt a módot, amit mérni akartunk.
 *
 * AZ ÚJ RENDSZER — az iPhone Braille-bevitelének mintájára:
 *
 *  1. **Ráteszed mind a hat ujjadat egyszerre.** Ennyi.
 *  2. A program megnézi, hol vannak, és **magától felismeri az
 *     elrendezést**: ha 3 pont balra és 3 jobbra esik, az CELLA; ha mind a
 *     hat egy sorban van, az ZONGORA. Nem kell menüben előre megmondani.
 *  3. Ha kevesebb ujjat érzett, **újra próbálhatod, ahányszor akarod** —
 *     vagy lefelé söpörve átválthatsz a régi, két menetes mérésre.
 *
 * A LÉNYEG: a mérés SOSE zárja el az utat a jobb mód elől. Ha a telefon tud
 * hatot, az derüljön ki; ha nem, arról a felhasználó kapjon tiszta szót.
 */
class TouchCalibrationActivity : Activity() {

    companion object {
        /** Ennyit várunk felengedés után, hogy a lassabb ujjak is beérjenek. */
        private const val SETTLE_MS = 700L

        /** Ekkora függőleges söprés fölött váltunk a két menetes mérésre. */
        private const val SWIPE_FRACTION = 0.18f

        fun start(context: Context) {
            context.startActivity(
                Intent(context, TouchCalibrationActivity::class.java).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private lateinit var tts: TtsManager
    private lateinit var label: TextView
    private lateinit var sounds: BrailleSounds
    private val handler = Handler(Looper.getMainLooper())

    /** A legtöbb ujj, amit EGYSZERRE láttunk — az ÖSSZES próbálkozás alatt. */
    private var deviceMax = 0

    /** A mostani próbálkozás legjobb pillanatképe. */
    private var tryMax = 0
    private var tryPoints: List<Pair<Float, Float>> = emptyList()

    /** Hányadik próbálkozás. Csak azért, hogy ne ismételjük ugyanazt a szöveget. */
    private var attempt = 0

    /**
     * Két menetes tartalék mérés: 0 = nem abban vagyunk, 1 = bal oszlop,
     * 2 = jobb oszlop.
     */
    private var stagedStep = 0

    private val collected = mutableListOf<BrailleAnchors.Anchor>()
    private var finished = false

    private var downY = 0f
    private var moved = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = BrailleLayoutPrefs.orientation(this).activityInfo()

        label = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            text = "Tedd rá mind a hat ujjadat"
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.BLACK)
                setPadding(32, 32, 32, 32)
                addView(label)
            }
        )

        tts = TtsManager(this)
        sounds = BrailleSounds(this)
        collected.clear()
        speakIntro()
    }

    /**
     * A TARTÁS AZ ELSŐ MONDAT, MINDIG.
     *
     * Itt tanulja meg a program, hol vannak az ujjaid. Ha most másképp fogod
     * a telefont, mint később írás közben, a megjegyzett helyek rosszak
     * lesznek, minden betű hibás lesz — és a felhasználó magát hibáztatja.
     */
    private fun speakIntro() {
        val hold = BrailleLayoutPrefs.orientation(this).speakHold()
        tts.speak(
            "A kezed megtanítása. $hold " +
                "Most tedd rá a kijelzőre MIND A HAT UJJADAT EGYSZERRE, úgy, ahogy " +
                "írni fogsz. Tartsd ott egy pillanatig, aztán engedd fel. " +
                "Az elrendezést magamtól felismerem: ha két oszlopba teszed, cella " +
                "lesz, ha egy sorba, zongora. " +
                "Ha kevesebb ujjat érzek, nyugodtan próbáld újra. " +
                "Ha egyszerre nem megy, söpörj lefelé, és két menetben mérünk."
        )
    }

    // ── A mérés ─────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (finished) return true
        val w = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val h = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

        // A LEGTÖBB EGYSZERRE LÁTOTT UJJ pillanatképe. Az ujjak sosem érnek le
        // tökéletesen egyszerre; ha csak a végállapotot néznénk, alábecsülnénk
        // a telefont — és épp ez volt a régi mérés hibája.
        if (event.pointerCount > tryMax) {
            tryMax = event.pointerCount
            if (event.pointerCount > deviceMax) deviceMax = event.pointerCount
            tryPoints = (0 until event.pointerCount).map {
                event.getX(it) / w to event.getY(it) / h
            }
            sounds.fingerDown()
            vibrate()
            label.text = "$tryMax ujj"
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacksAndMessages(null)
                downY = event.getY(0)
                moved = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN ->
                handler.removeCallbacksAndMessages(null)

            MotionEvent.ACTION_MOVE ->
                if (event.pointerCount == 1) moved = event.getY(0) - downY

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacksAndMessages(null)
                // EGY UJJ LEFELÉ SÖPÖRVE = váltás a két menetes mérésre.
                // Egy ujjal úgysem lehet kalibrálni, tehát ez a mozdulat
                // nem vesz el semmit a méréstől.
                if (stagedStep == 0 && tryMax == 1 && moved > h * SWIPE_FRACTION) {
                    startStaged()
                    return true
                }
                handler.postDelayed({ finishTry() }, SETTLE_MS)
            }
        }
        return true
    }

    private fun finishTry() {
        if (finished) return
        val points = tryPoints
        if (points.isEmpty()) return

        if (stagedStep > 0) {
            finishStagedStep(points)
            return
        }

        if (points.size >= 6) {
            assignFromSixPoints(points.take(6))
            complete()
            return
        }

        // KEVESEBB MINT HAT. NEM adjuk fel, és nem is döntünk helyette:
        // elmondjuk, mit mértünk, és rábízzuk, mit akar.
        attempt++
        val again = if (attempt == 1) {
            "Próbáld újra: tedd le mind a hat ujjadat egyszerre, jól elválasztva."
        } else {
            "Próbálhatod újra. Ha nem megy egyszerre, söpörj lefelé egy ujjal, " +
                "és két menetben mérünk: előbb a bal oszlop, aztán a jobb."
        }
        tryMax = 0
        tryPoints = emptyList()
        tts.speak("${points.size} ujjat éreztem a hatból. $again")
    }

    /**
     * AZ ELRENDEZÉS FELISMERÉSE — a hat pont helyéből.
     *
     * A vízszintes hézagokat nézzük. Ha a legnagyobb hézag pont KETTÉVÁGJA a
     * hatot háromra és háromra, és érdemben nagyobb a többinél, akkor két
     * oszlop van: ez a CELLA. Egyébként egy sorban vannak: ez a ZONGORA.
     *
     * MIÉRT NEM KÉRDEZZÜK MEG: mert a kéz már megmondta. Egy menüpont, ami
     * arról szól, amit a program úgyis lát, csak dolgot ad a felhasználónak.
     * A menüben persze továbbra is átállíthatja, ha mégis mást akar.
     */
    private fun assignFromSixPoints(points: List<Pair<Float, Float>>) {
        val byX = points.sortedBy { it.first }
        val gaps = (0 until byX.size - 1).map { byX[it + 1].first - byX[it].first }
        val biggest = gaps.indexOf(gaps.maxOrNull() ?: 0f)
        val others = gaps.filterIndexed { i, _ -> i != biggest }
        val avgOther = if (others.isEmpty()) 0f else others.sum() / others.size
        val isCell = biggest == 2 && gaps[biggest] > avgOther * 1.6f

        collected.clear()
        if (isCell) {
            // Bal oszlop fentről lefelé: 1, 2, 3. Jobb oszlop: 4, 5, 6.
            byX.take(3).sortedBy { it.second }.forEachIndexed { i, p ->
                collected.add(BrailleAnchors.Anchor(i + 1, p.first, p.second))
            }
            byX.drop(3).sortedBy { it.second }.forEachIndexed { i, p ->
                collected.add(BrailleAnchors.Anchor(i + 4, p.first, p.second))
            }
            BrailleLayoutPrefs.setLayout(this, BrailleLayoutPrefs.Layout.CELL)
        } else {
            // Egy sorban, balról jobbra: 1-től 6-ig.
            byX.forEachIndexed { i, p ->
                collected.add(BrailleAnchors.Anchor(i + 1, p.first, p.second))
            }
            BrailleLayoutPrefs.setLayout(this, BrailleLayoutPrefs.Layout.PIANO)
        }
    }

    // ── Tartalék: a régi, két menetes mérés ─────────────────────────────────

    private fun startStaged() {
        stagedStep = 1
        tryMax = 0
        tryPoints = emptyList()
        collected.clear()
        BrailleLayoutPrefs.setLayout(this, BrailleLayoutPrefs.Layout.CELL)
        tts.speak(
            "Két menetben mérünk. Először a BAL oszlop: tedd a kijelző bal oldalára " +
                "a bal kezed három ujját, EGYMÁS ALÁ, fentről lefelé. " +
                "A legfelső az egyes pont, a középső a kettes, a legalsó a hármas."
        )
    }

    private fun finishStagedStep(points: List<Pair<Float, Float>>) {
        val sorted = points.sortedBy { it.second }.take(3)
        val firstDot = if (stagedStep == 1) 1 else 4
        sorted.forEachIndexed { i, p ->
            collected.add(BrailleAnchors.Anchor(firstDot + i, p.first, p.second))
        }
        if (stagedStep == 1) {
            stagedStep = 2
            tryMax = 0
            tryPoints = emptyList()
            tts.speakThen("Bal oszlop megvan, ${sorted.size} ujjal.") {
                tts.speak(
                    "Most a JOBB oszlop: a jobb kezed három ujját, egymás alá. " +
                        "A legfelső a négyes pont, a középső az ötös, a legalsó a hatos."
                )
            }
        } else {
            complete()
        }
    }

    // ── Lezárás ─────────────────────────────────────────────────────────────

    private fun complete() {
        finished = true
        TouchCapability.saveMeasurement(this, deviceMax)
        BrailleAnchors.save(this, collected)

        val dots = collected.size
        label.text = "$dots pont"

        val what = if (dots >= 6) {
            val layout = BrailleLayoutPrefs.layout(this)
            "Mind a hat pont megvan. Az elrendezésed: ${layout.label}."
        } else {
            "FIGYELEM: csak $dots pontot sikerült megtanulnom a hatból. " +
                "Ha ez kevés, indítsd újra a mérést."
        }

        // A MÉRÉS AJÁNL, DE A VÁLASZTÁS ERŐSEBB. Ha a felhasználó korábban
        // kézzel beállított egy módot, azt NEM írjuk felül a háta mögött —
        // de szólunk, hogy most már többre is képes a telefonja.
        val recommended = TouchCapability.recommended(this)
        val chosen = TouchCapability.chosenMode(this)
        val modeNote = when {
            chosen == null ->
                "Az érvényes mód: ${recommended.label}."
            chosen == recommended ->
                "Az érvényes mód: ${chosen.label}."
            else ->
                "A mérés szerint a ${recommended.label} is menne, de te a " +
                    "${chosen.label} módot választottad. Ha váltanál, az Írásmód " +
                    "menüpontban teheted meg."
        }

        tts.speakThen(
            "$what A telefonod egyszerre $deviceMax ujjat érzett. $modeNote " +
                "Most már kipróbálhatod a Braille próbát."
        ) {
            handler.postDelayed({ finish() }, 1200L)
        }
    }

    private fun vibrate() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            }
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(
                    30L,
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } catch (_: Exception) {
        }
    }

    // A KALIBRÁLÁS MINDEN ÉRINTÉST KÉR: itt a hat ujj egyidejű letétele a
    // mérés maga. Ha az olvasó közben elfogná őket, a mérés alábecsülné a
    // telefont — és utána rossz módot ajánlanánk.

    override fun onResume() {
        super.onResume()
        BrailleInputActive.hold()
        com.superdl.launcher.screenreader.ScreenReaderService.refreshTouchOwnership()
    }

    override fun onPause() {
        BrailleInputActive.release()
        com.superdl.launcher.screenreader.ScreenReaderService.refreshTouchOwnership()
        super.onPause()
    }

    override fun onDestroy() {
        BrailleInputActive.release()
        com.superdl.launcher.screenreader.ScreenReaderService.refreshTouchOwnership()
        handler.removeCallbacksAndMessages(null)
        try {
            tts.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
