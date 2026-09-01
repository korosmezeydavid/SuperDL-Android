package com.superdl.launcher.podcast

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * OPML import/export – a podcast-világ szabványos feliratkozás-formátuma.
 * Ezzel áthozhatod a gyűjteményedet másik appból (Pocket Casts, AntennaPod,
 * Overcast stb.), vagy kimentheted a sajátodat.
 *
 * Az OPML egy egyszerű XML: minden podcast egy <outline> sor, benne a
 * nevével (text/title) és az RSS-feed címével (xmlUrl).
 */
object PodcastOpml {

    private const val TAG = "SuperDL.Opml"

    /** Az OPML tartalmából kiolvassa a podcastokat (feed URL + név). */
    fun parse(xml: String): List<Podcast> {
        val outlines = Regex("""<outline\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(xml)
            .map { it.value }
            .toList()

        val list = mutableListOf<Podcast>()
        for (o in outlines) {
            val feed = attr(o, "xmlUrl") ?: continue
            val title = attr(o, "text") ?: attr(o, "title") ?: feed
            list.add(
                Podcast(
                    id = feed.hashCode().toString(),
                    title = unescape(title),
                    author = "",
                    feedUrl = unescape(feed)
                )
            )
        }
        return list.distinctBy { it.feedUrl }
    }

    /**
     * FELSŐ KORLÁT A BEOLVASÁSRA — 1 megabájt.
     *
     * MIÉRT: egy podcast-feliratkozáslista pár tíz kilobájt. Ami ennél
     * nagyságrendekkel nagyobb, az nem OPML, hanem valami más, amit a
     * felhasználó véletlenül választott ki a fájlválasztóban — vakon ez
     * könnyen megesik.
     */
    const val MAX_BYTES = 1024 * 1024

    /** A beolvasás eredménye: vagy a lista, vagy egy KIMONDHATÓ hiba. */
    data class ImportResult(
        val podcasts: List<Podcast>,
        val error: String? = null
    )

    /**
     * Beolvasás fájlból (a felhasználó által választott OPML).
     *
     * A RÉGI VÁLTOZAT MEGÖLTE A PROGRAMOT. A teljes fájlt egyetlen String-be
     * olvasta, méret- és formátum-ellenőrzés nélkül. Egy véletlenül kiválasztott
     * videóra ez 268 megabájtos foglalást jelentett — OutOfMemoryError, és a
     * folyamat halála. A felhasználó ebből annyit látott, hogy semmi nem történik.
     *
     * Most három őr van rajta:
     *  1. legfeljebb 1 megabájtot olvasunk be, azon túl megállunk
     *  2. az első pár száz bájt alapján eldöntjük, OPML-e egyáltalán
     *  3. Throwable-t kapunk el, nem csak Exception-t — az OutOfMemoryError
     *     ugyanis Error, nem Exception, ezért szállt fel szabadon
     */
    fun parseStreamChecked(input: InputStream): ImportResult = try {
        val text = readLimited(input)
        when {
            text == null -> ImportResult(
                emptyList(),
                "Ez a fájl túl nagy egy podcast-listához. Egy feliratkozáslista " +
                    "néhány tíz kilobájt szokott lenni. Valószínűleg nem azt a fájlt " +
                    "választottad ki, amit szerettél volna."
            )
            text.isBlank() -> ImportResult(emptyList(), "Ez a fájl üres.")
            !looksLikeOpml(text) -> ImportResult(
                emptyList(),
                "Ez a fájl nem podcast-lista. A podcast-listák pont o p m l végű " +
                    "fájlok, ezeket más podcast alkalmazásból tudod kimenteni."
            )
            else -> {
                val list = parse(text)
                if (list.isEmpty()) {
                    ImportResult(
                        emptyList(),
                        "Ebben a listában nem találtam podcastot."
                    )
                } else {
                    ImportResult(list)
                }
            }
        }
    } catch (t: Throwable) {
        // SZÁNDÉKOSAN Throwable: az OutOfMemoryError nem Exception, és eddig
        // pont ezért ölte meg a háttérszálat, azon keresztül az egész programot.
        Log.w(TAG, "parseStream failed", t)
        ImportResult(
            emptyList(),
            "Ezt a fájlt nem sikerült beolvasni. Lehet, hogy sérült, vagy nem " +
                "podcast-lista."
        )
    }

    /** A régi belépési pont — megmarad, hogy a meglévő hívások se törjenek el. */
    fun parseStream(input: InputStream): List<Podcast> =
        parseStreamChecked(input).podcasts

    /**
     * Legfeljebb MAX_BYTES beolvasása. Ha a fájl ennél hosszabb, NULL —
     * és a maradékot el sem olvassuk.
     */
    private fun readLimited(input: InputStream): String? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            // Egy bájttal a korlát fölött már tudjuk, hogy túl nagy.
            if (total > MAX_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * OPML-nek látszik-e. Nem szigorú érvényesítés, csak józan ész:
     * az első pár száz karakterben ott kell lennie az opml vagy az outline
     * nyitó jelölésnek.
     */
    private fun looksLikeOpml(text: String): Boolean {
        val head = text.take(2000).lowercase()
        return head.contains("<opml") || head.contains("<outline")
    }

    /** A feliratkozásokból OPML-szöveget készít (exporthoz). */
    fun build(podcasts: List<Podcast>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<opml version="2.0">""")
        appendLine("  <head>")
        appendLine("    <title>SuperDL podcast feliratkozások</title>")
        appendLine("  </head>")
        appendLine("  <body>")
        podcasts.forEach { p ->
            val title = escape(p.title)
            val feed = escape(p.feedUrl)
            appendLine("""    <outline type="rss" text="$title" title="$title" xmlUrl="$feed" />""")
        }
        appendLine("  </body>")
        appendLine("</opml>")
    }

    /** Exportálás fájlba; visszaadja a fájlt vagy null-t. */
    fun export(context: Context, podcasts: List<Podcast>): File? = try {
        val dir = File(context.getExternalFilesDir(null), "podcast")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "superdl_feliratkozasok.opml")
        file.writeText(build(podcasts))
        file
    } catch (e: Exception) {
        Log.w(TAG, "export failed", e)
        null
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1)?.trim()?.ifBlank { null }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&apos;", "'")
}
