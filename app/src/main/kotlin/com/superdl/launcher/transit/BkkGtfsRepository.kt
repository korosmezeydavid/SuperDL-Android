package com.superdl.launcher.transit

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * BKK's public static GTFS archive. This is timetable/stop-order data, not a live vehicle feed.
 * Every method performs disk or network I/O and must be called from a worker thread.
 */
object BkkGtfsRepository {
    const val FEED_URL = "https://bkk.hu/gtfs/budapest_gtfs.zip"
    private const val TOTAL_DOWNLOAD_NANOS = 120L * 1_000_000_000

    @Synchronized
    fun ensureCached(context: Context, force: Boolean = false): GtfsFeedInfo =
        cache(context).ensureCached(force)

    @Synchronized
    fun feedInfo(context: Context): GtfsFeedInfo? = cache(context).feedInfo()

    @Synchronized
    fun listRoutes(context: Context): List<GtfsRoute> {
        val routes = cache(context).file("routes.txt") ?: return emptyList()
        return utf8Reader(routes).use(GtfsStopSequenceParser::listRoutes)
    }

    @Synchronized
    fun listPatterns(context: Context, routeId: String): List<GtfsTripStopSequence> =
        withFeed(context) { stops, routes, trips, times ->
            GtfsStopSequenceParser.forRoutePatterns(stops, routes, trips, times, routeId)
        } ?: emptyList()

    @Synchronized
    fun loadTrip(context: Context, tripId: String): GtfsTripStopSequence? =
        withFeed(context) { stops, routes, trips, times ->
            GtfsStopSequenceParser.forTrip(stops, routes, trips, times, tripId)
        }

    private fun cache(context: Context): GtfsFeedCache = GtfsFeedCache(
        root = File(context.applicationContext.filesDir, "bkk_gtfs_cache"),
        sourceUrl = FEED_URL,
        download = ::downloadArchive
    )

    private inline fun <T> withFeed(
        context: Context,
        read: (java.io.Reader, java.io.Reader, java.io.Reader, java.io.Reader) -> T
    ): T? {
        val cache = cache(context)
        val stops = cache.file("stops.txt") ?: return null
        val routes = cache.file("routes.txt") ?: return null
        val trips = cache.file("trips.txt") ?: return null
        val times = cache.file("stop_times.txt") ?: return null
        return utf8Reader(stops).use { stopReader ->
            utf8Reader(routes).use { routeReader ->
                utf8Reader(trips).use { tripReader ->
                    utf8Reader(times).use { timeReader ->
                        read(stopReader, routeReader, tripReader, timeReader)
                    }
                }
            }
        }
    }

    private fun downloadArchive(target: File) {
        var location = URL(FEED_URL)
        repeat(4) {
            if (location.protocol != "https") throw GtfsFeedException("A BKK adatforrása nem biztonságos HTTPS-cím.")
            val connection = (location.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "SuperDL-Android/GTFS")
            }
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val contentLength = connection.contentLengthLong
                        if (contentLength > GtfsFeedCache.MAX_ZIP_BYTES) {
                            throw GtfsFeedException("A BKK GTFS ZIP nagyobb a megengedett méretnél.")
                        }
                        val started = System.nanoTime()
                        var copied = 0L
                        BufferedInputStream(connection.inputStream).use { source ->
                            FileOutputStream(target).use { fileStream ->
                                val output = BufferedOutputStream(fileStream)
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val size = source.read(buffer)
                                    if (size < 0) break
                                    copied += size
                                    if (copied > GtfsFeedCache.MAX_ZIP_BYTES ||
                                        System.nanoTime() - started > TOTAL_DOWNLOAD_NANOS) {
                                        throw GtfsFeedException("A BKK GTFS letöltése túllépte a korlátot.")
                                    }
                                    output.write(buffer, 0, size)
                                }
                                output.flush()
                                fileStream.fd.sync()
                            }
                        }
                        if (copied == 0L || (contentLength >= 0 && copied != contentLength)) {
                            throw GtfsFeedException("A BKK GTFS letöltése hiányos.")
                        }
                        return
                    }
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, 308 -> {
                        val redirect = connection.getHeaderField("Location")
                            ?: throw GtfsFeedException("A BKK GTFS átirányítása hibás.")
                        location = URL(location, redirect)
                    }
                    else -> throw IOException("BKK GTFS HTTP ${connection.responseCode}")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw GtfsFeedException("A BKK GTFS túl sok átirányítást adott.")
    }
}
