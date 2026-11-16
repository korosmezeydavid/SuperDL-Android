package com.superdl.launcher.screenreader

import android.view.accessibility.AccessibilityNodeInfo

/**
 * TÁBLÁZAT- ÉS RÁCS-NAVIGÁCIÓ.
 *
 * MIÉRT KELL: ha egy alkalmazás táblázatot vagy rácsot használ (menetrend,
 * naptár-hónap, számlázási sorok, alkalmazás-rács), a soronkénti végiglépkedés
 * használhatatlan — egy tíz oszlopos táblázatban a második sor harmadik
 * cellájához negyven söprés vezetne.
 *
 * MEGOLDÁS: az Android jelöli a rács-szerkezetet (hányadik sor, hányadik
 * oszlop). Ha ez megvan, SOR és OSZLOP szerint is lehet ugrálni.
 *
 * KORLÁT: csak ott működik, ahol a fejlesztő tényleg rácsként jelölte a
 * tartalmat. Ha nincs jelölés, ezt megmondjuk, és nem tettetjük, hogy megy.
 */
object ScreenReaderTable {

    /** Egy cella helye a rácsban. */
    data class CellPosition(val row: Int, val column: Int, val rowSpan: Int, val colSpan: Int)

    /** Az elem rács-pozíciója, vagy null, ha nem rács-elem. */
    fun positionOf(node: AccessibilityNodeInfo): CellPosition? = try {
        node.collectionItemInfo?.let {
            CellPosition(it.rowIndex, it.columnIndex, it.rowSpan, it.columnSpan)
        }
    } catch (_: Exception) {
        null
    }

    /** A tartalmazó rács mérete (sorok, oszlopok), vagy null. */
    fun gridSizeOf(node: AccessibilityNodeInfo): Pair<Int, Int>? {
        return collectionOf(node)?.collectionInfo?.let { it.rowCount to it.columnCount }
    }

    internal fun tableOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        collectionOf(node)?.takeIf { DocumentStructure.TABLES.matches(it) }
            ?: generateSequence(node) { it.parent }.take(16)
                .firstOrNull { DocumentStructure.TABLES.matches(it) }

    private fun collectionOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        generateSequence(node) { it.parent }.take(16).firstOrNull { it.collectionInfo != null }

    /** Rács-elem-e egyáltalán? */
    fun isInGrid(node: AccessibilityNodeInfo): Boolean = positionOf(node) != null

    /**
     * A következő cella keresése SOR vagy OSZLOP irányban.
     *
     * @param nodes az összegyűjtött elemek
     * @param from a jelenlegi elem
     * @param byRow igaz = sorváltás (fel-le), hamis = oszlopváltás (balra-jobbra)
     * @param forward előre vagy vissza
     * @return a talált elem indexe a listában, vagy -1
     */
    fun findNeighbour(
        nodes: List<AccessibilityNodeInfo>,
        from: AccessibilityNodeInfo,
        byRow: Boolean,
        forward: Boolean
    ): Int {
        val here = positionOf(from) ?: return -1
        val owner = collectionOf(from) ?: tableOf(from) ?: return -1
        var bestIndex = -1
        var bestDistance = Int.MAX_VALUE
        nodes.forEachIndexed { index, candidate ->
            if (candidate.windowId != from.windowId) return@forEachIndexed
            val position = positionOf(candidate) ?: return@forEachIndexed
            if ((collectionOf(candidate) ?: tableOf(candidate)) != owner) return@forEachIndexed
            val distance = distanceInDirection(here, position, byRow, forward) ?: return@forEachIndexed
            if (distance < bestDistance) {
                bestIndex = index
                bestDistance = distance
            }
        }
        return bestIndex
    }

    internal fun distanceInDirection(
        from: CellPosition,
        to: CellPosition,
        byRow: Boolean,
        forward: Boolean
    ): Int? {
        val fromCross = if (byRow) from.column else from.row
        val toCross = if (byRow) to.column else to.row
        val fromCrossSpan = (if (byRow) from.colSpan else from.rowSpan).coerceAtLeast(1)
        val toCrossSpan = (if (byRow) to.colSpan else to.rowSpan).coerceAtLeast(1)
        if (fromCross < 0 || toCross < 0 ||
            fromCross >= toCross + toCrossSpan || toCross >= fromCross + fromCrossSpan
        ) return null
        val fromAxis = if (byRow) from.row else from.column
        val toAxis = if (byRow) to.row else to.column
        if (fromAxis < 0 || toAxis < 0) return null
        val fromAxisSpan = (if (byRow) from.rowSpan else from.colSpan).coerceAtLeast(1)
        val toAxisSpan = (if (byRow) to.rowSpan else to.colSpan).coerceAtLeast(1)
        val distance = if (forward) toAxis - (fromAxis + fromAxisSpan - 1)
            else fromAxis - (toAxis + toAxisSpan - 1)
        return distance.takeIf { it > 0 }
    }

    /** A cella helyének felolvasható leírása ("2. sor, 3. oszlop"). */
    fun speakPosition(node: AccessibilityNodeInfo): String {
        val pos = positionOf(node) ?: return ""
        val size = gridSizeOf(node)
        return if (size != null && size.first > 0 && size.second > 0) {
            "${pos.row + 1}. sor a ${size.first}-ből, ${pos.column + 1}. oszlop a ${size.second}-ből"
        } else {
            "${pos.row + 1}. sor, ${pos.column + 1}. oszlop"
        }
    }
}
