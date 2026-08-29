package com.superdl.launcher.screenreader

import android.view.accessibility.AccessibilityNodeInfo

/**
 * FÓKUSZ-HORGONY — hogy a helyed ne vesszen el, amikor változik a képernyő.
 *
 * A PROBLÉMA, AMIT MEGOLD:
 * A képernyőolvasó eddig SORSZÁMMAL tartotta a helyet: "a hetedik elemen
 * állok". Csakhogy egy csevegőben, levelezőben vagy hírfolyamban a lista
 * ÁTRENDEZŐDIK: érkezik egy üzenet, és a hetedik elem már egy másik.
 *
 * A felhasználó ilyenkor azt éli meg, hogy a program "elugrott" — pedig ő
 * nem csinált semmit. Ez az egyik legidegesítőbb hiba, mert kiszámíthatatlan.
 *
 * A MEGOLDÁS: ne a sorszámot jegyezzük meg, hanem MAGÁT AZ ELEMET — több
 * jellemzőjével együtt, hogy újraolvasás után biztosan felismerjük.
 *
 * MIÉRT TÖBB JELLEMZŐ: egy csevegőben húsz üzenet felirata lehet ugyanaz
 * ("Szia"). A felirat egymagában tehát kevés. A rendszerbeli azonosító és a
 * képernyőn elfoglalt hely együtt viszont már megkülönbözteti őket.
 */
data class FocusAnchor(
    /** A rendszerbeli azonosító (ha van) — ez a legerősebb jel. */
    val viewId: String,
    /** A felirat. */
    val label: String,
    /** Az elem osztálya (gomb, szövegmező, kép). */
    val className: String,
    /** Függőleges helyzet a képernyőn — átrendeződésnél is közel marad. */
    val topPosition: Int,
    /** Hányadik volt a listában — végső tartalék. */
    val index: Int
) {

    companion object {

        /** Horgony készítése egy elemről. */
        fun of(node: AccessibilityNodeInfo?, index: Int): FocusAnchor? {
            if (node == null) return null
            return try {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                FocusAnchor(
                    viewId = node.viewIdResourceName.orEmpty(),
                    label = ScreenReaderNavigator.labelOf(node).orEmpty(),
                    className = node.className?.toString().orEmpty(),
                    topPosition = bounds.top,
                    index = index
                )
            } catch (_: Exception) {
                null
            }
        }

        /**
         * A horgony megkeresése az ÚJ listában.
         *
         * NÉGY SZINTEN keresünk, a legpontosabbtól a leggyengébbig. Az első
         * találat nyer. Így egy átrendezett listában is a lehető legjobb
         * helyre kerülünk vissza.
         *
         * @return a talált elem sorszáma, vagy -1 ha semmi nem illik
         */
        fun findIn(anchor: FocusAnchor?, nodes: List<AccessibilityNodeInfo>): Int {
            if (anchor == null || nodes.isEmpty()) return -1

            // 1. AZONOSÍTÓ ÉS FELIRAT együtt — ez szinte biztos találat.
            if (anchor.viewId.isNotBlank()) {
                val exact = nodes.indexOfFirst { node ->
                    node.viewIdResourceName == anchor.viewId &&
                        ScreenReaderNavigator.labelOf(node).orEmpty() == anchor.label
                }
                if (exact >= 0) return exact
            }

            // 2. FELIRAT ÉS OSZTÁLY — ha az azonosító hiányzik vagy változott.
            if (anchor.label.isNotBlank()) {
                val byLabel = nodes.indexOfFirst { node ->
                    ScreenReaderNavigator.labelOf(node).orEmpty() == anchor.label &&
                        node.className?.toString().orEmpty() == anchor.className
                }
                if (byLabel >= 0) return byLabel
            }

            // 3. CSAK AZONOSÍTÓ — a felirat változhatott (pl. óra, számláló).
            if (anchor.viewId.isNotBlank()) {
                val byId = nodes.indexOfFirst { it.viewIdResourceName == anchor.viewId }
                if (byId >= 0) return byId
            }

            // 4. LEGKÖZELEBBI FÜGGŐLEGES HELY — ha az elem eltűnt, legalább
            //    ott maradjunk, ahol voltunk a képernyőn. Így a felhasználó
            //    nem a lista elejére kerül vissza.
            var bestIndex = -1
            var bestDistance = Int.MAX_VALUE
            nodes.forEachIndexed { i, node ->
                try {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    val distance = kotlin.math.abs(bounds.top - anchor.topPosition)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestIndex = i
                    }
                } catch (_: Exception) {
                }
            }
            // Csak akkor fogadjuk el, ha tényleg közel van (fél képernyőnyi).
            return if (bestDistance < 600) bestIndex else -1
        }
    }
}
