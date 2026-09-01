package com.superdl.launcher.screenreader

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * HANGTÉRKÉP — a képernyő SZERKEZETE, másfél másodperc alatt.
 *
 * MIT PÓTOL: a látó ember fél másodpercre ránéz egy képernyőre, és tudja,
 * milyen. Nem olvassa el — csak LÁTJA, hogy ez egy lista, vagy egy űrlap, vagy
 * egy majdnem üres oldal egy nagy gombbal. Ennek a fél másodpercnek nincs vak
 * megfelelője: a lépkedés lineáris és lassú, a felderítés pontos de pásztázni
 * kell. Se az egyik, se a másik nem ad ÁTTEKINTÉST.
 *
 * A LEGFONTOSABB DÖNTÉS ITT: SZERKEZET, NEM LELTÁR.
 * Negyven elem negyven kattanása nem információ, hanem zaj — pontosan olyan
 * használhatatlan, mint a végiglépkedés, csak gyorsabban az. Ezért a térkép
 * nem elemenként szólal meg, hanem CSOPORTONKÉNT: "fent egy sáv két elemmel,
 * középen egy hosszú lista, lent négy gomb."
 *
 * Ez az osztály csak a SZERKEZETET állapítja meg. Hogy az milyen hangokká
 * válik, az a ScreenMapPlayer dolga — és épp az a kérdés, amit hallás után
 * kell eldönteni.
 */
object ScreenMap {

    /** Mit lát a program egy elemben. A hangszín ezt fogja mutatni. */
    enum class Kind {
        BUTTON,   // megnyomható
        FIELD,    // beírható
        SWITCH,   // kapcsoló, jelölőnégyzet
        LIST,     // görgethető tartalom
        IMAGE,    // kép
        TEXT      // szöveg, felirat
    }

    /**
     * Egy SÁV: egymáshoz közel álló elemek csoportja.
     *
     * MIÉRT SÁV ÉS NEM RÁCS: a telefonképernyők túlnyomó része függőlegesen
     * tagolt — fejléc, tartalom, lábgombok. A vízszintes helyzetet a hang
     * bal-jobb aránya viszi, azt nem kell csoportosítani.
     */
    data class Band(
        val kind: Kind,
        val count: Int,
        /** A sáv közepe a képernyőn, 0..1 */
        val x: Float,
        val y: Float,
        /** Görgethető-e — ettől lesz egy sávból "hosszú lista". */
        val scrollable: Boolean
    )

    data class Item(val kind: Kind, val x: Float, val y: Float)

    data class Result(val bands: List<Band>, val items: List<Item>) {
        val isEmpty: Boolean get() = bands.isEmpty()
    }

    /** Ennél nagyobb függőleges hézag után új sáv kezdődik (a képernyő arányában). */
    private const val BAND_GAP = 0.055f

    /** Ennél több elemet nem szedünk össze — egy térkép nem lehet végtelen. */
    private const val MAX_ITEMS = 120

    fun of(root: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int): Result {
        if (root == null || screenWidth <= 0 || screenHeight <= 0) {
            return Result(emptyList(), emptyList())
        }

        val raw = mutableListOf<Triple<Kind, Rect, Boolean>>()
        try {
            collect(root, raw, screenWidth, screenHeight)
        } catch (_: Exception) {
        }
        if (raw.isEmpty()) return Result(emptyList(), emptyList())

        val sorted = raw.sortedBy { it.second.centerY() }

        val items = sorted.map {
            Item(
                it.first,
                it.second.centerX().toFloat() / screenWidth,
                it.second.centerY().toFloat() / screenHeight
            )
        }

        // ── SÁVOKRA BONTÁS ─────────────────────────────────────────────────
        // Új sáv ott kezdődik, ahol a függőleges hézag nagyobb a szokásosnál.
        // Ez nem tökéletes, de pont azt adja vissza, amit a szem is lát:
        // ami együtt van, az egy csoport.
        val bands = mutableListOf<Band>()
        var groupStart = 0
        for (i in 1..sorted.size) {
            val newBand = if (i == sorted.size) {
                true
            } else {
                val prev = sorted[i - 1].second.centerY().toFloat() / screenHeight
                val curr = sorted[i].second.centerY().toFloat() / screenHeight
                (curr - prev) > BAND_GAP
            }
            if (!newBand) continue

            val group = sorted.subList(groupStart, i)
            groupStart = i
            if (group.isEmpty()) continue

            // A sáv FAJTÁJA a leggyakoribb elemfajta. Ha van benne görgethető,
            // az viszi el: egy hosszú lista a képernyő legfontosabb ténye.
            val scrollable = group.any { it.third }
            val kind = if (scrollable) {
                Kind.LIST
            } else {
                group.groupingBy { it.first }.eachCount().maxByOrNull { it.value }?.key ?: Kind.TEXT
            }
            bands.add(
                Band(
                    kind = kind,
                    count = group.size,
                    x = group.map { it.second.centerX().toFloat() / screenWidth }.average().toFloat(),
                    y = group.map { it.second.centerY().toFloat() / screenHeight }.average().toFloat(),
                    scrollable = scrollable
                )
            )
        }

        return Result(bands, items)
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        out: MutableList<Triple<Kind, Rect, Boolean>>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (out.size >= MAX_ITEMS) return
        try {
            if (!node.isVisibleToUser) return
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // A képernyőn kívülre lógó vagy nulla méretű elem nem létezik a
            // felhasználó számára — ne is szóljon.
            val onScreen = rect.width() > 0 && rect.height() > 0 &&
                rect.bottom > 0 && rect.top < screenHeight &&
                rect.right > 0 && rect.left < screenWidth

            val kind = kindOf(node)
            if (onScreen && kind != null) {
                out.add(Triple(kind, rect, node.isScrollable))
                // Egy megnyomható elem BELSEJÉBE nem megyünk bele. A gomb
                // egy dolog, nem "gomb meg egy szöveg meg egy ikon" — a
                // térképnek az számít, amit a felhasználó egy dolognak érez.
                if (node.isClickable) return
            }

            for (i in 0 until node.childCount) {
                val child = try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                } ?: continue
                collect(child, out, screenWidth, screenHeight)
                if (out.size >= MAX_ITEMS) return
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Mi ez az elem — és megérdemli-e egyáltalán, hogy szóljon?
     *
     * A puszta elrendező dobozok (Layout, ViewGroup) NEM kerülnek bele: azok a
     * fejlesztőnek léteznek, a felhasználónak nem. Ha bekerülnének, a térkép
     * egy alkalmazás belső szerkezetét mutatná, nem a képernyőt.
     */
    private fun kindOf(node: AccessibilityNodeInfo): Kind? {
        val cls = node.className?.toString().orEmpty()
        val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()

        return when {
            node.isEditable || cls.contains("EditText") -> Kind.FIELD
            node.isCheckable || cls.contains("Switch") || cls.contains("CheckBox") ||
                cls.contains("RadioButton") -> Kind.SWITCH
            node.isScrollable -> Kind.LIST
            node.isClickable -> Kind.BUTTON
            cls.contains("ImageView") || cls.contains("ImageButton") -> Kind.IMAGE
            hasText -> Kind.TEXT
            else -> null
        }
    }

    /** Rövid, kimondható összefoglaló — a "Beszédes" hangnyelvhez. */
    fun speak(result: Result): String {
        if (result.isEmpty) return "Üres képernyő."
        val parts = result.bands.map { band ->
            val where = when {
                band.y < 0.28f -> "fent"
                band.y > 0.72f -> "lent"
                else -> "középen"
            }
            val what = when (band.kind) {
                Kind.LIST -> if (band.count > 6) "hosszú lista" else "lista"
                Kind.BUTTON -> if (band.count == 1) "egy gomb" else "${band.count} gomb"
                Kind.FIELD -> if (band.count == 1) "egy beírómező" else "${band.count} beírómező"
                Kind.SWITCH -> if (band.count == 1) "egy kapcsoló" else "${band.count} kapcsoló"
                Kind.IMAGE -> if (band.count == 1) "egy kép" else "${band.count} kép"
                Kind.TEXT -> if (band.count == 1) "egy felirat" else "${band.count} felirat"
            }
            "$where $what"
        }
        return parts.joinToString(", ") + "."
    }
}
