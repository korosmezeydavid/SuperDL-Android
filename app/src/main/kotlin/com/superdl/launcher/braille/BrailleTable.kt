package com.superdl.launcher.braille

/**
 * MAGYAR IRODALMI BRAILLE — HATPONTOS TÁBLA.
 *
 * FORRÁS: a LibLouis `hu-hu-g1.ctb` és `hu-chardefs.cti` táblája, ahogy az
 * NVDA-val települ. Karbantartó: **Hammer Attila, Infoalap**
 * (hammer.attila@infoalap.hu, www.infoalap.hu).
 *
 * MIÉRT INNEN, ÉS NEM FEJBŐL: a magyar Braille KÉT PONTON ELTÉR a nemzetközi
 * alaptól, és ezt fejből senki nem tudná:
 *
 *   - **q = 1-2-3-4-6**  (a nemzetközi alapban 1-2-3-4-5)
 *   - **z = 1-2-6**      (a nemzetközi alapban 1-3-5-6)
 *
 * A LibLouis-tábla ezt a két betűt KÉTSZER is felüldefiniálja — egyszer az
 * `include` előtt, egyszer utána. Ez nem elírás, hanem szándék.
 *
 * A PONTOK SZÁMOZÁSA:
 * ```
 *     1  4
 *     2  5
 *     3  6
 * ```
 * A cellát egyetlen `Int` bitmaszk írja le: az 1-es pont a 0. bit, a 6-os
 * pont az 5. bit. Így a hat ujj állapota egyetlen szám, és az összehasonlítás
 * ingyen van — ez a gesztus-kezelés forró útvonalán számít.
 */
object BrailleTable {

    /** Egy cella a pontok listájából. `cell(1, 3)` = az 1-es és 3-as pont. */
    fun cell(vararg dots: Int): Int {
        var mask = 0
        for (d in dots) if (d in 1..6) mask = mask or (1 shl (d - 1))
        return mask
    }

    /** Be van-e nyomva ez a pont a cellában? */
    fun hasDot(cellMask: Int, dot: Int): Boolean =
        dot in 1..6 && (cellMask and (1 shl (dot - 1))) != 0

    /** A cella pontjai emberi felsorolásban: „1-es, 3-as". */
    fun speakDots(cellMask: Int): String {
        val dots = (1..6).filter { hasDot(cellMask, it) }
        if (dots.isEmpty()) return "üres cella"
        return dots.joinToString(", ") { "$it-es" }
    }

    // ── JELZŐK ──────────────────────────────────────────────────────────────

    /** Szám-jelző: 3-4-5-6. Utána az a–j betűk 1–0 számjegyet jelentenek. */
    val NUMBER_SIGN = cell(3, 4, 5, 6)

    /** Nagybetű-jelző EGY betűre: 4-6. */
    val CAPS_LETTER = cell(4, 6)

    /**
     * A LibLouis `nonumsign 6`: az „irodalmi" számjegyek úgy készülnek, hogy
     * az a–j betűhöz hozzáadod a 6-os pontot — szám-jelző NÉLKÜL.
     */
    val LITERARY_DIGIT_MARK = 6

    // ── BETŰK ───────────────────────────────────────────────────────────────

    /**
     * A 26 alapbetű + a 9 magyar ékezetes.
     *
     * A `q` és a `z` MAGYAR értéke van itt, nem a nemzetközi — lásd a fájl
     * fejlécét. Ha valaki „kijavítja" őket a nemzetközire, a magyar szövegek
     * elromlanak, és ezt hetekig senki nem venné észre.
     */
    val LETTERS: Map<Int, Char> = mapOf(
        cell(1) to 'a',
        cell(1, 2) to 'b',
        cell(1, 4) to 'c',
        cell(1, 4, 5) to 'd',
        cell(1, 5) to 'e',
        cell(1, 2, 4) to 'f',
        cell(1, 2, 4, 5) to 'g',
        cell(1, 2, 5) to 'h',
        cell(2, 4) to 'i',
        cell(2, 4, 5) to 'j',
        cell(1, 3) to 'k',
        cell(1, 2, 3) to 'l',
        cell(1, 3, 4) to 'm',
        cell(1, 3, 4, 5) to 'n',
        cell(1, 3, 5) to 'o',
        cell(1, 2, 3, 4) to 'p',
        cell(1, 2, 3, 4, 6) to 'q',   // MAGYAR eltérés
        cell(1, 2, 3, 5) to 'r',
        cell(2, 3, 4) to 's',
        cell(2, 3, 4, 5) to 't',
        cell(1, 3, 6) to 'u',
        cell(1, 2, 3, 6) to 'v',
        cell(2, 4, 5, 6) to 'w',
        cell(1, 3, 4, 6) to 'x',
        cell(1, 3, 4, 5, 6) to 'y',
        cell(1, 2, 6) to 'z',         // MAGYAR eltérés

        // Ékezetes magyar betűk
        cell(4) to 'á',
        cell(1, 6) to 'é',
        cell(3, 4) to 'í',
        cell(2, 4, 6) to 'ó',
        cell(1, 2, 3, 4, 5) to 'ö',
        cell(1, 2, 4, 5, 6) to 'ő',
        cell(3, 4, 6) to 'ú',
        cell(1, 2, 3, 5, 6) to 'ü',
        cell(2, 3, 4, 5, 6) to 'ű'
    )

    // ── ÖSSZETETT BETŰK ─────────────────────────────────────────────────────

    /**
     * A HÉT MAGYAR KÉTJEGYŰ BETŰ — mindegyik EGYETLEN cella.
     *
     * MIÉRT HIÁNYZOTT EDDIG (2026-09-02, tesztelői visszajelzés): a táblát a
     * LibLouis `hu-chardefs.cti` fájlból írtam ki, ami a KARAKTEREKET
     * definiálja. Az összetett betűk viszont a `hu-hu-g1.ctb` `always`
     * szabályaiban vannak, mert a LibLouis szemében azok két karakterből
     * álló szavak. Nem néztem meg a második fájlt. A tesztelő pontosan
     * ezeket sorolta fel — és a LibLouis mind az ötöt megerősítette, plusz a
     * két hiányzót (ny, sz).
     *
     * A tanulság: **a tábla két fájl, nem egy**, és a második az irodalmi
     * szabályokat tartalmazza (idézőjel, zárójel, összetett betűk).
     *
     * Ezek a cellák a betű-rétegen szabadok voltak — csak a szám módbeli
     * irodalmi számjegyekkel esnek egybe, ami nem baj: szám módban szám,
     * betű módban betű.
     */
    val DIGRAPHS: Map<Int, String> = mapOf(
        cell(1, 4, 6) to "cs",
        cell(1, 4, 5, 6) to "gy",
        cell(4, 5, 6) to "ly",
        cell(1, 2, 4, 6) to "ny",
        cell(1, 5, 6) to "sz",
        cell(1, 2, 5, 6) to "ty",
        cell(3, 4, 5) to "zs"
    )

    // ── SZÁMJEGYEK ──────────────────────────────────────────────────────────

    /**
     * SZÁM-JELZŐ UTÁN: az a–j betűk jelentik az 1–9 és 0 számjegyeket.
     * Ez a „számítógépes" írásmód, és EGYÉRTELMŰ.
     */
    val DIGITS_AFTER_SIGN: Map<Int, Char> = mapOf(
        cell(1) to '1',
        cell(1, 2) to '2',
        cell(1, 4) to '3',
        cell(1, 4, 5) to '4',
        cell(1, 5) to '5',
        cell(1, 2, 4) to '6',
        cell(1, 2, 4, 5) to '7',
        cell(1, 2, 5) to '8',
        cell(2, 4) to '9',
        cell(2, 4, 5) to '0'
    )

    /**
     * IRODALMI SZÁMJEGYEK: a–j + 6-os pont, szám-jelző nélkül.
     *
     * FIGYELEM — EZ ÜTKÖZIK BETŰKKEL, és ez nem a mi hibánk, hanem magának a
     * magyar Braille-nek a tulajdonsága:
     *
     * | pontok | irodalmi szám | de ugyanez a betű |
     * |---|---|---|
     * | 1-6 | 1 | **é** |
     * | 1-2-6 | 2 | **z** |
     * | 2-4-6 | 9 | **ó** |
     * | 3-4-6 | 0 | **ú** |
     *
     * Papíron a szövegkörnyezet dönt. BEVITELNÉL nem dönthet a program
     * magától — ezért a `BrailleInputState` szám-módot használ, és a
     * felhasználó mondja meg, mit ír.
     */
    val LITERARY_DIGITS: Map<Int, Char> = mapOf(
        cell(1, 6) to '1',
        cell(1, 2, 6) to '2',
        cell(1, 4, 6) to '3',
        cell(1, 4, 5, 6) to '4',
        cell(1, 5, 6) to '5',
        cell(1, 2, 4, 6) to '6',
        cell(1, 2, 4, 5, 6) to '7',
        cell(1, 2, 5, 6) to '8',
        cell(2, 4, 6) to '9',
        cell(3, 4, 6) to '0'
    )

    // ── ÍRÁSJELEK ───────────────────────────────────────────────────────────

    /**
     * A BETŰ-RÉTEG ÍRÁSJELEI — Alph (2026-09-02) és a LibLouis `hu-hu-g1.ctb`
     * EGYBEHANGZÓ értékei.
     *
     * A korábbi lista a `hu-chardefs.cti`-ből jött, és több ponton MÁS volt
     * (pont = 3, felkiáltójel = 5, zárójel = 2-3-6 / 3-5-6). A g1 tábla
     * `always` szabályai felülírják ezeket — és pontosan azt mondják, amit
     * Alph: pont 2-5-6, felkiáltójel 2-3-5, idézőjel 2-3-6 / 3-5-6, zárójel
     * 2-3-4-6 / 1-3-5-6.
     *
     * AMI INNEN KIKERÜLT — `/`, `+`, `*`, `=`, `<`, `~`, `%`, `` ` ``,
     * `$` —, az a JEL-RÉTEGRE megy (lásd a táblázat-tervet). Nem vesztek el:
     * a betű-rétegen nem volt nekik hely az összetett betűk mellett.
     */
    val PUNCTUATION: Map<Int, Char> = mapOf(
        cell(2) to ',',
        cell(2, 5, 6) to '.',
        cell(2, 3, 5) to '!',
        // KÉRDŐJEL: 2-6. Egy körben 3-5 is felmerült, de Alph visszavonta,
        // és a LibLouis mind a három fájlja 2-6-ot mond. Két forrás egyezik.
        cell(2, 6) to '?',
        cell(2, 3) to ';',
        cell(2, 5) to ':',
        cell(3, 6) to '-',
        cell(6) to '\'',
        cell(4, 5) to '@',
        cell(2, 3, 6) to '„',      // bal idézőjel
        cell(3, 5, 6) to '”',      // jobb idézőjel
        cell(2, 3, 4, 6) to '(',
        cell(1, 3, 5, 6) to ')'
    )

    // ── A JEL-RÉTEG ─────────────────────────────────────────────────────────

    /**
     * JEL-JELZŐ: az 5-ös pont. Utána a következő cella a jel-rétegről jön.
     *
     * NEM MI TALÁLTUK KI: a magyar Braille szabványa (LibLouis `hu-hu-g1.ctb`)
     * pontosan így írja a ritkább jeleket — `$` = 5 + d, `/` = 5 + vessző,
     * `{` = 5 + nyitó zárójel. Alph 2026-09-02-án jóváhagyta.
     *
     * A SZABÁLY ugyanaz, mint a nagybetűnél: egyszer = a következő EGY
     * cellára, kétszer = zárolva, amíg újra le nem ütöd vagy szóköz nem jön.
     */
    val SYMBOL_SIGN = cell(5)

    /**
     * A jel-réteg tartalma: 5-ös pont UTÁN ez a cella ezt a jelet adja.
     *
     * Ahol a szabvány mond valamit, az van; ahol nem (`+`, `*`, `#`), ott Alph
     * saját kiosztása. A hiányzó jelek (`=`, `&`, `€`, `°`, `§`, `%`) helye
     * még nincs eldöntve — SZÁNDÉKOSAN nincsenek itt, nehogy egy tipp
     * megszokássá váljon.
     */
    val SYMBOLS: Map<Int, Char> = mapOf(
        cell(1, 4, 5) to '$',           // 5 + d      (szabvány)
        cell(2) to '/',                 // 5 + vessző (szabvány)
        cell(1, 3) to '<',              // 5 + k      (szabvány)
        cell(2, 3, 4, 5) to '~',        // 5 + t      (szabvány)
        cell(1, 2, 4, 5) to '|',        // 5 + g      (szabvány)
        cell(2, 3, 4, 6) to '{',        // 5 + (      (szabvány)
        cell(1, 3, 5, 6) to '}',        // 5 + )      (szabvány)
        cell(1, 6) to '\\',             // 5 + é      (szabvány)
        cell(1, 2, 4) to '^',           // 5 + f      (szabvány)
        cell(1, 4) to '`',              // 5 + c      (szabvány)
        cell(2, 3, 5) to '+',           // Alph
        cell(2, 3, 5, 6) to '*',        // Alph
        cell(3, 4, 5, 6) to '#'         // Alph
    )

    /**
     * A MÁSODIK ELŐTAG: a nagybetű-jelző (4-6) után ez a három cella nem
     * betű, hanem jel — a szabvány szerint. Mivel a nagybetű-jelző betűre vár,
     * egy írásjel utána amúgy sem jelentene semmit; ez a három kap értelmet.
     */
    val CAPS_SYMBOLS: Map<Int, Char> = mapOf(
        cell(2) to '>',                 // 4-6 + vessző
        cell(2, 3, 4, 6) to '[',        // 4-6 + (
        cell(1, 3, 5, 6) to ']'         // 4-6 + )
    )

    // ── FELOLDÁS ────────────────────────────────────────────────────────────

    /**
     * Mi ez a cella BETŰ módban?
     *
     * A sorrend nem véletlen: előbb a betű, csak utána az írásjel. Ahol
     * ütközés van (á és idézőjel), ott a betű nyer — mert az a gyakoribb,
     * és mert egy elveszett betű használhatatlanná tenné az írást.
     */
    fun letterOrPunctuation(cellMask: Int): Char? =
        LETTERS[cellMask] ?: PUNCTUATION[cellMask]

    /**
     * Ugyanaz, de SZÖVEGKÉNT — mert az összetett betűk két karakterből
     * állnak. Sorrend: egyjegyű betű, kétjegyű betű, írásjel.
     */
    fun resolveText(cellMask: Int): String? =
        LETTERS[cellMask]?.toString()
            ?: DIGRAPHS[cellMask]
            ?: PUNCTUATION[cellMask]?.toString()

    /** Mi ez a cella SZÁM módban? */
    fun digit(cellMask: Int): Char? =
        DIGITS_AFTER_SIGN[cellMask] ?: LITERARY_DIGITS[cellMask]

    /** Van-e egyáltalán jelentése ennek a cellának? */
    fun isKnown(cellMask: Int): Boolean =
        LETTERS.containsKey(cellMask) ||
            DIGRAPHS.containsKey(cellMask) ||
            PUNCTUATION.containsKey(cellMask) ||
            cellMask == NUMBER_SIGN ||
            cellMask == CAPS_LETTER ||
            cellMask == SYMBOL_SIGN
}
