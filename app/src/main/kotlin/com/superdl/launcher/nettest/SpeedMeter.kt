package com.superdl.launcher.nettest

import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Random

/** Monoton óra másodpercben (a Python `time.monotonic` párja). */
fun monoSeconds(): Double = System.nanoTime() / 1_000_000_000.0

/**
 * MEGSZAKÍTÁS. A felhasználó balra söpörve bármikor leállíthatja a mérést.
 *
 * Miért nem elég egy jelző? Mert egy épp OLVASÓ vagy ÍRÓ szál a jelzőt csak a
 * következő adag után látná — lassú vonalon ez másodpercekig tart, közben
 * pedig fogyna az adat. Ezért a nyitott kapcsolatokat nyilvántartjuk, és
 * megszakításkor azonnal lebontjuk őket.
 */
class StopSignal {
    @Volatile
    var isSet: Boolean = false
        private set

    private val open: MutableSet<Closeable> = Collections.synchronizedSet(LinkedHashSet())

    /** A jelző és a mentés közös zára (lásd `unlessStopped`). */
    private val gate = Any()

    /**
     * Megszakítás. A jelzőt a zár alatt állítjuk: így a „Nem mentettem el"
     * bemondás igaz — a mentés (`unlessStopped`) vagy előtte lefutott, vagy
     * már biztosan nem fut le.
     *
     * @param closeNow a nyitott kapcsolatokat azonnal bontja-e. A FŐ SZÁLRÓL
     *        `false`-szal hívandó, és a `closeAll()` külön háttérszálon: egy
     *        titkosított kapcsolat lezárása hálózati művelet, amit az Android
     *        a fő szálon nem enged.
     */
    fun set(closeNow: Boolean = true) {
        synchronized(gate) { isSet = true }
        if (closeNow) closeAll()
    }

    /** A `block` csak akkor fut, ha nincs megszakítás — és közben nem is lehet. */
    fun <T> unlessStopped(block: () -> T): T? = synchronized(gate) { if (isSet) null else block() }

    fun closeAll() {
        val list = synchronized(open) { open.toList() }
        list.forEach { runCatching { it.close() } }
    }

    fun register(c: Closeable) {
        open += c
        if (isSet) runCatching { c.close() }
    }

    fun unregister(c: Closeable) {
        open -= c
    }
}

/**
 * = nettest._Szamlalo — bájtszámláló BEMELEGÍTÉS-LEVONÁSSAL.
 *
 * Két csapdát kerülünk el vele:
 *  • A kapcsolatépítés (TCP+TLS) ideje nem sebesség — az órát az ELSŐ BÁJT
 *    indítja, nem a kérés kiküldése.
 *  • A TCP „lassú indítás" miatt az első másodperc mindig lassabb a valódi
 *    sebességnél. Ezt a szakaszt KIHAGYJUK; a mért érték a bemelegítés UTÁNI,
 *    egyenletes szakaszra vonatkozik.
 * Ha a mérés rövidebb a bemelegítésnél, becsületesen a teljes szakaszt
 * számoljuk (jobb egy óvatos szám, mint egy hamis).
 *
 * Az óra beinjektálható — így a teszt valódi várakozás nélkül ellenőrzi.
 */
class SpeedCounter(val warmup: Double = 1.0, private val clock: () -> Double = ::monoSeconds) {
    private val lock = Any()

    @Volatile
    var bytes: Long = 0L
        private set

    private var first: Double? = null
    private var last: Double? = null
    private var measureT0: Double? = null
    private var measureBytes0: Long = 0L

    fun add(n: Long) {
        val now = clock()
        synchronized(lock) {
            val f = first ?: now.also { first = it }
            bytes += n
            last = now
            if (measureT0 == null && now - f >= warmup) {
                measureT0 = now
                measureBytes0 = bytes
            }
        }
    }

    /** (Mbit/s, összes bájt) — a Mbit/s a bemelegítés utáni szakaszból. */
    fun result(): Pair<Double, Long> = synchronized(lock) {
        val f = first
        val l = last
        val t0 = measureT0
        when {
            f == null || l == null -> 0.0 to 0L
            t0 != null && l > t0 -> NetTestText.mbps(bytes - measureBytes0, l - t0) to bytes
            l > f -> NetTestText.mbps(bytes, l - f) to bytes
            else -> 0.0 to bytes
        }
    }
}

/** = nettest.SavEredmeny */
data class BandResult(
    /** a fél másodpercenkénti minták MEDIÁNJA */
    val mbps: Double = 0.0,
    /** a legjobb minta */
    val peakMbps: Double = 0.0,
    val bytes: Long = 0L,
    val samples: List<Double> = emptyList()
) {
    val fluctuating: Boolean get() = NetTestText.isFluctuating(mbps, peakMbps, samples)
}

/**
 * SÁVSZÉLESSÉG-MÉRÉS. Mérő-végpont: a Cloudflare NYILVÁNOSAN DOKUMENTÁLT,
 * ingyenes sebességmérője (speed.cloudflare.com) — ugyanaz, mint Windowson.
 * Az Ookla API-ja nem szabadon használható, ezért azt tudatosan kerüljük.
 */
object SpeedMeter {

    const val CF_DOWN = "https://speed.cloudflare.com/__down?bytes=%d"
    const val CF_UP = "https://speed.cloudflare.com/__up"

    /**
     * LETÖLTÉSI FORRÁSOK, sorrendben. Azért több, mert élesben kiderült: egy
     * kiszolgáló időnként visszafogja a nem böngésző klienst, és akkor a mérés
     * a valódi sebesség töredékét mutatná. (Ugyanaz a lista, mint Windowson.)
     */
    val DOWN_SOURCES = listOf(
        CF_DOWN,
        "https://github.com/korosmezeydavid/SuperDL/releases/download/v4.3.0/SuperDL.exe",
        "https://proof.ovh.net/files/100Mb.dat"
    )

    const val USER_AGENT = "SuperDL-nettest/1.0 (Android)"

    // mérési idők és felső adatkorlátok — a Windows-értékek
    const val DOWN_SECONDS = 7.0
    const val UP_SECONDS = 6.0
    const val DOWN_THREADS = 4
    const val UP_THREADS = 3
    val DOWN_CAP = 300 * NetTestText.MB
    val UP_CAP = 120 * NetTestText.MB
    const val SAVER_DOWN_SECONDS = 2.5
    const val SAVER_UP_SECONDS = 2.0
    val SAVER_DOWN_CAP = 6 * NetTestText.MB
    val SAVER_UP_CAP = 3 * NetTestText.MB

    private fun httpCloseable(c: HttpURLConnection) = Closeable { runCatching { c.disconnect() } }

    private fun open(url: String, timeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            // tömörítés nélkül: a mért bájt a valóban átvitt bájt legyen
            setRequestProperty("Accept-Encoding", "identity")
        }

    /**
     * = nettest._le_szal — IDŐALAPÚ letöltés: a határidőig tölt, adagonként új
     * kéréssel. (A fix, nagy bájtcél megbízhatatlan volt: a végpont a túl nagy
     * adagot visszafogja.)
     */
    fun downloadWorker(
        chunk: Long, counter: SpeedCounter, stop: StopSignal?, errors: MutableList<String>,
        deadline: Double, cap: Long, url: String, local: StopSignal? = null
    ) {
        val src = url.ifBlank { CF_DOWN }
        val buf = ByteArray(65536)
        while (monoSeconds() < deadline) {
            if (stop?.isSet == true || local?.isSet == true || counter.bytes >= cap) return
            var conn: HttpURLConnection? = null
            var handle: Closeable? = null
            try {
                val target = if ("%d" in src) src.replace("%d", chunk.toString()) else src
                val c = open(target, 15_000)
                conn = c
                val h = httpCloseable(c)
                handle = h
                stop?.register(h)
                local?.register(h)
                c.inputStream.use { input ->
                    while (monoSeconds() < deadline) {
                        if (stop?.isSet == true || local?.isSet == true || counter.bytes >= cap) return
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n > 0) counter.add(n.toLong())
                    }
                }
            } catch (t: Throwable) {
                if (stop?.isSet != true && local?.isSet != true) errors += "letöltés: ${describe(t)}"
                return
            } finally {
                handle?.let { stop?.unregister(it); local?.unregister(it) }
                runCatching { conn?.disconnect() }
            }
        }
    }

    /**
     * = nettest._fel_szal — IDŐALAPÚ feltöltés. Egy megkezdett adagot végig-
     * küldünk (a félbehagyott kérés hibát adna) — a túllövés legfeljebb egy
     * adagnyi. Véletlen adat: nem tömöríthető, a mérés így becsületes.
     */
    fun uploadWorker(
        chunk: Long, counter: SpeedCounter, stop: StopSignal?, errors: MutableList<String>,
        deadline: Double, cap: Long, local: StopSignal? = null
    ) {
        val block = ByteArray(65536).also { Random().nextBytes(it) }
        while (monoSeconds() < deadline) {
            if (stop?.isSet == true || local?.isSet == true || counter.bytes >= cap) return
            var conn: HttpURLConnection? = null
            var handle: Closeable? = null
            try {
                val c = open(CF_UP, 30_000)
                conn = c
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.setFixedLengthStreamingMode(chunk)
                val h = httpCloseable(c)
                handle = h
                stop?.register(h)
                local?.register(h)
                c.outputStream.use { out ->
                    var sent = 0L
                    while (sent < chunk) {
                        if (stop?.isSet == true || local?.isSet == true) return
                        val n = minOf(block.size.toLong(), chunk - sent).toInt()
                        out.write(block, 0, n)
                        sent += n
                        // ŐSZINTE MEGJEGYZÉS: a bájtot akkor számoljuk, amikor
                        // átadtuk a rendszer küldőpufferének, nem amikor
                        // ténylegesen elment — a feltöltés az elején így kicsit
                        // gyorsabbnak látszhat (a Windows-változat ugyanígy
                        // számol). A bemelegítés levonása ennek zömét elnyeli.
                        counter.add(n.toLong())
                    }
                }
                c.responseCode
                runCatching { c.inputStream.use { it.read(ByteArray(256)) } }
            } catch (t: Throwable) {
                if (stop?.isSet != true && local?.isSet != true) errors += "feltöltés: ${describe(t)}"
                return
            } finally {
                handle?.let { stop?.unregister(it); local?.unregister(it) }
                runCatching { conn?.disconnect() }
            }
        }
    }

    /**
     * A minták összegzése (= a `_savszelesseg` vége): a MEDIÁNT jelentjük, nem
     * az átlagot — akadozó vonalnál (villanásnyi csúcs, aztán megállás) az
     * átlag szépít, a medián az igazat mondja. A csúcsot külön megőrizzük,
     * mert a kettő KÜLÖNBSÉGE maga a diagnózis.
     */
    fun summarize(samples: List<Double>, averageMbps: Double, bytes: Long): BandResult {
        val useful = samples.filter { it >= 0 }
        val median = if (useful.isNotEmpty()) NetTestText.pyRound(median(useful), 2) else averageMbps
        return BandResult(
            mbps = if (median != 0.0) median else averageMbps,
            peakMbps = if (useful.isNotEmpty()) NetTestText.pyRound(useful.max(), 2) else averageMbps,
            bytes = bytes,
            samples = useful.map { NetTestText.pyRound(it, 1) }
        )
    }

    fun median(values: List<Double>): Double {
        val s = values.sorted()
        val n = s.size
        if (n == 0) return 0.0
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /**
     * = nettest._savszelesseg — `threads` párhuzamos kapcsolat `seconds` ideig
     * (egy kapcsolat sokszor nem tudja kitölteni a vonalat), fél másodpercenként
     * mintával. `cap`: felső bájtkorlát (mobilnet-védelem).
     *
     * TELEFONON PLUSZ: ha egy kiszolgáló a határidő után is „lóg" (lassú
     * olvasás), két másodperc múlva mi bontjuk a kapcsolatot — egy mobilon
     * elakadó kérés ne nyújtsa percekre a mérést.
     */
    fun measureBand(
        upload: Boolean, seconds: Double, threads: Int, stop: StopSignal?, errors: MutableList<String>,
        progress: ((Double) -> Unit)? = null, chunk: Long = 0L, cap: Long = 0L, url: String = ""
    ): BandResult {
        val piece = if (chunk > 0) chunk else if (upload) 8 * NetTestText.MB else 25 * NetTestText.MB
        val limit = if (cap > 0) cap else if (upload) UP_CAP else DOWN_CAP
        val warm = minOf(1.0, seconds / 4)
        val counter = SpeedCounter(warm)
        val deadline = monoSeconds() + seconds
        val local = StopSignal()
        val workers = (0 until threads).map {
            Thread({
                try {
                    if (upload) uploadWorker(piece, counter, stop, errors, deadline, limit, local)
                    else downloadWorker(piece, counter, stop, errors, deadline, limit, url, local)
                } catch (t: Throwable) {
                    // egy szál váratlan hibája se döntse be a mérést — a többi fut tovább
                    if (stop?.isSet != true) errors += (if (upload) "feltöltés: " else "letöltés: ") + describe(t)
                }
            }, if (upload) "SDL-nettest-fel" else "SDL-nettest-le").apply { isDaemon = true }
        }
        val start = monoSeconds()
        workers.forEach { it.start() }
        val samples = mutableListOf<Double>()
        var prevBytes = 0L
        var prevT = start
        while (workers.any { it.isAlive }) {
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
                local.set()
                break
            }
            val now = monoSeconds()
            val bytesNow = counter.bytes
            if (now - start >= warm && now > prevT) samples += NetTestText.mbps(bytesNow - prevBytes, now - prevT)
            prevBytes = bytesNow
            prevT = now
            if (now > deadline + 2.0) local.set()
            progress?.let {
                val elapsed = seconds - maxOf(0.0, deadline - now)
                runCatching { it(minOf(1.0, if (seconds > 0) elapsed / seconds else 1.0)) }
            }
        }
        workers.forEach { runCatching { it.join(2000) } }
        val (avg, bytes) = counter.result()
        return summarize(samples, avg, bytes)
    }

    /**
     * = nettest._le_forras — MELYIK forrásból mérjünk? Villámgyors mintával
     * sorra próbáljuk; az elsőt használjuk, amelyik tényleg ad adatot.
     *
     * TELEFONON MÁS: a Windows itt forrásonként akár 8 MB-ot is letölt, és ezt
     * nem számolja bele a mérés adatforgalmába. Takarékos módban (mobilnet!)
     * itt legfeljebb 1 MB megy, és a VALÓBAN letöltött bájtokat visszaadjuk,
     * hogy a „mérés adatforgalma" sor igaz legyen.
     * @return (a választott forrás, a próbák összes bájtja)
     */
    fun pickSource(stop: StopSignal?, errors: MutableList<String>, saver: Boolean): Pair<String, Long> {
        var total = 0L
        for (u in DOWN_SOURCES) {
            if (stop?.isSet == true) return "" to total
            val counter = SpeedCounter(0.0)
            val chunk = if (saver) NetTestText.MB else 4 * NetTestText.MB
            val cap = if (saver) NetTestText.MB else 8 * NetTestText.MB
            downloadWorker(chunk, counter, stop, mutableListOf(), monoSeconds() + 2.0, cap, u)
            total += counter.bytes
            if (counter.bytes >= 512 * 1024) return u to total
        }
        errors += "egyik mérő-forrás sem adott adatot"
        return DOWN_SOURCES.first() to total
    }

    /**
     * Rövid, felolvasható hibaleírás: magyar ok + a kivétel NEVE.
     *
     * ADATVÉDELEM: a kivétel ÜZENETÉT szándékosan nem írjuk ki. Az olyan
     * üzenetek, mint a „failed to connect to /1.1.1.1 … from /84.2.33.144",
     * a telefon saját (akár nyilvános) címét is tartalmazhatják — és ez a sor
     * a megosztott jelentésbe is bekerül.
     */
    fun describe(t: Throwable): String {
        val reason = when (t) {
            is java.net.SocketTimeoutException -> "időtúllépés"
            is java.net.UnknownHostException -> "a kiszolgáló neve nem oldható fel"
            is java.net.ConnectException, is java.net.NoRouteToHostException -> "a kapcsolat nem jött létre"
            is javax.net.ssl.SSLException -> "hiba a titkosított kapcsolatban"
            is java.io.FileNotFoundException -> "a kiszolgáló nem találja a kért címet"
            is java.net.SocketException -> "a kapcsolat megszakadt"
            is java.io.IOException -> "hálózati hiba"
            else -> "váratlan hiba"
        }
        return "$reason (${t.javaClass.simpleName})"
    }
}
