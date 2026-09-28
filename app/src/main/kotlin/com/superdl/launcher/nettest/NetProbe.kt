package com.superdl.launcher.nettest

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * KÉSLELTETÉS, DNS, PUBLIKUS ADATOK, SZOLGÁLTATÁSOK — és maga a mérés.
 *
 * A `nettest.py` mérő részének telefonos párja, csak java.net-tel (nincs
 * android.* import): a hívó a telefon-specifikus helyi adatokat egy
 * függvényként adja át (`collectLocal`), így ez a fájl JVM-en is fordul.
 *
 * MÉRÉSI ŐSZINTESÉG (zéró tolerancia a pontatlanságra):
 *  • Csak azt írjuk ki, amit tényleg MÉRÜNK. Csomagvesztést NEM mérünk —
 *    ICMP nélkül nem lehet rendesen —, helyette a sikeres próbák arányát.
 *  • A késleltetés TCP-kapcsolatnyitás ideje (nem ICMP-ping); a jelentés is
 *    kimondja.
 *  • A DNS-időbe a gyorsítótár is beleszámít; ezt is kimondjuk.
 */
object NetProbe {

    const val CF_TRACE = "https://www.cloudflare.com/cdn-cgi/trace"
    const val IPINFO = "https://ipinfo.io/json"

    /** Egy mérési fázis és a teljes mérés százaléka. */
    fun interface Progress {
        fun update(phase: String, percent: Double)
    }

    /**
     * = nettest.keslekedes — TCP-kapcsolatnyitás ideje (jogosultság nélkül).
     * @return (legkisebb ms, átlag ms, ingadozás ms, sikeres százalék)
     */
    fun latency(
        samples: Int = 12, stop: StopSignal? = null, host: String = "1.1.1.1", port: Int = 443
    ): DoubleArray {
        val times = mutableListOf<Double>()
        var tried = 0
        for (i in 0 until maxOf(3, samples)) {
            if (stop?.isSet == true) break
            tried++
            val t = System.nanoTime()
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 3000)
                    times += (System.nanoTime() - t) / 1_000_000.0
                }
            } catch (_: Exception) {
            }
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                break
            }
        }
        return latencyStats(times, maxOf(1, tried))
    }

    /** A késleltetés-statisztika (a mérésből kiemelve, hogy tesztelhető legyen). */
    fun latencyStats(times: List<Double>, tried: Int): DoubleArray {
        if (times.isEmpty()) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val diffs = (1 until times.size).map { kotlin.math.abs(times[it] - times[it - 1]) }
        return doubleArrayOf(
            NetTestText.pyRound(times.min(), 1),
            NetTestText.pyRound(times.sum() / times.size, 1),
            if (diffs.isNotEmpty()) NetTestText.pyRound(diffs.sum() / diffs.size, 1) else 0.0,
            NetTestText.pyRound(times.size.toDouble() / maxOf(1, tried) * 100, 1)
        )
    }

    /**
     * = nettest.dns_ido — a névfeloldás ideje, medián. A telefon (és a
     * szolgáltató) gyorsítótára is beleszámít — a jelentés ezt kimondja.
     */
    fun dnsTime(hosts: List<String> = listOf("github.com", "www.youtube.com", "cloudflare.com")): Double {
        val times = mutableListOf<Double>()
        for (h in hosts) {
            val t = System.nanoTime()
            try {
                InetAddress.getAllByName(h)
                times += (System.nanoTime() - t) / 1_000_000.0
            } catch (_: Exception) {
            }
        }
        return if (times.isNotEmpty()) NetTestText.pyRound(SpeedMeter.median(times), 1) else 0.0
    }

    /** = nettest.ipv6_elerheto — a Cloudflare IPv6-os DNS-e elérhető-e. */
    fun ipv6Reachable(timeoutMs: Int = 2500): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("2606:4700:4700::1111", 53), timeoutMs) }
        true
    } catch (_: Exception) {
        false
    }

    private fun httpText(url: String, timeoutMs: Int): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            setRequestProperty("User-Agent", SpeedMeter.USER_AGENT)
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            runCatching { c.disconnect() }
        }
    }

    /** A Cloudflare `cdn-cgi/trace` válaszának feldolgozása: `kulcs=érték` sorok. */
    fun parseTrace(text: String): Map<String, String> =
        text.lines().filter { '=' in it }.associate {
            it.substringBefore('=').trim() to it.substringAfter('=').trim()
        }

    /**
     * Az ipinfo „org" mezője: „AS5483 Magyar Telekom plc." → (5483, Magyar Telekom plc.).
     * = a `publikus_adatok` szétválasztása.
     */
    fun splitOrg(org: String): Pair<String, String> =
        if (org.startsWith("AS")) {
            val asn = org.substringBefore(' ')
            val name = if (' ' in org) org.substringAfter(' ') else ""
            asn.drop(2) to name
        } else "" to org

    /**
     * = nettest.publikus_adatok — publikus IP, ország, mérő-központ (Cloudflare
     * trace), majd — ha elérhető — a szolgáltató és a durva hely (ipinfo.io,
     * kulcs nélkül). Mindkettő HIBATŰRŐ. ADATVÉDELEM: ezek a kiszolgálók a
     * kapcsolatból amúgy is látják az IP-t; mi semmi mást nem küldünk.
     */
    fun publicInfo(timeoutMs: Int = 8000): NetPublic {
        val p = NetPublic()
        try {
            val d = parseTrace(httpText(CF_TRACE, minOf(timeoutMs, 6000)))
            p.ip = d["ip"].orEmpty()
            p.country = d["loc"].orEmpty()
            p.colo = d["colo"].orEmpty()
        } catch (_: Exception) {
        }
        try {
            val d = JSONObject(httpText(IPINFO, timeoutMs))
            p.ip = p.ip.ifBlank { str(d, "ip") }
            p.city = str(d, "city")
            p.country = str(d, "country").ifBlank { p.country }
            p.host = str(d, "hostname")
            val (asn, name) = splitOrg(str(d, "org"))
            p.asn = asn
            p.provider = name
        } catch (_: Exception) {
        }
        if (p.ip.isNotBlank() && p.host.isBlank()) p.host = reverseDns(p.ip, 3000)
        return p
    }

    /** Szövegmező JSON-ból; a JSON `null` (az Androidon „null" szöveg lenne) üres. */
    private fun str(o: JSONObject, key: String): String =
        if (!o.has(key) || o.isNull(key)) "" else o.optString(key, "")

    /**
     * Fordított DNS időkorláttal. Az InetAddress-nek nincs saját időkorlátja,
     * ezért külön szálon fut, és legfeljebb `timeoutMs`-ig várunk rá.
     */
    fun reverseDns(ip: String, timeoutMs: Long): String {
        var out = ""
        val t = Thread {
            try {
                val name = InetAddress.getByName(ip).canonicalHostName
                if (name != ip) out = name
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }
        t.start()
        try {
            t.join(timeoutMs)
        } catch (_: InterruptedException) {
        }
        return if (t.isAlive) "" else out
    }

    /** = nettest.szolgaltatas_probak — TCP-kapcsolatnyitás minden szolgáltatáshoz. */
    fun probeServices(
        targets: List<ServiceTarget>, stop: StopSignal? = null, progress: ((Double) -> Unit)? = null
    ): List<ServiceProbe> {
        val out = mutableListOf<ServiceProbe>()
        for ((i, s) in targets.withIndex()) {
            if (stop?.isSet == true) break
            val t = System.nanoTime()
            val ok = try {
                Socket().use { it.connect(InetSocketAddress(s.host, s.port), 4000) }
                true
            } catch (_: Exception) {
                false
            }
            out += ServiceProbe(s.name, ok, NetTestText.pyRound((System.nanoTime() - t) / 1_000_000.0, 1))
            progress?.let { runCatching { it((i + 1).toDouble() / targets.size) } }
        }
        return out
    }

    fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    /**
     * = nettest.merj — A TELJES MÉRÉS. A hívó szál BLOKKOL (háttérszálon hívandó).
     *
     * @param mode MODE_FULL / MODE_SAVER / MODE_QUICK (a gyors sebességet nem mér)
     * @param collectLocal a telefon helyi hálózati adatai (Android-oldali gyűjtő)
     */
    fun measure(
        mode: String,
        stop: StopSignal,
        progress: Progress?,
        collectLocal: () -> NetLocal,
        services: List<ServiceTarget> = NetTestText.ANDROID_SERVICES
    ): NetTestResult {
        val e = NetTestResult(time = timestamp(), mode = mode)
        val errors = e.errors
        fun say(phase: String, pct: Double) {
            try {
                progress?.update(phase, pct.coerceIn(0.0, 100.0))
            } catch (_: Throwable) {
            }
        }

        say("Helyi hálózat felmérése", 2.0)
        try {
            e.local = collectLocal()
        } catch (t: Throwable) {
            errors += "helyi hálózat: ${SpeedMeter.describe(t)}"
        }
        try {
            e.local.ipv6 = ipv6Reachable()
        } catch (_: Throwable) {
        }
        if (stop.isSet) return e.also { it.cancelled = true }

        say("Publikus adatok lekérdezése", 8.0)
        try {
            e.pub = publicInfo()
        } catch (t: Throwable) {
            errors += "publikus adatok: ${SpeedMeter.describe(t)}"
        }

        say("Késleltetés mérése", 14.0)
        try {
            val l = latency(samples = if (mode == MODE_SAVER) 6 else 12, stop = stop)
            e.speed.latencyMinMs = l[0]
            e.speed.latencyAvgMs = l[1]
            e.speed.jitterMs = l[2]
            e.speed.successPct = l[3]
            e.speed.dnsMs = dnsTime()
        } catch (t: Throwable) {
            errors += "késleltetés: ${SpeedMeter.describe(t)}"
        }
        if (stop.isSet) return e.also { it.cancelled = true }

        if (mode != MODE_QUICK) {
            val saver = mode == MODE_SAVER
            say("Letöltési sebesség mérése", 20.0)
            try {
                val (source, probeBytes) = SpeedMeter.pickSource(stop, errors, saver)
                val down = SpeedMeter.measureBand(
                    upload = false,
                    seconds = if (saver) SpeedMeter.SAVER_DOWN_SECONDS else SpeedMeter.DOWN_SECONDS,
                    threads = if (saver) 2 else SpeedMeter.DOWN_THREADS,
                    stop = stop, errors = errors,
                    progress = { p -> say("Letöltési sebesség mérése", 22 + 30 * p) },
                    chunk = if (saver) 4 * NetTestText.MB else 0L,
                    cap = if (saver) SpeedMeter.SAVER_DOWN_CAP else 0L,
                    url = source
                )
                e.speed.downMbps = down.mbps
                e.speed.downPeakMbps = down.peakMbps
                e.speed.downSamples = down.samples
                // A forrás-próba is adatforgalom — beleszámoljuk, hogy a sor igaz legyen.
                e.speed.downBytes = down.bytes + probeBytes
            } catch (t: Throwable) {
                errors += "letöltés: ${SpeedMeter.describe(t)}"
            }
            if (stop.isSet) return e.also { it.cancelled = true }

            say("Feltöltési sebesség mérése", 55.0)
            try {
                val up = SpeedMeter.measureBand(
                    upload = true,
                    seconds = if (saver) SpeedMeter.SAVER_UP_SECONDS else SpeedMeter.UP_SECONDS,
                    threads = if (saver) 2 else SpeedMeter.UP_THREADS,
                    stop = stop, errors = errors,
                    progress = { p -> say("Feltöltési sebesség mérése", 55 + 30 * p) },
                    // takarékosan kisebb adag: a megkezdett adagot végigküldjük,
                    // így ennyivel lőhet túl a plafonon — mobilneten ez számít
                    chunk = if (saver) 512 * 1024L else 0L,
                    cap = if (saver) SpeedMeter.SAVER_UP_CAP else 0L
                )
                e.speed.upMbps = up.mbps
                e.speed.upPeakMbps = up.peakMbps
                e.speed.upSamples = up.samples
                e.speed.upBytes = up.bytes
            } catch (t: Throwable) {
                errors += "feltöltés: ${SpeedMeter.describe(t)}"
            }
            if (stop.isSet) return e.also { it.cancelled = true }
        }

        say("Szolgáltatások ellenőrzése", 88.0)
        try {
            e.services = probeServices(services, stop) { p -> say("Szolgáltatások ellenőrzése", 88 + 12 * p) }
        } catch (t: Throwable) {
            errors += "szolgáltatások: ${SpeedMeter.describe(t)}"
        }
        if (stop.isSet) e.cancelled = true
        say("Kész", 100.0)
        return e
    }
}
