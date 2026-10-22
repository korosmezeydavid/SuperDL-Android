package com.superdl.launcher.transit

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GtfsFeedCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    private val files = linkedMapOf(
        "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\nS1,Első,47.4,19.1\nS2,Második,47.5,19.2\n",
        "routes.txt" to "route_id,route_short_name,route_long_name,route_type\nR1,4,Villamos,0\n",
        "trips.txt" to "route_id,trip_id,service_id,direction_id\nR1,T1,W,0\nR1,T2,W,0\n",
        "stop_times.txt" to "trip_id,stop_id,stop_sequence\nT1,S1,1\nT1,S2,2\nT2,S1,1\nT2,S2,2\n"
    )

    @Test fun cacheIsReusedUntilRefreshAndForcedFailurePreservesIt() {
        var now = 1_790_000_000_000L
        var downloads = 0
        var fail = false
        val cache = GtfsFeedCache(temporary.newFolder("cache"), "https://example.test/feed.zip", { target ->
            downloads++
            if (fail) throw IOException("offline")
            zip(target, files)
        }, { now })

        val first = cache.ensureCached()
        assertEquals(now, first.downloadedAtMillis)
        assertFalse(first.usedCachedCopy)
        assertEquals(1, downloads)
        assertNotNull(cache.file("stops.txt"))

        assertTrue(cache.ensureCached().usedCachedCopy)
        assertEquals(1, downloads)
        now += GtfsFeedCache.REFRESH_INTERVAL_MILLIS + 1
        fail = true
        val fallback = cache.ensureCached()
        assertTrue(fallback.usedCachedCopy)
        assertTrue(fallback.refreshFailed)
        assertTrue(fallback.isStale)
        assertEquals(first.downloadedAtMillis, fallback.downloadedAtMillis)
        assertEquals(2, downloads)
    }

    @Test fun invalidUpdateDoesNotReplaceCompleteFeed() {
        var content = files.toMap()
        val root = temporary.newFolder("cache")
        val cache = GtfsFeedCache(root, "https://example.test/feed.zip", { zip(it, content) }, { 1_790_000_000_000L })
        val good = cache.ensureCached()
        val previous = cache.file("stops.txt")!!.absolutePath

        content = files - "stop_times.txt"
        val result = cache.ensureCached(force = true)
        assertTrue(result.refreshFailed)
        assertEquals(good.downloadedAtMillis, result.downloadedAtMillis)
        assertEquals(previous, cache.file("stops.txt")!!.absolutePath)
        assertEquals(1, root.listFiles()!!.count { it.name.startsWith("feed-") })
    }

    @Test fun noCacheAndInvalidZipFailsClearly() {
        val cache = GtfsFeedCache(temporary.newFolder("cache"), "https://example.test/feed.zip", {
            zip(it, files - "trips.txt")
        })
        assertThrows(GtfsFeedException::class.java) { cache.ensureCached() }
        assertEquals(null, cache.feedInfo())
    }

    private fun zip(target: File, content: Map<String, String>) {
        ZipOutputStream(FileOutputStream(target)).use { output ->
            content.forEach { (name, text) ->
                output.putNextEntry(ZipEntry(name))
                output.write(text.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
        }
    }
}
