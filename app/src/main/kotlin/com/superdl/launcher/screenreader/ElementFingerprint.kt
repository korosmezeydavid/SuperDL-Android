package com.superdl.launcher.screenreader

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * ELEM-UJJLENYOMAT — hogy egy gombot HOLNAP is felismerjünk.
 *
 * A PROBLÉMA: a saját elnevezés eddig EGYETLEN azonosítóra épült. Ha az elemnek
 * van belső azonosítója (`viewIdResourceName`), az jó — frissítés után is
 * megvan. Ha nincs, a tartalék a képernyő-helyzet volt, 24 képpontos rácsra
 * kerekítve. Ez két bajjal jár:
 *
 *   1. Elmozdul. Más képernyőméret, forgatás, egy beszúrt sor — és a név
 *      eltűnik.
 *   2. És ami ROSSZABB: ha egy MÁSIK elem kerül ugyanabba a rácscellába, a
 *      program MAGABIZTOSAN rámondja a régi nevet. A téves név rosszabb, mint
 *      a semmilyen név — mert cselekvésre bír.
 *
 * A MEGOLDÁS: ne EGY azonosító legyen, hanem TÖBB, súlyozva. Az egyezés nem
 * igen/nem, hanem PONTSZÁM. És a program TUDJA, mennyire biztos a dolgában:
 * magas pontszámnál kimondja a nevet, közepesnél azt mondja, "valószínűleg",
 * alacsonynál hallgat. „Inkább hallgat, mint téveszt.”
 *
 * MIÉRT NEM TÖRLI EZ A RÉGIT: a pontos kulcs-találat MARAD az első út. Ez a
 * réteg csak akkor lép be, ha a pontos kulcs nem talált — vagyis ott segít,
 * ahol eddig egyszerűen elveszett a név. Meglévő elnevezés nem romolhat el tőle.
 */
object ElementFingerprint {

    /**
     * A KÜSZÖBÖK.
     *
     * FIGYELEM: ezek IDEIGLENES értékek. A terv szerint a küszöbnek MÉRÉSBŐL
     * kell jönnie, nem tippből — húsz elem, öt alkalmazás, frissítés előtt és
     * után. Amíg az a mérés nincs meg, szándékosan SZIGORÚAK: inkább maradjon
     * el egy név, mint hogy rossz hangozzon el.
     */
    const val THRESHOLD_SURE = 0.86f
    const val THRESHOLD_MAYBE = 0.68f

    /**
     * Egy elem ujjlenyomata.
     *
     * Csupa olyan adat, ami a kisegítő szolgáltatásnak amúgy is rendelkezésre
     * áll — új engedély nem kell hozzá. SZÖVEGET NEM TÁROLUNK: egy képernyőn
     * megjelenő szöveg lehet név, összeg, üzenet. A címke soha nem vihet magával
     * ilyet, sem itt, sem majd a beküldésnél.
     */
    data class Print(
        val viewId: String,
        val className: String,
        val parentClass: String,
        val siblingIndex: Int,
        val siblingCount: Int,
        val xRatio: Float,
        val yRatio: Float,
        val wRatio: Float,
        val hRatio: Float,
        val clickable: Boolean,
        val checkable: Boolean
    ) {
        /** Tárolható alak. Pontosvesszővel tagolt, hogy olcsó legyen. */
        fun serialize(): String = listOf(
            viewId, className, parentClass,
            siblingIndex.toString(), siblingCount.toString(),
            fmt(xRatio), fmt(yRatio), fmt(wRatio), fmt(hRatio),
            if (clickable) "1" else "0",
            if (checkable) "1" else "0"
        ).joinToString(";") { it.replace(";", ",") }

        private fun fmt(v: Float): String = ((v * 1000).toInt() / 1000f).toString()
    }

    fun parse(raw: String?): Print? {
        if (raw.isNullOrBlank()) return null
        val p = raw.split(";")
        if (p.size < 11) return null
        return try {
            Print(
                viewId = p[0],
                className = p[1],
                parentClass = p[2],
                siblingIndex = p[3].toInt(),
                siblingCount = p[4].toInt(),
                xRatio = p[5].toFloat(),
                yRatio = p[6].toFloat(),
                wRatio = p[7].toFloat(),
                hRatio = p[8].toFloat(),
                clickable = p[9] == "1",
                checkable = p[10] == "1"
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Ujjlenyomat készítése egy élő elemről. */
    fun of(node: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): Print? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        return try {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() <= 0 || rect.height() <= 0) return null

            val parent = node.parent
            val parentClass = parent?.className?.toString().orEmpty()
            var siblingIndex = -1
            var siblingCount = 0
            if (parent != null) {
                siblingCount = parent.childCount
                for (i in 0 until siblingCount) {
                    val child = try {
                        parent.getChild(i)
                    } catch (_: Exception) {
                        null
                    }
                    if (child != null && sameBounds(child, rect)) {
                        siblingIndex = i
                        break
                    }
                }
            }

            Print(
                viewId = node.viewIdResourceName.orEmpty(),
                className = node.className?.toString().orEmpty(),
                parentClass = parentClass,
                siblingIndex = siblingIndex,
                siblingCount = siblingCount,
                xRatio = rect.centerX().toFloat() / screenWidth,
                yRatio = rect.centerY().toFloat() / screenHeight,
                wRatio = rect.width().toFloat() / screenWidth,
                hRatio = rect.height().toFloat() / screenHeight,
                clickable = node.isClickable,
                checkable = node.isCheckable
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun sameBounds(node: AccessibilityNodeInfo, rect: Rect): Boolean = try {
        val r = Rect()
        node.getBoundsInScreen(r)
        r == rect
    } catch (_: Exception) {
        false
    }

    /**
     * MENNYIRE UGYANAZ A KETTŐ? 0.0 és 1.0 között.
     *
     * A súlyok mögötti gondolat: a belső azonosító a legerősebb bizonyíték, de
     * ha nincs, akkor sem vagyunk vakok — az osztály, a szülő, a testvérek közti
     * hely és a méret együtt majdnem ugyanolyan erős. A puszta HELYZET a
     * leggyengébb, mert az mozdul el a legkönnyebben; ezért kapja a legkisebb
     * súlyt, holott eddig ez volt az EGYETLEN tartalék.
     */
    fun score(a: Print, b: Print): Float {
        var total = 0f
        var weight = 0f

        fun add(w: Float, value: Float) {
            total += w * value
            weight += w
        }

        // A belső azonosító: ha MINDKETTŐNEK van és egyezik, az szinte döntő.
        // Ha mindkettőnek van és NEM egyezik, az viszont erős ELLENÉRV.
        if (a.viewId.isNotEmpty() && b.viewId.isNotEmpty()) {
            add(5f, if (a.viewId == b.viewId) 1f else 0f)
        }

        add(2f, if (a.className == b.className) 1f else 0f)
        add(1.5f, if (a.parentClass == b.parentClass) 1f else 0f)

        // Testvérek közti hely: csak akkor számít, ha ugyanannyi testvér van.
        // Ha az alkalmazás beszúrt egy sort, ez a jel értéktelen — ne rontsa el
        // az egyezést, inkább maradjon ki.
        if (a.siblingIndex >= 0 && b.siblingIndex >= 0 && a.siblingCount == b.siblingCount) {
            add(1.5f, if (a.siblingIndex == b.siblingIndex) 1f else 0f)
        }

        add(1f, similarity(a.wRatio, b.wRatio, 0.10f))
        add(1f, similarity(a.hRatio, b.hRatio, 0.10f))
        add(0.8f, similarity(a.xRatio, b.xRatio, 0.12f))
        add(0.8f, similarity(a.yRatio, b.yRatio, 0.12f))

        add(0.7f, if (a.clickable == b.clickable) 1f else 0f)
        add(0.5f, if (a.checkable == b.checkable) 1f else 0f)

        return if (weight <= 0f) 0f else (total / weight).coerceIn(0f, 1f)
    }

    /** 1.0, ha azonos; 0.0, ha a különbség eléri a tűrést; közte arányosan. */
    private fun similarity(a: Float, b: Float, tolerance: Float): Float {
        if (tolerance <= 0f) return if (a == b) 1f else 0f
        val diff = abs(a - b)
        return (1f - diff / tolerance).coerceIn(0f, 1f)
    }
}
