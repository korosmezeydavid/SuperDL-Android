package com.superdl.launcher.sms

import java.util.Calendar

/**
 * MIKOR MENJEN AZ ÜZENET — bediktálva, emberi módon.
 *
 * KÉTFÉLE MEGADÁS KELL, mert két különböző helyzet van:
 *
 *   „másfél óra múlva"   — ha veszélyes környéken jársz, nem akarod fejben
 *                          kiszámolni, hogy az öt óra negyven plusz másfél
 *                          óra mennyi;
 *   „délután ötkor"      — ha azt akarod, hogy a feleséged hétkor
 *                          felébresszen, az egy időpont.
 *
 * MIÉRT SAJÁT ÉRTELMEZŐ: a program számbillentyűs időbevitele négy számjegyet
 * vár (óra-óra-perc-perc). Az „egy óra múlva" ebbe nem fér bele, és egy vak
 * felhasználótól nem várható el, hogy fejben átszámolja.
 *
 * HA NEM ÉRTJÜK, NEM TIPPELÜNK. Egy félreértett időpont csendben rossz
 * órában küldene el egy üzenetet — ez rosszabb, mint megkérdezni még egyszer.
 */
object ScheduledSmsTime {

    /**
     * @return a küldés időpontja ezredmásodpercben, vagy null, ha nem értjük
     */
    fun parse(spoken: String, now: Long = System.currentTimeMillis()): Long? {
        val text = normalize(spoken)
        if (text.isBlank()) return null
        return parseRelative(text, now)
            ?: parseWithDate(text, now)
            ?: parseAbsolute(text, now)
    }

    // ── TELJES IDŐZÍTÉS: MIKOR ÉS MILYEN GYAKRAN ────────────────────────────

    /**
     * Egy kész időzítés: mikor és milyen gyakran.
     *
     * @param dateGiven igaz, ha a felhasználó DÁTUMOT mondott — ilyenkor
     *        a visszamondásba a dátum is belekerül
     */
    data class Plan(
        val triggerAt: Long,
        val repeat: SmsRepeat,
        val dateGiven: Boolean
    )

    /**
     * EGY MONDAT, MINDEN BENNE.
     *
     * Alph kérése szerint az időzített üzenet ismétlődhet is, és teljes
     * dátumra is állítható. Mindkettő ugyanabból a mondatból derül ki:
     *
     *   „minden nap reggel hétkor"         -> naponta, 07:00
     *   „hetente hétfőn nyolckor"          -> hetente, 08:00
     *   „december 24-én délután hatkor"    -> egyszer, dec. 24. 18:00
     *   „2027 január 1 éjfélkor"           -> egyszer, 2027.01.01. 00:00
     *   „két óra múlva"                    -> egyszer, most + 2 óra
     *
     * MIÉRT NEM KÉRDEZÜNK KÜLÖN: minden külön kérdés egy újabb söprés,
     * amit vakon kell megtalálni. Aki kimondta, már megmondta.
     */
    fun parseSchedule(spoken: String, now: Long = System.currentTimeMillis()): Plan? {
        val text = normalize(spoken)
        if (text.isBlank()) return null
        val repeat = SmsRepeat.detect(text)

        // 1. DÁTUM. Ez erősebb mindennél: aki dátumot mond, azt érti.
        parseWithDate(text, now)?.let { return Plan(it, repeat, dateGiven = true) }

        // 2. „... múlva". Ismétlődéssel ennek nincs értelme, de nem tiltjuk:
        //    az első alkalom onnantól számol, a többi már a gyakoriság dolga.
        parseRelative(text, now)?.let { return Plan(it, repeat, dateGiven = false) }

        // 3. Sima időpont.
        parseAbsolute(text, now)?.let { return Plan(it, repeat, dateGiven = false) }

        return null
    }

    /**
     * ÉV, HÓNAP, NAP — ÉS UTÁNA AZ ÓRA.
     *
     * A dátumot KIVESSZÜK a szövegből, mielőtt az időt keresnénk benne.
     * Enélkül a „december 24-én hatkor" huszonnégy órát jelentene.
     */
    private fun parseWithDate(text: String, now: Long): Long? {
        val found = extractDate(text, now) ?: return null
        val rest = found.rest

        // Az óra a maradékból. Ha nincs benne óra, reggel kilenc — és ezt
        // a visszamondás úgyis kimondja, tehát ellenőrizhető.
        var hour = 9
        var minute = 0
        val timed = parseClock(rest)
        if (timed != null) {
            hour = timed.first
            minute = timed.second
        } else if (rest.contains("ejfel")) {
            hour = 0
        } else if (rest.contains("delben") || rest.contains("delkor")) {
            hour = 12
        }

        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.YEAR, found.year)
            set(Calendar.MONTH, found.month0)
            set(Calendar.DAY_OF_MONTH, minOf(found.day, getActualMaximum(Calendar.DAY_OF_MONTH)))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // ÉV NÉLKÜL MONDOTT, MÁR ELMÚLT DÁTUM: jövőre értette. Aki
        // decemberben azt mondja, hogy „január 5", nem a múlt hónapra gondol.
        if (!found.yearGiven && cal.timeInMillis <= now) {
            cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    private data class FoundDate(
        val year: Int,
        val month0: Int,
        val day: Int,
        val yearGiven: Boolean,
        val rest: String
    )

    private val MONTHS = listOf(
        "januar", "februar", "marcius", "aprilis", "majus", "junius",
        "julius", "augusztus", "szeptember", "oktober", "november", "december"
    )

    private fun extractDate(text: String, now: Long): FoundDate? {
        val thisYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)

        // 1. „2027 december 24" — évvel
        val names = MONTHS.joinToString("|")
        Regex("(\\d{4})\\D{0,3}($names)\\D{0,3}(\\d{1,2})").find(text)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[2])
            val day = m.groupValues[3].toIntOrNull()
            if (month >= 0 && day != null && day in 1..31) {
                return FoundDate(
                    year = m.groupValues[1].toInt(),
                    month0 = month,
                    day = day,
                    yearGiven = true,
                    rest = text.removeRange(m.range)
                )
            }
        }

        // 2. „december 24" — év nélkül
        Regex("($names)\\D{0,3}(\\d{1,2})").find(text)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[1])
            val day = m.groupValues[2].toIntOrNull()
            if (month >= 0 && day != null && day in 1..31) {
                return FoundDate(
                    year = thisYear,
                    month0 = month,
                    day = day,
                    yearGiven = false,
                    rest = text.removeRange(m.range)
                )
            }
        }

        // 3. „2027 12 24" vagy „2027.12.24"
        Regex("(\\d{4})[.\\s/-]+(\\d{1,2})[.\\s/-]+(\\d{1,2})").find(text)?.let { m ->
            val month = m.groupValues[2].toIntOrNull()
            val day = m.groupValues[3].toIntOrNull()
            if (month != null && month in 1..12 && day != null && day in 1..31) {
                return FoundDate(
                    year = m.groupValues[1].toInt(),
                    month0 = month - 1,
                    day = day,
                    yearGiven = true,
                    rest = text.removeRange(m.range)
                )
            }
        }

        return null
    }

    /** Csak az óra és a perc egy szövegdarabból. Null, ha nincs benne. */
    private fun parseClock(text: String): Pair<Int, Int>? {
        val pm = text.contains("delutan") || text.contains("este")
        val am = text.contains("reggel") || text.contains("delelott") || text.contains("hajnal")

        var hour: Int? = null
        var minute = 0

        Regex("(\\d{1,2})\\s*(?:ora|orakor)?\\s*(\\d{1,2})?\\s*(?:perc|perckor)?")
            .find(text)?.let { m ->
                hour = m.groupValues[1].toIntOrNull()
                m.groupValues[2].toIntOrNull()?.let { minute = it }
            }

        if (hour == null) {
            val words = mapOf(
                "egy" to 1, "ket" to 2, "ketto" to 2, "harom" to 3, "negy" to 4,
                "ot" to 5, "hat" to 6, "het" to 7, "nyolc" to 8, "kilenc" to 9,
                "tiz" to 10, "tizenegy" to 11, "tizenketto" to 12
            )
            for ((w, n) in words) {
                if (Regex("\\b$w(kor|)\\b").containsMatchIn(text)) {
                    hour = n
                    break
                }
            }
        }

        var h = hour ?: return null
        if (h !in 0..23) return null
        if (minute !in 0..59) return null
        if (pm && h in 1..11) h += 12
        if (am && h == 12) h = 0
        return h to minute
    }

    /**
     * A TELJES VISSZAMONDÁS: dátum, óra, gyakoriság.
     *
     * Ez az ellenőrzés. Egy időzített üzenet a jövőben megy el, amikor
     * már senki nem néz oda — az EGYETLEN esély a hibát elkapni az, ha
     * most visszahalljuk, mit értettünk.
     */
    fun speakPlan(plan: Plan, now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = plan.triggerAt }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val time = "${h.toString().padStart(2, '0')} óra ${m.toString().padStart(2, '0')} perc"

        val sb = StringBuilder()
        if (plan.repeat != SmsRepeat.NONE) {
            sb.append(plan.repeat.speak)
            sb.append(", első alkalommal ")
        }
        if (plan.dateGiven || !withinTwoDays(plan.triggerAt, now)) {
            val fmt = java.text.SimpleDateFormat("yyyy. MMMM d.", java.util.Locale("hu"))
            sb.append(fmt.format(java.util.Date(plan.triggerAt)))
            sb.append(' ')
            sb.append(time)
        } else {
            sb.append(speakWhen(plan.triggerAt, now))
        }
        return sb.toString()
    }

    private fun withinTwoDays(at: Long, now: Long): Boolean =
        at - now in 0..(2 * 24 * 60 * 60_000L)

    /** Amit visszamondunk a felhasználónak, hogy ellenőrizhesse. */
    fun speakWhen(triggerAt: Long, now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val time = "${h.toString().padStart(2, '0')} óra ${m.toString().padStart(2, '0')} perc"
        val left = triggerAt - now
        // MIÉRT: eddig minden nem-mai napra „holnap" hangzott el, a
        // holnaputánra is. A naptári napok különbsége dönt.
        val offset = dayOffset(triggerAt, now)
        val napText = when {
            offset <= 0 -> ""
            offset == 1 -> "holnap "
            offset == 2 -> "holnapután "
            else -> java.text.SimpleDateFormat("yyyy. MMMM d.", java.util.Locale("hu"))
                .format(java.util.Date(triggerAt)) + " "
        }
        val leftText = when {
            left < 60_000 -> "kevesebb mint egy perc múlva"
            left < 60 * 60_000 -> "${left / 60_000} perc múlva"
            else -> {
                val hh = left / (60 * 60_000)
                val mm = (left % (60 * 60_000)) / 60_000
                if (mm == 0L) "$hh óra múlva" else "$hh óra $mm perc múlva"
            }
        }
        return "$napText$time, $leftText"
    }

    /**
     * Hány naptári nappal van `a` a `now` napja után (negatív: előtte).
     * MIÉRT kerekítés: a nyári időszámítás váltásakor egy nap 23 vagy 25 óra,
     * az egész osztás ilyenkor egy nappal elcsúszna.
     */
    private fun dayOffset(a: Long, now: Long): Int {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = now }
        listOf(ca, cb).forEach {
            it.set(Calendar.HOUR_OF_DAY, 0)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }
        return Math.round((ca.timeInMillis - cb.timeInMillis) / 86_400_000.0).toInt()
    }

    // ── „… múlva" ───────────────────────────────────────────────────────────

    /** A „… óra múlva" kimondott számnevei. */
    private val RELATIVE_HOUR_WORDS = mapOf(
        "egy" to 1L, "ket" to 2L, "ketto" to 2L, "harom" to 3L,
        "negy" to 4L, "ot" to 5L, "hat" to 6L, "het" to 7L,
        "nyolc" to 8L, "kilenc" to 9L, "tiz" to 10L
    )

    private fun parseRelative(text: String, now: Long): Long? {
        if (!text.contains("mulva")) return null

        // „másfél óra múlva" — külön, mert nem szám alakban hangzik el.
        if (text.contains("masfel ora")) return now + 90 * 60_000L
        // MIÉRT: a „két és fél óra" eddig a „fél óra" szabályra futott rá, és
        // 30 perc lett belőle. Az egész órát is hozzá kell adni.
        Regex("(\\d+|[a-z]+)\\s+es\\s+(haromnegyed|negyed|fel)\\s+ora").find(text)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: RELATIVE_HOUR_WORDS[m.groupValues[1]]
                ?: return null
            val extra = when (m.groupValues[2]) {
                "fel" -> 30L
                "negyed" -> 15L
                else -> 45L
            }
            return now + n * 60 * 60_000L + extra * 60_000L
        }
        if (text.contains("fel ora")) return now + 30 * 60_000L
        // MIÉRT ELŐBB: a „háromnegyed óra" szövegében a „negyed óra" is benne van.
        if (text.contains("haromnegyed ora")) return now + 45 * 60_000L
        if (text.contains("negyed ora")) return now + 15 * 60_000L

        var total = 0L
        var found = false

        Regex("(\\d+)\\s*(orat|ora|oraval)").find(text)?.let {
            total += (it.groupValues[1].toLongOrNull() ?: 0L) * 60 * 60_000L
            found = true
        }
        Regex("(\\d+)\\s*(percet|perc|perccel)").find(text)?.let {
            total += (it.groupValues[1].toLongOrNull() ?: 0L) * 60_000L
            found = true
        }
        // „egy óra múlva" — a kimondott számnév is előfordul
        if (!found) {
            for ((w, n) in RELATIVE_HOUR_WORDS) {
                if (Regex("\\b$w\\s+ora").containsMatchIn(text)) {
                    total += n * 60 * 60_000L
                    found = true
                    break
                }
            }
        }
        if (!found || total <= 0L) return null
        return now + total
    }

    // ── „délután ötkor", „hét óra harminc" ──────────────────────────────────

    private fun parseAbsolute(text: String, now: Long): Long? {
        val pm = text.contains("delutan") || text.contains("este")
        val am = text.contains("reggel") || text.contains("delelott") || text.contains("hajnal")

        var hour: Int? = null
        var minute = 0

        Regex("(\\d{1,2})\\s*(?:ora|orakor)?\\s*(\\d{1,2})?\\s*(?:perc|perckor)?")
            .find(text)?.let { m ->
                hour = m.groupValues[1].toIntOrNull()
                m.groupValues[2].toIntOrNull()?.let { minute = it }
            }

        if (hour == null) {
            val words = mapOf(
                "egy" to 1, "ket" to 2, "ketto" to 2, "harom" to 3, "negy" to 4,
                "ot" to 5, "hat" to 6, "het" to 7, "nyolc" to 8, "kilenc" to 9,
                "tiz" to 10, "tizenegy" to 11, "tizenketto" to 12
            )
            for ((w, n) in words) {
                if (Regex("\\b$w(kor|)\\b").containsMatchIn(text)) {
                    hour = n
                    break
                }
            }
        }

        var h = hour ?: return null
        if (h !in 0..23) return null
        if (minute !in 0..59) return null
        if (pm && h in 1..11) h += 12
        if (am && h == 12) h = 0

        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // MÚLTBA NEM KÜLDÜNK. Ha a megadott idő ma már elmúlt, holnap lesz —
        // ez az, amit az ember is ért alatta.
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)

        // A KIMONDOTT NAP ERŐSEBB A SZÁMOLÁSNÁL. A „holnap délben" reggel
        // kilenckor eddig MÁRA esett, mert dél még nem múlt el — pedig aki
        // azt mondja, hogy holnap, az holnapot ért alatta.
        if (text.contains("holnaputan")) {
            while (!sameDayOffset(cal.timeInMillis, now, 2)) cal.add(Calendar.DAY_OF_YEAR, 1)
        } else if (text.contains("holnap")) {
            while (!sameDayOffset(cal.timeInMillis, now, 1)) cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** Pontosan `offset` nappal van-e `a` a `now` napja után. */
    private fun sameDayOffset(a: Long, now: Long, offset: Int): Boolean =
        // MIÉRT: a dayOffset kerekít, így a nyári időszámítás váltása sem csúsztat.
        dayOffset(a, now) == offset

    /** Ékezetek le, kisbetű — így egy szabály elég mindkét írásmódra. */
    private fun normalize(raw: String): String {
        val lower = raw.lowercase().trim()
        val sb = StringBuilder()
        for (c in lower) {
            sb.append(
                when (c) {
                    'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'
                    'ó', 'ö', 'ő' -> 'o'
                    'ú', 'ü', 'ű' -> 'u'
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}
