package com.superdl.launcher.help

import com.superdl.launcher.legal.LegalSection
import com.superdl.launcher.legal.SectionAction

/**
 * KÖSZÖNET ÉS TÁMOGATÁS — lehetőség, nem kérés.
 *
 * A szöveg és az adatok a Windows-változatból jönnek szóról szóra
 * (`SuperDownloader/superdl/supportwin.py`), egyetlen szó igazításával: ott
 * „a letöltés, a média, a hírek és az olvasás" volt a cél, itt a telefon.
 *
 * A SORREND SZÁNDÉKOS, ÉS NEM SZABAD MEGFORDÍTANI: az első szakasz a
 * köszönet, és csak utána jön a lehetőség. Vakon a „támogatás" szó könnyen
 * kérésnek hangzik — a levél első bekezdése pont ezt oldja fel: „a köszönet
 * a legfontosabb, és azt már most megkaptam azzal, hogy használod."
 *
 * EZ A KÉPERNYŐ SOHA NEM JÖN ELŐ MAGÁTÓL. Nincs felugró kérés, nincs
 * számláló, nincs emlékeztető. Csak ha a felhasználó idejön a Névjegyből,
 * vagy egy súgó végén ő söpör rá. Aki ezt a szabályt megszegi, az a
 * programot koldussá teszi — és akkor a levél minden szava hazugság lesz.
 */
object SupportInfo {

    const val TITLE = "Köszönet és támogatás"

    const val BENEFICIARY = "Kőrösmezey Dávid Richárd"
    const val ACCOUNT_NUMBER = "12100011-19842198"
    const val IBAN = "HU61 1210 0011 1984 2198 0000 0000"
    const val BIC = "GNBAHUHB"
    const val REVOLUT_URL = "https://revolut.me/davidkoros"

    /** Számjegyenként, hogy a beszélő ne „tizenkétmillió"-t mondjon. */
    private fun spellDigits(s: String): String =
        s.map { c -> if (c == ' ') "," else c.toString() }.joinToString(" ")

    fun sections(): List<LegalSection> = listOf(
        LegalSection(
            "A levél",
            "Szia! Ezt a programot Kőrösmezey Dávid Richárd készíti – egyedül, szabad " +
                "időben, azzal a céllal, hogy a telefon használata mindenki számára, " +
                "képernyőolvasóval is, könnyű és élvezetes legyen. " +
                "A Super DL ingyenes, és az is marad. Támogatni SEMMI nem kötelez – a " +
                "program minden funkciója ugyanúgy működik nélküle is. Ezt tényleg " +
                "szívből írom: a köszönet a legfontosabb, és azt már most megkaptam " +
                "azzal, hogy használod. " +
                "De ha örömödet lelted benne, és szeretnél hozzájárulni a " +
                "továbbfejlesztéshez – vagy csak meghívnál egy kávéra, egy uzsonnára, " +
                "annyira, amennyit jónak látsz –, azt hálásan köszönöm. Minden korty és " +
                "falat erőt ad a következő ötletekhez. " +
                "Köszönöm, hogy velem tartasz ezen az úton. Dávid."
        ),
        LegalSection(
            "Banki adatok, Magyarország",
            "Kedvezményezett: $BENEFICIARY. " +
                "Számlaszám: ${spellDigits(ACCOUNT_NUMBER)}. " +
                "IBAN: ${spellDigits(IBAN)}. " +
                "BIC, vagyis SWIFT: G N B A H U H B. " +
                "Jobbra söpréssel az IBAN-t a vágólapra másolom, hogy a banki " +
                "alkalmazásba beilleszthesd.",
            SectionAction.Copy(IBAN.replace(" ", ""), "Az IBAN a vágólapra másolva.")
        ),
        LegalSection(
            "Revolut",
            "A Revolut-cím: revolut pont me per davidkoros. " +
                "Jobbra söpréssel megnyitom a böngészőben.",
            SectionAction.OpenUrl(REVOLUT_URL)
        )
    )

    /**
     * A SÚGÓK ZÁRÓ SZAKASZA — minden súgó ezzel végződik. Egy semleges gomb:
     * nem felszólít, hanem megmutatja, hogy van ilyen.
     */
    fun helpFooter(): LegalSection = LegalSection(
        "Fejlesztés támogatása",
        "A Super DL ingyenes, és az is marad. Ha szeretnéd támogatni a " +
            "fejlesztést, a Névjegyben megtalálod a lehetőségeket — de ez sosem " +
            "kötelező.",
        SectionAction.OpenSupport
    )
}
