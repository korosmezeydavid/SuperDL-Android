package com.superdl.launcher.braille

/**
 * A SÍN — Braille-írás EGYETLEN ujjal.
 *
 * MIÉRT KELL EGYÁLTALÁN: a hatpontos Braille-hez hat egyidejű érintés kellene.
 * Az Android képesség-jelzője viszont csak ötöt ígér, és a régebbi, olcsóbb
 * kijelzők hármat is alig tudnak. Ha csak a hatujjas mód lenne, a felhasználók
 * nagy része SOHA nem tudna Braille-t írni ezen a programon.
 *
 * A MODELL:
 *
 *     BAL oszlop        SÍN         JOBB oszlop
 *       (1)          [1. sor]         (4)
 *       (2)          [2. sor]         (5)   <- ide teszed le az ujjad
 *       (3)          [3. sor]         (6)
 *
 * 1. Leteszed az ujjad BÁRHOVÁ. Az a pont lesz a sín közepe, a 2. sor.
 *    Nem neked kell megkeresni a felületet — a felület találja meg a kezedet.
 * 2. Fel-le mozogva a sínen sort váltasz: 1., 2., 3.
 * 3. Balra lépve leütöd a sor BAL pontját, jobbra lépve a JOBB pontját.
 * 4. Visszatérsz a sínre, és jöhet a következő pont.
 * 5. Felemeled az ujjad: a betű beíródik.
 *
 * HÁROM DÖNTÉS, AMI NÉLKÜL EZ VAKON HASZNÁLHATATLAN LENNE:
 *
 * 1. A SOR BEFAGY, amint oldalra indulsz. Ha közben az ujjad függőlegesen is
 *    elcsúszik — márpedig elcsúszik —, akkor nem a szomszédos pont íródik be.
 *    Enélkül a felhasználó MAGÁT hibáztatná a program hibájáért.
 *
 * 2. A FÜGGŐLEGES NULLPONT A LETÉTEL HELYE, és a betű végéig ugyanaz marad.
 *    Ha minden visszatéréskor újraszámolnánk, az ujj nyolc mozdulat alatt
 *    észrevétlenül elvándorolna, és a 3. sorból 2. sor lenne.
 *
 * 3. VISSZATÉRÉS ÉLESÍT. Egy pont csak akkor üthető le újra, ha közben
 *    visszaértél a sínre. Enélkül egyetlen oldalra lépés remegő kézzel
 *    három pontot is beírna.
 *
 * A „balról egyenesen jobbra" azért szabad, mert közben áthaladsz a sínen, és
 * az újra élesít. A kétoszlopos betűknél ez feleannyi mozdulat.
 *
 * EZ AZ OSZTÁLY NEM ISMER SEM KÉPERNYŐT, SEM HANGOT. Csak a mozgásból csinál
 * eseményeket; a rezgést és a beszédet a hívó intézi. Így ugyanez a sín
 * kiszolgálja a próbapadot és később az éles billentyűzetet is.
 */
class RailInput(density: Float, private val listener: Listener) {

    interface Listener {
        /** Új sorba értél a sínen (1, 2 vagy 3). */
        fun onRow(row: Int)

        /** Egy pont leütve (added = true) vagy visszavonva (added = false). */
        fun onDot(dot: Int, added: Boolean)

        /** Az egész cella törölve — az ujjat messze lehúztad. */
        fun onCleared()

        /** Megálltál: mondd be, hol vagy és mi van beírva. */
        fun onIdle(row: Int, dots: Set<Int>)
    }

    companion object {
        /** Ennyi ideig kell mozdulatlannak lenned, hogy megmondjuk, hol vagy. */
        const val IDLE_MS = 400L

        private const val ROW_STEP_DP = 46f
        private const val SIDE_STRIKE_DP = 56f
        private const val RETURN_DP = 18f

        /** A 3. soron ennyivel túl lehúzva az egész cella törlődik. */
        private const val CLEAR_DP = 165f
    }

    private val rowStep = ROW_STEP_DP * density
    private val sideStrike = SIDE_STRIKE_DP * density
    private val returnBand = RETURN_DP * density
    private val clearPull = CLEAR_DP * density

    private var originX = 0f
    private var originY = 0f

    /** A jelenlegi sor. Letételkor mindig a 2. — a sín közepe. */
    var row = 2
        private set

    /** Az eddig leütött pontok. */
    private val dots = mutableSetOf<Int>()

    /** Igaz, amíg oldalt vagy: ilyenkor a sor be van fagyva. */
    private var offRail = false

    /** Igaz, ha a mostani oldalra lépés már leütött egy pontot. */
    private var struck = false

    /** Már törölt ebben a húzásban? Egy lehúzás egyszer töröl, nem folyamatosan. */
    private var cleared = false

    fun begin(x: Float, y: Float) {
        originX = x
        originY = y
        row = 2
        dots.clear()
        offRail = false
        struck = false
        cleared = false
    }

    /**
     * Egy ujj elmozdult. A visszatérési érték csak annyit mond, történt-e
     * valami — a hívó ebből tudja, hogy újra kell indítania a megállás-órát.
     */
    fun move(x: Float, y: Float): Boolean {
        val dx = x - originX
        val dy = y - originY
        var changed = false

        // ── TÖRLÉS: messze lehúzva, jóval a 3. sor alatt ────────────────────
        if (dy > clearPull) {
            if (!cleared) {
                cleared = true
                dots.clear()
                row = 2
                offRail = false
                struck = false
                listener.onCleared()
                changed = true
            }
            return changed
        }
        cleared = false

        val ax = kotlin.math.abs(dx)

        // ── VISSZATÉRÉS A SÍNRE: itt élesedik újra minden ───────────────────
        if (ax < returnBand) {
            offRail = false
            struck = false

            // A SOR CSAK A SÍNEN VÁLTOZHAT. Oldalt befagy — lásd fent.
            val newRow = (2 + Math.round(dy / rowStep)).coerceIn(1, 3)
            if (newRow != row) {
                row = newRow
                listener.onRow(row)
                changed = true
            }
            return changed
        }

        // ── OLDALRA LÉPÉS ──────────────────────────────────────────────────
        offRail = true
        if (!struck && ax >= sideStrike) {
            struck = true
            // Bal oszlop: 1, 2, 3. Jobb oszlop: 4, 5, 6. A sor adja, melyik.
            val dot = if (dx < 0) row else row + 3
            val added = dots.add(dot)
            if (!added) dots.remove(dot)   // UGYANOTT MÉGEGYSZER = VISSZAVONÁS
            listener.onDot(dot, added)
            changed = true
        }
        return changed
    }

    /** Megálltál. A hívó hívja, ha IDLE_MS-ig nem mozdultál. */
    fun idle() = listener.onIdle(row, dots.toSet())

    /**
     * Felengedted az ujjad: itt a betű.
     *
     * ÜRES CELLA = SZÓKÖZ. Ez a Braille-ben megszokott: leteszed, felengeded,
     * és nem ütöttél le pontot. Nem kell új mozdulatot tanulni rá.
     */
    fun finish(): Int = BrailleTable.cell(*dots.toIntArray())

    /** Amit a megálláskor kimondunk. */
    fun describe(row: Int, dots: Set<Int>): String {
        val where = "$row. sor."
        if (dots.isEmpty()) return "$where Még nincs pont."
        return "$where Beírva: ${dots.sorted().joinToString(", ")}."
    }
}
