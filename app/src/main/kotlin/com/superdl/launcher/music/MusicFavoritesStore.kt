package com.superdl.launcher.music

import android.content.Context
import android.net.Uri

/**
 * KEDVENC ZENÉK.
 *
 * ALPH KÉRÉSE (2026-09-21): a lejátszó panelén legyen egy plusz gomb —
 * „kedvencnek jelöl" —, és a Zene menüben egy „Kedvenc zenék" pont, ahonnan
 * csak azokat hallgathatod.
 *
 * MIÉRT NEM CSAK AZ AZONOSÍTÓT TÁROLJUK: a rendszer médiatárában a szám
 * azonosítója újraolvasáskor MEGVÁLTOZHAT (kártyacsere, újraindexelés). Ha
 * csak azt jegyeznénk meg, a kedvencek egy nap némán eltűnnének. Ezért a
 * CÍM és az ELŐADÓ is elmentődik, és a listázáskor azzal is keresünk. Egy
 * kedvenc lista, ami magától kiürül, rosszabb, mint ha nem is lenne.
 */
object MusicFavoritesStore {

    private const val PREFS = "superdl_music_favorites"
    private const val KEY = "items"

    /** A mezőelválasztó: fájlnévben és címben sem fordul elő. */
    private const val SEP = "\u0001"
    private const val SOR = "\n"

    data class Kedvenc(
        val uri: String,
        val title: String,
        val artist: String,
        val durationMs: Long
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Kedvenc> =
        prefs(context).getString(KEY, "").orEmpty()
            .split(SOR)
            .filter { it.isNotBlank() }
            .mapNotNull { sor ->
                val r = sor.split(SEP)
                if (r.size < 4) return@mapNotNull null
                Kedvenc(
                    uri = r[0],
                    title = r[1],
                    artist = r[2],
                    durationMs = r[3].toLongOrNull() ?: 0L
                )
            }

    private fun save(context: Context, lista: List<Kedvenc>) {
        val szoveg = lista.joinToString(SOR) {
            listOf(it.uri, it.title, it.artist, it.durationMs.toString()).joinToString(SEP)
        }
        prefs(context).edit().putString(KEY, szoveg).apply()
    }

    fun isFavorite(context: Context, track: MusicTrack): Boolean {
        val uri = track.contentUri.toString()
        return all(context).any { it.uri == uri || egyezik(it, track) }
    }

    private fun egyezik(k: Kedvenc, t: MusicTrack): Boolean =
        k.title.equals(t.title, ignoreCase = true) &&
            k.artist.equals(t.artist, ignoreCase = true)

    /** @return igaz, ha MOSTANTÓL kedvenc; hamis, ha lekerült. */
    fun toggle(context: Context, track: MusicTrack): Boolean {
        val lista = all(context).toMutableList()
        val uri = track.contentUri.toString()
        val meglevo = lista.indexOfFirst { it.uri == uri || egyezik(it, track) }
        if (meglevo >= 0) {
            lista.removeAt(meglevo)
            save(context, lista)
            return false
        }
        // A LEGÚJABB KEDVENC ELÖLRE. Amit most jelöltél meg, azt akarod
        // legközelebb a leghamarabb megtalálni.
        lista.add(
            0,
            Kedvenc(uri, track.title, track.artist, track.durationMs)
        )
        save(context, lista)
        return true
    }

    fun remove(context: Context, track: MusicTrack) {
        val uri = track.contentUri.toString()
        save(context, all(context).filterNot { it.uri == uri || egyezik(it, track) })
    }

    /**
     * A KEDVENCEK LEJÁTSZHATÓ ALAKBAN.
     *
     * A friss könyvtárral összevetve: ha a szám még megvan, a FRISS adatait
     * használjuk (az azonosítója közben változhatott), és ilyenkor a mentett
     * bejegyzést is helyre igazítjuk. Amit sehogy nem találunk, azt a mentett
     * adatokból adjuk vissza — hátha mégis lejátszható.
     */
    fun tracks(context: Context): List<MusicTrack> {
        val mentett = all(context)
        if (mentett.isEmpty()) return emptyList()
        val konyvtar = try {
            MusicHelper.getTracks(context)
        } catch (_: Throwable) {
            emptyList()
        }
        val uriSzerint = konyvtar.associateBy { it.contentUri.toString() }
        var valtozott = false
        val frissitett = mutableListOf<Kedvenc>()
        val ki = mutableListOf<MusicTrack>()
        for (k in mentett) {
            val talalt = uriSzerint[k.uri]
                ?: konyvtar.firstOrNull {
                    it.title.equals(k.title, ignoreCase = true) &&
                        it.artist.equals(k.artist, ignoreCase = true)
                }
            if (talalt != null) {
                ki.add(talalt)
                val ujUri = talalt.contentUri.toString()
                if (ujUri != k.uri) valtozott = true
                frissitett.add(Kedvenc(ujUri, talalt.title, talalt.artist, talalt.durationMs))
            } else {
                ki.add(
                    MusicTrack(
                        id = k.uri.substringAfterLast('/').toLongOrNull() ?: 0L,
                        title = k.title,
                        artist = k.artist,
                        durationMs = k.durationMs,
                        contentUri = Uri.parse(k.uri)
                    )
                )
                frissitett.add(k)
            }
        }
        if (valtozott) save(context, frissitett)
        return ki
    }

    fun count(context: Context): Int = all(context).size
}
