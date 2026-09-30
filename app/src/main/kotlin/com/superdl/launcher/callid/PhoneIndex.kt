package com.superdl.launcher.callid

/**
 * Egy OpenStreetMap-találat: cég vagy intézmény, amelynek ez a telefonszáma.
 * Magánszemély itt nincs — az OSM ilyet nem gyűjt.
 */
data class PhoneIndexEntry(
    val e164: String,
    val name: String,
    val type: String,
    val place: String
) {
    /** „Arany Patika, gyógyszertár, Szeged" — az üres részeket kihagyva. */
    fun speak(): String = listOf(name, type, place).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * AZ OFFLINE CÉGINDEX (assets/telefon_index_hu.tsv, OSM-ből, ODbL 1.0).
 *
 * MIÉRT SOROKAT TARTUNK MEG, NEM OBJEKTUMOKAT: az index több tízezer sor
 * lehet. Soronként egy String a legtakarékosabb; a mezőkre bontás csak
 * találatkor történik. A sorokat a kulcs (E.164) szerint rendezzük, és
 * bináris kereséssel lépünk az első egyezőre — egy számhoz TÖBB sor is
 * tartozhat (egy épületben rendelő és patika, közös központi szám).
 *
 * Tiszta Kotlin: az Android-rész (NumberIdentifier) csak a sorokat adja át.
 */
class PhoneIndex private constructor(
    private val lines: Array<String>,
    /** A fejlécből: „© OpenStreetMap-közreműködők, ODbL 1.0 — letöltve: …". */
    val sourceNote: String
) {
    val size: Int get() = lines.size

    fun lookup(e164: String?): List<PhoneIndexEntry> {
        if (e164.isNullOrBlank() || lines.isEmpty()) return emptyList()
        var lo = 0
        var hi = lines.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keyOf(lines[mid]) < e164) lo = mid + 1 else hi = mid
        }
        val result = mutableListOf<PhoneIndexEntry>()
        val seenNames = HashSet<String>()
        var i = lo
        while (i < lines.size && keyOf(lines[i]) == e164) {
            val entry = toEntry(lines[i])
            if (entry != null && seenNames.add(entry.name)) result.add(entry)
            i++
        }
        return result
    }

    companion object {
        val EMPTY = PhoneIndex(emptyArray(), "")

        /**
         * Sorok beolvasása. A megjegyzéssort (#) és a hibás sort csendben
         * átugorjuk: egy elrontott sor miatt a többi találat még használható.
         */
        fun parse(input: Sequence<String>): PhoneIndex {
            var note = ""
            val kept = ArrayList<String>()
            for (rawLine in input) {
                val line = rawLine.trimEnd('\r', '\n')
                if (line.isBlank()) continue
                if (line.startsWith("#")) {
                    if (note.isEmpty()) note = line.removePrefix("#").trim().removePrefix("forrás:").trim()
                    continue
                }
                if (toEntry(line) == null) continue
                kept.add(line)
            }
            // A fájl elvileg rendezett, de nem erre építünk: rendezetlen sorokon
            // a bináris keresés csendben rossz választ adna.
            // (Csak akkor rendezünk, ha kell — a rendezett fájlnál ez megspórolja
            // a kulcsok ismételt kivágását.)
            val sorted = (1 until kept.size).all { keyOf(kept[it - 1]) <= keyOf(kept[it]) }
            if (!sorted) kept.sortBy { keyOf(it) }
            return PhoneIndex(kept.toTypedArray(), note)
        }

        fun parse(text: String): PhoneIndex = parse(text.lineSequence())

        private fun keyOf(line: String): String {
            val tab = line.indexOf('\t')
            return if (tab < 0) line else line.substring(0, tab)
        }

        private fun toEntry(line: String): PhoneIndexEntry? {
            val parts = line.split('\t')
            if (parts.size < 2) return null
            val key = parts[0].trim()
            val name = parts[1].trim()
            if (!key.startsWith("+") || key.length < 9 || key.drop(1).any { !it.isDigit() }) return null
            if (name.isEmpty()) return null
            return PhoneIndexEntry(
                e164 = key,
                name = name,
                type = parts.getOrNull(2)?.trim().orEmpty(),
                place = parts.getOrNull(3)?.trim().orEmpty()
            )
        }
    }
}
