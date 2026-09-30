package com.superdl.launcher.callfilter

import kotlin.math.abs

/**
 * KI LÁTSZHAT A HÍVÁSOK KÖZÖTT — A TISZTA DÖNTÉS, ANDROID NÉLKÜL.
 *
 * ALPH DÖNTÉSE (2026-09-30): „akit odaraktunk, azt pontosan azért raktuk oda,
 * hogy ne is tudjunk róla! ne is tudjon még gondolati szinten se zaklatni,
 * irritálni." Vagyis a FEKETELISTÁS szám sehol nem jelenhet meg: se a
 * hívásnaplóban, se a nem fogadott hívások között, se a visszahívási
 * kérdésekben, se a Szűrt hívások listájában.
 *
 * A TÖBBI SZŰRT HÍVÁS (rejtett szám, Ne zavarj, fókusz, ismeretlen szám) más:
 * azokat a „Szűrt hívások a hívásnaplóban" beállítás dönti el — alapból
 * látszanak, ahogy eddig. A Szűrt hívások listájában MINDIG megmaradnak, így
 * semmi nem vész el.
 *
 * A FEHÉRLISTA MINDENT FELÜLÍR — itt is. Aki rajta van, azt a szűrő soha nem
 * utasítja el, tehát rejteni sincs mit.
 *
 * MIÉRT KÜLÖN FÁJL, ANDROID NÉLKÜL: ez a döntés egy vak felhasználónál nem
 * „kinézet", hanem az, hogy mit hall. Ha itt hibázunk, vagy eltűnik egy fontos
 * hívás, vagy megszólal az, akit épp elhallgattatni akart. Ezért JVM-en,
 * telefon nélkül tesztelhető formában van.
 */
object CallLogVisibility {

    /** A szűrő ezzel az okkal naplózza a feketelistás hívást. */
    const val REASON_BLACKLIST = "feketelista"

    /**
     * Mennyi eltérés lehet a hívásnapló sorának ideje és a szűrt hívás
     * feljegyzése között. A rendszer a hívás KEZDETÉT írja a naplóba, mi a
     * döntés pillanatát — ez másodpercek. Két perc bőven elég, de nem akkora,
     * hogy egy későbbi, rendes hívást is elnyeljen.
     */
    const val FILTER_MATCH_WINDOW_MS = 2 * 60_000L

    /**
     * A feketelistás hívás utáni törlésnél ennyi időn belüli sorokat nézünk.
     * Ugyanaz az elv: a rendszer a hívás kezdetét írja be.
     */
    const val PURGE_WINDOW_MS = 2 * 60_000L

    /**
     * Rejtett szám jelölései. A rendszer hívásnaplója a rejtett számot hol
     * üresen, hol „-1", „-2" alakban, hol szóval írja be.
     */
    private val HIDDEN_TOKENS = setOf(
        "unknown", "private", "rejtett", "ismeretlen", "hidden",
        "anonymous", "withheld", "unavailable", "restricted", "payphone"
    )

    /** Egy szűrt hívás lenyomata — csak ami az egyeztetéshez kell. */
    data class FilteredMark(val number: String, val at: Long, val reason: String = "")

    // ── Telefonszámok ────────────────────────────────────────────────────

    /**
     * Rejtett-e a szám. Rejtett számot nem lehet feketelistára tenni, és
     * rejtett számú sort sosem törlünk — ott nincs mihez igazodni.
     */
    fun isHiddenNumber(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return true
        if (t.lowercase() in HIDDEN_TOKENS) return true
        // A rendszer negatív kódokkal jelöli a nem kijelzett számot (-1, -2, -3).
        if (t.startsWith("-") && t.length <= 3 && t.drop(1).all { it.isDigit() }) return true
        return t.none { it.isDigit() }
    }

    /**
     * Egységes alak: nemzetközi „+36…", ha kiderül, különben a puszta számjegyek.
     *
     * MIÉRT: ugyanaz a szám a feketelistán „06 30…", a hívásnaplóban
     * „+36 30…", máskor „0036…" alakban áll. Szó szerinti egyezéssel a
     * tiltott szám átcsúszna a rejtésen.
     */
    fun canonical(phone: String): String {
        val trimmed = phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        val firstMeaningful = trimmed.firstOrNull { it.isDigit() || it == '+' }
        return when {
            firstMeaningful == '+' -> "+$digits"
            digits.startsWith("00") && digits.length > 4 -> "+" + digits.drop(2)
            // MAGYAR BELFÖLDI ALAK: a 06 a nemzetközi +36 megfelelője.
            digits.startsWith("06") && digits.length >= 9 -> "+36" + digits.drop(2)
            else -> digits
        }
    }

    /**
     * Ugyanaz-e a két szám — A REJTÉSHEZ.
     *
     * Ha mindkettő nemzetközi alakú (vagy 06-tal kezdődő belföldi), csak a
     * teljes egyezés számít. Ha az egyik előhívó nélkül áll („30 123 4567"),
     * akkor PONTOSAN a +36 és ő maga kell, hogy kiadja a másikat, és legalább
     * nyolc számjegy kell hozzá. Más ország, csonka szám, „utolsó hét
     * számjegy" soha nem egyezik.
     */
    fun samePhone(a: String, b: String): Boolean {
        if (isHiddenNumber(a) || isHiddenNumber(b)) return false
        val x = canonical(a)
        val y = canonical(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val xi = x.startsWith("+")
        val yi = y.startsWith("+")
        if (xi == yi) return false
        val intl = if (xi) x else y
        val national = if (xi) y else x
        if (national.length < MIN_NATIONAL_DIGITS) return false
        return intl == "+36$national"
    }

    /** Ennyi számjegy alatt egy szám túl rövid ahhoz, hogy törlést engedjen. */
    const val MIN_NATIONAL_DIGITS = 8

    /**
     * SZIGORÚ E.164 ALAK — CSAK A TÖRLÉSHEZ.
     *
     * „+…", „00…" és a magyar „06…" alakot fogadjuk el; minden mást (előhívó
     * nélküli szám, rövid szám) null jelez: az ilyen számot nem tudjuk
     * biztosan egy országhoz kötni. A +36 számoknál legalább nyolc nemzeti
     * számjegy kell, más országnál legalább kilenc számjegy összesen.
     */
    fun strictE164(raw: String): String? {
        if (isHiddenNumber(raw)) return null
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val firstMeaningful = trimmed.firstOrNull { it.isDigit() || it == '+' }
        val e164 = when {
            firstMeaningful == '+' -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            digits.startsWith("06") -> "+36" + digits.drop(2)
            else -> return null
        }
        val ok = if (e164.startsWith("+36")) {
            e164.length - 3 >= MIN_NATIONAL_DIGITS
        } else {
            e164.length - 1 >= MIN_NATIONAL_DIGITS + 1
        }
        return if (ok) e164 else null
    }

    /**
     * Ugyanaz-e a két szám — A TÖRLÉSHEZ. SOHA nem laza.
     *
     * MIÉRT KÜLÖN: a rejtés hibája legfeljebb egy rossz helyen elhallgatott
     * sor; a törlésé VISSZAFORDÍTHATATLAN adatvesztés. Ezért itt nincs „utolsó
     * hét számjegy", nincs rendszer-összevetés (PhoneNumberUtils), nincs
     * csonka egyezés: a két szám E.164 alakja legyen azonos. Ha valamelyiket
     * nem lehet E.164 alakra hozni, a számjegyeknek kell pontosan egyezniük,
     * és legalább nyolcnak lenniük.
     */
    fun samePhoneStrict(a: String, b: String): Boolean {
        if (isHiddenNumber(a) || isHiddenNumber(b)) return false
        val ea = strictE164(a)
        val eb = strictE164(b)
        if (ea != null && eb != null) return ea == eb
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        return da.length >= MIN_NATIONAL_DIGITS && da == db
    }

    fun matchesAny(
        number: String,
        list: List<String>,
        same: (String, String) -> Boolean = ::samePhone
    ): Boolean {
        if (isHiddenNumber(number)) return false
        return list.any { stored -> safeSame(same, stored, number) }
    }

    /**
     * Feketelistás-e — A FEHÉRLISTA NYER. Ha egy szám mindkettőn rajta van,
     * a szűrő átengedi, tehát itt sem rejtjük és nem töröljük.
     */
    fun isBlacklistedNumber(
        number: String,
        blacklist: List<String>,
        whitelist: List<String>,
        same: (String, String) -> Boolean = ::samePhone
    ): Boolean {
        if (isHiddenNumber(number)) return false
        if (matchesAny(number, whitelist, same)) return false
        return matchesAny(number, blacklist, same)
    }

    // ── Szűrt hívások és a hívásnapló ────────────────────────────────────

    /**
     * Egy hívásnapló-sor, ahogy a rejtés látja.
     *
     * @param filterable lehet-e ez a sor egy SZŰRT hívás nyoma. Csak az
     *   elutasított, blokkolt, nem fogadott (és hangposta) sor lehet az —
     *   a fogadott, a máshol felvett és a kimenő hívás SOSEM: az ténylegesen
     *   létrejött beszélgetés, azt nem rejthetjük el „szűrtként".
     * @param normalized a rendszer normalizált száma (NORMALIZED_NUMBER), ha van.
     */
    data class LogRow(
        val number: String,
        val date: Long,
        val filterable: Boolean,
        val normalized: String = ""
    )

    private fun numbersOf(row: LogRow): List<String> =
        listOf(row.number, row.normalized).filter { it.isNotBlank() && !isHiddenNumber(it) }

    /**
     * Mely szűrt-hívás-nyomokat rejtsük: MINDEN szűrt hívás LEGFELJEBB EGY
     * sort rejt el — az időben hozzá legközelebbit, ami azonos számú (vagy
     * mindkettő rejtett), elrejthető típusú, és az ablakon belül van. Így egy
     * szűrt hívás nem nyelheti el a közvetlenül utána jött, valódi hívást is.
     */
    fun filteredRowsToHide(
        rows: List<LogRow>,
        marks: List<FilteredMark>,
        windowMs: Long = FILTER_MATCH_WINDOW_MS,
        same: (String, String) -> Boolean = ::samePhone,
        excluded: Set<Int> = emptySet()
    ): Set<Int> {
        val claimed = HashSet<Int>()
        for (mark in marks) {
            val markHidden = isHiddenNumber(mark.number)
            var best = -1
            var bestDist = Long.MAX_VALUE
            rows.forEachIndexed { i, row ->
                if (!row.filterable || i in claimed || i in excluded) return@forEachIndexed
                val dist = abs(row.date - mark.at)
                if (dist > windowMs || dist >= bestDist) return@forEachIndexed
                val nums = numbersOf(row)
                val rowHidden = nums.isEmpty()
                val match = when {
                    rowHidden && markHidden -> true
                    rowHidden || markHidden -> false
                    else -> nums.any { safeSame(same, mark.number, it) }
                }
                if (match) {
                    best = i
                    bestDist = dist
                }
            }
            if (best >= 0) claimed.add(best)
        }
        return claimed
    }

    /**
     * A DÖNTÉS: mely sorokat rejtsük el mindenhol, ahol a SuperDL hívást mutat.
     *
     * 1. Fehérlistás szám: SOHA nem rejtjük.
     * 2. Feketelistás szám: MINDIG rejtjük — irányától és típusától függetlenül.
     * 3. Szűrt hívás: csak ha a beállítás „rejtve", csak elrejthető típusú
     *    sornál, és szűrt hívásonként legfeljebb egy sort.
     *
     * @return a rejtendő sorok indexei a `rows` listában
     */
    fun hiddenRowIndices(
        rows: List<LogRow>,
        blacklist: List<String>,
        whitelist: List<String>,
        hideFiltered: Boolean,
        marks: List<FilteredMark>,
        windowMs: Long = FILTER_MATCH_WINDOW_MS,
        same: (String, String) -> Boolean = ::samePhone
    ): Set<Int> {
        val out = HashSet<Int>()
        val whitelisted = HashSet<Int>()
        rows.forEachIndexed { i, row ->
            val nums = numbersOf(row)
            when {
                nums.any { matchesAny(it, whitelist, same) } -> whitelisted.add(i)
                nums.any { matchesAny(it, blacklist, same) } -> out.add(i)
            }
        }
        if (hideFiltered && marks.isNotEmpty()) {
            out.addAll(filteredRowsToHide(rows, marks, windowMs, same, excluded = whitelisted))
        }
        return out
    }

    /** Egyetlen sor döntése — ha nincs körülötte más sor, amivel versenghetne. */
    fun shouldHide(
        number: String,
        date: Long,
        filterable: Boolean,
        blacklist: List<String>,
        whitelist: List<String>,
        hideFiltered: Boolean,
        filtered: List<FilteredMark>,
        same: (String, String) -> Boolean = ::samePhone
    ): Boolean = hiddenRowIndices(
        listOf(LogRow(number, date, filterable)),
        blacklist, whitelist, hideFiltered, filtered, same = same
    ).isNotEmpty()

    /**
     * A Szűrt hívások listájából is kimarad a feketelistás szám — és nem is
     * számoljuk. Egy „három feketelistás hívás elrejtve" mondat épp arra
     * emlékeztetne, akiről nem akarsz tudni.
     *
     * Ha a számot azóta levetted a feketelistáról, újra látszik: onnantól
     * nem tiltott, és joggal kíváncsi lehetsz, mikor keresett.
     */
    fun visibleFilteredCalls(
        items: List<FilteredCall>,
        blacklist: List<String>,
        whitelist: List<String>,
        same: (String, String) -> Boolean = ::samePhone
    ): List<FilteredCall> =
        items.filterNot { isBlacklistedNumber(it.number, blacklist, whitelist, same) }

    // ── Törlés: csak szigorú egyezéssel ──────────────────────────────────

    /**
     * Törölhető-e ez a sor, mert feketelistás szám sora.
     *
     * A FEHÉRLISTA VÉDŐ OLDALON LAZA: ha a sor száma akár lazán is egyezik egy
     * fehérlistás számmal, NEM töröljük. A FEKETELISTA-egyezés viszont
     * SZIGORÚ ([samePhoneStrict]) — a rendszer normalizált számát (ha van)
     * és a nyers számot is megnézzük, de mindkettőt szigorúan.
     */
    fun isPurgeRow(
        rowNumber: String,
        rowNormalized: String,
        blacklist: List<String>,
        whitelist: List<String>,
        protectiveSame: (String, String) -> Boolean = ::samePhone
    ): Boolean {
        val nums = listOf(rowNormalized, rowNumber).filter { it.isNotBlank() && !isHiddenNumber(it) }
        if (nums.isEmpty()) return false
        if (nums.any { matchesAny(it, whitelist, protectiveSame) }) return false
        return nums.any { n -> blacklist.any { b -> samePhoneStrict(b, n) } }
    }

    /**
     * A feketelistás hívás utáni takarításnál: ez a sor az a hívás-e.
     * Az időablakon belül van, SZIGORÚAN azonos a hívó számával, és a sor
     * maga is törölhető ([isPurgeRow]). Rejtett számot sosem.
     */
    fun isPurgeTarget(
        rowNumber: String,
        rowNormalized: String,
        rowDate: Long,
        blockedNumber: String,
        blockedAt: Long,
        blacklist: List<String>,
        whitelist: List<String>,
        windowMs: Long = PURGE_WINDOW_MS,
        protectiveSame: (String, String) -> Boolean = ::samePhone
    ): Boolean {
        if (isHiddenNumber(blockedNumber)) return false
        if (abs(rowDate - blockedAt) > windowMs) return false
        val nums = listOf(rowNormalized, rowNumber).filter { it.isNotBlank() && !isHiddenNumber(it) }
        if (nums.none { samePhoneStrict(it, blockedNumber) }) return false
        return isPurgeRow(rowNumber, rowNormalized, blacklist, whitelist, protectiveSame)
    }

    // ── Értesítések szövege ──────────────────────────────────────────────

    private val NUMBER_IN_TEXT = Regex("""\+?\d[\d \-()/.]{5,}\d""")

    /**
     * Szerepel-e a szám egy értesítés szövegében (pl. a tárcsázó „Nem fogadott
     * hívás: +36 30 …" értesítése). Csak legalább hét számjegyű részeket
     * nézünk, hogy egy időpont vagy dátum ne adjon hamis egyezést.
     */
    fun textMentionsNumber(
        text: String,
        number: String,
        same: (String, String) -> Boolean = ::samePhone
    ): Boolean {
        if (text.isBlank() || isHiddenNumber(number)) return false
        return NUMBER_IN_TEXT.findAll(text).any { match ->
            val candidate = match.value
            candidate.count { it.isDigit() } >= 7 && safeSame(same, candidate, number)
        }
    }

    /** Szerepel-e a szövegben bármelyik feketelistás (és nem fehérlistás) szám. */
    fun textMentionsBlacklisted(
        text: String,
        blacklist: List<String>,
        whitelist: List<String>,
        same: (String, String) -> Boolean = ::samePhone
    ): Boolean {
        if (text.isBlank() || blacklist.isEmpty()) return false
        return NUMBER_IN_TEXT.findAll(text).any { match ->
            val candidate = match.value
            candidate.count { it.isDigit() } >= 7 &&
                isBlacklistedNumber(candidate, blacklist, whitelist, same)
        }
    }

    /** Egy elszálló összevetés ne vigye magával a listát. */
    private fun safeSame(same: (String, String) -> Boolean, a: String, b: String): Boolean =
        try {
            same(a, b)
        } catch (_: Throwable) {
            false
        }
}
