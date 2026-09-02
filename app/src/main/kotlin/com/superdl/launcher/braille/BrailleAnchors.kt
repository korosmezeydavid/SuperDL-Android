package com.superdl.launcher.braille

import android.content.Context

/**
 * AZ UJJAK HELYE — a Braille-bevitel legfontosabb trükkje.
 *
 * A PROBLÉMA, AMI NÉLKÜL EZ NEM MŰKÖDNE:
 *
 * Ha három ujjadból csak kettőt teszel le, a telefon két érintést lát — de
 * azt NEM tudja, melyik két ujjadról van szó. A mutató és a középső? A
 * középső és a gyűrűs? Ebből másik betű lenne. Látó ember odanéz; te nem
 * tudsz odanézni.
 *
 * A MEGOLDÁS: egyszer leteszed az ujjaidat, és a program megjegyzi, hol
 * vannak. Onnantól minden érintést a megtanult pontokhoz köti.
 *
 * MIÉRT KÉT DIMENZIÓ (2026-09-02-i átírás):
 *
 * Az első változat csak az X-koordinátát tárolta, mert egyetlen elrendezést
 * feltételeztem: hat ujj egy sorban. **Ez hibás feltevés volt — hat ujj
 * álló telefonon nem fér el egymás mellett.** A cella-elrendezésben (bal
 * oszlop 1-2-3, jobb oszlop 4-5-6) a pontokat a FÜGGŐLEGES helyük
 * különbözteti meg, nem a vízszintes. Egy dimenzióval ez leírhatatlan.
 *
 * Most mind a két koordinátát tároljuk, a kijelző méretéhez viszonyítva
 * (0-tól 1-ig), és a hozzárendelés ugyanaz mindkét elrendezésben.
 */
object BrailleAnchors {

    private const val PREFS = "superdl_braille"
    private const val KEY_POINTS = "anchor_points"

    /** Egy megtanult ujjhely. A `dot` az 1-től 6-ig terjedő pontszám. */
    data class Anchor(val dot: Int, val x: Float, val y: Float)

    fun load(context: Context): List<Anchor> {
        val raw = prefs(context).getString(KEY_POINTS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val bits = part.split(",")
            if (bits.size != 3) return@mapNotNull null
            val dot = bits[0].trim().toIntOrNull() ?: return@mapNotNull null
            val x = bits[1].trim().toFloatOrNull() ?: return@mapNotNull null
            val y = bits[2].trim().toFloatOrNull() ?: return@mapNotNull null
            if (dot !in 1..6) null else Anchor(dot, x, y)
        }.sortedBy { it.dot }
    }

    fun save(context: Context, anchors: List<Anchor>) {
        val text = anchors
            .filter { it.dot in 1..6 && it.x.isFinite() && it.y.isFinite() }
            .sortedBy { it.dot }
            .joinToString(";") { "${it.dot},${it.x},${it.y}" }
        prefs(context).edit().putString(KEY_POINTS, text).apply()
    }

    /** Kész vagyunk-e? Legalább az egyik oszlopnak meg kell lennie. */
    fun hasAnchors(context: Context): Boolean = load(context).size >= 3

    fun isComplete(context: Context): Boolean = load(context).size >= 6

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_POINTS).apply()
    }

    /**
     * ÉRINTÉSEK → PONTSZÁMOK.
     *
     * MIÉRT „LEGJOBB TELJES HOZZÁRENDELÉS", ÉS NEM „MINDEGYIK A
     * LEGKÖZELEBBIHEZ":
     *
     * Az első változat minden érintést külön-külön a legközelebbi ponthoz
     * kötött, és ha kettő ugyanoda esett, feladta: „nem tudtam
     * szétválasztani az ujjaidat". A tesztelő ezt sűrűn kapta, jogos
     * bosszúsággal — **a hiba az enyém volt, nem a kezéé.**
     *
     * Ez a változat az ÖSSZES lehetséges párosítást végignézi, és azt
     * választja, amelyiknél a teljes eltérés a legkisebb, úgy hogy KÉT UJJ
     * SOSEM KAPHATJA UGYANAZT A PONTOT. Hat pontnál ez legfeljebb 720
     * lehetőség — a telefonnak ez semmi, nekünk viszont az a különbség,
     * hogy soha nem kell feladni.
     *
     * Az elrendezés (cella vagy zongora) ITT NEM SZÁMÍT: a megtanult
     * pontok már magukban hordozzák, hol vannak.
     */
    fun assign(anchors: List<Anchor>, touches: List<Pair<Float, Float>>): Set<Int> =
        match(anchors, touches).keys

    /**
     * Ugyanaz, mint az `assign`, de megmondja azt is, HOL ért le az ujj.
     * Ez kell a kéz követéséhez — lásd `nudge`.
     */
    fun match(
        anchors: List<Anchor>,
        touches: List<Pair<Float, Float>>
    ): Map<Int, Pair<Float, Float>> {
        if (anchors.isEmpty() || touches.isEmpty()) return emptyMap()
        val k = minOf(touches.size, anchors.size)
        val used = BooleanArray(anchors.size)
        val current = IntArray(k)
        var bestCost = Float.MAX_VALUE
        var bestPick: IntArray? = null

        fun distance(t: Pair<Float, Float>, a: Anchor): Float {
            val dx = t.first - a.x
            val dy = t.second - a.y
            return dx * dx + dy * dy
        }

        fun search(i: Int, cost: Float) {
            if (cost >= bestCost) return          // nincs értelme tovább nézni
            if (i == k) {
                bestCost = cost
                bestPick = current.copyOf()
                return
            }
            for (j in anchors.indices) {
                if (used[j]) continue
                used[j] = true
                current[i] = j
                search(i + 1, cost + distance(touches[i], anchors[j]))
                used[j] = false
            }
        }

        search(0, 0f)
        val pick = bestPick ?: return emptyMap()
        val out = HashMap<Int, Pair<Float, Float>>(k)
        for (i in 0 until k) out[anchors[pick[i]].dot] = touches[i]
        return out
    }

    /**
     * A KÉZ ELVÁNDOROL — ÉS A PONTOKNAK KÖVETNIÜK KELL.
     *
     * MIÉRT KELL EZ:
     *
     * A megtanult ujjhelyek egy pillanatképet rögzítenek. Írás közben viszont
     * a kéz csúszik: pár betű után az ujjak már egy-két centivel odébb érnek
     * le, a pontok meg ott maradnak, ahol voltak. Egy idő után minden betű
     * mellémegy — és a felhasználó azt hiszi, ő ír rosszul. Pedig nem: a
     * program mérése avult el.
     *
     * A MEGOLDÁS: minden sikeres betű után megnézzük, mennyivel tért el az
     * ujj a saját pontjától, és a pontokat ennyivel arrébb visszük. Nem
     * ugrásszerűen: csak az eltérés egy részével, hogy egyetlen elvétett
     * koppintás ne rántsa félre az egész billentyűzetet.
     *
     * OSZLOPONKÉNT SZÁMOLUNK, mert a két kéz külön-külön vándorol.
     *
     * FORRÁS: az ötlet a Soft Braille Keyboard nevű, Apache 2.0 licencű
     * programból való (Daniel Dalton és a Google). A kódját NEM másoltuk;
     * ez a megvalósítás a miénk. Az ötletért jár az elismerés.
     */
    fun nudge(
        anchors: List<Anchor>,
        matches: Map<Int, Pair<Float, Float>>,
        rate: Float = 0.35f
    ): List<Anchor> {
        if (anchors.isEmpty() || matches.isEmpty()) return anchors

        // Oszloponkénti átlagos eltérés. A bal oszlop az 1-2-3, a jobb a 4-5-6.
        val sum = arrayOf(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f))
        val count = intArrayOf(0, 0)
        for (a in anchors) {
            val touch = matches[a.dot] ?: continue
            val side = if (a.dot <= 3) 0 else 1
            sum[side][0] += touch.first - a.x
            sum[side][1] += touch.second - a.y
            count[side]++
        }
        if (count[0] == 0 && count[1] == 0) return anchors

        // BIZTONSÁGI HATÁR: egy pont sosem ugorhat a kijelző tizedénél többet
        // egy betű alatt. Enélkül egy félrenyúlás elvinné az egész kezet.
        val maxStep = 0.10f
        return anchors.map { a ->
            val side = if (a.dot <= 3) 0 else 1
            if (count[side] == 0) return@map a
            val dx = (sum[side][0] / count[side] * rate).coerceIn(-maxStep, maxStep)
            val dy = (sum[side][1] / count[side] * rate).coerceIn(-maxStep, maxStep)
            Anchor(a.dot, (a.x + dx).coerceIn(0f, 1f), (a.y + dy).coerceIn(0f, 1f))
        }
    }

    // ── FORGATÁS ────────────────────────────────────────────────────────────
    //
    // MIÉRT KELL (2026-09-02 este, tesztelői visszajelzés):
    //
    // A kalibrálás és a próbapad KÉNYSZERÍTI a tájolást (álló vagy fekvő),
    // ezért a megtanult pontok abban a képkeretben vannak. Egy billentyűzet
    // viszont NEM tudja elforgatni a gazda-alkalmazást: ha az álló, a
    // billentyűzet is álló koordinátákat kap — miközben a felhasználó a
    // telefont ugyanúgy fekvőben fogja, ahogy kalibrált. A pontok és az ujjak
    // két különböző koordináta-rendszerben vannak, és minden betű hibás.
    //
    // A megoldás: a pontokat ÁTSZÁMOLJUK a képernyő aktuális forgatására.
    // A kéz ugyanott van; csak a számok kerete más.

    /**
     * Egy forgatott képkeret pontja → a készülék természetes (álló) kerete.
     * A `rotation` a `Display.getRotation()` értéke (0, 1, 2, 3 = 0°, 90°,
     * 180°, 270°). A 90° az óramutatóval ELLENTÉTES forgatás: a keret bal
     * széle a készülék teteje.
     */
    private fun toNatural(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        1 -> (1f - y) to x
        2 -> (1f - x) to (1f - y)
        3 -> y to (1f - x)
        else -> x to y
    }

    /** A készülék természetes kerete → egy forgatott képkeret pontja. */
    private fun fromNatural(nx: Float, ny: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        1 -> ny to (1f - nx)
        2 -> (1f - nx) to (1f - ny)
        3 -> (1f - ny) to nx
        else -> nx to ny
    }

    /**
     * A pontok átszámolása az egyik képernyő-forgatásból a másikba.
     * Ha a kettő azonos, változatlanul ad vissza.
     */
    fun rotate(anchors: List<Anchor>, fromRotation: Int, toRotation: Int): List<Anchor> {
        if (fromRotation == toRotation) return anchors
        return anchors.map { a ->
            val (nx, ny) = toNatural(a.x, a.y, fromRotation)
            val (x, y) = fromNatural(nx, ny, toRotation)
            Anchor(a.dot, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
    }

    /**
     * Melyik forgatásban készült a kalibrálás? A kalibráló képernyő a
     * beállított tartást kényszeríti: álló = 0°, fekvő = 90° (a töltő jobbra).
     */
    fun referenceRotation(context: Context): Int =
        if (BrailleLayoutPrefs.orientation(context) == BrailleLayoutPrefs.Orientation.LANDSCAPE) 1 else 0

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
