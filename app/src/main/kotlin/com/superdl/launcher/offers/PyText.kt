package com.superdl.launcher.offers

/**
 * A Python szöveg-függvényeinek pontos Kotlin-párjai azokhoz a gyűjtőkhöz,
 * amelyek a Windows-modul kimenetét BETŰRE PONTOSAN adják vissza (Pepco,
 * Libri, Illatorium).
 *
 * MIÉRT külön, és nem az OfferText: az OfferText kényelmi tisztító (pl. a
 * névvel jelölt HTML-entitásokat kis- és nagybetűtől függetlenül oldja fel),
 * a Python `html.unescape` viszont kis-nagybetű-érzékeny, és a pontosvessző
 * nélküli régi entitásokat („&amp" , „&eacute") is feloldja. Ha a két
 * programnak ugyanazt kell mondania ugyanarról a termékről, a Python
 * szabályait kell követni — azokat az OfferText megváltoztatása nélkül.
 */
internal object PyText {

    // ---- html.unescape -------------------------------------------------

    /** A Python `html._charref` mintája. */
    private val CHARREF = Regex("&(#[0-9]+;?|#[xX][0-9a-fA-F]+;?|[^\\t\\n\\u000C <&#;]{1,32};?)")

    /** A Latin-1 felső fele (U+00A0–U+00FF) HTML-nevei, sorrendben. */
    private val LATIN1 = listOf(
        "nbsp", "iexcl", "cent", "pound", "curren", "yen", "brvbar", "sect",
        "uml", "copy", "ordf", "laquo", "not", "shy", "reg", "macr",
        "deg", "plusmn", "sup2", "sup3", "acute", "micro", "para", "middot",
        "cedil", "sup1", "ordm", "raquo", "frac14", "frac12", "frac34", "iquest",
        "Agrave", "Aacute", "Acirc", "Atilde", "Auml", "Aring", "AElig", "Ccedil",
        "Egrave", "Eacute", "Ecirc", "Euml", "Igrave", "Iacute", "Icirc", "Iuml",
        "ETH", "Ntilde", "Ograve", "Oacute", "Ocirc", "Otilde", "Ouml", "times",
        "Oslash", "Ugrave", "Uacute", "Ucirc", "Uuml", "Yacute", "THORN", "szlig",
        "agrave", "aacute", "acirc", "atilde", "auml", "aring", "aelig", "ccedil",
        "egrave", "eacute", "ecirc", "euml", "igrave", "iacute", "icirc", "iuml",
        "eth", "ntilde", "ograve", "oacute", "ocirc", "otilde", "ouml", "divide",
        "oslash", "ugrave", "uacute", "ucirc", "uuml", "yacute", "thorn", "yuml"
    )

    /**
     * A pontosvessző NÉLKÜL is érvényes (régi) nevek — a Python `html5`
     * táblájának „;" nélküli kulcsai: a Latin-1 nevek + AMP, COPY, GT, LT,
     * QUOT, REG, amp, gt, lt, quot.
     */
    private val LEGACY: Map<String, String> = HashMap<String, String>().apply {
        LATIN1.forEachIndexed { i, n -> put(n, (0xA0 + i).toChar().toString()) }
        put("amp", "&"); put("AMP", "&"); put("lt", "<"); put("LT", "<")
        put("gt", ">"); put("GT", ">"); put("quot", "\""); put("QUOT", "\"")
        put("COPY", "©"); put("REG", "®")
    }

    /**
     * Csak pontosvesszővel érvényes nevek. A teljes HTML5-tábla 2231 elem;
     * itt a magyar boltoldalakon ténylegesen előforduló írásjelek és a
     * magyar kettős ékezetesek (ő, ű) vannak. Ami ebben nincs, az — a
     * Pythonnal ellentétben — feloldatlanul marad.
     */
    private val NAMED: Map<String, String> = mapOf(
        "apos" to "'", "ndash" to "–", "mdash" to "—", "hellip" to "…",
        "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘",
        "rsquo" to "’", "sbquo" to "‚", "laquo" to "«", "raquo" to "»",
        "euro" to "€", "trade" to "™", "bull" to "•", "middot" to "·",
        "Odblac" to "Ő", "odblac" to "ő", "Udblac" to "Ű", "udblac" to "ű",
        "thinsp" to " ", "ensp" to " ", "emsp" to " ", "zwnj" to "‌",
        "zwj" to "‍", "lrm" to "‎", "rlm" to "‏", "dagger" to "†",
        "Dagger" to "‡", "permil" to "‰", "prime" to "′", "Prime" to "″",
        "hyphen" to "‐", "dash" to "‐", "minus" to "−", "NewLine" to "\n",
        "Tab" to "\t", "excl" to "!", "num" to "#", "dollar" to "$", "percnt" to "%",
        "lpar" to "(", "rpar" to ")", "ast" to "*", "plus" to "+", "comma" to ",",
        "period" to ".", "sol" to "/", "colon" to ":", "semi" to ";", "equals" to "=",
        "quest" to "?", "commat" to "@", "lsqb" to "[", "rsqb" to "]", "bsol" to "\\",
        "lowbar" to "_", "grave" to "`", "lcub" to "{", "rcub" to "}", "verbar" to "|",
        "vert" to "|", "check" to "✓", "star" to "☆", "starf" to "★",
        "hearts" to "♥", "larr" to "←", "rarr" to "→", "uarr" to "↑",
        "darr" to "↓", "harr" to "↔", "deg" to "°", "frasl" to "⁄",
        "OElig" to "Œ", "oelig" to "œ", "Scaron" to "Š", "scaron" to "š",
        "Zcaron" to "Ž", "zcaron" to "ž", "Yuml" to "Ÿ", "fnof" to "ƒ",
        "circ" to "ˆ", "tilde" to "˜", "lsaquo" to "‹", "rsaquo" to "›"
    )

    /** A Python `html._invalid_charrefs` (a Windows-1252 szerinti javítás). */
    private val INVALID_CHARREFS: Map<Int, String> = mapOf(
        0x00 to "�", 0x0d to "\r", 0x80 to "€", 0x81 to "\u0081", 0x82 to "‚",
        0x83 to "ƒ", 0x84 to "„", 0x85 to "…", 0x86 to "†", 0x87 to "‡",
        0x88 to "ˆ", 0x89 to "‰", 0x8a to "Š", 0x8b to "‹", 0x8c to "Œ",
        0x8d to "\u008D", 0x8e to "Ž", 0x8f to "\u008F", 0x90 to "\u0090", 0x91 to "‘",
        0x92 to "’", 0x93 to "“", 0x94 to "”", 0x95 to "•", 0x96 to "–",
        0x97 to "—", 0x98 to "˜", 0x99 to "™", 0x9a to "š", 0x9b to "›",
        0x9c to "œ", 0x9d to "\u009D", 0x9e to "ž", 0x9f to "Ÿ"
    )

    /** A Python `html._invalid_codepoints`: ezek helyére semmi sem kerül. */
    private fun invalidCodepoint(n: Long): Boolean =
        n in 0x1..0x8 || n == 0xBL || n in 0xE..0x1F || n in 0x7F..0x9F ||
            n in 0xFDD0..0xFDEF || (n <= 0x10FFFF && (n and 0xFFFE) == 0xFFFEL)

    private fun named(s: String): String? = LEGACY[s] ?: NAMED[s]

    private fun replaceCharref(s: String): String {
        if (s[0] == '#') {
            val hex = s.length > 1 && (s[1] == 'x' || s[1] == 'X')
            val digits = s.substring(if (hex) 2 else 1).trimEnd(';')
            // túl hosszú szám: a Pythonban is > 0x10FFFF lenne → U+FFFD
            val num = digits.toLongOrNull(if (hex) 16 else 10) ?: Long.MAX_VALUE
            INVALID_CHARREFS[num.toInt().takeIf { num in 0..0x9F } ?: -1]?.let { return it }
            if (num in 0xD800..0xDFFF || num > 0x10FFFF) return "�"
            if (invalidCodepoint(num)) return ""
            return String(Character.toChars(num.toInt()))
        }
        if (s.endsWith(";")) {
            // pontosvesszővel: először a teljes név
            val full = s.dropLast(1)
            named(full)?.let { return it }
        } else {
            LEGACY[s]?.let { return it }
        }
        // a leghosszabb, pontosvessző nélkül is érvényes előtag
        for (x in s.length - 1 downTo 2) {
            LEGACY[s.substring(0, x)]?.let { return it + s.substring(x) }
        }
        return "&$s"
    }

    /** A Python `html.unescape`. */
    fun unescape(s: String): String {
        if (s.indexOf('&') < 0) return s
        return CHARREF.replace(s) { replaceCharref(it.groupValues[1]) }
    }

    // ---- str.isspace / strip -------------------------------------------

    /** A Python `str.isspace()` egy karakterre. */
    fun isSpace(c: Char): Boolean = when (c) {
        '\t', '\n', '\u000B', '\u000C', '\r', '\u001C', '\u001D', '\u001E', '\u001F',
        ' ', '\u0085', ' ', ' ', ' ', ' ', ' ', ' ', '　' -> true
        else -> c in ' '..' '
    }

    /** A Python `\s` a regexben (Unicode-szöveg). */
    const val SPACE_CLASS = "[\\t\\n\\u000B\\u000C\\r\\u001C-\\u001F \\u0085\\u00A0\\u1680" +
        "\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]"

    /** A Python `str.strip()`. */
    fun strip(s: String): String = s.trim { isSpace(it) }

    /** A Python `s[:1].upper() + s[1:]` (az első KÓDPONT nagybetűsítése). */
    fun upperFirst(s: String): String {
        if (s.isEmpty()) return s
        val n = Character.charCount(s.codePointAt(0))
        return s.substring(0, n).uppercase() + s.substring(n)
    }

    // ---- számok ----------------------------------------------------------

    /** A Python `round(x)`: a pontos felénél a PÁROS felé kerekít (2.5 → 2). */
    fun round(x: Double): Int = Math.rint(x).toInt()

    private val AR_SZAM = Regex("(\\p{Nd}{1,3}(?:[ .]\\p{Nd}{3})+|\\p{Nd}+)")
    private val AR_SZAM_SEP = Regex("[ .]")

    /** A `termek.ar_szam`: „1 169 Ft", „1169 Ft", „899.-" → 1169 / 899. */
    fun arSzam(s: String?): Int? {
        val t = s.orEmpty().replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
        val m = AR_SZAM.find(t) ?: return null
        return AR_SZAM_SEP.replace(m.groupValues[1], "").toIntOrNull()
    }

    // ---- rendezés ---------------------------------------------------------

    /** A Python szöveg-összehasonlítása: kódpontonként (nem UTF-16 egységenként). */
    val CODEPOINT_ORDER: Comparator<String> = Comparator { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a.codePointAt(i)
            val cb = b.codePointAt(j)
            if (ca != cb) return@Comparator ca.compareTo(cb)
            i += Character.charCount(ca)
            j += Character.charCount(cb)
        }
        (a.length - i).compareTo(b.length - j)
    }
}
