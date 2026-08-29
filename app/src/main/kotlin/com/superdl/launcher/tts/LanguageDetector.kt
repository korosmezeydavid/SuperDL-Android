package com.superdl.launcher.tts

import java.util.Locale

/**
 * NYELVFELISMERÉS — hogy az idegen szöveg ne legyen érthetetlen hangzavar.
 *
 * A PROBLÉMA:
 * A magyar beszédmotor az angol szöveget betű szerint, magyar kiejtéssel
 * mondja ki — az eredmény értelmezhetetlen. Márpedig magyar képernyőkön
 * RENGETEG angol szöveg van: hibaüzenetek, alkalmazásnevek, dalcímek,
 * levelek, weboldalak.
 *
 * Ez a különbség aközött, hogy egy angol nyelvű levelet "meg lehet hallgatni"
 * vagy "értelmetlen hangzavar".
 *
 * A MEGOLDÁS:
 * Felismerjük a szöveg nyelvét, és arra a mondatra átváltjuk a beszédmotort.
 *
 * >>> A LEGFONTOSABB TERVEZÉSI DÖNTÉS: INKÁBB NE VÁLTSUNK, MINT ROSSZUL <<<
 * Egy magyar mondat, amiben egyetlen angol szó van ("nyisd meg a Facebook
 * alkalmazást"), MAGYAR mondat. Ha ilyenkor angolra váltanánk, az EGÉSZ
 * mondat érthetetlen lenne — vagyis pont az ellenkezőjét érnénk el.
 * Ezért a felismerés SZÁNDÉKOSAN ÓVATOS: csak akkor vált, ha a szöveg
 * TÚLNYOMÓRÉSZT idegen, és elég hosszú ahhoz, hogy biztosak lehessünk.
 */
object LanguageDetector {

    /** Ennél rövidebb szövegnél meg sem próbáljuk — túl bizonytalan. */
    private const val MIN_LENGTH = 18

    /** Ennél kevesebb szónál sem. */
    private const val MIN_WORDS = 3

    /**
     * A magyar nyelv jellegzetes betűi. Ha ilyet találunk, a szöveg szinte
     * biztosan magyar — ezek más nyelvekben alig fordulnak elő.
     */
    private val hungarianLetters = charArrayOf('á', 'é', 'í', 'ó', 'ö', 'ő', 'ú', 'ü', 'ű')

    /** Gyakori magyar szavak — ezek erős jelek. */
    private val hungarianWords = setOf(
        "a", "az", "és", "hogy", "nem", "is", "van", "ez", "de", "meg", "el",
        "egy", "csak", "már", "vagy", "mint", "ki", "be", "fel", "le", "át",
        "volt", "lesz", "kell", "lehet", "kérlek", "köszönöm", "igen", "nincs"
    )

    /** Gyakori angol szavak. */
    private val englishWords = setOf(
        "the", "and", "you", "your", "for", "with", "this", "that", "have",
        "from", "not", "are", "was", "will", "can", "has", "but", "all",
        "please", "click", "here", "more", "new", "get", "now", "how", "what",
        "we", "our", "us", "it", "is", "to", "of", "in", "on", "at", "by"
    )

    /** Gyakori német szavak. */
    private val germanWords = setOf(
        "der", "die", "das", "und", "ist", "nicht", "sie", "mit", "für",
        "auf", "ein", "eine", "wir", "ich", "sind", "haben", "werden", "bitte"
    )

    /**
     * A szöveg nyelve — vagy null, ha magyarnak tekintjük (nem kell váltani).
     *
     * @return a beállítandó nyelv, vagy null ha marad a magyar
     */
    fun detect(text: String): Locale? {
        val trimmed = text.trim()
        if (trimmed.length < MIN_LENGTH) return null

        val lower = trimmed.lowercase(Locale("hu", "HU"))

        // 1. MAGYAR ÉKEZET = MAGYAR. Ez a legerősebb jel, és azonnal dönt.
        //    Egyetlen ő vagy ű gyakorlatilag kizárja, hogy angol legyen.
        if (lower.any { it in hungarianLetters }) return null

        val words = lower.split(Regex("[^\\p{L}]+")).filter { it.length >= 2 }
        if (words.size < MIN_WORDS) return null

        // 2. MAGYAR SZAVAK. Ha akár egy is van, magyarnak vesszük — mert egy
        //    magyar mondatban lévő angol szavak miatt NEM szabad váltani.
        val hungarianHits = words.count { it in hungarianWords }
        if (hungarianHits > 0) return null

        // 3. IDEGEN SZAVAK ARÁNYA. Csak akkor váltunk, ha a szöveg
        //    ÉRDEMBEN idegen: legalább a szavak ötöde ismert idegen szó.
        val englishHits = words.count { it in englishWords }
        val germanHits = words.count { it in germanWords }
        val threshold = (words.size / 5).coerceAtLeast(2)

        return when {
            englishHits >= threshold && englishHits >= germanHits -> Locale.ENGLISH
            germanHits >= threshold -> Locale.GERMAN
            else -> null
        }
    }
}
