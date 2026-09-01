package com.superdl.launcher.gestures

/**
 * AZ IRÁNYSZAVAK ÁTFORDÍTÁSA ELFORGATOTT MÓDBAN.
 *
 * A PROBLÉMA: a program több száz helyen mondja, hogy „söpörj fel-le a
 * választáshoz, jobbra a megnyitáshoz". Elforgatva ez HAZUGSÁG lenne — és
 * vakon egy hazug utasítás rosszabb, mint a semmi: a felhasználó azt hiszi,
 * ő rontja el.
 *
 * A MEGOLDÁS: a beszéd utolsó pillanatában kicseréljük az irányszavakat.
 * Így egyetlen képernyő szövegét sem kellett átírni, és a jövőben írt
 * mondatok is automatikusan helyesek lesznek.
 *
 * ═══ KÉT HIBA, AMIT EZ A FÁJL MÁR MEGTANULT ═══
 *
 * 1. A KÉTSZERES FORDÍTÁS. (Tesztelői hibajelentés, 2026-09-01.)
 *    A „Felület elforgatása" menüpont kimondja az új szabályt: „Elforgatva
 *    jobbra. Balra söprés az előző elem…" — ez a mondat MÁR fizikai
 *    irányokban beszél, mégis átment a fordítón, és „lefelé söprés az előző
 *    elem" lett belőle. Vagyis a program pont azt a mondatot hazudta el,
 *    aminek a helyes kezelést kellett volna megtanítania.
 *    Javítás: a `literal()` jelöléssel ellátott szöveget nem fordítjuk.
 *
 * 2. AZ ÚTVONAL-LEÍRÁS ELRONTÁSA. (Ezt még nem jelentette senki, mert nem
 *    derült ki — de ott volt.) A korábbi lista tartalmazta a puszta
 *    „jobbra a" kifejezést. Ez illeszkedik a „fordulj jobbra a saroknál"
 *    mondatra is, egy navigációs útvonal-leírásban. Elforgatva ebből
 *    „fordulj felfelé a saroknál" lett volna — vakon, az utcán, egy
 *    kereszteződésben. Ennél rosszabbat nem tud csinálni egy program.
 *    Javítás: `looksLikeInstruction()`. Csak azokban a MONDATOKBAN cserélünk,
 *    amikben ott van a kezelési utasítás jellegzetes jele — „söprés",
 *    „söpörj", vagy a „fel-le" páros. Egy útvonal-leírásban ezek nincsenek
 *    benne, tehát hozzá se nyúlunk.
 *
 * MIÉRT JELÖLŐKÖN KERESZTÜL CSERÉLÜNK: a cserék egymásba érnének. Ha a
 * „fel-le"-ből „balra-jobbra" lesz, a következő lépés a frissen beírt
 * „balra" szót is átírná. A jelölők ezt kizárják: a valódi szavak csak a
 * legvégén kerülnek be, amikor már nincs több csere.
 */
object GestureWords {

    // A jelölők nem nyomtatható vezérlőkarakterek: betűkből álló jelölő
    // beleakadhatna egy valódi szóba, ezek viszont soha nem fordulnak elő
    // felolvasott szövegben.
    private val NAV = 1.toChar().toString()
    private val ENTER = 2.toChar().toString()
    private val BACK = 3.toChar().toString()
    private val LITERAL = 4.toChar().toString()

    /**
     * „EZT NE FORDÍTSD." Olyan szöveghez, ami MÁR fizikai irányokban beszél —
     * mindenekelőtt magához az elforgatási szabályhoz. Enélkül a program
     * lefordítaná a saját tanítását, és a felhasználó a rossz mozdulatot
     * tanulná meg.
     */
    fun literal(text: String): String = LITERAL + text

    /**
     * A szöveg átfordítása az aktuális módra. Alap kezelésben érintetlenül
     * adja vissza — ott nincs mit fordítani, és a fölösleges munkát is
     * megspóroljuk a beszéd forró útvonalán.
     */
    fun translate(text: String): String {
        if (text.startsWith(LITERAL)) return text.substring(LITERAL.length)

        val mode = GestureOrientation.modeFast()
        if (mode == GestureOrientation.Mode.NORMAL || text.isEmpty()) return text
        if (!looksLikeInstruction(text)) return text

        var out = text

        // ── 1. Lépkedés: a fel-le páros ───────────────────────────────────
        listOf("fel-le", "fel le", "föl-le", "föl le", "fel vagy le").forEach {
            out = out.replace(it, NAV, ignoreCase = true)
        }

        // ── 2. Belépés, megerősítés: jobbra ───────────────────────────────
        out = out.replace("jobbra", ENTER, ignoreCase = true)

        // ── 3. Vissza, mégse: balra ───────────────────────────────────────
        out = out.replace("balra", BACK, ignoreCase = true)

        // ── 4. A jelölők feloldása — csak most kerülnek be valódi szavak ──
        return resolve(out, mode)
    }

    /**
     * A KIJELZŐ nyilai. Ugyanaz az elv, csak karakterekkel — a látó
     * segítőnek is stimmelnie kell, aki a vak felhasználó mellett ül.
     */
    fun translateHint(text: String): String {
        if (text.startsWith(LITERAL)) return text.substring(LITERAL.length)

        val mode = GestureOrientation.modeFast()
        if (mode == GestureOrientation.Mode.NORMAL || text.isEmpty()) return text

        // A nyilaknál nincs szükség az utasítás-vizsgálatra: a ⬆⬇ ➡ ⬅
        // karakterek a súgósorban mindig kezelési utasítást jelentenek.
        val arrows = resolve(
            text.replace("⬆⬇", NAV)
                .replace("➡", ENTER)
                .replace("⬅", BACK),
            mode,
            navWord = "⬅➡"
        )
        // A súgósorban nyilak ÉS szavak is vannak. Mindkettőnek stimmelnie kell.
        return translate(arrows)
    }

    private fun resolve(
        text: String,
        mode: GestureOrientation.Mode,
        navWord: String = "balra-jobbra"
    ): String {
        val right = mode == GestureOrientation.Mode.ROTATED_RIGHT
        val enterWord = if (navWord == "⬅➡") {
            if (right) "⬆" else "⬇"
        } else {
            if (right) "felfelé" else "lefelé"
        }
        val backWord = if (navWord == "⬅➡") {
            if (right) "⬇" else "⬆"
        } else {
            if (right) "lefelé" else "felfelé"
        }
        return text
            .replace(NAV, navWord)
            .replace(ENTER, enterWord)
            .replace(BACK, backWord)
    }

    /**
     * KEZELÉSI UTASÍTÁS-E EZ A MONDAT?
     *
     * Ez a fájl legfontosabb sora. Csak akkor cserélünk irányszót, ha a
     * mondat a program KEZELÉSÉRŐL szól. Az „ahol nem vagyunk biztosak, ott
     * nem nyúlunk hozzá" itt nem óvatoskodás: egy útvonal-leírásban elrontott
     * irány vakon, az utcán, valódi veszély. Egy le nem fordított utasítás
     * legrosszabb esetben kényelmetlen.
     */
    private fun looksLikeInstruction(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("söpr") || t.contains("sopr") ||
            t.contains("söpör") || t.contains("pöcc") ||
            t.contains("fel-le") || t.contains("fel le") ||
            t.contains("föl-le") || t.contains("fel vagy le")
    }
}
