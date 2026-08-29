package com.superdl.launcher.screenreader

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * FELDERÍTÉS ÉRINTÉSSEL — a képernyő tapintásos bejárása.
 *
 * MIÉRT KELL, HA VAN LÉPKEDÉS:
 * A fel-le söprés lineáris: elemről elemre halad. Ez sokszor jobb a
 * tapogatásnál, DE van, amikor rossz:
 *   - egy 40 elemes weboldalon a 35. gombig 35 söprés
 *   - térbeli elrendezésnél (számbillentyűzet, naptár-rács) a lineáris
 *     sorrend értelmetlen
 *   - ha valaki MÁR TUDJA, hol a gomb, fölösleges végiglépkednie
 *
 * MŰKÖDÉS:
 *   1. Ráteszed az ujjad egy elemre, és RAJTA TARTOD 3 másodpercig.
 *   2. Bekapcsol a felderítés. Pásztázol: ahol elem van, bemondja,
 *      és a hang megmondja azt is, HOL vagy a képernyőn.
 *   3. Ahol felemeled az ujjad, ODA UGRIK A FÓKUSZ — de NEM nyomja meg.
 *   4. Jobbra söpréssel nyomod meg, ha tényleg azt akartad.
 *
 * MIÉRT NEM AKTIVÁL AZONNAL A FELENGEDÉS:
 * Vakon a felengedés helye BIZONYTALAN. Ha azonnal megnyomna, a felhasználó
 * véletlenül törölne, küldene vagy vásárolna. A kétlépcsős megoldás itt nem
 * lassítás, hanem BIZTONSÁG.
 *
 * TECHNIKAI MEGJEGYZÉS:
 * A kisegítő szolgáltatás NEM kapja meg az érintés nyers koordinátáit — a
 * rendszer viszont jelzi, MELYIK ELEM fölött jár az ujj. Ezért a helyzetet az
 * elem képernyőn elfoglalt helyéből számoljuk. Ez pontosan elég: a felhasználót
 * úgyis az elem érdekli, nem a pixel.
 */
class TouchExplorer {

    /** Fut-e éppen a felderítés. */
    var active = false
        private set

    /** Az az elem, amelyen az ujj ÉPPEN áll. */
    private var hoveredNode: AccessibilityNodeInfo? = null

    /** Mióta áll ugyanazon az elemen (a hosszú nyomás méréséhez). */
    private var hoverStartedAt = 0L

    /** Az utolsó helyzetjelző hang ideje — hogy ne pattogjon túl sűrűn. */
    private var lastSoundAt = 0L

    /** Ennél sűrűbben nem szólal meg a helyzetjelző hang. */
    private val soundIntervalMs = 90L

    /**
     * Ennyi ideig kell egy helyben tartani az ujjat a felderítés indításához.
     *
     * ÁLLÍTHATÓ, mert nagyon eltérő igények vannak: aki gyakorlott, annak a
     * három másodperc örökkévalóság; akinek viszont remeg a keze vagy lassan
     * mozog, annak az egy másodperc véletlenül is összejön.
     */
    var holdToStartMs = 3_000L

    /**
     * Az ujj új elemre ért.
     * @return igaz, ha ez ÚJ elem (tehát érdemes bemondani)
     */
    fun onHoverEnter(node: AccessibilityNodeInfo?): Boolean {
        val previous = hoveredNode
        hoveredNode = node
        if (node == null) return false
        if (previous != null && sameNode(previous, node)) return false
        // Új elemre értünk: a nyomva tartás számlálója újraindul.
        hoverStartedAt = System.currentTimeMillis()
        return true
    }

    /**
     * Elértük-e a felderítés indításának feltételét?
     * Ugyanazon az elemen kell maradni a teljes idő alatt.
     */
    fun shouldStart(): Boolean {
        if (active || hoveredNode == null || hoverStartedAt == 0L) return false
        return System.currentTimeMillis() - hoverStartedAt >= holdToStartMs
    }

    fun start() {
        active = true
        lastSoundAt = 0L
    }

    fun stop() {
        active = false
        hoveredNode = null
        hoverStartedAt = 0L
    }

    /** Az érintés véget ért (felemelted az ujjad). */
    fun onTouchEnd() {
        hoverStartedAt = 0L
    }

    /** Szabad-e MOST megszólaltatni a helyzetjelző hangot? */
    fun mayPlaySound(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSoundAt < soundIntervalMs) return false
        lastSoundAt = now
        return true
    }

    /** Az az elem, amelyen az ujj áll — a felengedéskori fókuszáláshoz. */
    fun currentNode(): AccessibilityNodeInfo? = hoveredNode

    private fun sameNode(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean = try {
        val ra = Rect()
        val rb = Rect()
        a.getBoundsInScreen(ra)
        b.getBoundsInScreen(rb)
        ra == rb && a.viewIdResourceName == b.viewIdResourceName
    } catch (_: Exception) {
        false
    }

    companion object {

        /**
         * Az elem helyzete a képernyőn, 0 és 1 közötti arányban.
         * Ebből számoljuk a térbeli hangot: a magasságot és a bal-jobb oldalt.
         *
         * @return (x, y) pár, vagy null ha nem meghatározható
         */
        fun screenPositionOf(
            node: AccessibilityNodeInfo?,
            screenWidth: Int,
            screenHeight: Int
        ): Pair<Float, Float>? {
            if (node == null || screenWidth <= 0 || screenHeight <= 0) return null
            return try {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                // Az elem KÖZEPÉT vesszük — ez felel meg annak, amit a
                // felhasználó az ujja alatt érez.
                val x = rect.centerX().toFloat() / screenWidth
                val y = rect.centerY().toFloat() / screenHeight
                x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
            } catch (_: Exception) {
                null
            }
        }
    }
}
