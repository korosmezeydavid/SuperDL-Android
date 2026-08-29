package com.superdl.launcher.book

import java.io.File

/**
 * Hangoskönyv-felismerés: egy hangfájlokat tartalmazó MAPPA egy hangoskönyv
 * (a benne lévő hangfájlok a sávjai, természetes sorrendben). Ez megegyezik a
 * PC-oldali „mappa = könyv" logikával, hogy a könyvjelző-szinkron kulcsa (a
 * mappa neve) a két eszközön stimmeljen.
 *
 * A könyv a szokásos BookEntry-ként jelenik meg, `format = "audiobook"`, a
 * `path` a MAPPA útja. A sávokat a lejátszó a mappából olvassa ki.
 */
object AudiobookLibrary {

    const val FORMAT = "audiobook"

    val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus",
        "wav", "flac", "wma", "mp2", "mka"
    )

    fun isAudio(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    fun isAudiobook(entry: BookEntry): Boolean = entry.format == FORMAT

    /**
     * Egy hangoskönyv sávjai TELJES úttal. Ha a `folderPath` MAPPA, REKURZÍVAN
     * begyűjti az almappák (kötetek) hangfájljait is, a relatív út szerinti
     * természetes sorrendben. Ha FÁJL, akkor önmaga az egyetlen sáv.
     */
    fun tracksIn(folderPath: String): List<File> {
        val root = File(folderPath)
        if (root.isFile) return if (isAudio(root.name)) listOf(root) else emptyList()
        val out = ArrayList<File>()
        collectAudio(root, out)
        out.sortWith(compareBy(NaturalKey) { relPath(root, it) })
        return out
    }

    private fun collectAudio(dir: File, out: MutableList<File>) {
        val kids = dir.listFiles() ?: return
        for (f in kids) {
            if (f.isDirectory) collectAudio(f, out)
            else if (isAudio(f.name)) out.add(f)
        }
    }

    /** A sáv relatív útja a könyv gyökerétől (perjelekkel) – a kötet-almappát is
     * tartalmazza, így a kötetek közti azonos fájlnevek sem ütköznek. */
    fun relPath(root: File, f: File): String =
        f.absolutePath.removePrefix(root.absolutePath)
            .trimStart('/', '\\').replace('\\', '/')

    private fun hasAudioRecursive(dir: File): Boolean {
        val kids = dir.listFiles() ?: return false
        for (f in kids) {
            if (f.isFile && isAudio(f.name)) return true
            if (f.isDirectory && hasAudioRecursive(f)) return true
        }
        return false
    }

    /** Természetes rendezés: „2" a „10" előtt (a számokat számként hasonlítja). */
    private val NaturalKey = Comparator<String> { a, b -> naturalCompare(a, b) }

    private fun naturalCompare(a: String, b: String): Int {
        val x = a.lowercase()
        val y = b.lowercase()
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            val cx = x[i]
            val cy = y[j]
            if (cx.isDigit() && cy.isDigit()) {
                var ni = i
                var nj = j
                while (ni < x.length && x[ni].isDigit()) ni++
                while (nj < y.length && y[nj].isDigit()) nj++
                val nx = x.substring(i, ni).trimStart('0').ifEmpty { "0" }
                val ny = y.substring(j, nj).trimStart('0').ifEmpty { "0" }
                val cmp = if (nx.length != ny.length) nx.length - ny.length
                          else nx.compareTo(ny)
                if (cmp != 0) return cmp
                i = ni
                j = nj
            } else {
                if (cx != cy) return cx.compareTo(cy)
                i++
                j++
            }
        }
        return (x.length - i) - (y.length - j)
    }

    /**
     * A könyv-térképbe felveszi a hangoskönyveket. Egy gyökér KÖZVETLEN
     * gyermek-mappája, ha BÁRHOL (akár almappában, pl. kötetekben) van hangfájl,
     * EGYETLEN hangoskönyv (a kötet-almappák a sávjai). Egy gyökérben közvetlenül
     * álló hangfájl önálló, egy sávos hangoskönyv. Így a „több kötet egy mappán
     * belül" egy könyvként jelenik meg. A `roots` a bejárt gyökerek (BookLibrary).
     */
    fun collectInto(roots: List<File>, found: LinkedHashMap<String, BookEntry>) {
        val seen = HashSet<String>()
        for (root in roots) {
            val kids = root.listFiles() ?: continue
            for (child in kids) {
                if (!seen.add(child.absolutePath)) continue
                if (child.isDirectory) {
                    if (hasAudioRecursive(child)) {
                        val tracks = tracksIn(child.absolutePath)
                        found[child.absolutePath] = BookEntry(
                            path = child.absolutePath,
                            title = child.name,
                            format = FORMAT,
                            sizeBytes = tracks.sumOf { it.length() }
                        )
                    }
                } else if (isAudio(child.name)) {
                    found[child.absolutePath] = BookEntry(
                        path = child.absolutePath,
                        title = child.nameWithoutExtension,
                        format = FORMAT,
                        sizeBytes = child.length()
                    )
                }
            }
        }
    }
}
