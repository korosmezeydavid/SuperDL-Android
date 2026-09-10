package com.superdl.launcher.radio

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * LEJÁTSZÁSI LISTA BEOLVASÁSA A RÁDIÓBA.
 *
 * MIÉRT KÜLÖN OSZTÁLY, HA VAN MÁR RadioPlaylistResolver. A kettő két
 * különböző kérdésre válaszol, és ez a különbség adta a hibát:
 *
 *   - a `RadioPlaylistResolver` LEJÁTSZÁSKOR fut, és az ELSŐ használható
 *     stream-címet keresi. Neki egy cím kell, mert egy dolgot kell szólaltatni.
 *   - ez az osztály FELVÉTELKOR fut, és MINDEN bejegyzést kigyűjt, névvel
 *     együtt. Egy m3u fájlban tíz állomás is lehet, és mind a tíz kell.
 *
 * A HIBA, AMIT EZ JAVÍT (Mezei Géza, 2026-09-09, szó szerint):
 *
 *   „ugyan vannak m3u fájljaim, de amikor a szuper fájlkezelőbe bemegyek,
 *    megkeresek egy mappát ahol a rádió fájlok vannak, másolni szeretném az
 *    adott fájlt, nem tudom hogy megoldani hogy úgy kerüljön a vágólapra hogy
 *    azt a rádióba bemásoljam, mert a másolandó helyek között nem szerepel,
 *    menüpont sincs rá. A súgó se tartalmaz erre vonatkozóan semmit."
 *
 * Nem a másolás hiányzott, hanem a beolvasás. Egy lejátszási listát nem
 * vágólapon át kell a rádióba juttatni, hanem meg kell nyitni.
 */
object RadioPlaylistImporter {

    private const val TIMEOUT_MS = 15_000
    private const val USER_AGENT = "SuperDL/1.0 (accessibility launcher)"

    /** Legfeljebb ennyi bejegyzést veszünk át egy listából. */
    private const val MAX_BEJEGYZES = 300

    /** Egy beolvasott bejegyzés: a hallható neve és a címe. */
    data class Bejegyzes(val nev: String, val url: String)

    /**
     * FELISMERJÜK-E EZT A FÁJLT LEJÁTSZÁSI LISTÁNAK.
     *
     * A `listen.pls` a leggyakoribb név a neten (SomaFM, Shoutcast), de a
     * kiterjesztés a mérvadó. Az `.m3u8` szándékosan benne van: az élő
     * adásoknál ma már ez a gyakoribb.
     */
    fun listaFajl(nev: String): Boolean {
        val n = nev.substringBefore('?').substringBefore('#').lowercase()
        return n.endsWith(".m3u") || n.endsWith(".m3u8") || n.endsWith(".pls")
    }

    /** Beolvasás a telefonon lévő fájlból. */
    fun fajlbol(file: File): List<Bejegyzes> = try {
        feldolgoz(file.readText(Charsets.UTF_8), file.name)
    } catch (_: Exception) {
        emptyList()
    }

    /** Beolvasás a fájlkezelő által adott hivatkozásból (content:// is lehet). */
    fun uribol(context: Context, uri: Uri, nev: String): List<Bejegyzes> = try {
        val szoveg = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        if (szoveg.isNullOrBlank()) emptyList() else feldolgoz(szoveg, nev)
    } catch (_: Exception) {
        emptyList()
    }

    /** Beolvasás a hálózatról — ide tartozik a listen.pls típusú hivatkozás is. */
    fun halozatrol(url: String): List<Bejegyzes> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode !in 200..299) return emptyList()
            val szoveg = conn.inputStream.bufferedReader().use { it.readText() }
            feldolgoz(szoveg, url.substringAfterLast('/').substringBefore('?'))
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * A FELDOLGOZÁS.
     *
     * A `tartalekNev` akkor kell, ha a lista nem ad nevet a bejegyzéseknek —
     * a sima m3u tipikusan nem ad. Ilyenkor a FÁJL nevéből csinálunk nevet,
     * mert egy „http kettőspont perjel perjel" kezdetű felolvasás vakon
     * használhatatlan a kedvencek listájában.
     */
    fun feldolgoz(szoveg: String, tartalekNev: String): List<Bejegyzes> {
        val sorok = szoveg.lineSequence().map { it.trim() }.toList()
        val plsSzeru = sorok.any { it.startsWith("File", ignoreCase = true) && it.contains("=") }
        val nyers = if (plsSzeru) plsFeldolgoz(sorok) else m3uFeldolgoz(sorok)
        val alap = tisztaNev(tartalekNev)
        return nyers
            .filter { it.url.startsWith("http", ignoreCase = true) }
            .distinctBy { it.url }
            .take(MAX_BEJEGYZES)
            .mapIndexed { i, b ->
                if (b.nev.isNotBlank()) b
                else b.copy(nev = if (nyers.size > 1) "$alap ${i + 1}" else alap)
            }
    }

    /**
     * .pls: `File1=`, `Title1=` párok. A sorszám köti össze a kettőt, ezért
     * nem elég sorrendben olvasni — egy listában a Title sor a File elé is
     * kerülhet, és van, ahol egyáltalán nincs cím.
     */
    private fun plsFeldolgoz(sorok: List<String>): List<Bejegyzes> {
        val cimek = mutableMapOf<String, String>()
        val nevek = mutableMapOf<String, String>()
        for (sor in sorok) {
            val egyenlo = sor.indexOf('=')
            if (egyenlo <= 0) continue
            val kulcs = sor.substring(0, egyenlo).trim()
            val ertek = sor.substring(egyenlo + 1).trim()
            when {
                kulcs.startsWith("File", ignoreCase = true) ->
                    cimek[kulcs.drop(4)] = ertek
                kulcs.startsWith("Title", ignoreCase = true) ->
                    nevek[kulcs.drop(5)] = ertek
            }
        }
        return cimek.entries
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .map { Bejegyzes(nev = nevek[it.key].orEmpty().trim(), url = it.value) }
    }

    /**
     * .m3u: a `#EXTINF:` sor hordozza a nevet, utána jön a cím. A név a
     * vessző UTÁNI rész — az előtte lévő szám a hossz, ami élő adásnál -1.
     */
    private fun m3uFeldolgoz(sorok: List<String>): List<Bejegyzes> {
        val eredmeny = mutableListOf<Bejegyzes>()
        var kovetkezoNev = ""
        for (sor in sorok) {
            if (sor.isBlank()) continue
            if (sor.startsWith("#")) {
                if (sor.startsWith("#EXTINF", ignoreCase = true)) {
                    kovetkezoNev = sor.substringAfter(',', "").trim()
                }
                continue
            }
            eredmeny += Bejegyzes(nev = kovetkezoNev, url = sor)
            kovetkezoNev = ""
        }
        return eredmeny
    }

    /** A fájlnévből hallható állomásnév: kiterjesztés és aláhúzások nélkül. */
    private fun tisztaNev(nev: String): String {
        val alap = nev.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
        return if (alap.isBlank()) "Saját állomás" else alap
    }

    /**
     * A BEJEGYZÉSEK MENTÉSE A KEDVENCEK KÖZÉ.
     *
     * @return hány ÚJ állomás került be. Ami már megvolt, azt nem duplázzuk:
     *   aki kétszer olvassa be ugyanazt a fájlt, ne kapjon kétszer mindent.
     */
    fun ment(context: Context, bejegyzesek: List<Bejegyzes>): Int {
        // A tár azonos stream-cím esetén szándékosan nem duplikál, de nem is
        // jelzi vissza. Ezért a DARABSZÁMOT nézzük előtte-utána: ez az egyetlen
        // igaz mérőszám arról, hogy tényleg bekerült-e valami.
        val elotte = RadioStore.getStations(context).size
        bejegyzesek.forEachIndexed { i, b ->
            RadioStore.addStation(
                context,
                RadioStation(
                    id = "user_" + System.currentTimeMillis() + "_" + i,
                    name = b.nev,
                    streamUrl = b.url
                )
            )
        }
        return (RadioStore.getStations(context).size - elotte).coerceAtLeast(0)
    }
}
