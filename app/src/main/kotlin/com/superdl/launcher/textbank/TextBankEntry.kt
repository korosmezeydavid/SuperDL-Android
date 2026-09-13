package com.superdl.launcher.textbank

/**
 * EGY SABLON A SZÖVEGTÁRBAN.
 *
 * @param id         egyedi azonosító
 * @param name       a NEVE — enélkül hangosan nem választható ki
 * @param text       maga a szöveg (e-mail cím, számlaszám, gyakori válasz)
 * @param matrixSlot a mátrix billentyűzet gombja, ha van hozzákötve (pl. "KEY_7")
 *
 * MIÉRT KELL NÉV: a „hetes gomb tartalma" nem kimondható, a „számlaszám" igen.
 * A régi, csak gombhoz kötött szövegtár pontosan ezen bukott el: a billentyűzeten
 * kívül semmi nem tudta megszólítani a tételeit.
 */
data class TextBankEntry(
    val id: Int,
    val name: String,
    val text: String,
    val matrixSlot: String? = null
) {
    /**
     * RÖVID bemondás listázáskor.
     *
     * A NÉV hangzik el elöl, utána a szöveg ELEJE. A teljes szöveg csak a
     * megerősítésnél jön — egy huszonnégy jegyű számlaszámot végighallgatni
     * minden egyes lépkedésnél elviselhetetlen lenne.
     */
    fun speakShort(): String {
        val preview = if (text.length > 30) text.take(30) + "…" else text
        return "$name: $preview"
    }

    /**
     * KELL-E BETŰZNI EZT A SZÖVEGET?
     *
     * Egy mondatot („Mindjárt indulok, várj meg.") betűzni felesleges kínzás.
     * Egy számlaszámot vagy e-mail címet viszont a beszédmotor összemos, és a
     * pont meg a kötőjel el is tűnhet — ott a betűzés az EGYETLEN ellenőrzés.
     *
     * A szabály: ha nincs benne szóköz, és nem túl hosszú, akkor ez egy
     * AZONOSÍTÓ, nem mondat. Ez a különbség hallható, és nem kell hozzá
     * beállítás — magától jó döntést hoz.
     */
    val needsSpelling: Boolean
        get() = text.length in 6..60 && !text.contains(' ')

    /**
     * A SZÖVEG KARAKTERENKÉNT, szóközökkel elválasztva.
     *
     * MIÉRT VAN RÁ SZÜKSÉG: egy számlaszámnál vagy e-mail címnél a folyamatos
     * felolvasás nem ellenőrzés — a beszédmotor összemossa a számjegyeket, és
     * a pont meg a kötőjel el is tűnhet. Betűzve viszont hallható, mi van ott.
     */
    fun spellOut(): String = text.map { ch ->
        when (ch) {
            ' ' -> "szóköz"
            '.' -> "pont"
            '-' -> "kötőjel"
            '_' -> "alulvonás"
            '@' -> "kukac"
            '/' -> "perjel"
            ':' -> "kettőspont"
            ',' -> "vessző"
            '+' -> "plusz"
            else -> ch.toString()
        }
    }.joinToString(" ")
}
