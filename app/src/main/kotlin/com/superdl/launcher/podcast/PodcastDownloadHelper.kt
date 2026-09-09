package com.superdl.launcher.podcast

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Podcast-epizódok letöltése offline hallgatáshoz.
 *
 * A hangfájlok a KÖZÖS Letöltések mappa „Super DL" almappájába kerülnek —
 * oda, ahol a felhasználó a fájlkezelőjével is megtalálja őket —, a hozzájuk
 * tartozó adatok (cím, hossz, műsor) pedig egy kis JSON-katalógusba.
 */
object PodcastDownloadHelper {

    private const val TAG = "SuperDL.PodcastDl"
    private const val PREFS = "superdl"
    private const val KEY_DOWNLOADS = "podcast_downloads"

    /**
     * HOVA KERÜLJÖN A LETÖLTÖTT ADÁS.
     *
     * A HIBA, AMIT EZ JAVÍT (Mezei Géza, 2026-09-09, 1.63.5). Szó szerint:
     *
     *   „ha letöltök egy epizódot akkor ugyan elmondja hogy letöltéseimben
     *    találom. De nem férhető hozzá szabadon, például se a fájlkezelőben
     *    se a tárhelyen, csak a letöltéseimben, még a felvételekben sem, de
     *    még a download mappába se."
     *
     * Igaza volt, és nem apróságban. A fájlok eddig az
     * `Android/data/com.superdl.launcher/files/` mappába kerültek. Ezt az
     * Android 11 óta a fájlkezelők NEM nyithatják meg — se a miénk, se a
     * gyáriak. A letöltött adás tehát létezett, de a felhasználó számára
     * elérhetetlen volt: nem tudta átmásolni, nem tudta elküldeni, és nem
     * látta, mennyi helyet foglal.
     *
     * Mostantól a KÖZÖS Letöltések mappába megy, egy Super DL nevű almappába.
     * Ez az a hely, amit minden fájlkezelő megnyit, és amit a felhasználó a
     * telefonja „Letöltések" pontja alatt keres.
     *
     * A tartalék megmarad: ha a közös mappa nem írható (nincs meg a fájl-
     * engedély, vagy a gyártó lezárta), marad a régi, saját mappa. Jobb egy
     * nehezen elérhető letöltés, mint semmi.
     */
    private fun kozosMappa(): File? = try {
        val d = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            ),
            "Super DL"
        )
        if (!d.exists()) d.mkdirs()
        if (d.isDirectory && d.canWrite()) d else null
    } catch (_: Exception) {
        null
    }

    private fun sajatMappa(context: Context): File {
        val d = File(context.getExternalFilesDir(null), "podcast_downloads")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun dir(context: Context): File = kozosMappa() ?: sajatMappa(context)

    /**
     * A KÖZÖS MAPPÁT HASZNÁLJUK-E — HOGY NE MONDJUNK VALÓTLANT.
     *
     * A Letöltéseim bevezetője megmondja, hol vannak a fájlok. Ha nincs
     * fájl-engedély, akkor viszont a tartalék helyre kerülnek, ahova a
     * fájlkezelő nem lát be. Ilyenkor azt mondani, hogy „a Letöltések mappa
     * Super DL almappájában", pontosan az a hazugság lenne, ami miatt Géza
     * eddig hiába kereste őket.
     */
    fun kozosMappatHasznal(): Boolean = kozosMappa() != null

    /**
     * AZ EPIZÓD AZONOSÍTÓJA — A KÉRDŐJEL UTÁNI RÉSZ NÉLKÜL.
     *
     * A MÁSIK HIBA UGYANEBBŐL A LEVÉLBŐL: „kipróbáltam hogy a telefont
     * repülő üzemmódban teszem úgy hogy már le volt töltve egy pár adás,
     * de még akkor is le akarta tölteni, nem tudja hogy az már le van
     * töltve."
     *
     * Az ok: a fájl nevét eddig a TELJES hangfájl-hivatkozásból számoltuk.
     * A podcast-szolgáltatók viszont mérőszámokat fűznek a hivatkozás végére
     * (kérdőjel után időbélyeg, munkamenet-azonosító), és ez minden
     * lekérdezésnél más. Más hivatkozás, más fájlnév — a program tehát
     * ugyanazt az adást minden alkalommal újnak látta.
     *
     * A kérdőjel utáni rész ezért lemarad. Ami marad, az maga a hangfájl
     * címe, ami nem változik.
     */
    private fun azonosito(audioUrl: String): String =
        audioUrl.substringBefore('?').substringBefore('#').trim()

    private fun kulcs(ep: PodcastEpisode): String =
        azonosito(ep.audioUrl).hashCode().toString().replace("-", "n")

    /**
     * A FÁJL NEVE — OLVASHATÓAN, DE EGYEDIEN.
     *
     * Eddig a név egy szám volt („n1837462911.mp3"). A saját mappában ez
     * mindegy volt, mert oda senki nem látott be. A közös Letöltések mappában
     * viszont már számít: aki fájlkezelővel keresi az adást, annak a CÍMET
     * kell hallania, nem egy számsort.
     *
     * A végére mégis odakerül az azonosító, mert két adás címe lehet azonos
     * („1. rész"), és két külön adás nem írhatja felül egymást.
     */
    private fun fajlnev(ep: PodcastEpisode): String {
        val nyers = listOf(ep.podcastTitle, ep.title)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        val tiszta = nyers
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(70)
            .trim()
        return if (tiszta.isBlank()) "Super DL adas ${kulcs(ep)}.mp3"
        else "$tiszta ${kulcs(ep)}.mp3"
    }

    private fun fileFor(context: Context, ep: PodcastEpisode): File =
        File(dir(context), fajlnev(ep))

    /**
     * LE VAN-E MÁR TÖLTVE.
     *
     * A keresést a `korabbiFajl` végzi. Emellett magát az epizódot is
     * megnézzük: a Letöltéseim listában az `audioUrl` MÁR a helyi útvonal,
     * nem a hálózati hivatkozás.
     */
    fun isDownloaded(context: Context, ep: PodcastEpisode): Boolean {
        if (korabbiFajl(context, ep) != null) return true
        return try {
            val f = File(ep.audioUrl)
            f.isAbsolute && f.exists()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * MEGVAN-E MÁR VALAHOL — ÉS HOL.
     *
     * Négy helyen nézünk, és mind a négyre szükség van:
     *   - a mostani néven, a közös mappában;
     *   - a régi, számokból álló néven ugyanott;
     *   - a régi saját mappában (aki korábban töltött le, annak ott van);
     *   - a katalógusban felírt útvonalon (ha a mappa időközben megváltozott,
     *     mert például most kapta meg a fájl-engedélyt).
     *
     * Aki egyszer letöltött egy adást, annak nem szabad másodszor is
     * letöltenie — mobilneten ez pénz, vakon pedig még bosszantóbb.
     */
    private fun korabbiFajl(context: Context, ep: PodcastEpisode): File? {
        val k = kulcs(ep)
        val jeloltek = mutableListOf<File>()
        kozosMappa()?.let {
            jeloltek += File(it, fajlnev(ep))
            jeloltek += File(it, "superdl_$k.mp3")
            jeloltek += File(it, "$k.mp3")
        }
        val sajat = sajatMappa(context)
        jeloltek += File(sajat, fajlnev(ep))
        jeloltek += File(sajat, "superdl_$k.mp3")
        jeloltek += File(sajat, "$k.mp3")
        jeloltek.firstOrNull { it.exists() }?.let { return it }
        // Utolsó esély: amit a katalógus mond. Az útvonalban ott van a kulcs.
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DOWNLOADS, null) ?: return null
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val path = arr.getJSONObject(i).optString("path")
                if (path.contains(k)) {
                    val f = File(path)
                    if (f.exists()) return f
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * EGY LETÖLTÖTT ADÁS TÖRLÉSE.
     *
     * A HARMADIK HIBA UGYANABBÓL A LEVÉLBŐL: „a letöltéseinkből nem lehet
     * kitörölni semmit, így viszont, ha úgy tetszik, foglalja a helyet."
     *
     * Eddig tényleg nem lehetett: a program tudott letölteni, de nem tudott
     * elengedni. Aki egy hónapig hallgatott adásokat, annak megtelt a
     * telefonja, és nem volt módja rá, hogy ezen belülről változtasson.
     *
     * A fájlt ÉS a katalógus-bejegyzést is töröljük. Ha csak az egyiket
     * tennénk, a lista vagy hazudna (ott a cím, nincs mögötte semmi), vagy a
     * hely maradna foglalva egy olyan fájllal, amiről már senki nem tud.
     */
    fun delete(context: Context, ep: PodcastEpisode): Boolean {
        val k = kulcs(ep)
        val jeloltek = mutableListOf<File>()
        kozosMappa()?.let {
            jeloltek += File(it, fajlnev(ep))
            jeloltek += File(it, "superdl_$k.mp3")
            jeloltek += File(it, "$k.mp3")
        }
        val sajatMappa = sajatMappa(context)
        jeloltek += File(sajatMappa, fajlnev(ep))
        jeloltek += File(sajatMappa, "superdl_$k.mp3")
        jeloltek += File(sajatMappa, "$k.mp3")
        try {
            val sajat = File(ep.audioUrl)
            if (sajat.isAbsolute) jeloltek += sajat
        } catch (_: Exception) {
        }
        korabbiFajl(context, ep)?.let { jeloltek += it }
        var sikerult = false
        for (f in jeloltek) {
            try {
                if (f.exists() && f.delete()) {
                    sikerult = true
                    ertesitsAFajlkezelot(context, f)
                }
            } catch (_: Exception) {
            }
        }
        removeFromCatalog(context, ep)
        return sikerult
    }

    /**
     * SZÓLUNK A RENDSZERNEK, HOGY VÁLTOZOTT A FÁJL.
     *
     * Enélkül a közös mappába írt hangfájl csak a következő újraindítás után
     * jelenne meg a fájlkezelőkben és a zenelejátszókban — a felhasználó
     * pedig azt látná, hogy hiába töltötte le, „nincs sehol". Törlésnél
     * ugyanez fordítva: ott maradna a listákban egy fájl, ami már nincs.
     */
    private fun ertesitsAFajlkezelot(context: Context, file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                arrayOf("audio/mpeg"),
                null
            )
        } catch (_: Exception) {
        }
    }

    private fun removeFromCatalog(context: Context, ep: PodcastEpisode) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return
        try {
            val arr = JSONArray(raw)
            val uj = JSONArray()
            val sajatUt = fileFor(context, ep).absolutePath
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = o.optString("path")
                // A bejegyzést az útvonal VAGY a cím alapján ismerjük fel: a
                // Letöltéseim listában az epizód audioUrl-je már az útvonal,
                // a műsorlistában viszont még a hálózati hivatkozás.
                val egyezik = (path.isNotBlank() && path == ep.audioUrl) ||
                    path == sajatUt ||
                    (ep.title.isNotBlank() && o.optString("title") == ep.title)
                if (!egyezik) uj.put(o)
            }
            prefs.edit().putString(KEY_DOWNLOADS, uj.toString()).apply()
        } catch (_: Exception) {
        }
    }

    /** Letölti az epizódot. Háttérszálon futtasd! */
    fun download(context: Context, ep: PodcastEpisode): Boolean {
        // MÁR MEGVAN? Akkor nem töltjük le újra, csak gondoskodunk róla, hogy
        // a katalógusban is szerepeljen. Aki repülő üzemmódban is „letöltést"
        // látott ott, ahol a fájl már ott volt, pontosan ezt hiányolta.
        korabbiFajl(context, ep)?.let {
            addToCatalog(context, ep, it.absolutePath)
            return true
        }
        val target = fileFor(context, ep)
        if (target.exists()) return true
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(ep.audioUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SuperDL/1.9")
            }
            if (conn.responseCode !in 200..299) return false
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            }
            addToCatalog(context, ep, target.absolutePath)
            // Szólunk a rendszernek, különben a fájlkezelők és a zenelejátszók
            // csak újraindítás után látnák meg — a felhasználó pedig azt
            // hinné, hogy a letöltés nem sikerült.
            ertesitsAFajlkezelot(context, target)
            true
        } catch (e: Exception) {
            Log.w(TAG, "download failed", e)
            try {
                if (target.exists()) target.delete()
            } catch (_: Exception) {
            }
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** A letöltött epizódok (a helyi fájlra mutató audioUrl-lel). */
    fun downloadedEpisodes(context: Context): List<PodcastEpisode> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val path = o.optString("path")
                if (path.isBlank() || !File(path).exists()) return@mapNotNull null
                PodcastEpisode(
                    title = o.optString("title"),
                    audioUrl = path,
                    durationSeconds = o.optInt("duration"),
                    publishedText = o.optString("published"),
                    description = o.optString("description"),
                    podcastTitle = o.optString("podcastTitle")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun addToCatalog(context: Context, ep: PodcastEpisode, path: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DOWNLOADS, null)
        val regi = try {
            if (raw != null) JSONArray(raw) else JSONArray()
        } catch (_: Exception) {
            JSONArray()
        }
        // UGYANAZ AZ ADÁS NE SZEREPELJEN KÉTSZER. A letöltés most már akkor is
        // idejut, ha a fájl korábbról megvolt — enélkül a Letöltéseim lista
        // minden ellenőrzésnél nőne egy sorral ugyanabból az adásból.
        val k = kulcs(ep)
        val arr = JSONArray()
        for (i in 0 until regi.length()) {
            val o = regi.optJSONObject(i) ?: continue
            val p = o.optString("path")
            if (p == path || p.contains(k)) continue
            arr.put(o)
        }
        arr.put(JSONObject().apply {
            put("title", ep.title)
            put("path", path)
            put("duration", ep.durationSeconds)
            put("published", ep.publishedText)
            put("description", ep.description)
            put("podcastTitle", ep.podcastTitle)
        })
        prefs.edit().putString(KEY_DOWNLOADS, arr.toString()).apply()
    }
}
