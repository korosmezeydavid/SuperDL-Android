package com.superdl.launcher.transit

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipInputStream

data class GtfsFeedInfo(
    val downloadedAtMillis: Long,
    val isStale: Boolean,
    val sourceUrl: String,
    val usedCachedCopy: Boolean = false,
    val refreshFailed: Boolean = false
)

class GtfsFeedException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Immutable, versioned files and an atomically replaced pointer keep the last complete feed usable
 * when a download, ZIP extraction, or process dies partway through an update. All calls are blocking.
 */
internal class GtfsFeedCache(
    private val root: File,
    private val sourceUrl: String,
    private val download: (File) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val REFRESH_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val MAX_ZIP_BYTES = 160L * 1024 * 1024
        const val MAX_UNPACKED_BYTES = 900L * 1024 * 1024
        private val REQUIRED = mapOf(
            "stops.txt" to setOf("stop_id", "stop_name", "stop_lat", "stop_lon"),
            "routes.txt" to setOf("route_id"),
            "trips.txt" to setOf("route_id", "trip_id"),
            "stop_times.txt" to setOf("trip_id", "stop_id", "stop_sequence")
        )
        private val VERSION_NAME = Regex("feed-[0-9]{13}-[0-9a-f]{8}")
    }

    private data class Active(val dir: File, val downloadedAtMillis: Long)

    @Synchronized
    fun ensureCached(force: Boolean = false): GtfsFeedInfo {
        if (!root.isDirectory && !root.mkdirs()) throw GtfsFeedException("Nem hozható létre a GTFS gyorsítótár.")
        val old = active()
        val currentTime = now()
        if (!force && old != null && currentTime - old.downloadedAtMillis < REFRESH_INTERVAL_MILLIS) {
            return info(old, usedCachedCopy = true)
        }
        return try {
            val updated = refresh(currentTime)
            info(updated)
        } catch (failure: Exception) {
            if (old != null) info(old, usedCachedCopy = true, refreshFailed = true)
            else throw GtfsFeedException("A BKK menetrendi adatai nem érhetők el, és nincs korábbi mentés.", failure)
        }
    }

    @Synchronized
    fun feedInfo(): GtfsFeedInfo? = active()?.let { info(it, usedCachedCopy = true) }

    @Synchronized
    fun file(name: String): File? = active()?.dir?.resolve(name)?.takeIf {
        name in REQUIRED && it.isFile && it.length() > 0
    }

    private fun info(active: Active, usedCachedCopy: Boolean = false, refreshFailed: Boolean = false) =
        GtfsFeedInfo(
            downloadedAtMillis = active.downloadedAtMillis,
            isStale = now() - active.downloadedAtMillis >= REFRESH_INTERVAL_MILLIS,
            sourceUrl = sourceUrl,
            usedCachedCopy = usedCachedCopy,
            refreshFailed = refreshFailed
        )

    private fun active(): Active? {
        val pointer = File(root, "current.txt")
        if (pointer.isFile) {
            val parts = runCatching { pointer.readText(StandardCharsets.UTF_8).trim().split('|') }
                .getOrDefault(emptyList())
            if (parts.size == 2 && VERSION_NAME.matches(parts[0])) {
                val timestamp = parts[1].toLongOrNull()
                val dir = File(root, parts[0])
                if (timestamp != null && validDirectory(dir)) return Active(dir, timestamp)
            }
        }
        // Recovery if a pointer update was interrupted; never use a staging directory.
        return root.listFiles()?.asSequence()
            ?.filter { VERSION_NAME.matches(it.name) && validDirectory(it) }
            ?.maxByOrNull { it.name }
            ?.let { Active(it, it.name.substringAfter("feed-").take(13).toLong()) }
    }

    private fun validDirectory(dir: File): Boolean = dir.isDirectory &&
        File(dir, "ready.flag").isFile && REQUIRED.keys.all {
            File(dir, it).let { file -> file.isFile && file.length() > 0 }
        }

    private fun refresh(timestamp: Long): Active {
        val id = UUID.randomUUID().toString().replace("-", "").take(8)
        val stage = File(root, "staging-$id")
        if (!stage.mkdir()) throw GtfsFeedException("Nem hozható létre a GTFS ideiglenes mappa.")
        try {
            val zip = File(stage, "feed.zip")
            download(zip)
            if (zip.length() == 0L || zip.length() > MAX_ZIP_BYTES) {
                throw GtfsFeedException("A BKK GTFS ZIP mérete nem elfogadható.")
            }
            extractRequired(zip, stage)
            zip.delete()
            validate(stage)
            File(stage, "ready.flag").writeText("ok\n", StandardCharsets.UTF_8)
            val version = File(root, "feed-$timestamp-$id")
            move(stage, version)
            try {
                val pointerTemp = File(root, "current.tmp")
                FileOutputStream(pointerTemp).use { output ->
                    output.write("${version.name}|$timestamp\n".toByteArray(StandardCharsets.UTF_8))
                    output.fd.sync()
                }
                move(pointerTemp, File(root, "current.txt"), replace = true)
            } catch (failure: Exception) {
                // The previous pointer still identifies a complete feed. The new version can be
                // recovered on the next run if there was no previous one.
                throw failure
            }
            pruneOldVersions(version)
            return Active(version, timestamp)
        } finally {
            if (stage.exists()) stage.deleteRecursively()
        }
    }

    private fun extractRequired(zipFile: File, stage: File) {
        val seen = hashSetOf<String>()
        var total = 0L
        var entries = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries++
                if (entries > 100) throw GtfsFeedException("Túl sok bejegyzés van a GTFS ZIP-ben.")
                if (entry.isDirectory) continue
                val baseName = entry.name.substringAfterLast('/')
                val required = baseName in REQUIRED
                if (required && !seen.add(baseName)) {
                    throw GtfsFeedException("Ismétlődő GTFS fájl: $baseName")
                }
                val output = if (required) BufferedOutputStream(FileOutputStream(File(stage, baseName))) else null
                var entryBytes = 0L
                try {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        entryBytes += size
                        if (total > MAX_UNPACKED_BYTES || entryBytes > MAX_UNPACKED_BYTES) {
                            throw GtfsFeedException("A GTFS ZIP kibontott mérete túllépi a korlátot.")
                        }
                        output?.write(buffer, 0, size)
                    }
                } finally {
                    output?.close()
                }
                input.closeEntry()
            }
        }
        if (!seen.containsAll(REQUIRED.keys)) {
            throw GtfsFeedException("Hiányzó GTFS fájlok: ${(REQUIRED.keys - seen).sorted().joinToString()}")
        }
    }

    private fun validate(stage: File) {
        for ((name, columns) in REQUIRED) {
            val file = File(stage, name)
            if (!file.isFile || file.length() == 0L) throw GtfsFeedException("Üres GTFS fájl: $name")
            utf8Reader(file).buffered().use { reader ->
                val header = reader.readLine()?.removePrefix("\uFEFF")?.split(',')
                    ?.map { it.trim().trim('"') }?.toSet().orEmpty()
                if (!header.containsAll(columns)) {
                    throw GtfsFeedException("Hiányzó oszlopok a GTFS $name fájlban.")
                }
                if (reader.readLine() == null) throw GtfsFeedException("Nincs adat a GTFS $name fájlban.")
            }
        }
        utf8Reader(File(stage, "routes.txt")).use { reader ->
            if (GtfsStopSequenceParser.listRoutes(reader).isEmpty()) {
                throw GtfsFeedException("A GTFS járatlista üres.")
            }
        }
    }

    private fun pruneOldVersions(current: File) {
        val previous = root.listFiles()?.filter {
            it != current && VERSION_NAME.matches(it.name) && it.isDirectory
        }?.maxByOrNull { it.name }
        root.listFiles()?.filter {
            it != current && it != previous && VERSION_NAME.matches(it.name) && it.isDirectory
        }?.forEach { it.deleteRecursively() }
    }

    private fun move(from: File, to: File, replace: Boolean = false) {
        val options = if (replace) arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            else arrayOf(StandardCopyOption.ATOMIC_MOVE)
        try {
            Files.move(from.toPath(), to.toPath(), *options)
        } catch (_: AtomicMoveNotSupportedException) {
            if (replace) Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
            else Files.move(from.toPath(), to.toPath())
        }
    }
}

internal fun utf8Reader(file: File): InputStreamReader = InputStreamReader(
    FileInputStream(file),
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
)
