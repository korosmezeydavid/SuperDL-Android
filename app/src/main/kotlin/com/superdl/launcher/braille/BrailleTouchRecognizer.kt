package com.superdl.launcher.braille

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

/**
 * ÉRINTÉSEKBŐL BRAILLE-CELLA ÉS PARANCS — a próbapad ÉS a billentyűzet
 * KÖZÖS motorja.
 *
 * MIÉRT KÜLÖN OSZTÁLY: a próbapad és az éles billentyűzet ugyanazt a kezet
 * nézi, ugyanazokkal a szabályokkal. Ha kétszer lenne megírva, egy javítás
 * csak az egyiket javítaná, és a másik csendben rosszabb maradna. Ez az
 * osztály NEM tud sem képernyőről, sem szövegmezőről — csak érintéseket
 * kap, és eseményeket ad.
 *
 * HÁROM ÍRÁSMÓD, EGY MOTOR:
 *  - FULL: hat ujj egyszerre, a megtanult pontokhoz párosítva.
 *  - TWO_PASS: két koppintás ad egy cellát (bal fél + jobb fél).
 *  - RAIL: egy ujj a sínen — külön kis motor, a `RailInput`.
 *
 * PARANCSOK (FULL és TWO_PASS):
 *  - egy ujj elhúzva jobbra: szóköz; balra: törlés; lefelé: felolvasás;
 *  - két ujj balra: törlés; két ujj jobbra: szóköz;
 *  - három ujj balra: SZÓ törlése; három ujj le: felolvasás;
 *  - összecsippentés: bezárás.
 * PARANCSOK (RAIL): a söprés foglalt, ezért két ujj koppintás = törlés,
 *  három ujj koppintás = felolvasás, összecsippentés = bezárás.
 *
 * A KÉZ KÖVETÉSE: minden felismert cella után a pontok utánamennek az
 * ujjaknak (`BrailleAnchors.nudge`). A hívó dolga menteni — `anchors`.
 */
class BrailleTouchRecognizer(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        /** Egy teljes cella (két menetnél a két fél EGYÜTT). 0 = üres cella. */
        fun onCell(mask: Int)

        /** Két menetes módban az első fél megvan. */
        fun onHalfCell()

        /** A fél cella lejárt (nem jött a másik fele időben) — elfelejtve. */
        fun onHalfCellExpired()

        fun onSpace()
        fun onBackspace()
        fun onDeleteWord()
        fun onReadBack()
        fun onClose()

        /** Két ujjal lefelé: új sor. Ugyanaz, mint a mátrixnál. */
        fun onEnter()

        /** Két ujjal felfelé: billentyűzet-választó. */
        fun onSwitchKeyboard()

        /** Egy új ujj ért a kijelzőhöz — billentyűhang. */
        fun onFingerDown()

        // A sín visszajelzései — továbbadva a RailInput-ból.
        fun onRailRow(row: Int)
        fun onRailDot(dot: Int, added: Boolean)
        fun onRailCleared()
        fun onRailIdle(row: Int, dots: Set<Int>)
    }

    companion object {
        /** Ennyit várunk a felengedés után, hogy a lassabb ujjak is beérjenek. */
        const val SETTLE_MS = 220L

        /** Ekkora elmozdulás fölött már söprés, nem pontleütés (a kijelző arányában). */
        const val SWIPE_FRACTION = 0.18f

        /**
         * A FÉLBEHAGYOTT FÉL CELLA ennyi idő után elfelejtődik. Enélkül egy
         * meggondolt első fél a következő koppintással hibás betűvé olvadna.
         */
        const val HALF_CELL_TIMEOUT_MS = 3000L
    }

    private val handler = Handler(Looper.getMainLooper())

    var mode: TouchCapability.Mode = TouchCapability.Mode.TWO_PASS
        private set

    /** A megtanult (és követett) ujjhelyek. A hívó mentse el, ha kilép. */
    var anchors: List<BrailleAnchors.Anchor> = emptyList()
        private set

    private var twoPass = false
    private var rail: RailInput? = null

    /** Két menetes módban: az első fél cella, amíg a másik meg nem jön. */
    private var pendingHalf: Int? = null

    private var maxPointers = 0
    private var bestPoints: List<Pair<Float, Float>> = emptyList()
    private var railFingers = 0

    private var downX = 0f
    private var downY = 0f
    private var moved = 0f
    private var startSpread = 0f
    private var closed = false

    /** Hány cellát ismertünk fel — a hívó ebből tudja, mikor érdemes menteni. */
    var cellsSinceStart = 0
        private set

    /**
     * Beállítás induláskor. Sín módban nem kell kalibráció; a többiben a
     * hívó dolga megnézni, van-e elég pont (`anchors.size >= 3`).
     */
    fun configure(mode: TouchCapability.Mode, anchors: List<BrailleAnchors.Anchor>) {
        this.mode = mode
        this.anchors = anchors
        closed = false
        pendingHalf = null
        cellsSinceStart = 0
        if (mode == TouchCapability.Mode.RAIL) {
            rail = RailInput(context.resources.displayMetrics.density, railListener)
            twoPass = false
        } else {
            rail = null
            twoPass = mode != TouchCapability.Mode.FULL || anchors.size < 6
        }
    }

    val isRail: Boolean get() = rail != null
    val isTwoPass: Boolean get() = twoPass

    fun reset() {
        handler.removeCallbacksAndMessages(null)
        pendingHalf = null
        maxPointers = 0
        bestPoints = emptyList()
        railFingers = 0
        startSpread = 0f
        closed = false
    }

    // ── Belépés ─────────────────────────────────────────────────────────────

    fun onTouchEvent(event: MotionEvent, width: Float, height: Float): Boolean {
        if (closed) return true
        if (checkPinch(event, width)) return true
        return if (rail != null) onRailTouch(event) else onCellTouch(event, width, height)
    }

    // ── Csippentés: a közös kijárat ─────────────────────────────────────────

    private fun spreadOf(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        var max = 0f
        for (i in 0 until event.pointerCount) {
            for (j in i + 1 until event.pointerCount) {
                val dx = event.getX(i) - event.getX(j)
                val dy = event.getY(i) - event.getY(j)
                max = kotlin.math.max(max, kotlin.math.hypot(dx, dy))
            }
        }
        return max
    }

    private fun checkPinch(event: MotionEvent, width: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN ->
                if (event.pointerCount == 2) startSpread = spreadOf(event)
            MotionEvent.ACTION_MOVE -> {
                if (startSpread <= 0f || event.pointerCount < 2) return false
                // A küszöb a kijelző hatoda: remegő kéz sose zárjon be véletlenül.
                if (spreadOf(event) < startSpread - width / 6f) {
                    startSpread = 0f
                    closed = true
                    handler.removeCallbacksAndMessages(null)
                    listener.onClose()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> startSpread = 0f
        }
        return false
    }

    // ── Cella módok (FULL, TWO_PASS) ────────────────────────────────────────

    private fun onCellTouch(event: MotionEvent, w: Float, h: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacksAndMessages(null)
                maxPointers = 0
                bestPoints = emptyList()
                downX = event.getX(0)
                downY = event.getY(0)
                moved = 0f
                snapshot(event, w, h)
            }
            MotionEvent.ACTION_POINTER_DOWN -> snapshot(event, w, h)
            MotionEvent.ACTION_MOVE -> {
                // A söprést a LEGELSŐ ujj útjából mérjük. Több ujjnál a kis
                // elmozdulás természetes — a kéz nem szobor —, ezért a
                // parancshoz nagy, határozott mozdulat kell.
                val dx = event.getX(0) - downX
                val dy = event.getY(0) - downY
                moved = kotlin.math.max(moved, kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy)))
                snapshot(event, w, h)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacksAndMessages(null)
                val dx = event.getX(0) - downX
                val dy = event.getY(0) - downY
                handler.postDelayed({ commitTouch(dx, dy, w) }, SETTLE_MS)
            }
        }
        return true
    }

    /**
     * A LEGTÖBB EGYSZERRE LÁTOTT UJJ pillanatképe. Az ujjak sosem érnek le
     * tökéletesen egyszerre; a végállapot alábecsülné a cellát.
     */
    private fun snapshot(event: MotionEvent, w: Float, h: Float) {
        if (event.pointerCount <= maxPointers) return
        maxPointers = event.pointerCount
        listener.onFingerDown()
        bestPoints = (0 until event.pointerCount).map {
            event.getX(it) / w to event.getY(it) / h
        }
    }

    private fun commitTouch(dx: Float, dy: Float, width: Float) {
        val fingers = maxPointers
        val points = bestPoints
        maxPointers = 0
        bestPoints = emptyList()
        val wasMoved = moved
        moved = 0f

        // ── SÖPRÉS-PARANCSOK ────────────────────────────────────────────
        // A küszöb a kijelző 18%-a: egy cella letételekor az ujj ennyit
        // sosem csúszik, egy szándékos söprés viszont mindig túllépi.
        val threshold = width * SWIPE_FRACTION
        if (wasMoved > threshold) {
            val horizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy)
            when {
                fingers >= 3 && horizontal && dx < 0 -> { pendingHalf = null; listener.onDeleteWord() }
                fingers >= 3 && !horizontal && dy > 0 -> listener.onReadBack()
                fingers == 2 && horizontal && dx < 0 -> { pendingHalf = null; listener.onBackspace() }
                fingers == 2 && horizontal && dx > 0 -> { pendingHalf = null; listener.onSpace() }
                fingers == 2 && !horizontal && dy > 0 -> { pendingHalf = null; listener.onEnter() }
                fingers == 2 && !horizontal && dy < 0 -> listener.onSwitchKeyboard()
                fingers == 1 && horizontal && dx > 0 -> { pendingHalf = null; listener.onSpace() }
                fingers == 1 && horizontal && dx < 0 -> { pendingHalf = null; listener.onBackspace() }
                fingers == 1 && !horizontal && dy > 0 -> listener.onReadBack()
                else -> {}   // ismeretlen söprés: nem írunk semmit
            }
            return
        }

        if (fingers == 0 || points.isEmpty()) return

        val matched = BrailleAnchors.match(anchors, points)
        if (matched.isEmpty()) return

        // A KÉZ KÖVETÉSE: a pontok utánamennek az ujjaknak.
        anchors = BrailleAnchors.nudge(anchors, matched)
        cellsSinceStart++

        val mask = BrailleTable.cell(*matched.keys.toIntArray())
        if (twoPass) handleTwoPass(mask) else listener.onCell(mask)
    }

    /**
     * KÉT MENET: az első koppintás fél cella, a második a másik fele.
     * A megtanult ujjhelyek tudják, melyik oszlop volt — nem kell fejben
     * tartani, hányadik menetben vagy.
     */
    private fun handleTwoPass(mask: Int) {
        val first = pendingHalf
        if (first == null) {
            pendingHalf = mask
            listener.onHalfCell()
            // A félbehagyott fél cella elévül — lásd HALF_CELL_TIMEOUT_MS.
            handler.postDelayed({
                if (pendingHalf != null) {
                    pendingHalf = null
                    listener.onHalfCellExpired()
                }
            }, HALF_CELL_TIMEOUT_MS)
            return
        }
        pendingHalf = null
        handler.removeCallbacksAndMessages(null)
        listener.onCell(first or mask)
    }

    /** Van-e függő fél cella? A hívó ebből tudja, mit mondjon `onHalfCell`-nél. */
    val hasPendingHalf: Boolean get() = pendingHalf != null

    // ── Sín mód ─────────────────────────────────────────────────────────────

    private val railListener = object : RailInput.Listener {
        override fun onRow(row: Int) = listener.onRailRow(row)
        override fun onDot(dot: Int, added: Boolean) = listener.onRailDot(dot, added)
        override fun onCleared() = listener.onRailCleared()
        override fun onIdle(row: Int, dots: Set<Int>) = listener.onRailIdle(row, dots)
    }

    fun describeRail(row: Int, dots: Set<Int>): String =
        rail?.describe(row, dots) ?: ""

    private fun scheduleRailIdle() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ rail?.idle() }, RailInput.IDLE_MS)
    }

    private fun onRailTouch(event: MotionEvent): Boolean {
        val r = rail ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacksAndMessages(null)
                railFingers = 1
                downX = event.getX(0)
                downY = event.getY(0)
                r.begin(event.getX(0), event.getY(0))
                listener.onFingerDown()
                scheduleRailIdle()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                handler.removeCallbacksAndMessages(null)
                railFingers = kotlin.math.max(railFingers, event.pointerCount)
            }
            MotionEvent.ACTION_MOVE -> {
                if (railFingers == 1 && event.pointerCount == 1) {
                    if (r.move(event.getX(0), event.getY(0))) scheduleRailIdle()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacksAndMessages(null)
                // SÍN MÓDBAN AZ EGY UJJ ÍR, tehát a parancsok mind több
                // ujjasak. Két ujj: koppintás = törlés, lefelé = új sor,
                // felfelé = billentyűzet-váltás. Három ujj: felolvasás.
                val dy = event.getY(0) - downY
                val vertical = kotlin.math.abs(dy) > context.resources.displayMetrics.heightPixels * 0.12f
                when {
                    railFingers >= 3 -> listener.onReadBack()
                    railFingers == 2 && vertical && dy > 0 -> listener.onEnter()
                    railFingers == 2 && vertical && dy < 0 -> listener.onSwitchKeyboard()
                    railFingers == 2 -> listener.onBackspace()
                    else -> {
                        cellsSinceStart++
                        listener.onCell(r.finish())
                    }
                }
                railFingers = 0
            }
        }
        return true
    }
}
