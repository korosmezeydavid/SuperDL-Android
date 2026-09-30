package com.superdl.launcher.callid

import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale

/**
 * EGY TELEFONSZÁM LEÍRÁSA — név nélkül, csak abból, ami a számból kiderül.
 *
 * MIÉRT libphonenumber: a Google számterv-adatbázisa a telefonon belül van,
 * internet nélkül megmondja a szám típusát (mobil, vezetékes, emelt díjas…),
 * az országot, a vezetékes körzetet és a mobilszám EREDETI szolgáltatóját.
 * Így a „Szám azonosítása" akkor is ad valami igazat, ha nevet nem talál —
 * és a szám közben sehova nem megy el.
 *
 * Tiszta JVM kód (a libphonenumber sima Java), ezért gép mellett is tesztelhető.
 */
object NumberDescriber {

    enum class Kind { HIDDEN, SHORT, INVALID, DOMESTIC, FOREIGN }

    /**
     * @param e164 a szám nemzetközi alakja (+36…), ha értelmezhető — ezzel
     *   keresünk az OSM-indexben; rövid, rejtett és értelmezhetetlen számnál null.
     * @param dialable a vágólapra tett alak: belföldi számnál „06…", külföldinél
     *   „+…". Szóköz nélkül, hogy bármelyik keresőmezőbe beilleszthető legyen.
     */
    data class Info(
        val raw: String,
        val kind: Kind,
        val e164: String?,
        val dialable: String,
        val description: String
    )

    const val HIDDEN_TEXT = "Rejtett számot nem lehet azonosítani."
    const val PORTABILITY_NOTE = "a szám azóta átvihető másik szolgáltatóhoz"

    private val HU = Locale("hu")
    private const val HOME_REGION = "HU"

    /**
     * A jól ismert magyar rövid számok. Csak az került ide, amiben biztosak
     * vagyunk: egy rossz név itt rosszabb, mint a semmi.
     */
    private val KNOWN_SHORT = mapOf(
        "112" to "az egységes segélyhívó szám",
        "104" to "a mentők hívószáma",
        "105" to "a tűzoltók hívószáma",
        "107" to "a rendőrség hívószáma",
        "1818" to "a Kormányzati Ügyfélvonal"
    )

    /**
     * Rejtett szám: a rendszer üres számot, negatív „megjelenítési kódot"
     * (-1, -2, -3) vagy szöveges jelölést ad (Private, Unknown…) — gyártónként
     * másképp. Ezekből semmi nem azonosítható.
     */
    fun isHidden(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return true
        if (t.startsWith("-")) return true
        if (t.none { it.isDigit() }) return true
        return false
    }

    fun describe(raw: String): Info {
        if (isHidden(raw)) return Info(raw, Kind.HIDDEN, null, "", HIDDEN_TEXT)
        return try {
            describeUnsafe(raw)
        } catch (_: Throwable) {
            // A leírás soha nem döntheti le a hívásnaplót: ha a számterv-adat
            // bármiért nem olvasható, ennyit mondunk, és a többi lépés megy tovább.
            Info(raw, Kind.INVALID, null, raw.trim(), "A szám típusát most nem sikerült megállapítani.")
        }
    }

    private fun describeUnsafe(raw: String): Info {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        // RÖVID SZÁM: nincs előtte + vagy 0, és legfeljebb 6 számjegy (112,
        // 1818, 1777…). Ezek szolgáltatóké és hivataloké, személyhez nem tartoznak.
        if (!trimmed.startsWith("+") && !digits.startsWith("0") && digits.length in 3..6) {
            val known = KNOWN_SHORT[digits]
            val lead = numberArticle(digits).replaceFirstChar { it.uppercaseChar() }
            val text = if (known != null) "$lead $digits ${known}." else "$lead $digits rövid szolgáltatói szám."
            return Info(raw, Kind.SHORT, null, digits, text)
        }
        val util = PhoneNumberUtil.getInstance()
        val number: Phonenumber.PhoneNumber = try {
            util.parse(trimmed, HOME_REGION)
        } catch (_: Exception) {
            return invalid(raw)
        }
        if (!util.isValidNumber(number)) return invalid(raw)
        val region = util.getRegionCodeForNumber(number)
        val e164 = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        val type = util.getNumberType(number)
        val domestic = region == HOME_REGION
        val dialable = if (domestic) "06" + util.getNationalSignificantNumber(number) else e164
        val text = if (domestic) describeDomestic(number, type) else describeForeign(number, type, region)
        return Info(raw, if (domestic) Kind.DOMESTIC else Kind.FOREIGN, e164, dialable, text)
    }

    private fun invalid(raw: String) = Info(
        raw,
        Kind.INVALID,
        null,
        raw.trim(),
        "Ez nem érvényes telefonszám: hiányos, elírt, vagy nem illik egyik ismert számtervbe sem."
    )

    private fun describeDomestic(number: Phonenumber.PhoneNumber, type: PhoneNumberType): String =
        when (type) {
            PhoneNumberType.MOBILE -> "Magyar mobilszám" + carrierClause(number) + "."
            PhoneNumberType.FIXED_LINE, PhoneNumberType.FIXED_LINE_OR_MOBILE ->
                "Magyar vezetékes szám" + areaClause(number) + "."
            PhoneNumberType.PREMIUM_RATE ->
                "Magyar emelt díjas szám — figyelem, a visszahívása drága lehet."
            else -> "Magyar ${typeWord(type)}."
        }

    private fun describeForeign(
        number: Phonenumber.PhoneNumber,
        type: PhoneNumberType,
        region: String?
    ): String {
        val country = countryName(region)
        val where = if (country.isNotBlank()) ", ország: $country" else ""
        return when (type) {
            PhoneNumberType.MOBILE -> "Külföldi mobilszám$where" + carrierClause(number) + "."
            PhoneNumberType.FIXED_LINE, PhoneNumberType.FIXED_LINE_OR_MOBILE ->
                "Külföldi vezetékes szám$where."
            PhoneNumberType.PREMIUM_RATE ->
                "Külföldi emelt díjas szám$where — figyelem, a visszahívása drága lehet."
            else -> "Külföldi ${typeWord(type)}$where."
        }
    }

    /** A típus magyarul, névelő nélkül, „szám" szóval a végén. */
    private fun typeWord(type: PhoneNumberType): String = when (type) {
        PhoneNumberType.MOBILE -> "mobilszám"
        PhoneNumberType.FIXED_LINE -> "vezetékes szám"
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> "vezetékes vagy mobilszám"
        PhoneNumberType.TOLL_FREE -> "ingyenesen hívható zöld szám"
        PhoneNumberType.PREMIUM_RATE -> "emelt díjas szám"
        PhoneNumberType.SHARED_COST -> "megosztott díjas kék szám"
        PhoneNumberType.VOIP -> "internetes, VoIP szám"
        PhoneNumberType.PERSONAL_NUMBER -> "személyes szám"
        PhoneNumberType.PAGER -> "személyhívó szám"
        PhoneNumberType.UAN -> "vállalati egységes hívószám"
        PhoneNumberType.VOICEMAIL -> "hangposta szám"
        else -> "ismeretlen típusú szám"
    }

    /**
     * A mobilszám EREDETI szolgáltatója. Szándékosan így mondjuk: a
     * számhordozás óta a szám bármelyik szolgáltatónál lehet, a számtervből
     * csak az derül ki, kié volt a tartomány.
     */
    private fun carrierClause(number: Phonenumber.PhoneNumber): String {
        val carrier = shortCarrier(
            PhoneNumberToCarrierMapper.getInstance().getNameForNumber(number, HU).orEmpty().trim()
        )
        if (carrier.isBlank()) return ""
        return ", eredetileg ${article(carrier)} $carrier számtartományából — $PORTABILITY_NOTE"
    }

    /** „Yettel Hungary" → „Yettel": a beszédben elég a márkanév. */
    internal fun shortCarrier(name: String): String = when (name) {
        "Magyar Telekom" -> "Telekom"
        else -> name.removeSuffix(" Hungary").trim()
    }

    /**
     * Magyar névelő a kiejtés szerint. A „One" [van]-nak hangzik, ezért „a";
     * egyébként magánhangzó (és az „egy"-nek, „öt"-nek ejtett 1 és 5) előtt „az".
     */
    internal fun article(word: String): String {
        if (word.startsWith("One")) return "a"
        val first = word.firstOrNull()?.lowercaseChar() ?: return "a"
        return if (first in "aáeéiíoóöőuúüű15") "az" else "a"
    }

    /**
     * Névelő egy számjegyekkel írt szám elé, ahogy KIMONDJUK: a 112 „száz…"
     * (a), az 1818 „ezer…" (az), az 5-tel kezdődő „öt…" (az).
     */
    internal fun numberArticle(digits: String): String = when {
        digits.startsWith("5") -> "az"
        digits.startsWith("1") && (digits.length == 1 || digits.length == 4 || digits.length == 7) -> "az"
        else -> "a"
    }

    private fun areaClause(number: Phonenumber.PhoneNumber): String {
        val area = PhoneNumberOfflineGeocoder.getInstance()
            .getDescriptionForNumber(number, HU, HOME_REGION).orEmpty().trim()
        // Ha a körzet helyett csak az ország jön vissza, azt nem mondjuk: a
        // „magyar" szó már benne van a mondatban.
        if (area.isBlank() || area == countryName(HOME_REGION)) return ""
        return ", körzet: $area"
    }

    /** Az ország neve magyarul (Locale("hu")), pl. „Ausztria". */
    internal fun countryName(region: String?): String {
        if (region.isNullOrBlank() || region == "ZZ" || region == "001") return ""
        return try {
            Locale("", region).getDisplayCountry(HU).takeIf { it.isNotBlank() && it != region }.orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }
}
