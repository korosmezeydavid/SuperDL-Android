package com.superdl.launcher.braille

import android.content.Context

/**
 * A BRAILLE-BEVITEL SÚGÓJA — egy helyen, egyben, felolvasva.
 *
 * MIÉRT KELL, HA A MENÜPONTOK ÚGYIS BESZÉLNEK (Alph, 2026-09-02):
 *
 * > „igaz hogy maguk a menük is súgnak, de duplasúgás a sztereó"
 *
 * A menüpontok egyenként elmondják, mit csinálnak — de a RENDSZERT senki
 * nem mondja el: hogy miért kell előbb kalibrálni, mi a különbség a három
 * írásmód között, hogyan lépsz jel-rétegre. Aki vakon először találkozik
 * vele, annak egy helyen, sorban kell hallania az egészet.
 *
 * A SZÖVEG A JELENLEGI BEÁLLÍTÁST TÜKRÖZI: nem általánosságban beszél, hanem
 * azt mondja, ami MOST érvényes ezen a telefonon. Egy súgó, ami mást mond,
 * mint ami történik, rosszabb a semminél.
 */
object BrailleHelp {

    fun speak(context: Context): String {
        val mode = TouchCapability.effectiveMode(context)
        val layout = BrailleLayoutPrefs.layout(context)
        val orientation = BrailleLayoutPrefs.orientation(context)
        val learned = BrailleAnchors.load(context).size
        val measured = TouchCapability.measured(context)

        val parts = mutableListOf<String>()

        parts += "Braille billentyűzet súgó. Hatpontos magyar irodalmi Braille, " +
            "összetett betűkkel: cs, gy, ly, ny, sz, ty, zs."

        // ── Hol tartasz most ────────────────────────────────────────────
        parts += when {
            measured == 0 ->
                "Még nem tanítottad meg a kezed. Ez az első lépés: a kezem " +
                    "megtanítása menüpontban tedd rá mind a hat ujjadat egyszerre."
            learned >= 6 ->
                "A kezed meg van tanítva, mind a hat pont megvan. A telefonod " +
                    "$measured ujjat érzett egyszerre."
            else ->
                "A kezed részben van megtanítva: $learned pont a hatból."
        }
        parts += "A jelenlegi írásmód: ${mode.label}. ${mode.speakDescription()}"
        parts += "Tartás: ${orientation.label}. ${orientation.speakHold()}"
        parts += "Elrendezés: ${layout.label}."

        // ── A három írásmód ─────────────────────────────────────────────
        parts += "Három írásmód van. Igazi Braille: hat ujjal, egyszerre. " +
            "Két menet: három ujjal, előbb a bal oszlop, aztán a jobb. " +
            "Sín: egyetlen ujjal, fel-le sort választasz, oldalra lépve ütöd le " +
            "a pontot. Az Írásmód menüpontban váltasz köztük. A mérés ajánl, " +
            "de te döntesz."

        // ── Az írás szabályai ───────────────────────────────────────────
        parts += "Üres cella, vagyis letétel pont nélkül: szóköz. " +
            "Söprés jobbra: szóköz. Söprés balra: törlés. Söprés lefelé: " +
            "a leírt szöveg felolvasása. Összecsippentés két ujjal: kilépés."

        parts += "Jelzők. Szám-jelző: hármas, négyes, ötös, hatos pont, utána " +
            "az a-tól j-ig betűk az egytől nulláig számjegyek; a szóköz zárja. " +
            "Nagybetű: négyes, hatos pont egyszer, a következő betű nagy. " +
            "Kétszer egymás után: az egész szó nagy, szóközig."

        parts += "Jel-réteg: az ötös pont. Utána a következő cella nem betű, " +
            "hanem jel. Például ötös pont és d: dollár; ötös pont és vessző: " +
            "per jel; ötös pont és nyitó zárójel: kapcsos zárójel. " +
            "Ötös pont kétszer: jel mód zárolva, amíg újra le nem ütöd. " +
            "Ugyanaz a szabály mindenhol: egyszer egy cellára, kétszer amíg " +
            "vissza nem kapcsolod."

        // ── Ha valami nem megy ──────────────────────────────────────────
        parts += "Ha a program gyakran nem ismeri fel a betűt, tanítsd meg újra " +
            "a kezed. Ha a tartást vagy az elrendezést megváltoztatod, a kezed " +
            "helyét is újra kell tanítani. A Beállítás felolvasása menüpont " +
            "bármikor elmondja, mi az érvényes."

        return parts.joinToString(" ")
    }
}
