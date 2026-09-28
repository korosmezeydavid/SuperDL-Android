package com.superdl.launcher.nettest

// GENERÁLT FÁJL — ne szerkeszd kézzel!
// Előállítja: tools/nettest_vart.py, a windowsos nettest.py FUTTATÁSÁVAL.
// Minden érték a Python-eredeti valódi kimenete; a NetTestTextTest ezzel veti össze a Kotlin-átiratot.

object PyExpected {
    val GRADE_INPUT = listOf(-30, -55, -56, -65, -66, -72, -73, -80, -81, -100, -200, -201)
    val GRADE = listOf("kiváló" to "Itt minden gond nélkül megy a videó és a hívás is.", "kiváló" to "Itt minden gond nélkül megy a videó és a hívás is.", "jó" to "Ez bőven elég mindenre.", "jó" to "Ez bőven elég mindenre.", "elfogadható" to "Böngészésre, levelezésre jó; nagy letöltésnél lassulhat.", "elfogadható" to "Böngészésre, levelezésre jó; nagy letöltésnél lassulhat.", "gyenge" to "Itt már akadozhat a videó és a hívás. Mesh-hálózatnál ide érdemes még egy egységet tenni.", "gyenge" to "Itt már akadozhat a videó és a hívás. Mesh-hálózatnál ide érdemes még egy egységet tenni.", "használhatatlan" to "Ezen a helyen a kapcsolat gyakorlatilag megszakad.", "használhatatlan" to "Ezen a helyen a kapcsolat gyakorlatilag megszakad.", "használhatatlan" to "Ezen a helyen a kapcsolat gyakorlatilag megszakad.", "használhatatlan" to "Ezen a helyen a kapcsolat gyakorlatilag megszakad.")
    val SIGNAL_TEXT = listOf(Pair(Triple(0, 0, true), "A Wi-Fi jelerősségét nem sikerült megállapítani."), Pair(Triple(-62, 0, true), "-62 dBm – jó. Ez bőven elég mindenre."), Pair(Triple(-49, 88, false), "-49 dBm (a jelminőségből számolva) – kiváló, jelminőség 88 százalék. Itt minden gond nélkül megy a videó és a hívás is."), Pair(Triple(-81, 40, true), "-81 dBm – használhatatlan, jelminőség 40 százalék. Ezen a helyen a kapcsolat gyakorlatilag megszakad."), Pair(Triple(-72, 0, false), "-72 dBm (a jelminőségből számolva) – elfogadható. Böngészésre, levelezésre jó; nagy letöltésnél lassulhat."), Pair(Triple(-55, 100, true), "-55 dBm – kiváló, jelminőség 100 százalék. Itt minden gond nélkül megy a videó és a hívás is."), Pair(Triple(-99, 1, false), "-99 dBm (a jelminőségből számolva) – használhatatlan, jelminőség 1 százalék. Ezen a helyen a kapcsolat gyakorlatilag megszakad."))
    val DBM_ESTIMATE = listOf(0 to -100, 1 to -100, 3 to -98, 50 to -75, 88 to -56, 99 to -50, 100 to -50, 150 to -50, -5 to -100)
    val FREQUENCY = listOf(-100 to 220.0, -85 to 220.0, -70 to 376.5893690701351, -62 to 501.6170109883278, -60 to 538.8877434122992, -50 to 771.1316990095823, -35 to 1320.0, -20 to 1320.0)
    val TIME_TEXT = listOf(0.0 to "0 másodperc", 0.4 to "0 másodperc", 0.5 to "0 másodperc", 1.5 to "2 másodperc", 2.5 to "2 másodperc", 59.4 to "59 másodperc", 59.5 to "60 másodperc", 59.6 to "60 másodperc", 60.0 to "1 perc", 61.0 to "1 perc 1 másodperc", 119.5 to "2 perc", 200.0 to "3 perc 20 másodperc", 3599.0 to "59 perc 59 másodperc", 3600.0 to "1 óra 0 perc", 3661.0 to "1 óra 1 perc", 7322.5 to "2 óra 2 perc", -3.0 to "0 másodperc")
    val ONE_GIG = listOf(0.0 to "nem mérhető", -1.0 to "nem mérhető", 0.5 to "4 óra 33 perc", 1.0 to "2 óra 16 perc", 8.19 to "16 perc 40 másodperc", 50.0 to "2 perc 44 másodperc", 100.0 to "1 perc 22 másodperc", 941.3 to "9 másodperc", 1000.0 to "8 másodperc", 13.653 to "10 perc")
    val MASK = listOf("" to "", "  " to "", "84.2.33.144" to "84.2.xxx.xxx", "2001:db8:85a3::8a2e:370:7334" to "2001:db8:xxxx:xxxx", "fe80::1" to "fe80::xxxx:xxxx", "1.2.3" to "1.2.3", "::1" to "::xxxx:xxxx", " 10.0.0.1 " to "10.0.xxx.xxx", "a:b" to "a:b")
    val NUM = listOf(0.0 to "0", 0.04 to "0", 0.05 to "0,1", 0.15 to "0,1", 0.25 to "0,2", 0.35 to "0,3", 1.0 to "1", 12.345 to "12,3", 99.95 to "100", 100.0 to "100", 2.675 to "2,7", 1000000.0 to "1000000", 45.3 to "45,3", 9.87 to "9,9", 0.45 to "0,5")
    val MBPS = listOf(Triple(0L, 1.0, 0.0), Triple(1000000L, 0.0, 0.0), Triple(1000000L, 1.0, 8.0), Triple(123456789L, 7.3, 135.3), Triple(65536L, 0.5, 1.05), Triple(5242880L, 2.49, 16.84))
    val GRADE_SPEED = listOf(0.0 to "nem mérhető", -1.0 to "nem mérhető", 4.99 to "lassú", 5.0 to "átlagos", 29.9 to "átlagos", 30.0 to "gyors", 99.9 to "gyors", 100.0 to "nagyon gyors", 499.0 to "nagyon gyors", 500.0 to "kiemelkedően gyors", 1200.0 to "kiemelkedően gyors")
    fun profiles(): List<NetTestResult> = listOf(
        NetTestResult(time = "2026-09-28 20:15:00", mode = "teljes").apply {
            speed.downMbps = 45.3; speed.upMbps = 9.87
            speed.downPeakMbps = 52.1; speed.upPeakMbps = 11.2
            speed.downSamples = listOf<Double>(40.1, 45.3, 47.0, 44.2, 52.1); speed.upSamples = listOf<Double>(9.8, 10.1)
            speed.latencyMinMs = 22.1; speed.latencyAvgMs = 25.67
            speed.jitterMs = 1.53; speed.successPct = 100.0; speed.dnsMs = 12.34
            speed.downBytes = 47309376L; speed.upBytes = 9437184L
            pub = NetPublic(ip = "84.2.33.144", host = "catv-84-2-33-144.catv.fixed.telekom.hu", provider = "Magyar Telekom plc.", asn = "5483", city = "Budapest", country = "HU", colo = "BUD")
            services = listOf(ServiceProbe("Modulok és frissítés (GitHub)", true, 34.5), ServiceProbe("YouTube (letöltés, keresés)", true, 21.0))
            errors.addAll(listOf())
            cancelled = false
        },
        NetTestResult(time = "2026-09-28 20:15:00", mode = "teljes").apply {
            speed.downMbps = 3.2; speed.upMbps = 0.4
            speed.downPeakMbps = 3.9; speed.upPeakMbps = 0.5
            speed.downSamples = listOf<Double>(3.1, 3.2, 3.3); speed.upSamples = listOf<Double>(0.4)
            speed.latencyMinMs = 180.0; speed.latencyAvgMs = 250.5
            speed.jitterMs = 35.25; speed.successPct = 91.7; speed.dnsMs = 80.0
            speed.downBytes = 2097152L; speed.upBytes = 300000L
            pub = NetPublic(ip = "2001:4c4c:1234:5600::1", host = "", provider = "Vodafone", asn = "", city = "", country = "HU", colo = "VIE")
            services = listOf()
            errors.addAll(listOf())
            cancelled = false
        },
        NetTestResult(time = "2026-09-28 20:15:00", mode = "teljes").apply {
            speed.downMbps = 10.0; speed.upMbps = 5.0
            speed.downPeakMbps = 45.0; speed.upPeakMbps = 6.0
            speed.downSamples = listOf<Double>(2.1, 10.0, 45.0, 9.5, 11.0); speed.upSamples = listOf<Double>(5.0, 4.9, 6.0)
            speed.latencyMinMs = 15.0; speed.latencyAvgMs = 18.25
            speed.jitterMs = 2.0; speed.successPct = 100.0; speed.dnsMs = 5.0
            speed.downBytes = 10485760L; speed.upBytes = 4194304L
            pub = NetPublic(ip = "", host = "", provider = "", asn = "", city = "", country = "", colo = "")
            services = listOf()
            errors.addAll(listOf())
            cancelled = false
        },
        NetTestResult(time = "2026-09-28 20:15:00", mode = "takarekos").apply {
            speed.downMbps = 120.55; speed.upMbps = 30.25
            speed.downPeakMbps = 130.0; speed.upPeakMbps = 31.0
            speed.downSamples = listOf<Double>(118.0, 120.55, 125.0); speed.upSamples = listOf<Double>(30.25)
            speed.latencyMinMs = 8.0; speed.latencyAvgMs = 8.5
            speed.jitterMs = 0.25; speed.successPct = 100.0; speed.dnsMs = 0.35
            speed.downBytes = 6291456L; speed.upBytes = 3145728L
            pub = NetPublic(ip = "10.20.30.40", host = "", provider = "", asn = "12345", city = "Győr", country = "", colo = "")
            services = listOf()
            errors.addAll(listOf())
            cancelled = false
        },
        NetTestResult(time = "2026-09-28 20:15:00", mode = "gyors").apply {
            speed.downMbps = 0.0; speed.upMbps = 0.0
            speed.downPeakMbps = 0.0; speed.upPeakMbps = 0.0
            speed.downSamples = listOf<Double>(); speed.upSamples = listOf<Double>()
            speed.latencyMinMs = 20.0; speed.latencyAvgMs = 22.0
            speed.jitterMs = 1.0; speed.successPct = 100.0; speed.dnsMs = 3.0
            speed.downBytes = 0L; speed.upBytes = 0L
            pub = NetPublic(ip = "84.2.1.1", host = "", provider = "Digi", asn = "", city = "", country = "", colo = "")
            services = listOf(ServiceProbe("Modulok és frissítés (GitHub)", true, 12.25), ServiceProbe("Névfeloldás (DNS-kiszolgáló)", false, 4000.0))
            errors.addAll(listOf())
            cancelled = false
        },
        NetTestResult(time = "2026-09-28 20:15:00", mode = "gyors").apply {
            speed.downMbps = 0.0; speed.upMbps = 0.0
            speed.downPeakMbps = 0.0; speed.upPeakMbps = 0.0
            speed.downSamples = listOf<Double>(); speed.upSamples = listOf<Double>()
            speed.latencyMinMs = 0.0; speed.latencyAvgMs = 0.0
            speed.jitterMs = 0.0; speed.successPct = 33.3; speed.dnsMs = 0.0
            speed.downBytes = 0L; speed.upBytes = 0L
            pub = NetPublic(ip = "", host = "", provider = "", asn = "", city = "", country = "", colo = "")
            services = listOf()
            errors.addAll(listOf())
            cancelled = false
        },
        NetTestResult(time = "2026-09-28 20:15:00", mode = "teljes").apply {
            speed.downMbps = 0.0; speed.upMbps = 0.0
            speed.downPeakMbps = 0.0; speed.upPeakMbps = 0.0
            speed.downSamples = listOf<Double>(); speed.upSamples = listOf<Double>()
            speed.latencyMinMs = 0.0; speed.latencyAvgMs = 0.0
            speed.jitterMs = 0.0; speed.successPct = 0.0; speed.dnsMs = 0.0
            speed.downBytes = 0L; speed.upBytes = 0L
            pub = NetPublic(ip = "", host = "", provider = "", asn = "", city = "", country = "", colo = "")
            services = listOf(ServiceProbe("Modulok és frissítés (GitHub)", false, 4001.2), ServiceProbe("YouTube (letöltés, keresés)", false, 4000.9))
            errors.addAll(listOf("letöltés: timed out", "feltöltés: timed out", "egyik mérő-forrás sem adott adatot", "a", "b", "c", "d"))
            cancelled = false
        },
        NetTestResult(time = "2026-09-28 20:15:00", mode = "teljes").apply {
            speed.downMbps = 600.0; speed.upMbps = 250.0
            speed.downPeakMbps = 640.0; speed.upPeakMbps = 260.0
            speed.downSamples = listOf<Double>(590.0, 600.0, 640.0); speed.upSamples = listOf<Double>(250.0)
            speed.latencyMinMs = 3.0; speed.latencyAvgMs = 4.0
            speed.jitterMs = 0.5; speed.successPct = 100.0; speed.dnsMs = 1.0
            speed.downBytes = 314572800L; speed.upBytes = 125829120L
            pub = NetPublic(ip = "1.2.3.4", host = "", provider = "", asn = "", city = "", country = "", colo = "")
            services = listOf()
            errors.addAll(listOf())
            cancelled = true
        },
        NetTestResult(time = "2026-09-28 20:15:00", mode = "teljes").apply {
            speed.downMbps = 0.25; speed.upMbps = 0.05
            speed.downPeakMbps = 0.35; speed.upPeakMbps = 0.15
            speed.downSamples = listOf<Double>(0.25, 0.2); speed.upSamples = listOf<Double>(0.05)
            speed.latencyMinMs = 0.0; speed.latencyAvgMs = 0.0
            speed.jitterMs = 0.0; speed.successPct = 100.0; speed.dnsMs = 0.05
            speed.downBytes = 100000L; speed.upBytes = 20000L
            pub = NetPublic(ip = "1.2.3.4", host = "", provider = "", asn = "", city = "", country = "", colo = "")
            services = listOf()
            errors.addAll(listOf())
            cancelled = false
        },
        NetTestResult(time = "2026-09-28 20:15:00", mode = "teljes").apply {
            speed.downMbps = 25.0; speed.upMbps = 2.0
            speed.downPeakMbps = 27.0; speed.upPeakMbps = 9.0
            speed.downSamples = listOf<Double>(24.0, 25.0, 27.0, 25.5); speed.upSamples = listOf<Double>(0.5, 2.0, 9.0, 1.9)
            speed.latencyMinMs = 40.0; speed.latencyAvgMs = 210.0
            speed.jitterMs = 12.0; speed.successPct = 100.0; speed.dnsMs = 25.0
            speed.downBytes = 26214400L; speed.upBytes = 2097152L
            pub = NetPublic(ip = "5.6.7.8", host = "", provider = "", asn = "", city = "", country = "", colo = "")
            services = listOf()
            errors.addAll(listOf())
            cancelled = false
        }
    )
    val VERDICT = listOf("Az interneted gyors: 45,3 megabit letöltés, 9,9 megabit feltöltés, 25,7 ezredmásodperc késleltetés. Elég ehhez: zenehallgatás, internetes rádió, videónézés hd-ben (1080p), videónézés 4k-ban, hangkonferencia (csevejcenter).", "Az interneted lassú: 3,2 megabit letöltés, 0,4 megabit feltöltés, 250,5 ezredmásodperc késleltetés. Elég ehhez: zenehallgatás, internetes rádió, online játékok (uno, póker…). Határeset vagy kevés ehhez: videónézés hd-ben (1080p), videónézés 4k-ban, hangkonferencia (csevejcenter), távsegítség (hang és vezérlés), élő multistream (super stream).", "Az interneted átlagos: 10 megabit letöltés, 5 megabit feltöltés, 18,2 ezredmásodperc késleltetés. Elég ehhez: zenehallgatás, internetes rádió, videónézés hd-ben (1080p), hangkonferencia (csevejcenter), távsegítség (hang és vezérlés). Határeset vagy kevés ehhez: videónézés 4k-ban. A vonal AKADOZIK: a sebesség 2,1 és 45 megabit között ugrált a mérés alatt. Ez tipikusan túlterhelt vonalra, gyenge wifire vagy a szolgáltató korlátozására utal – érdemes megismételni a mérést később is, és a naplóból megmutatni a szolgáltatónak.", "Az interneted nagyon gyors: 120,5 megabit letöltés, 30,2 megabit feltöltés, 8,5 ezredmásodperc késleltetés. Elég ehhez: zenehallgatás, internetes rádió, videónézés hd-ben (1080p), videónézés 4k-ban, hangkonferencia (csevejcenter). Ez TAKARÉKOS mérés volt, kevés adatforgalommal – a sebesség csak tájékoztató, a valódi érték ennél nagyobb is lehet.", "Gyors ellenőrzés: a kapcsolat él. Nem érhető el: Névfeloldás (DNS-kiszolgáló) – ez nem az internet sebességének a hibája.", "Gyors ellenőrzés: a kapcsolat akadozik.", "Az internet sebességét most nem sikerült megmérni. Lehet, hogy nincs kapcsolat, vagy a mérő-kiszolgáló nem elérhető. Nem érhető el: Modulok és frissítés (GitHub), YouTube (letöltés, keresés) – ez nem az internet sebességének a hibája.", "A mérés megszakítva. Az interneted kiemelkedően gyors: 600 megabit letöltés, 250 megabit feltöltés, 4 ezredmásodperc késleltetés. Elég ehhez: zenehallgatás, internetes rádió, videónézés hd-ben (1080p), videónézés 4k-ban, hangkonferencia (csevejcenter).", "Az interneted lassú: 0,2 megabit letöltés, 0,1 megabit feltöltés, 0 ezredmásodperc késleltetés. Határeset vagy kevés ehhez: zenehallgatás, internetes rádió, videónézés hd-ben (1080p), videónézés 4k-ban, hangkonferencia (csevejcenter), távsegítség (hang és vezérlés), online játékok (uno, póker…), élő multistream (super stream).", "Az interneted átlagos: 25 megabit letöltés, 2 megabit feltöltés, 210 ezredmásodperc késleltetés. Elég ehhez: zenehallgatás, internetes rádió, videónézés hd-ben (1080p), videónézés 4k-ban, távsegítség (hang és vezérlés). Határeset vagy kevés ehhez: hangkonferencia (csevejcenter), élő multistream (super stream).")
    val SPEED_LINES = listOf(
        listOf("Letöltési sebesség: 45,3 megabit per másodperc (csúcs: 52,1)", "Feltöltési sebesség: 9,9 megabit per másodperc (csúcs: 11,2)", "Egy gigabájt letöltése ezen a sebességen: 3 perc 1 másodperc", "Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 25,7, legkisebb 22,1 ezredmásodperc", "Ingadozás (jitter): 1,5 ezredmásodperc", "Sikeres kapcsolat-próbák: 100 százalék", "Névfeloldás (DNS) ideje: 12,3 ezredmásodperc – a gyorsítótár is beleszámít", "A mérés adatforgalma: 45,1 megabájt letöltés, 9 megabájt feltöltés"),
        listOf("Letöltési sebesség: 3,2 megabit per másodperc (csúcs: 3,9)", "Feltöltési sebesség: 0,4 megabit per másodperc (csúcs: 0,5)", "Egy gigabájt letöltése ezen a sebességen: 42 perc 40 másodperc", "Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 250,5, legkisebb 180 ezredmásodperc", "Ingadozás (jitter): 35,2 ezredmásodperc", "Sikeres kapcsolat-próbák: 91,7 százalék", "Névfeloldás (DNS) ideje: 80 ezredmásodperc – a gyorsítótár is beleszámít", "A mérés adatforgalma: 2 megabájt letöltés, 0,3 megabájt feltöltés"),
        listOf("Letöltési sebesség: 10 megabit per másodperc (csúcs: 45)", "Feltöltési sebesség: 5 megabit per másodperc (csúcs: 6)", "Egy gigabájt letöltése ezen a sebességen: 13 perc 39 másodperc", "FIGYELEM – a letöltés erősen ingadozott: 2,1, 10, 45, 9,5, 11 megabit másodpercenként", "Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 18,2, legkisebb 15 ezredmásodperc", "Ingadozás (jitter): 2 ezredmásodperc", "Sikeres kapcsolat-próbák: 100 százalék", "Névfeloldás (DNS) ideje: 5 ezredmásodperc – a gyorsítótár is beleszámít", "A mérés adatforgalma: 10 megabájt letöltés, 4 megabájt feltöltés"),
        listOf("Letöltési sebesség: 120,5 megabit per másodperc (csúcs: 130)", "Feltöltési sebesség: 30,2 megabit per másodperc (csúcs: 31)", "Egy gigabájt letöltése ezen a sebességen: 1 perc 8 másodperc", "Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 8,5, legkisebb 8 ezredmásodperc", "Ingadozás (jitter): 0,2 ezredmásodperc", "Sikeres kapcsolat-próbák: 100 százalék", "Névfeloldás (DNS) ideje: 0,3 ezredmásodperc – a gyorsítótár is beleszámít", "A mérés adatforgalma: 6 megabájt letöltés, 3 megabájt feltöltés"),
        listOf("Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 22, legkisebb 20 ezredmásodperc", "Ingadozás (jitter): 1 ezredmásodperc", "Sikeres kapcsolat-próbák: 100 százalék", "Névfeloldás (DNS) ideje: 3 ezredmásodperc – a gyorsítótár is beleszámít"),
        listOf("Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 0, legkisebb 0 ezredmásodperc", "Ingadozás (jitter): 0 ezredmásodperc", "Sikeres kapcsolat-próbák: 33,3 százalék", "Névfeloldás (DNS) ideje: 0 ezredmásodperc – a gyorsítótár is beleszámít"),
        listOf("Letöltési sebesség: 0 megabit per másodperc (csúcs: 0)", "Feltöltési sebesség: 0 megabit per másodperc (csúcs: 0)", "Egy gigabájt letöltése ezen a sebességen: nem mérhető", "Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 0, legkisebb 0 ezredmásodperc", "Ingadozás (jitter): 0 ezredmásodperc", "Sikeres kapcsolat-próbák: 0 százalék", "Névfeloldás (DNS) ideje: 0 ezredmásodperc – a gyorsítótár is beleszámít", "A mérés adatforgalma: 0 megabájt letöltés, 0 megabájt feltöltés"),
        listOf("Letöltési sebesség: 600 megabit per másodperc (csúcs: 640)", "Feltöltési sebesség: 250 megabit per másodperc (csúcs: 260)", "Egy gigabájt letöltése ezen a sebességen: 14 másodperc", "Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 4, legkisebb 3 ezredmásodperc", "Ingadozás (jitter): 0,5 ezredmásodperc", "Sikeres kapcsolat-próbák: 100 százalék", "Névfeloldás (DNS) ideje: 1 ezredmásodperc – a gyorsítótár is beleszámít", "A mérés adatforgalma: 300 megabájt letöltés, 120 megabájt feltöltés"),
        listOf("Letöltési sebesség: 0,2 megabit per másodperc (csúcs: 0,3)", "Feltöltési sebesség: 0,1 megabit per másodperc (csúcs: 0,1)", "Egy gigabájt letöltése ezen a sebességen: 9 óra 6 perc", "Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 0, legkisebb 0 ezredmásodperc", "Ingadozás (jitter): 0 ezredmásodperc", "Sikeres kapcsolat-próbák: 100 százalék", "Névfeloldás (DNS) ideje: 0,1 ezredmásodperc – a gyorsítótár is beleszámít", "A mérés adatforgalma: 0,1 megabájt letöltés, 0 megabájt feltöltés"),
        listOf("Letöltési sebesség: 25 megabit per másodperc (csúcs: 27)", "Feltöltési sebesség: 2 megabit per másodperc (csúcs: 9)", "Egy gigabájt letöltése ezen a sebességen: 5 perc 28 másodperc", "FIGYELEM – a feltöltés erősen ingadozott: 0,5, 2, 9, 1,9 megabit másodpercenként", "Késleltetés (TCP-válaszidő, nem ICMP-ping): átlag 210, legkisebb 40 ezredmásodperc", "Ingadozás (jitter): 12 ezredmásodperc", "Sikeres kapcsolat-próbák: 100 százalék", "Névfeloldás (DNS) ideje: 25 ezredmásodperc – a gyorsítótár is beleszámít", "A mérés adatforgalma: 25 megabájt letöltés, 2 megabájt feltöltés")
    )
    val PUBLIC_LINES = listOf(
        listOf("— Publikus adatok —", "Publikus IP-cím: 84.2.xxx.xxx", "(A teljes IP-cím elrejtve – a „Teljes IP megjelenítése” gombbal kérhető)", "Hoszt neve (fordított DNS): catv-84-2-33-144.catv.fixed.telekom.hu", "Szolgáltató: Magyar Telekom plc. (AS5483)", "Hely a szolgáltató szerint: Budapest, HU", "Mérő-kiszolgáló: BUD"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 2001:4c4c:xxxx:xxxx", "(A teljes IP-cím elrejtve – a „Teljes IP megjelenítése” gombbal kérhető)", "Szolgáltató: Vodafone", "Hely a szolgáltató szerint: HU", "Mérő-kiszolgáló: VIE"),
        listOf("— Publikus adatok —", "Publikus IP-cím: ismeretlen"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 10.20.xxx.xxx", "(A teljes IP-cím elrejtve – a „Teljes IP megjelenítése” gombbal kérhető)", "Hely a szolgáltató szerint: Győr"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 84.2.xxx.xxx", "(A teljes IP-cím elrejtve – a „Teljes IP megjelenítése” gombbal kérhető)", "Szolgáltató: Digi"),
        listOf("— Publikus adatok —", "Publikus IP-cím: ismeretlen"),
        listOf("— Publikus adatok —", "Publikus IP-cím: ismeretlen"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 1.2.xxx.xxx", "(A teljes IP-cím elrejtve – a „Teljes IP megjelenítése” gombbal kérhető)"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 1.2.xxx.xxx", "(A teljes IP-cím elrejtve – a „Teljes IP megjelenítése” gombbal kérhető)"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 5.6.xxx.xxx", "(A teljes IP-cím elrejtve – a „Teljes IP megjelenítése” gombbal kérhető)")
    )
    val PUBLIC_LINES_FULL = listOf(
        listOf("— Publikus adatok —", "Publikus IP-cím: 84.2.33.144", "Hoszt neve (fordított DNS): catv-84-2-33-144.catv.fixed.telekom.hu", "Szolgáltató: Magyar Telekom plc. (AS5483)", "Hely a szolgáltató szerint: Budapest, HU", "Mérő-kiszolgáló: BUD"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 2001:4c4c:1234:5600::1", "Szolgáltató: Vodafone", "Hely a szolgáltató szerint: HU", "Mérő-kiszolgáló: VIE"),
        listOf("— Publikus adatok —", "Publikus IP-cím: ismeretlen"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 10.20.30.40", "Hely a szolgáltató szerint: Győr"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 84.2.1.1", "Szolgáltató: Digi"),
        listOf("— Publikus adatok —", "Publikus IP-cím: ismeretlen"),
        listOf("— Publikus adatok —", "Publikus IP-cím: ismeretlen"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 1.2.3.4"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 1.2.3.4"),
        listOf("— Publikus adatok —", "Publikus IP-cím: 5.6.7.8")
    )
    val NEED_LINES = listOf(
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: megfelelő", "Videónézés HD-ben (1080p): megfelelő", "Videónézés 4K-ban: megfelelő", "Hangkonferencia (Csevejcenter): megfelelő", "Távsegítség (hang és vezérlés): megfelelő", "Online játékok (UNO, Póker…): megfelelő", "Élő multistream (Super Stream): megfelelő"),
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: megfelelő", "Videónézés HD-ben (1080p): kevés – legalább 8 megabit letöltés kellene", "Videónézés 4K-ban: kevés – legalább 25 megabit letöltés kellene", "Hangkonferencia (Csevejcenter): kevés – legalább 0,5 megabit feltöltés kellene; a késleltetés magas", "Távsegítség (hang és vezérlés): kevés – legalább 1 megabit feltöltés kellene; a késleltetés magas", "Online játékok (UNO, Póker…): megfelelő", "Élő multistream (Super Stream): kevés – legalább 5 megabit feltöltés kellene"),
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: megfelelő", "Videónézés HD-ben (1080p): megfelelő", "Videónézés 4K-ban: kevés – legalább 25 megabit letöltés kellene", "Hangkonferencia (Csevejcenter): megfelelő", "Távsegítség (hang és vezérlés): megfelelő", "Online játékok (UNO, Póker…): megfelelő", "Élő multistream (Super Stream): megfelelő"),
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: megfelelő", "Videónézés HD-ben (1080p): megfelelő", "Videónézés 4K-ban: megfelelő", "Hangkonferencia (Csevejcenter): megfelelő", "Távsegítség (hang és vezérlés): megfelelő", "Online játékok (UNO, Póker…): megfelelő", "Élő multistream (Super Stream): megfelelő"),
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: kevés – legalább 0,5 megabit letöltés kellene", "Videónézés HD-ben (1080p): kevés – legalább 8 megabit letöltés kellene", "Videónézés 4K-ban: kevés – legalább 25 megabit letöltés kellene", "Hangkonferencia (Csevejcenter): kevés – legalább 1 megabit letöltés kellene; legalább 0,5 megabit feltöltés kellene", "Távsegítség (hang és vezérlés): kevés – legalább 1,5 megabit letöltés kellene; legalább 1 megabit feltöltés kellene", "Online játékok (UNO, Póker…): kevés – legalább 0,5 megabit letöltés kellene; legalább 0,3 megabit feltöltés kellene", "Élő multistream (Super Stream): kevés – legalább 2 megabit letöltés kellene; legalább 5 megabit feltöltés kellene"),
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: kevés – legalább 0,5 megabit letöltés kellene", "Videónézés HD-ben (1080p): kevés – legalább 8 megabit letöltés kellene", "Videónézés 4K-ban: kevés – legalább 25 megabit letöltés kellene", "Hangkonferencia (Csevejcenter): kevés – legalább 1 megabit letöltés kellene; legalább 0,5 megabit feltöltés kellene", "Távsegítség (hang és vezérlés): kevés – legalább 1,5 megabit letöltés kellene; legalább 1 megabit feltöltés kellene", "Online játékok (UNO, Póker…): kevés – legalább 0,5 megabit letöltés kellene; legalább 0,3 megabit feltöltés kellene", "Élő multistream (Super Stream): kevés – legalább 2 megabit letöltés kellene; legalább 5 megabit feltöltés kellene"),
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: kevés – legalább 0,5 megabit letöltés kellene", "Videónézés HD-ben (1080p): kevés – legalább 8 megabit letöltés kellene", "Videónézés 4K-ban: kevés – legalább 25 megabit letöltés kellene", "Hangkonferencia (Csevejcenter): kevés – legalább 1 megabit letöltés kellene; legalább 0,5 megabit feltöltés kellene", "Távsegítség (hang és vezérlés): kevés – legalább 1,5 megabit letöltés kellene; legalább 1 megabit feltöltés kellene", "Online játékok (UNO, Póker…): kevés – legalább 0,5 megabit letöltés kellene; legalább 0,3 megabit feltöltés kellene", "Élő multistream (Super Stream): kevés – legalább 2 megabit letöltés kellene; legalább 5 megabit feltöltés kellene"),
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: megfelelő", "Videónézés HD-ben (1080p): megfelelő", "Videónézés 4K-ban: megfelelő", "Hangkonferencia (Csevejcenter): megfelelő", "Távsegítség (hang és vezérlés): megfelelő", "Online játékok (UNO, Póker…): megfelelő", "Élő multistream (Super Stream): megfelelő"),
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: kevés – legalább 0,5 megabit letöltés kellene", "Videónézés HD-ben (1080p): kevés – legalább 8 megabit letöltés kellene", "Videónézés 4K-ban: kevés – legalább 25 megabit letöltés kellene", "Hangkonferencia (Csevejcenter): kevés – legalább 1 megabit letöltés kellene; legalább 0,5 megabit feltöltés kellene", "Távsegítség (hang és vezérlés): kevés – legalább 1,5 megabit letöltés kellene; legalább 1 megabit feltöltés kellene", "Online játékok (UNO, Póker…): kevés – legalább 0,5 megabit letöltés kellene; legalább 0,3 megabit feltöltés kellene", "Élő multistream (Super Stream): kevés – legalább 2 megabit letöltés kellene; legalább 5 megabit feltöltés kellene"),
        listOf("— Mire elég ez a net? —", "Zenehallgatás, internetes rádió: megfelelő", "Videónézés HD-ben (1080p): megfelelő", "Videónézés 4K-ban: megfelelő", "Hangkonferencia (Csevejcenter): kevés – a késleltetés magas", "Távsegítség (hang és vezérlés): megfelelő", "Online játékok (UNO, Póker…): megfelelő", "Élő multistream (Super Stream): kevés – legalább 5 megabit feltöltés kellene")
    )
    val WARNING_LINES = listOf(
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf(),
        listOf("— Mérési figyelmeztetések —", "Figyelmeztetés: letöltés: timed out", "Figyelmeztetés: feltöltés: timed out", "Figyelmeztetés: egyik mérő-forrás sem adott adatot", "Figyelmeztetés: a", "Figyelmeztetés: b", "Figyelmeztetés: c"),
        listOf(),
        listOf(),
        listOf()
    )
    val WINDOWS_FULL_IP_HINT = "(A teljes IP-cím elrejtve – a „Teljes IP megjelenítése” gombbal kérhető)"
    val HISTORY_INPUT = listOf(Triple("2026-09-01 20:00:00", 45.3, Pair(9.87, 25.67)), Triple("2026-09-02 20:00:00", 0.0, Pair(0.0, 30.0)), Triple("2026-09-03 20:00:00", 12.25, Pair(1.05, 41.25)), Triple("2026-09-04 20:00:00", 100.0, Pair(20.0, 9.0)))
    val HISTORY_LINES = listOf("2026-09-04 20:00:00 – le 100, fel 20 megabit, késleltetés 9 ezredmásodperc", "2026-09-03 20:00:00 – le 12,2, fel 1,1 megabit, késleltetés 41,2 ezredmásodperc", "2026-09-02 20:00:00 – le 0, fel 0 megabit, késleltetés 30 ezredmásodperc", "2026-09-01 20:00:00 – le 45,3, fel 9,9 megabit, késleltetés 25,7 ezredmásodperc")
    val HISTORY_AVG_10 = "Az utolsó 3 mérés átlaga: 52,5 megabit letöltés."
    val HISTORY_AVG_2 = "Az utolsó 2 mérés átlaga: 56,1 megabit letöltés."
    val HISTORY_AVG_NONE = ""
    data class Walk(val threshold: Int, val readings: List<Int>, val marks: Map<Int, String>,
                    val spoken: List<Boolean>, val placeTexts: List<String>, val emptyMark: Boolean,
                    val best: Int, val weakest: Int, val average: Int, val summary: String,
                    val saveText: String, val saveTextNet: String)
    val WALKS = listOf(
        Walk(3, listOf<Int>(-60, -61, -62, -63, -64, -58, -58, -70, -71, -80), mapOf<Int, String>(3 to "konyha", 7 to "hálószoba", 9 to "  padlás  "), listOf<Boolean>(true, false, false, true, false, true, false, true, false, true), listOf("konyha: -63 dBm – jó", "hálószoba: -70 dBm – elfogadható", "padlás: -80 dBm – gyenge"), true, -58, -80, -65, "10 mérés. Legerősebb: -58 dBm, leggyengébb: -80 dBm, átlag: -65 dBm.\nMegjelölt helyek:\n  konyha: -63 dBm – jó\n  hálószoba: -70 dBm – elfogadható\n  padlás: -80 dBm – gyenge\nEzeken a helyeken gyenge a jel, ide érdemes még egy mesh-egységet tenni: padlás.", "SuperDL – Wi-Fi jelerősség-bejárás\n\n10 mérés. Legerősebb: -58 dBm, leggyengébb: -80 dBm, átlag: -65 dBm.\nMegjelölt helyek:\n  konyha: -63 dBm – jó\n  hálószoba: -70 dBm – elfogadható\n  padlás: -80 dBm – gyenge\nEzeken a helyeken gyenge a jel, ide érdemes még egy mesh-egységet tenni: padlás.\n\nMinden mérés (dBm):\n-60, -61, -62, -63, -64, -58, -58, -70, -71, -80", "SuperDL – Wi-Fi jelerősség-bejárás\nHálózat: Otthoni wifi\n\n10 mérés. Legerősebb: -58 dBm, leggyengébb: -80 dBm, átlag: -65 dBm.\nMegjelölt helyek:\n  konyha: -63 dBm – jó\n  hálószoba: -70 dBm – elfogadható\n  padlás: -80 dBm – gyenge\nEzeken a helyeken gyenge a jel, ide érdemes még egy mesh-egységet tenni: padlás.\n\nMinden mérés (dBm):\n-60, -61, -62, -63, -64, -58, -58, -70, -71, -80"),
        Walk(3, listOf<Int>(-50, -52, -55), mapOf<Int, String>(2 to "nappali"), listOf<Boolean>(true, false, true), listOf("nappali: -55 dBm – kiváló"), true, -50, -55, -52, "3 mérés. Legerősebb: -50 dBm, leggyengébb: -55 dBm, átlag: -52 dBm.\nMegjelölt helyek:\n  nappali: -55 dBm – kiváló\nA megjelölt helyeken mindenhol legalább elfogadható a jel.", "SuperDL – Wi-Fi jelerősség-bejárás\n\n3 mérés. Legerősebb: -50 dBm, leggyengébb: -55 dBm, átlag: -52 dBm.\nMegjelölt helyek:\n  nappali: -55 dBm – kiváló\nA megjelölt helyeken mindenhol legalább elfogadható a jel.\n\nMinden mérés (dBm):\n-50, -52, -55", "SuperDL – Wi-Fi jelerősség-bejárás\nHálózat: Otthoni wifi\n\n3 mérés. Legerősebb: -50 dBm, leggyengébb: -55 dBm, átlag: -52 dBm.\nMegjelölt helyek:\n  nappali: -55 dBm – kiváló\nA megjelölt helyeken mindenhol legalább elfogadható a jel.\n\nMinden mérés (dBm):\n-50, -52, -55"),
        Walk(1, listOf<Int>(-60, -60, -61, -59), mapOf<Int, String>(), listOf<Boolean>(true, false, true, true), listOf(), true, -59, -61, -60, "4 mérés. Legerősebb: -59 dBm, leggyengébb: -61 dBm, átlag: -60 dBm.", "SuperDL – Wi-Fi jelerősség-bejárás\n\n4 mérés. Legerősebb: -59 dBm, leggyengébb: -61 dBm, átlag: -60 dBm.\n\nMinden mérés (dBm):\n-60, -60, -61, -59", "SuperDL – Wi-Fi jelerősség-bejárás\nHálózat: Otthoni wifi\n\n4 mérés. Legerősebb: -59 dBm, leggyengébb: -61 dBm, átlag: -60 dBm.\n\nMinden mérés (dBm):\n-60, -60, -61, -59"),
        Walk(0, listOf<Int>(-60, -60, -61), mapOf<Int, String>(0 to ""), listOf<Boolean>(true, false, true), listOf("névtelen pont: -60 dBm – jó"), true, -60, -61, -60, "3 mérés. Legerősebb: -60 dBm, leggyengébb: -61 dBm, átlag: -60 dBm.\nMegjelölt helyek:\n  névtelen pont: -60 dBm – jó\nA megjelölt helyeken mindenhol legalább elfogadható a jel.", "SuperDL – Wi-Fi jelerősség-bejárás\n\n3 mérés. Legerősebb: -60 dBm, leggyengébb: -61 dBm, átlag: -60 dBm.\nMegjelölt helyek:\n  névtelen pont: -60 dBm – jó\nA megjelölt helyeken mindenhol legalább elfogadható a jel.\n\nMinden mérés (dBm):\n-60, -60, -61", "SuperDL – Wi-Fi jelerősség-bejárás\nHálózat: Otthoni wifi\n\n3 mérés. Legerősebb: -60 dBm, leggyengébb: -61 dBm, átlag: -60 dBm.\nMegjelölt helyek:\n  névtelen pont: -60 dBm – jó\nA megjelölt helyeken mindenhol legalább elfogadható a jel.\n\nMinden mérés (dBm):\n-60, -60, -61"),
        Walk(5, listOf<Int>(), mapOf<Int, String>(), listOf<Boolean>(), listOf(), true, 0, 0, 0, "Nem történt mérés.", "SuperDL – Wi-Fi jelerősség-bejárás\n\nNem történt mérés.\n\nMinden mérés (dBm):\n", "SuperDL – Wi-Fi jelerősség-bejárás\nHálózat: Otthoni wifi\n\nNem történt mérés.\n\nMinden mérés (dBm):\n"),
        Walk(3, listOf<Int>(-61, -62, -64, -63), mapOf<Int, String>(), listOf<Boolean>(true, false, true, false), listOf(), true, -61, -64, -62, "4 mérés. Legerősebb: -61 dBm, leggyengébb: -64 dBm, átlag: -62 dBm.", "SuperDL – Wi-Fi jelerősség-bejárás\n\n4 mérés. Legerősebb: -61 dBm, leggyengébb: -64 dBm, átlag: -62 dBm.\n\nMinden mérés (dBm):\n-61, -62, -64, -63", "SuperDL – Wi-Fi jelerősség-bejárás\nHálózat: Otthoni wifi\n\n4 mérés. Legerősebb: -61 dBm, leggyengébb: -64 dBm, átlag: -62 dBm.\n\nMinden mérés (dBm):\n-61, -62, -64, -63")
    )
}
