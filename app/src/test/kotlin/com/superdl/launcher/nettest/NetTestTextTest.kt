package com.superdl.launcher.nettest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A telefonos Internet-teszt szövegei a WINDOWSOS EREDETIHEZ mérve.
 *
 * A `PyExpected` a Python `nettest.py` valódi kimenete (tools/nettest_vart.py
 * állítja elő). Ahol a telefon SZÁNDÉKOSAN mást mond, azt itt külön,
 * kiírt szöveggel teszteljük — hogy az eltérés döntés legyen, ne véletlen.
 */
class NetTestTextTest {

    // ─────────────────────────────── egyező függvények (Python == Kotlin)

    @Test
    fun jelMinosites() {
        PyExpected.GRADE_INPUT.zip(PyExpected.GRADE).forEach { (dbm, expected) ->
            assertEquals("dBm=$dbm", expected, NetTestText.signalGrade(dbm))
        }
    }

    @Test
    fun jelSzoveg() {
        PyExpected.SIGNAL_TEXT.forEach { (input, expected) ->
            assertEquals(expected, NetTestText.signalText(input.first, input.second, input.third))
        }
    }

    @Test
    fun dbmBecsles() {
        PyExpected.DBM_ESTIMATE.forEach { (pct, expected) ->
            assertEquals("pct=$pct", expected, NetTestText.dbmEstimate(pct))
        }
    }

    @Test
    fun jelFrekvencia() {
        PyExpected.FREQUENCY.forEach { (dbm, expected) ->
            assertEquals("dBm=$dbm", expected, NetTestText.signalFrequency(dbm.toDouble()), 1e-9)
        }
    }

    @Test
    fun idoSzoveg() {
        PyExpected.TIME_TEXT.forEach { (sec, expected) ->
            assertEquals("s=$sec", expected, NetTestText.timeText(sec))
        }
    }

    @Test
    fun egyGigaIdeje() {
        PyExpected.ONE_GIG.forEach { (mbps, expected) ->
            assertEquals("mbps=$mbps", expected, NetTestText.oneGigTime(mbps))
        }
    }

    @Test
    fun maszkolIp() {
        PyExpected.MASK.forEach { (ip, expected) ->
            assertEquals("ip='$ip'", expected, NetTestText.maskIp(ip))
        }
    }

    @Test
    fun szamFormatum() {
        PyExpected.NUM.forEach { (x, expected) -> assertEquals("x=$x", expected, NetTestText.num(x)) }
    }

    @Test
    fun mbps() {
        PyExpected.MBPS.forEach { (b, t, expected) ->
            assertEquals("b=$b t=$t", expected, NetTestText.mbps(b, t), 0.0)
        }
    }

    @Test
    fun fokozat() {
        PyExpected.GRADE_SPEED.forEach { (x, expected) -> assertEquals("x=$x", expected, NetTestText.grade(x)) }
    }

    @Test
    fun osszefoglaloWindowsIgenyekkel() {
        val profiles = PyExpected.profiles()
        profiles.forEachIndexed { i, e ->
            assertEquals("profil #$i", PyExpected.VERDICT[i], NetTestText.verdict(e, NetTestText.WINDOWS_NEEDS))
        }
    }

    @Test
    fun sebessegSorok() {
        PyExpected.profiles().forEachIndexed { i, e ->
            assertEquals("profil #$i", PyExpected.SPEED_LINES[i], NetTestText.speedLines(e))
        }
    }

    @Test
    fun publikusSorok() {
        PyExpected.profiles().forEachIndexed { i, e ->
            // két szándékos eltérés: a teljes IP kérésének módja, és a gépnév
            // (fordított DNS) maszkolt nézetben NEM látszik — a cím benne van
            val expected = PyExpected.PUBLIC_LINES[i]
                .filterNot { it.startsWith("Hoszt neve (fordított DNS): ") }
                .map { if (it == PyExpected.WINDOWS_FULL_IP_HINT) NetTestText.FULL_IP_HINT else it }
            assertEquals("profil #$i", expected, NetTestText.publicLines(e, fullIp = false))
            assertEquals("profil #$i teljes", PyExpected.PUBLIC_LINES_FULL[i], NetTestText.publicLines(e, fullIp = true))
        }
    }

    @Test
    fun mireElegSorok() {
        PyExpected.profiles().forEachIndexed { i, e ->
            assertEquals("profil #$i", PyExpected.NEED_LINES[i], NetTestText.needLinesRaw(e.speed, NetTestText.WINDOWS_NEEDS))
        }
    }

    @Test
    fun figyelmeztetesSorok() {
        PyExpected.profiles().forEachIndexed { i, e ->
            assertEquals("profil #$i", PyExpected.WARNING_LINES[i], NetTestText.warningLines(e))
        }
    }

    @Test
    fun naploSorokEsAtlag() {
        val entries = PyExpected.HISTORY_INPUT.map { (time, down, rest) ->
            NetTestHistory.Entry(time, MODE_FULL, down, rest.first, rest.second)
        }
        assertEquals(PyExpected.HISTORY_LINES, NetTestHistory.lines(entries))
        assertEquals(PyExpected.HISTORY_AVG_10, NetTestHistory.average(entries))
        assertEquals(PyExpected.HISTORY_AVG_2, NetTestHistory.average(entries, 2))
        assertEquals(PyExpected.HISTORY_AVG_NONE, NetTestHistory.average(entries.subList(1, 2)))
        assertEquals(listOf("Még nincs korábbi mérés."), NetTestHistory.lines(emptyList()))
    }

    @Test
    fun wifiBejarasNaplo() {
        PyExpected.WALKS.forEachIndexed { i, w ->
            val log = WifiWalkLog(w.threshold)
            assertEquals("#$i üres jelölés", w.emptyMark, log.mark("x") == null)
            val spoken = mutableListOf<Boolean>()
            val texts = mutableListOf<String>()
            w.readings.forEachIndexed { j, d ->
                spoken += log.add(d)
                w.marks[j]?.let { name -> texts += log.placeText(log.mark(name)!!) }
            }
            assertEquals("#$i bemondás", w.spoken, spoken)
            assertEquals("#$i pontok", w.placeTexts, texts)
            assertEquals("#$i legjobb", w.best, log.best())
            assertEquals("#$i leggyengébb", w.weakest, log.weakest())
            assertEquals("#$i átlag", w.average, log.average())
            assertEquals("#$i összefoglaló", w.summary, log.summary())
            assertEquals("#$i mentés", w.saveText, log.saveText())
            assertEquals("#$i mentés hálózattal", w.saveTextNet, log.saveText("Otthoni wifi"))
        }
    }

    // ─────────────────────────────── SZÁNDÉKOS TELEFONOS ELTÉRÉSEK

    @Test
    fun telefonosBejarasIdoponttal() {
        val log = WifiWalkLog()
        log.add(-60)
        val text = log.saveText("Otthon", "2026-09-28 20:00:00")
        assertTrue(text.startsWith("SuperDL – Wi-Fi jelerősség-bejárás\nHálózat: Otthon\nIdőpont: 2026-09-28 20:00:00\n\n"))
    }

    @Test
    fun telefonosForgalomBecsles() {
        assertEquals("legfeljebb kb. 11 megabájt", NetTestText.estimatedTraffic(MODE_SAVER))
        assertEquals("elhanyagolható (néhány kilobájt)", NetTestText.estimatedTraffic(MODE_QUICK))
        assertEquals(
            "a sebességedtől függően kb. 20 és legfeljebb 460 megabájt között",
            NetTestText.estimatedTraffic(MODE_FULL)
        )
    }

    @Test
    fun telefonosModNevEkezettel() {
        val e = NetTestResult(time = "2026-09-28 20:15:00", mode = MODE_SAVER)
        assertEquals("Mérés ideje: 2026-09-28 20:15:00 (takarékos mérés)", NetTestText.lines(e).first())
    }

    @Test
    fun telefonosSzolgaltatasSorok() {
        val e = NetTestResult().apply {
            services = listOf(
                ServiceProbe("Csevejcenter (Ably)", true, 34.25),
                ServiceProbe("YouTube (keresés, lejátszás)", false, 4001.0)
            )
        }
        assertEquals(
            listOf(
                "— SuperDL-szolgáltatások elérhetősége —",
                "Csevejcenter (Ably): elérhető (34,2 ezredmásodperc)",
                "YouTube (keresés, lejátszás): NEM érhető el"
            ),
            NetTestText.serviceLines(e)
        )
    }

    @Test
    fun telefonosMireElegGyorsNelNincs() {
        val quick = NetTestResult(mode = MODE_QUICK)
        assertTrue(NetTestText.needLines(quick).isEmpty())
        val failed = NetTestResult(mode = MODE_FULL)
        assertEquals(
            listOf(
                "— Mire elég ez a net? —",
                "Mire elég ez a net: nem tudom megmondani, mert a sebességet nem sikerült megmérni"
            ),
            NetTestText.needLines(failed)
        )
    }

    @Test
    fun telefonosIgenyek() {
        val s = NetSpeed(downMbps = 10.0, upMbps = 1.2, latencyAvgMs = 30.0)
        val q = NetTestText.qualifications(s)
        assertEquals(NetTestText.ANDROID_NEEDS.map { it.name }, q.map { it.name })
        assertEquals(
            listOf(true, true, false, true, true, false),
            q.map { it.ok }
        )
        val e = NetTestResult(mode = MODE_FULL).apply {
            speed.downMbps = 10.0; speed.upMbps = 1.2; speed.latencyAvgMs = 30.0
        }
        assertEquals(
            "Az interneted átlagos: 10 megabit letöltés, 1,2 megabit feltöltés, 30 ezredmásodperc késleltetés. " +
                "Elég ehhez: zenehallgatás, internetes rádió, videónézés hd-ben (1080p), hanghívás interneten " +
                "(például whatsapp), videóhívás. Határeset vagy kevés ehhez: videónézés 4k-ban, élő videós adás " +
                "a telefonról (például tiktok live).",
            NetTestText.verdict(e)
        )
    }

    @Test
    fun telefonosItelet_WifiMobilVpn() {
        val base = NetTestResult(mode = MODE_FULL).apply {
            speed.downMbps = 20.0; speed.upMbps = 5.0; speed.latencyAvgMs = 20.0
        }
        val start = NetTestText.verdict(base)

        val weakWifi = base.copy(local = NetLocal(kind = KIND_WIFI, wifi = WifiData(rssi = -78, frequencyMhz = 5180)))
        assertEquals(
            "$start A wifi jele gyenge (-78 dBm) – a lassúság oka jó eséllyel ez, nem a szolgáltató.",
            NetTestText.verdict(weakWifi)
        )
        val band24 = base.copy(local = NetLocal(kind = KIND_WIFI, wifi = WifiData(rssi = -50, frequencyMhz = 2437)))
        assertEquals(
            "$start A wifi a lassabb 2,4 gigahertzes sávon van; ha a router tud 5 gigahertzet, azon gyorsabb lehet.",
            NetTestText.verdict(band24)
        )
        val vpnMobile = base.copy(
            local = NetLocal(kind = KIND_MOBILE, vpn = true, mobile = MobileData(level = 1))
        )
        assertEquals(
            "$start Figyelem: VPN-kapcsolat aktív, ez lassíthatja és elrejtheti a valódi helyzetet. " +
                "A mobiljel gyenge (1 a 4 fokozatból) – a lassúság oka jó eséllyel ez, nem a szolgáltató. " +
                "Ablak mellett vagy a szabadban jobb lehet.",
            NetTestText.verdict(vpnMobile)
        )
        val captive = base.copy(local = NetLocal(kind = KIND_WIFI, captivePortal = true))
        assertTrue(NetTestText.verdict(captive).contains("bejelentkezést kér"))
        val none = base.copy(local = NetLocal(kind = KIND_NONE))
        assertTrue(NetTestText.verdict(none).contains("nincs aktív hálózati kapcsolat"))
    }

    @Test
    fun telefonosKapcsolatSorok() {
        val e = NetTestResult().apply {
            local = NetLocal(
                kind = KIND_WIFI, connection = "vezeték nélküli (Wi-Fi)",
                localIp = "192.168.1.23", gateway = "192.168.1.1", dnsServers = listOf("192.168.1.1"),
                privateDns = "bekapcsolva (dns.google)", mtu = 1500, ipv6 = true, metered = false,
                linkDownKbps = 150000, linkUpKbps = 50000,
                wifi = WifiData(
                    ssid = "Otthoni", rssi = -62, frequencyMhz = 5180, rxMbps = 866, txMbps = 780,
                    standard = "Wi-Fi 5 (802.11ac)"
                ),
                mobile = MobileData(operator = "Telekom HU", generation = "4G (LTE)", level = 3, dbm = -95)
            )
        }
        assertEquals(
            listOf(
                "— Kapcsolat —",
                "Kapcsolat típusa: vezeték nélküli (Wi-Fi)",
                "A telefon sávszélesség-becslése: le 150, fel 50 megabit – ez a rendszer becslése, nem mérés",
                "Wi-Fi hálózat: Otthoni",
                "Wi-Fi jelerősség: -62 dBm – jó. Ez bőven elég mindenre.",
                "Wi-Fi sáv: 5 GHz (csatorna: 36)",
                "Wi-Fi kapcsolati sebesség: le 866, fel 780 megabit",
                "Wi-Fi szabvány: Wi-Fi 5 (802.11ac)",
                "Mobilhálózat: Telekom HU, 4G (LTE) – most nem ezen megy a net",
                "Mobiljel erőssége: 3 a 4 fokozatból – jó (-95 dBm)",
                "Helyi IP-cím: 192.168.1.23",
                "Átjáró (router): 192.168.1.1",
                "DNS-kiszolgálók: 192.168.1.1",
                "Privát DNS: bekapcsolva (dns.google)",
                "MTU (csomagméret): 1500",
                "IPv6 elérhető: igen"
            ),
            NetTestText.localLines(e)
        )
        val hidden = NetTestResult().apply {
            local = NetLocal(kind = KIND_WIFI, wifi = WifiData(rssi = -70), vpn = true, metered = true)
        }
        val lines = NetTestText.localLines(hidden)
        assertTrue(lines.any { it.startsWith("Wi-Fi hálózat neve: nem látható – a hálózat nevét a helymeghatározás nélkül nem látom") })
        assertTrue(lines.contains("VPN aktív: igen – a mérés az ő útvonalán ment"))
        assertTrue(lines.contains("Forgalomkorlátos (mért) kapcsolat: igen – a telefon szerint; vigyázz az adatforgalommal"))
    }

    @Test
    fun savEsCsatorna() {
        assertEquals("2,4 GHz", NetTestText.bandFromFrequency(2412))
        assertEquals(1, NetTestText.channelFromFrequency(2412))
        assertEquals(6, NetTestText.channelFromFrequency(2437))
        assertEquals(13, NetTestText.channelFromFrequency(2472))
        assertEquals(14, NetTestText.channelFromFrequency(2484))
        assertEquals("5 GHz", NetTestText.bandFromFrequency(5180))
        assertEquals(36, NetTestText.channelFromFrequency(5180))
        assertEquals(165, NetTestText.channelFromFrequency(5825))
        assertEquals("6 GHz", NetTestText.bandFromFrequency(5955))
        assertEquals(1, NetTestText.channelFromFrequency(5955))
        assertEquals(233, NetTestText.channelFromFrequency(7115))
        assertEquals(2, NetTestText.channelFromFrequency(5935))
        assertEquals("", NetTestText.bandFromFrequency(0))
        assertEquals(0, NetTestText.channelFromFrequency(0))
        assertEquals("Wi-Fi 6E (802.11ax, 6 GHz)", NetTestText.wifiStandardName(6, 5955))
        assertEquals("Wi-Fi 6 (802.11ax)", NetTestText.wifiStandardName(6, 5180))
        assertEquals("Wi-Fi 7 (802.11be)", NetTestText.wifiStandardName(8))
        assertEquals("", NetTestText.wifiStandardName(0))
    }

    @Test
    fun mobilGeneracio() {
        assertEquals("5G", NetTestText.mobileGeneration(20))
        assertEquals("4G (LTE)", NetTestText.mobileGeneration(13))
        assertEquals("4G (LTE); 5G-jelet is lát a telefon", NetTestText.mobileGeneration(13, nrSignalSeen = true))
        assertEquals("3G (HSPA+)", NetTestText.mobileGeneration(15))
        assertEquals("3G", NetTestText.mobileGeneration(3))
        assertEquals("2G", NetTestText.mobileGeneration(2))
        assertEquals("", NetTestText.mobileGeneration(0))
    }

    @Test
    fun rssiErvenyesseg() {
        assertTrue(NetTestText.validRssi(-62))
        assertFalse(NetTestText.validRssi(-127))
        assertFalse(NetTestText.validRssi(0))
        assertTrue(NetTestText.validRssi(-110))
        assertTrue(NetTestText.validRssi(-5))
    }

    @Test
    fun ipBetuzes() {
        assertEquals("84 pont 2 pont 33 pont 144", NetTestText.spellIp("84.2.33.144"))
    }

    @Test
    fun jelentesMindigMaszkolt() {
        val e = PyExpected.profiles()[0]
        val r = NetTestText.report(e)
        assertFalse(r.contains("84.2.33.144"))
        assertTrue(r.contains("Publikus IP-cím: 84.2.xxx.xxx"))
        assertTrue(r.startsWith("SuperDL – Internet-teszt jelentés\n"))
        assertTrue(r.endsWith("\n"))
    }

    @Test
    fun naploJsonOdaVissza() {
        val e = PyExpected.profiles()[0].apply {
            local = NetLocal(kind = KIND_MOBILE, connection = "mobilnet, 4G (LTE)")
        }
        val entry = NetTestHistory.entryOf(e)
        assertEquals("84.2.xxx.xxx", entry.maskedIp)
        val back = NetTestHistory.fromJson(NetTestHistory.toJson(listOf(entry)))
        assertEquals(listOf(entry), back)
        assertEquals(
            listOf("2026-09-28 20:15:00 – le 45,3, fel 9,9 megabit, késleltetés 25,7 ezredmásodperc – mobilnet, 4G (LTE)"),
            NetTestHistory.lines(back)
        )
        val quick = NetTestHistory.Entry("2026-09-28 21:00:00", MODE_QUICK, 0.0, 0.0, 22.0)
        assertEquals(
            listOf("2026-09-28 21:00:00 – gyors ellenőrzés, késleltetés 22 ezredmásodperc"),
            NetTestHistory.lines(listOf(quick))
        )
        assertTrue(NetTestHistory.fromJson("ez nem json").isEmpty())
        assertTrue(NetTestHistory.fromJson(null).isEmpty())
        var list = emptyList<NetTestHistory.Entry>()
        repeat(205) { list = NetTestHistory.append(list, quick.copy(time = "t$it")) }
        assertEquals(200, list.size)
        assertEquals("t5", list.first().time)
    }

    @Test
    fun adatvedelemGepnevEsNyilvanosHelyiCim() {
        val e = PyExpected.profiles()[0]
        val lines = NetTestText.lines(e)
        assertFalse(lines.any { it.contains("telekom.hu") })
        assertFalse(NetTestText.report(e).contains("catv-84-2-33-144"))
        assertTrue(NetTestText.publicLines(e, fullIp = true).contains(
            "Hoszt neve (fordított DNS): catv-84-2-33-144.catv.fixed.telekom.hu"))

        assertFalse(NetTestText.isPublicAddress("192.168.1.23"))
        assertFalse(NetTestText.isPublicAddress("10.1.2.3"))
        assertFalse(NetTestText.isPublicAddress("172.20.0.1"))
        assertFalse(NetTestText.isPublicAddress("100.72.1.2"))
        assertFalse(NetTestText.isPublicAddress("fe80::1%wlan0"))
        assertFalse(NetTestText.isPublicAddress("fd12:3456::1"))
        assertFalse(NetTestText.isPublicAddress(""))
        assertTrue(NetTestText.isPublicAddress("2a00:1110:1234:5678::9"))
        assertTrue(NetTestText.isPublicAddress("84.2.33.144"))
        assertTrue(NetTestText.isPublicAddress("172.32.0.1"))

        val mobile = NetTestResult().apply {
            local = NetLocal(kind = KIND_MOBILE, localIp = "2a00:1110:1234:5678::9", gateway = "10.0.0.1")
        }
        val ll = NetTestText.localLines(mobile)
        assertTrue(ll.contains("Helyi IP-cím: 2a00:1110:xxxx:xxxx (nyilvános cím, ezért rejtve)"))
        assertTrue(ll.contains("Átjáró (router): 10.0.0.1"))
        assertFalse(NetTestText.report(mobile).contains("5678"))
    }

    @Test
    fun hibaleirasbanNincsCim() {
        val d = SpeedMeter.describe(java.net.ConnectException("failed to connect to /1.1.1.1 (port 443) from /84.2.33.144"))
        assertEquals("a kapcsolat nem jött létre (ConnectException)", d)
        assertEquals("időtúllépés (SocketTimeoutException)", SpeedMeter.describe(java.net.SocketTimeoutException("x 84.2.33.144")))
        assertEquals("váratlan hiba (IllegalStateException)", SpeedMeter.describe(IllegalStateException("10.0.0.5")))
    }

    @Test
    fun bejarasCsendesMeresNemMozditjaAzAlapot() {
        val log = WifiWalkLog()
        assertTrue(log.add(-60))                        // kimondva: az alap -60
        assertFalse(log.add(-70, canSpeak = false))     // épp beszélünk: nem mondjuk
        assertFalse(log.add(-71, canSpeak = false))     // diktálás közben sem
        assertTrue(log.add(-71))                        // szóhoz jutunk: a változás elhangzik
        assertFalse(log.add(-72))                       // az új alap már -71
        assertEquals(5, log.readings.size)
    }

    @Test
    fun uresNaploJeloles() {
        assertNull(WifiWalkLog().mark("konyha"))
    }
}
