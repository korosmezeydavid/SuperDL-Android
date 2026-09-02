package com.superdl.launcher.braille

/**
 * A BRAILLE-BEVITEL ÁLLAPOTA — jelzők, szám-mód, nagybetű.
 *
 * MIÉRT KELL ÁLLAPOT EGYÁLTALÁN: a Braille-ben egy cella jelentése FÜGG
 * ATTÓL, MI VOLT ELŐTTE. A szám-jelző (3-4-5-6) után az `a` már nem `a`,
 * hanem `1`. A nagybetű-jelző (4-6) után a következő betű nagy.
 *
 * ÉS VAN EGY MAGYAR SAJÁTOSSÁG, ami miatt ez itt nem elméleti kérdés:
 * az irodalmi számjegyek ütköznek a betűkkel. Az `1-6` egyszerre jelenti az
 * `é` betűt és az `1` számjegyet. Papíron a szövegkörnyezet dönt — bevitelnél
 * viszont **a felhasználónak kell megmondania**, mit ír, mert a program nem
 * találhatja ki. Egy rosszul kitalált karakter csendben elrontja a szöveget,
 * és vakon ezt a legnehezebb észrevenni.
 *
 * A DÖNTÉSÜNK: a betű az alapértelmezett. Számot a szám-jelzővel írsz
 * (3-4-5-6), és onnantól SZÁM MÓDBAN vagy, amíg szóköz vagy betű nem jön.
 * Ez a „számítógépes" írásmód, és egyértelmű.
 */
class BrailleInputState {

    companion object {
        /**
         * Ezek a jelek NEM szakítják meg a szám módot, ha szám közben jönnek.
         * A hivatalos magyar LibLouis-tábla `midendnumericmodechars ,:.-`
         * sora alapján. Innen jön, hogy a „3,5" és a „2026.09.02" egyetlen
         * szám-jelzővel leírható.
         */
        val NUMBER_KEEPING_CHARS = setOf(',', ':', '.', '-')
    }

    /** A mód emberi neve — a „hol vagyok" lekérdezéshez. A RÉTEG MINDIG BENNE VAN. */
    fun speakMode(): String {
        val base = when {
            symbolLock -> "jel mód zárolva"
            symbolPending -> "jel mód, egy jelre"
            numberMode -> "szám mód"
            else -> "betű mód"
        }
        return when {
            capsWord -> "$base, nagybetűs szó"
            capsPending -> "$base, nagybetű következik"
            else -> base
        }
    }

    /** A következő EGY cella a jel-rétegről jön (5-ös pont egyszer). */
    var symbolPending: Boolean = false
        private set

    /** MINDEN cella a jel-rétegről jön, amíg ki nem kapcsolod (5-ös pont kétszer). */
    var symbolLock: Boolean = false
        private set

    /** Szám módban vagyunk-e (a szám-jelző után). */
    var numberMode: Boolean = false
        private set

    /** A következő EGY betű nagy lesz. */
    var capsPending: Boolean = false
        private set

    /**
     * AZ EGÉSZ SZÓ NAGY LESZ, a szóközig — a nagybetű-jelző KÉTSZER egymás
     * után (Alph szabálya, 2026-09-02). Ugyanaz az elv, mint mindenhol:
     * egyszer = egy cellára, kétszer = amíg vissza nem kapcsolod.
     */
    var capsWord: Boolean = false
        private set

    /**
     * Egy cella feldolgozása.
     *
     * @return amit a szövegmezőbe kell írni, vagy `null`, ha a cella csak
     *         jelző volt (szám-jelző, nagybetű-jelző) és nem ír ki semmit.
     */
    fun consume(cellMask: Int): Result {
        // ── A JEL-RÉTEG ELŐTAGJA: 5-ös pont ─────────────────────────────
        if (cellMask == BrailleTable.SYMBOL_SIGN) {
            return when {
                // Zárolva volt → kikapcsol. Ez a kijárat.
                symbolLock -> {
                    symbolLock = false
                    Result(null, "betű mód")
                }
                // Másodszor egymás után → zárol.
                symbolPending -> {
                    symbolPending = false
                    symbolLock = true
                    Result(null, "jel mód zárolva")
                }
                else -> {
                    symbolPending = true
                    Result(null, "jel mód")
                }
            }
        }

        // ── JEL-RÉTEG: a következő cella nem betű, hanem jel ─────────────
        if (symbolPending || symbolLock) {
            symbolPending = false
            if (cellMask == 0) {
                // A szóköz a zárolást is oldja — mint mindent.
                symbolLock = false
                numberMode = false
                capsPending = false
                capsWord = false
                return Result(" ", "szóköz. betű mód")
            }
            val sym = BrailleTable.SYMBOLS[cellMask]
            if (sym != null) return Result(sym.toString(), speakName(sym.toString()))
            // Ismeretlen jel: NEM írunk semmit, és megmondjuk, miért. Egy
            // csendben elnyelt cella vakon a legrosszabb hiba.
            return Result(
                null,
                "nincs ilyen jel: ${BrailleTable.speakDots(cellMask)}" +
                    if (symbolLock) "" else ". betű mód"
            )
        }

        // ── Jelzők ──────────────────────────────────────────────────────
        if (cellMask == BrailleTable.NUMBER_SIGN) {
            numberMode = true
            return Result(null, "szám mód")
        }
        if (cellMask == BrailleTable.CAPS_LETTER) {
            // MÁSODSZOR EGYMÁS UTÁN = nagybetűs szó. A két állapotnak KÜLÖN
            // hangja van: ha ugyanazt mondaná, nem tudnád, hányat ütöttél.
            if (capsPending && !capsWord) {
                capsPending = false
                capsWord = true
                return Result(null, "nagybetűs szó")
            }
            capsPending = true
            return Result(null, "nagybetű")
        }
        if (cellMask == 0) {
            // ÜRES CELLA = SZÓKÖZ. És a szóköz LEZÁRJA a szám módot ÉS a
            // nagybetűs szót — ez a Braille szabálya, nem a mi találmányunk.
            numberMode = false
            capsPending = false
            capsWord = false
            return Result(" ", "szóköz")
        }

        // ── Szám mód ────────────────────────────────────────────────────
        if (numberMode) {
            // Az a–j betűk a számjegyek. j = 0 (a felhasználó megerősítette).
            val digit = BrailleTable.DIGITS_AFTER_SIGN[cellMask]
            if (digit != null) return Result(digit.toString(), digit.toString())

            // A VESSZŐ, KETTŐSPONT, PONT ÉS KÖTŐJEL BENT TARTJA A SZÁM MÓDOT.
            //
            // Ez nem a mi ötletünk: a hivatalos magyar LibLouis-tábla mondja
            // ki (`midendnumericmodechars ,:.-`). Ezért írható le EGYETLEN
            // szám-jelzővel a „3,5", a „12:30", a „2026.09.02" és a „10-15".
            val keeper = BrailleTable.PUNCTUATION[cellMask]
            if (keeper != null && keeper in NUMBER_KEEPING_CHARS) {
                return Result(keeper.toString(), speakName(keeper.toString()))
            }

            // Bármi más (k–z betű) kilépteti a szám módot, és betűként
            // íródik — így a „12kg" is leírható szóköz nélkül.
            numberMode = false
            val text = BrailleTable.resolveText(cellMask)
            if (text != null) {
                val out = applyCaps(text)
                return Result(out, "betű mód. ${speakName(out)}")
            }
            return Result(null, "ismeretlen cella: ${BrailleTable.speakDots(cellMask)}")
        }

        // ── A MÁSODIK ELŐTAG: nagybetű-jelző + vessző / zárójel = > [ ] ──
        // A szabvány így írja. A nagybetű-jelző betűre vár; ez a három cella
        // nem betű, tehát nem vesz el semmit — csak értelmet kap.
        if (capsPending && !capsWord) {
            val sym = BrailleTable.CAPS_SYMBOLS[cellMask]
            if (sym != null) {
                capsPending = false
                return Result(sym.toString(), speakName(sym.toString()))
            }
        }

        // ── Betű vagy írásjel ───────────────────────────────────────────
        val text = BrailleTable.resolveText(cellMask)
            ?: return Result(null, "ismeretlen cella: ${BrailleTable.speakDots(cellMask)}")

        val out = applyCaps(text)
        return Result(out, speakName(out))
    }

    /**
     * A NAGYBETŰ ALKALMAZÁSA — szövegre, mert a kétjegyű betű két karakter.
     *
     * Egy betűre szóló jelzőnél csak az ELSŐ karakter lesz nagy („Cs"), a
     * nagybetűs szónál az egész („CS"). Ez a magyar helyesírás szabálya, és
     * ha fordítva csinálnánk, a „Csaba" „CSaba" lenne.
     */
    private fun applyCaps(text: String): String {
        if (text.isEmpty() || !text[0].isLetter()) {
            capsPending = false
            return text
        }
        val out = when {
            capsWord -> text.uppercase()
            capsPending -> text.replaceFirstChar { it.uppercaseChar() }
            else -> text
        }
        capsPending = false
        return out
    }

    /** Minden jelző elengedése — új szó, vagy a bevitel újraindítása. */
    fun reset() {
        numberMode = false
        capsPending = false
        capsWord = false
        symbolPending = false
        symbolLock = false
    }

    /**
     * @param text amit ki kell írni (null = a cella csak jelző volt)
     * @param speak amit ki kell MONDANI — ez nem mindig ugyanaz, mert a
     *              pontot vagy a szóközt hallani kell, nem „elolvasni"
     */
    data class Result(val text: String?, val speak: String)

    private fun speakName(out: String): String = when (out) {
        " " -> "szóköz"
        "." -> "pont"
        "," -> "vessző"
        "?" -> "kérdőjel"
        "!" -> "felkiáltójel"
        "-" -> "kötőjel"
        ":" -> "kettőspont"
        ";" -> "pontosvessző"
        "'" -> "aposztróf"
        "(" -> "nyitó zárójel"
        ")" -> "csukó zárójel"
        "„" -> "nyitó idézőjel"
        "”" -> "csukó idézőjel"
        "/" -> "per jel"
        "@" -> "kukac"
        "%" -> "százalék"
        "+" -> "plusz"
        "=" -> "egyenlő"
        "*" -> "csillag"
        "#" -> "kettőskereszt"
        "$" -> "dollár"
        "<" -> "kisebb"
        ">" -> "nagyobb"
        "~" -> "hullám"
        "|" -> "függőleges vonal"
        "{" -> "nyitó kapcsos zárójel"
        "}" -> "csukó kapcsos zárójel"
        "[" -> "nyitó szögletes zárójel"
        "]" -> "csukó szögletes zárójel"
        "\\" -> "fordított per jel"
        "^" -> "kalap"
        "`" -> "visszafelé ékezet"
        else -> out
    }
}
