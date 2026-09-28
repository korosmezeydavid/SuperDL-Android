package com.superdl.launcher.nettest

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

/**
 * INTERNET-TESZT — SZÖVEGEK ÉS ÉRTÉKELÉS.
 *
 * A windowsos `nettest.py` szöveges részének HŰ átirata (osszefoglalo, sorok,
 * minositesek, _fokozat, jel_minosites, ido_szoveg …). Miért hű? Mert a két
 * SuperDL ugyanarra a kérdésre ugyanazt kell mondja — a felhasználó a gépen és
 * a telefonon is ugyanazt a mondatot tanulja meg („Az interneted gyors: …").
 * A `NetTestTextTest` a Python-eredeti kimenetével veti össze.
 *
 * Ahol a telefon MÁS (nincs „adapter", nincs Windows-féle mért-kapcsolat
 * beállítás, viszont van mobilhálózat és valódi dBm), ott szándékosan eltér —
 * ezek a helyek a kódban meg vannak jelölve („TELEFONON MÁS").
 *
 * Android-mentes: JVM-en tesztelhető.
 */
object NetTestText {

    const val MB: Long = 1024L * 1024L

    // ─────────────────────────────────────────────────────────── számok

    /**
     * Python `round(x, n)` — a BINÁRIS érték pontos tizedes alakjából,
     * páros felé kerekítve. A Kotlin `String.format` felfelé kerekít, ezért
     * adna néha más számot, mint a Windows („0,25" → 0,3 a 0,2 helyett).
     */
    fun pyRound(x: Double, digits: Int): Double =
        if (x.isNaN() || x.isInfinite()) x
        else BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).toDouble()

    /** Python `round(x)` egészre (páros felé). */
    fun pyRoundInt(x: Double): Long = Math.rint(x).toLong()

    /** = nettest._szam: „12,5", „100", „0" — tizedesvesszővel, felesleges nulla nélkül. */
    fun num(x: Double): String {
        if (x.isNaN() || x.isInfinite()) return x.toString()
        val s = BigDecimal(x).setScale(1, RoundingMode.HALF_EVEN).toPlainString()
        return s.trimEnd('0').trimEnd('.').replace('.', ',')
    }

    /** = nettest._mbps: bájt és másodperc → Mbit/s, két tizedesre. */
    fun mbps(bytes: Long, seconds: Double): Double {
        if (seconds <= 0) return 0.0
        return pyRound(bytes * 8 / seconds / 1_000_000.0, 2)
    }

    /** = nettest.ido_szoveg — „3 perc 20 másodperc". */
    fun timeText(seconds: Double): String {
        val s = maxOf(0.0, seconds)
        if (s < 60) return "${pyRoundInt(s)} másodperc"
        val total = pyRoundInt(s)
        var min = total / 60
        val sec = total % 60
        if (min < 60) return if (sec != 0L) "$min perc $sec másodperc" else "$min perc"
        val hour = min / 60
        min %= 60
        return "$hour óra $min perc"
    }

    /** = nettest.egy_giga_ideje — ez a legkézzelfoghatóbb szám. */
    fun oneGigTime(mbps: Double): String {
        if (mbps <= 0) return "nem mérhető"
        return timeText(1024 * 8 / mbps)
    }

    /**
     * MENNYI ADATOT HASZNÁL a mérés — mobilneten ez pénz, ezért ELŐRE szólunk.
     *
     * TELEFONON MÁS: a Windows „kb. 5 megabájtot" mond a takarékosra, de a
     * forrás-próba (legfeljebb 1 MB), a letöltés (6 MB-os plafon) és a
     * feltöltés (3 MB + a megkezdett adagok) együtt ennél többet is vihet. Itt
     * a VALÓDI felső határt mondjuk — inkább legyen kevesebb, mint ígértük.
     * A teljes teszt felső határa ugyanígy számolva: 300 MB letöltés + 8 MB
     * forrás-próba + 120 MB feltöltés + legfeljebb három megkezdett 8 MB-os
     * feltöltési adag ≈ 452 MB (a Windows „20–250 megabájtot" mond).
     */
    fun estimatedTraffic(mode: String): String = when (mode) {
        MODE_SAVER -> "legfeljebb kb. 11 megabájt"
        MODE_QUICK -> "elhanyagolható (néhány kilobájt)"
        else -> "a sebességedtől függően kb. 20 és legfeljebb 460 megabájt között"
    }

    /** Felolvasható módnév. TELEFONON MÁS: a Windows a kódot írja ki („takarekos"). */
    fun modeName(mode: String): String = when (mode) {
        MODE_SAVER -> "takarékos"
        MODE_QUICK -> "gyors"
        MODE_FULL -> "teljes"
        else -> mode
    }

    // ─────────────────────────────────────────────────────────── IP-cím

    /**
     * = nettest.maszkol_ip — a publikus IP MASZKOLT alakja az alapértelmezés,
     * mert élő adásban vagy képernyőfelvételen fel is olvasódna.
     */
    fun maskIp(ip: String?): String {
        val s = (ip ?: "").trim()
        if (s.isEmpty()) return ""
        if (':' in s) {
            val r = s.split(":")
            return if (r.size > 2) r.take(2).joinToString(":") + ":xxxx:xxxx" else s
        }
        val r = s.split(".")
        if (r.size == 4) return "${r[0]}.${r[1]}.xxx.xxx"
        return s
    }

    /** = nettestwin._betuzve — pontonként tagolva, hogy hallás után is leírható legyen. */
    fun spellIp(ip: String?): String =
        (ip ?: "").replace(".", " pont ").replace(":", " kettőspont ")

    // ─────────────────────────────────────────────────────────── Wi-Fi jel

    /** = nettest.dbm_becsles (Windows-százalékból dBm). A telefon valódi dBm-et ad, itt csak a teljesség kedvéért. */
    fun dbmEstimate(percent: Int): Int {
        val j = percent.coerceIn(0, 100)
        return pyRoundInt(j / 2.0 - 100).toInt()
    }

    /** = nettest.JEL_FOKOZATOK — a dBm önmagában semmit nem mond; a fokozat igen. */
    val SIGNAL_BANDS: List<Triple<Int, String, String>> = listOf(
        Triple(-55, "kiváló", "Itt minden gond nélkül megy a videó és a hívás is."),
        Triple(-65, "jó", "Ez bőven elég mindenre."),
        Triple(-72, "elfogadható", "Böngészésre, levelezésre jó; nagy letöltésnél lassulhat."),
        Triple(
            -80, "gyenge",
            "Itt már akadozhat a videó és a hívás. Mesh-hálózatnál ide érdemes még egy egységet tenni."
        ),
        Triple(-200, "használhatatlan", "Ezen a helyen a kapcsolat gyakorlatilag megszakad.")
    )

    /** = nettest.jel_minosites → (fokozat, magyarázat) */
    fun signalGrade(dbm: Int): Pair<String, String> {
        for ((limit, name, explanation) in SIGNAL_BANDS) {
            if (dbm >= limit) return name to explanation
        }
        val last = SIGNAL_BANDS.last()
        return last.second to last.third
    }

    /** = nettest.jel_szoveg — felolvasható mondat a jelerősségről. */
    fun signalText(dbm: Int, percent: Int = 0, measured: Boolean = true): String {
        if (dbm == 0) return "A Wi-Fi jelerősségét nem sikerült megállapítani."
        val (grade, explanation) = signalGrade(dbm)
        val from = if (measured) "" else " (a jelminőségből számolva)"
        var r = "$dbm dBm$from – $grade"
        if (percent != 0) r += ", jelminőség $percent százalék"
        return "$r. $explanation"
    }

    const val TONE_LOW_HZ = 220.0      // −85 dBm
    const val TONE_HIGH_HZ = 1320.0    // −35 dBm

    /**
     * = nettest.jel_frekvencia — a jelerősséghez tartozó hangmagasság. Zenei
     * (logaritmikus) lépték: a fül így hallja egyenletesnek. Járkálás közben ez
     * a leggyorsabb visszajelzés, nem kell megvárni a bemondást.
     */
    fun signalFrequency(dbm: Double): Double {
        val d = dbm.coerceIn(-85.0, -35.0)
        val ratio = (d + 85.0) / 50.0
        return TONE_LOW_HZ * (TONE_HIGH_HZ / TONE_LOW_HZ).pow(ratio)
    }

    /**
     * ÉPESZŰ TARTOMÁNY (a Windows `_rssi` szűrője): a wifi RSSI −110 és −5 dBm
     * közé esik. Az Android kapcsolat nélkül −127-et ad — azt NEM mondjuk be
     * jelerősségnek.
     */
    fun validRssi(dbm: Int): Boolean = dbm in -110..-5

    /** A sáv a frekvenciából (a Windows a csatornából számolja; a telefon frekvenciát ad). */
    fun bandFromFrequency(mhz: Int): String = when (mhz) {
        in 2400..2500 -> "2,4 GHz"
        in 4900..5924 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        in 57000..71000 -> "60 GHz"
        else -> ""
    }

    /** A csatorna a frekvenciából (IEEE 802.11 csatornakiosztás). 0 = nem ismert. */
    fun channelFromFrequency(mhz: Int): Int = when (mhz) {
        2484 -> 14
        in 2412..2472 -> (mhz - 2407) / 5
        in 4910..4980 -> (mhz - 4000) / 5
        in 5000..5924 -> (mhz - 5000) / 5
        5935 -> 2
        in 5950..7125 -> (mhz - 5950) / 5
        in 57000..71000 -> (mhz - 56160) / 2160
        else -> 0
    }

    /**
     * A Wi-Fi szabvány neve (ScanResult.WIFI_STANDARD_* kódok, Android 11-től).
     * A 6 GHz-es Wi-Fi 6 a „6E" — ezt a frekvenciából tudjuk.
     */
    fun wifiStandardName(code: Int, frequencyMhz: Int = 0): String = when (code) {
        1 -> "régebbi szabvány (802.11a, b vagy g)"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> if (bandFromFrequency(frequencyMhz) == "6 GHz") "Wi-Fi 6E (802.11ax, 6 GHz)" else "Wi-Fi 6 (802.11ax)"
        7 -> "WiGig (802.11ad)"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> ""
    }

    // ─────────────────────────────────────────────────────────── mobilhálózat

    /**
     * A mobilhálózat generációja a TelephonyManager.NETWORK_TYPE_* kódból.
     * A számokat írjuk, nem a konstansokat: a NETWORK_TYPE_NR csak Android
     * 10-től létezik, a kód viszont régebbin is lefut.
     *
     * ŐSZINTESÉG: a nem önálló 5G (NSA) a rendszernek sokszor „LTE"; ha a
     * telefon közben 5G-jelet is lát, ezt külön kimondjuk, de nem állítjuk,
     * hogy 5G-n forgalmaz.
     */
    fun mobileGeneration(networkType: Int, nrSignalSeen: Boolean = false): String = when (networkType) {
        1, 2, 4, 7, 11, 16 -> "2G"
        15 -> "3G (HSPA+)"
        3, 5, 6, 8, 9, 10, 12, 14, 17 -> "3G"
        13 -> if (nrSignalSeen) "4G (LTE); 5G-jelet is lát a telefon" else "4G (LTE)"
        19 -> "4G+ (LTE-A)"
        20 -> "5G"
        18 -> "wifi-hívás (IWLAN)"
        else -> ""
    }

    /** A rendszer 0–4 fokozatú jelszintje szavakkal. */
    fun mobileLevelText(level: Int): String = when (level) {
        0 -> "nagyon gyenge vagy nincs"
        1 -> "gyenge"
        2 -> "közepes"
        3 -> "jó"
        4 -> "kiváló"
        else -> "ismeretlen"
    }

    // ─────────────────────────────────────────────────────────── értékelés

    /**
     * = nettest.ingadozo — a csúcs a tipikus érték többszöröse. Ez a „néha
     * megy, néha nem" panasz mérhető formája; vakon máshonnan nem észlelhető.
     */
    fun isFluctuating(median: Double, peak: Double, samples: List<Double>?): Boolean =
        median > 0 && peak > 3 * median && (samples?.size ?: 0) >= 4

    /** = nettest._fokozat */
    fun grade(down: Double): String = when {
        down <= 0 -> "nem mérhető"
        down < 5 -> "lassú"
        down < 30 -> "átlagos"
        down < 100 -> "gyors"
        down < 500 -> "nagyon gyors"
        else -> "kiemelkedően gyors"
    }

    /** = nettest._IGENYEK (a Windows SuperDL saját funkcióihoz kötve) — a teszt ezzel veti össze. */
    val WINDOWS_NEEDS: List<NetNeed> = listOf(
        NetNeed("Zenehallgatás, internetes rádió", 0.5, 0.0, 0.0),
        NetNeed("Videónézés HD-ben (1080p)", 8.0, 0.0, 0.0),
        NetNeed("Videónézés 4K-ban", 25.0, 0.0, 0.0),
        NetNeed("Hangkonferencia (Csevejcenter)", 1.0, 0.5, 200.0),
        NetNeed("Távsegítség (hang és vezérlés)", 1.5, 1.0, 250.0),
        NetNeed("Online játékok (UNO, Póker…)", 0.5, 0.3, 400.0),
        NetNeed("Élő multistream (Super Stream)", 2.0, 5.0, 0.0)
    )

    /**
     * TELEFONON MÁS: a telefonos SuperDL-ben nincs Távsegítség és Super Stream,
     * a játékok pedig nem online-ok. Ami a telefonon SZÁMÍT: hívás az
     * interneten át, és az élő videós adás a telefonról. A küszöbök a
     * Windows-listáéval azonos nagyságrendűek.
     */
    val ANDROID_NEEDS: List<NetNeed> = listOf(
        NetNeed("Zenehallgatás, internetes rádió", 0.5, 0.0, 0.0),
        NetNeed("Videónézés HD-ben (1080p)", 8.0, 0.0, 0.0),
        NetNeed("Videónézés 4K-ban", 25.0, 0.0, 0.0),
        NetNeed("Hanghívás interneten (például WhatsApp)", 0.5, 0.5, 300.0),
        NetNeed("Videóhívás", 1.5, 1.0, 250.0),
        NetNeed("Élő videós adás a telefonról (például TikTok LIVE)", 2.0, 5.0, 0.0)
    )

    /**
     * A telefonos SuperDL szolgáltatásai — ettől több ez egy sima
     * sebességmérőnél: kiderül, hogy „nem az internet a baj, hanem épp a
     * GitHub nem érhető el". A gépek és portok a KÓDBÓL vannak kiolvasva:
     * katalógus és verzió-ellenőrzés: raw.githubusercontent.com
     * (CatalogClient), a csevegő: rest.ably.io (NetRoom), a fájlküldés a
     * wormhole-william alapértelmezett szerverei.
     */
    val ANDROID_SERVICES: List<ServiceTarget> = listOf(
        ServiceTarget("Katalógus és frissítés-ellenőrzés (GitHub)", "raw.githubusercontent.com", 443),
        ServiceTarget("Frissítés letöltése (GitHub)", "github.com", 443),
        ServiceTarget("YouTube (keresés, lejátszás)", "www.youtube.com", 443),
        ServiceTarget("Csevejcenter (Ably)", "rest.ably.io", 443),
        ServiceTarget("Fájlküldés gépre, kódszerver (wormhole)", "relay.magic-wormhole.io", 4000),
        ServiceTarget("Fájlküldés gépre, átjátszó (wormhole transit)", "transit.magic-wormhole.io", 4001),
        ServiceTarget("Névfeloldás (DNS-kiszolgáló)", "1.1.1.1", 53)
    )

    /**
     * = nettest.minositesek — MIRE ELÉG ez a net. Nem a számok érdeklik a
     * felhasználót, hanem hogy megy-e, amit szeretne.
     */
    fun qualifications(s: NetSpeed, needs: List<NetNeed> = ANDROID_NEEDS): List<NetQualification> =
        needs.map { n ->
            var ok = true
            val why = mutableListOf<String>()
            if (n.downMbps != 0.0 && s.downMbps < n.downMbps) {
                ok = false
                why += "legalább ${num(n.downMbps)} megabit letöltés kellene"
            }
            if (n.upMbps != 0.0 && s.upMbps < n.upMbps) {
                ok = false
                why += "legalább ${num(n.upMbps)} megabit feltöltés kellene"
            }
            if (n.maxLatencyMs != 0.0 && s.latencyAvgMs != 0.0 && s.latencyAvgMs > n.maxLatencyMs) {
                ok = false
                why += "a késleltetés magas"
            }
            NetQualification(n.name, ok, why.joinToString("; "))
        }

    /**
     * = nettest.osszefoglalo — AZ ELSŐ MONDAT: ítélet emberi nyelven, mielőtt
     * bármi szám jönne.
     */
    fun verdict(e: NetTestResult, needs: List<NetNeed> = ANDROID_NEEDS): String {
        val s = e.speed
        var r = when {
            e.mode == MODE_QUICK ->
                "Gyors ellenőrzés: a kapcsolat ${if (s.successPct >= 50) "él" else "akadozik"}."
            s.downMbps <= 0 ->
                "Az internet sebességét most nem sikerült megmérni. " +
                    "Lehet, hogy nincs kapcsolat, vagy a mérő-kiszolgáló nem elérhető."
            else ->
                "Az interneted ${grade(s.downMbps)}: ${num(s.downMbps)} megabit letöltés, " +
                    "${num(s.upMbps)} megabit feltöltés, ${num(s.latencyAvgMs)} ezredmásodperc késleltetés."
        }
        val q = qualifications(s, needs)
        val good = q.filter { it.ok }.map { it.name }
        val bad = q.filter { !it.ok }.map { it.name }
        if (s.downMbps > 0) {
            if (good.isNotEmpty()) r += " Elég ehhez: " + good.take(4).joinToString(", ").lowercase() + "."
            if (bad.isNotEmpty()) r += " Határeset vagy kevés ehhez: " + bad.joinToString(", ").lowercase() + "."
        }
        if (isFluctuating(s.downMbps, s.downPeakMbps, s.downSamples)) {
            r += " A vonal AKADOZIK: a sebesség ${num(s.downSamples.minOrNull() ?: 0.0)} és " +
                "${num(s.downPeakMbps)} megabit között ugrált a mérés alatt. Ez tipikusan túlterhelt " +
                "vonalra, gyenge wifire vagy a szolgáltató korlátozására utal – érdemes megismételni a " +
                "mérést később is, és a naplóból megmutatni a szolgáltatónak."
        }
        r += localVerdict(e)
        val badServices = e.services.filter { !it.ok }.map { it.name }
        if (badServices.isNotEmpty()) {
            r += " Nem érhető el: ${badServices.joinToString(", ")} – ez nem az internet sebességének a hibája."
        }
        if (e.mode == MODE_SAVER && s.downMbps > 0) {
            r += " Ez TAKARÉKOS mérés volt, kevés adatforgalommal – a sebesség csak tájékoztató, " +
                "a valódi érték ennél nagyobb is lehet."
        }
        if (e.cancelled) r = "A mérés megszakítva. $r"
        return r
    }

    /**
     * TELEFONON MÁS: a helyi hálózatról szóló mondatok. A Windows a jel
     * SZÁZALÉKÁT nézi (50 alatt gyenge); a telefon valódi dBm-et ad, ezért a
     * jelfokozatokkal egyező −72 dBm-es határt használjuk. A VPN nevét a
     * telefon nem árulja el, csak azt, hogy VPN-en megy a forgalom. És ami a
     * gépen nincs: gyenge mobiljel, bejelentkezést kérő wifi.
     */
    private fun localVerdict(e: NetTestResult): String {
        val h = e.local
        val s = e.speed
        val sb = StringBuilder()
        if (h.kind == KIND_NONE) {
            sb.append(" A telefon szerint most nincs aktív hálózati kapcsolat – kapcsold be a wifit vagy a mobilnetet.")
        }
        if (h.captivePortal) {
            sb.append(
                " Ez a hálózat bejelentkezést kér (például szállodai vagy vonati wifi) – " +
                    "amíg nem lépsz be az oldalán, az internet nem megy."
            )
        }
        if (h.vpn) {
            sb.append(" Figyelem: VPN-kapcsolat aktív, ez lassíthatja és elrejtheti a valódi helyzetet.")
        }
        val w = h.wifi
        if (h.kind == KIND_WIFI && w != null && w.rssi != 0) {
            if (w.rssi < -72) {
                sb.append(" A wifi jele gyenge (${w.rssi} dBm) – a lassúság oka jó eséllyel ez, nem a szolgáltató.")
            } else if (w.band == "2,4 GHz" && s.downMbps < 60) {
                sb.append(
                    " A wifi a lassabb 2,4 gigahertzes sávon van; ha a router tud 5 gigahertzet, " +
                        "azon gyorsabb lehet."
                )
            }
        }
        val m = h.mobile
        if (h.kind == KIND_MOBILE && m != null && m.level in 0..1) {
            sb.append(
                " A mobiljel gyenge (${m.level} a 4 fokozatból) – a lassúság oka jó eséllyel ez, " +
                    "nem a szolgáltató. Ablak mellett vagy a szabadban jobb lehet."
            )
        }
        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────── részletek

    /**
     * = nettest.sorok — a részletek soronként. MINDEN SOR ÖNMAGÁBAN ÉRTELMES,
     * mert a felhasználó egyenként, fel-le söpréssel hallgatja őket: nincs
     * magában álló „igen", mindig ott a címke is.
     */
    fun lines(e: NetTestResult, fullIp: Boolean = false, needs: List<NetNeed> = ANDROID_NEEDS): List<String> =
        listOf("Mérés ideje: ${e.time} (${modeName(e.mode)} mérés)") +
            speedLines(e) + localLines(e) + publicLines(e, fullIp) +
            serviceLines(e) + needLines(e, needs) + warningLines(e)

    /** A sebesség és a késleltetés sorai — a Windows-változattal betűre egyezik. */
    fun speedLines(e: NetTestResult): List<String> {
        val s = e.speed
        val out = mutableListOf<String>()
        if (e.mode != MODE_QUICK) {
            out += "Letöltési sebesség: ${num(s.downMbps)} megabit per másodperc (csúcs: ${num(s.downPeakMbps)})"
            out += "Feltöltési sebesség: ${num(s.upMbps)} megabit per másodperc (csúcs: ${num(s.upPeakMbps)})"
            out += "Egy gigabájt letöltése ezen a sebességen: ${oneGigTime(s.downMbps)}"
            if (isFluctuating(s.downMbps, s.downPeakMbps, s.downSamples)) {
                out += "FIGYELEM – a letöltés erősen ingadozott: " +
                    s.downSamples.joinToString(", ") { num(it) } + " megabit másodpercenként"
            }
            if (isFluctuating(s.upMbps, s.upPeakMbps, s.upSamples)) {
                out += "FIGYELEM – a feltöltés erősen ingadozott: " +
                    s.upSamples.joinToString(", ") { num(it) } + " megabit másodpercenként"
            }
        }
        out += "Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag ${num(s.latencyAvgMs)}, " +
            "legkisebb ${num(s.latencyMinMs)} ezredmásodperc"
        out += "Ingadozás (jitter): ${num(s.jitterMs)} ezredmásodperc"
        out += "Sikeres kapcsolat-próbák: ${num(s.successPct)} százalék"
        out += "Névfeloldás (DNS) ideje: ${num(s.dnsMs)} ezredmásodperc – a gyorsítótár is beleszámít"
        if (e.mode != MODE_QUICK) {
            out += "A mérés adatforgalma: ${num(s.downBytes.toDouble() / MB)} megabájt letöltés, " +
                "${num(s.upBytes.toDouble() / MB)} megabájt feltöltés"
        }
        return out
    }

    /** TELEFONON MÁS: a kapcsolat sorai — itt a telefon mást (és többet) tud, mint a gép. */
    fun localLines(e: NetTestResult): List<String> {
        val h = e.local
        val out = mutableListOf("— Kapcsolat —")
        out += "Kapcsolat típusa: ${h.connection.ifBlank { "ismeretlen" }}"
        if (h.captivePortal) {
            out += "Bejelentkezést kérő hálózat: igen – amíg nem lépsz be az oldalán, az internet nem megy"
        }
        if (h.linkDownKbps > 0 || h.linkUpKbps > 0) {
            out += "A telefon sávszélesség-becslése: le ${num(h.linkDownKbps / 1000.0)}, fel " +
                "${num(h.linkUpKbps / 1000.0)} megabit – ez a rendszer becslése, nem mérés"
        }
        val w = h.wifi
        if (w != null) {
            out += if (w.ssid.isNotBlank()) "Wi-Fi hálózat: ${w.ssid}"
            else "Wi-Fi hálózat neve: nem látható – a hálózat nevét a helymeghatározás nélkül nem látom " +
                "(ehhez helymeghatározási engedély és bekapcsolt helymeghatározás kell)"
            if (w.rssi != 0) {
                val (g, expl) = signalGrade(w.rssi)
                out += "Wi-Fi jelerősség: ${w.rssi} dBm – $g. $expl"
            } else {
                out += "Wi-Fi jelerősség: nem sikerült megállapítani"
            }
            if (w.band.isNotBlank()) {
                out += if (w.channel > 0) "Wi-Fi sáv: ${w.band} (csatorna: ${w.channel})" else "Wi-Fi sáv: ${w.band}"
            }
            if (w.rxMbps > 0 || w.txMbps > 0) {
                out += "Wi-Fi kapcsolati sebesség: le ${num(w.rxMbps.toDouble())}, fel ${num(w.txMbps.toDouble())} megabit"
            } else if (w.linkMbps > 0) {
                out += "Wi-Fi kapcsolati sebesség: ${num(w.linkMbps.toDouble())} megabit"
            }
            if (w.standard.isNotBlank()) out += "Wi-Fi szabvány: ${w.standard}"
        }
        val m = h.mobile
        if (m != null) {
            val gen = m.generation.ifBlank { "a hálózat típusa ismeretlen" }
            var line = "Mobilhálózat: ${m.operator.ifBlank { "ismeretlen szolgáltató" }}, $gen"
            if (m.roaming) line += ", barangolás (roaming) aktív"
            if (h.kind != KIND_MOBILE && h.kind.isNotBlank()) line += " – most nem ezen megy a net"
            out += line
            out += if (m.level >= 0) {
                "Mobiljel erőssége: ${m.level} a 4 fokozatból – ${mobileLevelText(m.level)}" +
                    if (m.dbm != 0) " (${m.dbm} dBm)" else ""
            } else {
                "Mobiljel erőssége: ezen a telefonon nem kérdezhető le"
            }
            if (m.note.isNotBlank()) out += m.note
        }
        if (h.localIp.isNotBlank()) out += "Helyi IP-cím: ${maskIfPublic(h.localIp)}"
        if (h.gateway.isNotBlank()) out += "Átjáró (router): ${maskIfPublic(h.gateway)}"
        if (h.dnsServers.isNotEmpty()) out += "DNS-kiszolgálók: ${h.dnsServers.joinToString(", ")}"
        if (h.privateDns.isNotBlank()) out += "Privát DNS: ${h.privateDns}"
        if (h.mtu > 0) out += "MTU (csomagméret): ${h.mtu}"
        out += "IPv6 elérhető: ${if (h.ipv6) "igen" else "nem"}"
        if (h.vpn) out += "VPN aktív: igen – a mérés az ő útvonalán ment"
        if (h.metered) {
            out += "Forgalomkorlátos (mért) kapcsolat: igen – a telefon szerint; vigyázz az adatforgalommal"
        }
        return out
    }

    /** A publikus adatok sorai. Egyetlen eltérés a Windowstól: a „teljes IP" kérésének módja. */
    fun publicLines(e: NetTestResult, fullIp: Boolean = false): List<String> {
        val p = e.pub
        val out = mutableListOf("— Publikus adatok —")
        out += "Publikus IP-cím: " + if (fullIp) p.ip.ifBlank { "ismeretlen" } else maskIp(p.ip).ifBlank { "ismeretlen" }
        if (!fullIp && p.ip.isNotBlank()) out += FULL_IP_HINT
        // A GÉPNÉV (fordított DNS) CSAK TELJES IP-KÉRÉSRE: a szolgáltatók a
        // nevébe kódolják a címet (84-2-33-144.pool…, hexában is) — maszkolt
        // IP mellett kiírni olyan lenne, mintha nem is maszkolnánk.
        if (fullIp && p.host.isNotBlank()) out += "Hoszt neve (fordított DNS): ${p.host}"
        if (p.provider.isNotBlank()) {
            out += "Szolgáltató: ${p.provider}" + if (p.asn.isNotBlank()) " (AS${p.asn})" else ""
        }
        if (p.city.isNotBlank() || p.country.isNotBlank()) {
            out += "Hely a szolgáltató szerint: " + listOf(p.city, p.country).filter { it.isNotBlank() }.joinToString(", ")
        }
        if (p.colo.isNotBlank()) out += "Mérő-kiszolgáló: ${p.colo}"
        return out
    }

    /**
     * NYILVÁNOS-E a cím? Mobilneten (főleg csak-IPv6-os hálózaton) a „helyi"
     * cím sokszor maga a nyilvános cím — azt ugyanúgy maszkoljuk, mint a
     * publikus IP-t. Magáncím: 10/8, 172.16/12, 192.168/16, 100.64/10
     * (szolgáltatói NAT), 127/8, 169.254/16; IPv6-ban a helyi (fe80::/10), az
     * egyedi helyi (fc00::/7) és a ::1.
     */
    fun isPublicAddress(ip: String?): Boolean {
        val s = (ip ?: "").trim().substringBefore('%').lowercase()
        if (s.isEmpty()) return false
        if (':' in s) {
            if (s == "::1" || s == "::") return false
            val first = s.substringBefore(':').ifEmpty { "0" }
            val v = first.toIntOrNull(16) ?: return true
            if (v and 0xffc0 == 0xfe80) return false      // link-local
            if (v and 0xfe00 == 0xfc00) return false      // egyedi helyi (ULA)
            return true
        }
        val o = s.split('.').map { it.toIntOrNull() ?: return false }
        if (o.size != 4) return false
        return when {
            o[0] == 10 || o[0] == 127 || o[0] == 0 -> false
            o[0] == 172 && o[1] in 16..31 -> false
            o[0] == 192 && o[1] == 168 -> false
            o[0] == 169 && o[1] == 254 -> false
            o[0] == 100 && o[1] in 64..127 -> false
            else -> true
        }
    }

    /** A helyi cím kiírva: magáncím változatlanul, nyilvános cím maszkolva. */
    fun maskIfPublic(ip: String): String =
        if (isPublicAddress(ip)) "${maskIp(ip)} (nyilvános cím, ezért rejtve)" else ip

    /** TELEFONON MÁS: a Windows gombot említ; a telefonon a sor műveletei közt van. */
    const val FULL_IP_HINT =
        "(A teljes IP-cím elrejtve – bármelyik soron jobbra söpörve a Teljes IP-cím bemondása művelettel kérhető)"

    /**
     * TELEFONON MÁS: a sikertelen próbánál NEM mondunk ezredmásodpercet — az
     * csak az időkorlát lenne, és úgy hangzana, mintha mértünk volna valamit.
     */
    fun serviceLines(e: NetTestResult): List<String> {
        if (e.services.isEmpty()) return emptyList()
        return listOf("— SuperDL-szolgáltatások elérhetősége —") + e.services.map {
            if (it.ok) "${it.name}: elérhető (${num(it.ms)} ezredmásodperc)" else "${it.name}: NEM érhető el"
        }
    }

    /**
     * „Mire elég ez a net?" — TELEFONON MÁS: gyors ellenőrzésnél (és ha a
     * sebességet nem sikerült megmérni) nem soroljuk fel, hogy minden „kevés":
     * azt nem mértük, és hamis riasztás lenne.
     */
    fun needLines(e: NetTestResult, needs: List<NetNeed> = ANDROID_NEEDS): List<String> {
        if (e.mode == MODE_QUICK) return emptyList()
        if (e.speed.downMbps <= 0) {
            return listOf(
                "— Mire elég ez a net? —",
                "Mire elég ez a net: nem tudom megmondani, mert a sebességet nem sikerült megmérni"
            )
        }
        return needLinesRaw(e.speed, needs)
    }

    /** A Windows-kimenettel betűre egyező „Mire elég" szakasz (a teszt ezzel veti össze). */
    fun needLinesRaw(s: NetSpeed, needs: List<NetNeed>): List<String> =
        listOf("— Mire elég ez a net? —") + qualifications(s, needs).map {
            "${it.name}: ${if (it.ok) "megfelelő" else "kevés"}" + if (it.reason.isNotBlank()) " – ${it.reason}" else ""
        }

    fun warningLines(e: NetTestResult): List<String> {
        val errs = synchronized(e.errors) { e.errors.toList() }
        if (errs.isEmpty()) return emptyList()
        return listOf("— Mérési figyelmeztetések —") + errs.take(6).map { "Figyelmeztetés: $it" }
    }

    /**
     * = nettest.jelentes — másolható, megosztható szöveg (segítségkérő
     * levélbe). A publikus IP ebben MINDIG maszkolt: a megosztott szöveg
     * továbbkerülhet, és ott már nem mi döntjük el, ki olvassa.
     */
    fun report(e: NetTestResult, needs: List<NetNeed> = ANDROID_NEEDS): String {
        val head = listOf(
            "SuperDL – Internet-teszt jelentés", "=".repeat(40), "", verdict(e, needs), "",
            "(A publikus IP-cím adatvédelmi okból maszkolva.)", ""
        )
        return (head + lines(e, fullIp = false, needs = needs)).joinToString("\n") + "\n"
    }

    // ─────────────────────────────────────────────────────────── napló

    /** = nettest.naplo_sorok egy sora */
    fun historyLine(time: String, down: Double, up: Double, latencyAvg: Double): String =
        "$time – le ${num(down)}, fel ${num(up)} megabit, késleltetés ${num(latencyAvg)} ezredmásodperc"

    /** = nettest.naplo_atlag — üres, ha nincs sikeres sebességmérés. */
    fun historyAverage(downs: List<Double>): String {
        val ok = downs.filter { it > 0 }
        if (ok.isEmpty()) return ""
        return "Az utolsó ${ok.size} mérés átlaga: ${num(ok.sum() / ok.size)} megabit letöltés."
    }
}
