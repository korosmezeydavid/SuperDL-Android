package com.superdl.launcher.home

import android.content.Context
import android.location.Location
import com.superdl.launcher.gps.GpsLocationHelper
import com.superdl.launcher.transit.OsmHelper

/**
 * MELYIK HELYZETET KÜLDJÜK KI, ÉS HOGYAN NEVEZZÜK MEG.
 *
 * MIÉRT KELL EZ KÜLÖN: az otthon-döntés sokszor a wifiből vagy a mobilcellából
 * születik meg, és olyankor a GPS-t el sem indítottuk. A döntéshez ez elég —
 * a SEGÍTŐNEK viszont nem. Aki megkapja az üzenetet, meg akar találni, és
 * ehhez a legpontosabb helyzet kell, amit össze tudunk szedni.
 *
 * A SZABÁLY, AMI NEM ALKU TÁRGYA: régi helyzetet soha nem adunk ki frissnek.
 * Ha csak egy húsz perces mérésünk van, azt küldjük ki — de megmondjuk, hogy
 * húsz perces. Egy húsz perces helyzet sokkal többet ér, mint a semmi; egy
 * frissnek hazudott húsz perces helyzet viszont rosszabb a semminél, mert
 * rossz helyre viszi a keresőt.
 */
object HomeLocationResolver {

    /** Ennél régebbi mérésnél már kiírjuk, hogy hány perces. */
    private const val FRESH_MS = 3 * 60_000L

    /** A fordított geokódolás nem várathat: inkább maradjon el az utcanév. */
    private const val ADDRESS_TIMEOUT_MS = 6_000L

    /**
     * A legjobb elérhető helyzet a felsoroltakból: a FRISSEBB nyer, és azonos
     * frissesség mellett a PONTOSABB. A null értékek kiesnek.
     */
    fun best(vararg candidates: Location?): Location? {
        var best: Location? = null
        for (candidate in candidates) {
            if (candidate == null) continue
            best = better(best, candidate)
        }
        return best
    }

    /** A legjobb helyzet, beleértve a rendszer utolsó ismert mérését is. */
    fun bestWithLastKnown(context: Context, vararg candidates: Location?): Location? {
        val lastKnown = try {
            GpsLocationHelper.getLastLocation(context)
        } catch (_: Throwable) {
            null
        }
        return best(*candidates, lastKnown)
    }

    private fun better(current: Location?, candidate: Location): Location {
        if (current == null) return candidate
        // Öt percnél nagyobb korkülönbségnél a frissesség dönt: egy régi,
        // méterre pontos mérés is rossz helyre visz, ha azóta elindultunk.
        val ageDiff = candidate.time - current.time
        if (ageDiff > 5 * 60_000L) return candidate
        if (ageDiff < -5 * 60_000L) return current
        if (!candidate.hasAccuracy()) return current
        if (!current.hasAccuracy()) return candidate
        return if (candidate.accuracy < current.accuracy) candidate else current
    }

    /** Hány perces a mérés. Negatív vagy értelmetlen időnél null. */
    fun ageMinutes(location: Location): Int? {
        val fixedAt = location.time
        if (fixedAt <= 0L) return null
        val age = System.currentTimeMillis() - fixedAt
        if (age < 0L) return null
        if (age < FRESH_MS) return null
        return (age / 60_000L).toInt()
    }

    /**
     * Rövid, felolvasható cím a koordinátából. HÁLÓZATOT IGÉNYEL, ezért
     * háttérszálon, rövid határidővel hívandó — és ha nem jön meg, egyszerűen
     * elmarad. Az üzenet kiküldését SEMMI nem késleltetheti.
     */
    fun addressOrNull(location: Location): String? = try {
        val worker = AddressWorker(location)
        val thread = Thread(worker, "sdl-cim")
        thread.isDaemon = true
        thread.start()
        thread.join(ADDRESS_TIMEOUT_MS)
        shorten(worker.result)
    } catch (_: Throwable) {
        null
    }

    private class AddressWorker(private val location: Location) : Runnable {
        @Volatile
        var result: String? = null

        override fun run() {
            result = try {
                OsmHelper.reverseGeocode(location.latitude, location.longitude)
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * A térkép-szolgáltatás teljes címe SMS-be hosszú, és a végén az ország
     * meg az irányítószám senkit nem érdekel. Az első néhány rész viszont
     * pont az, amit egy segítő hallani akar: „Nyugati tér, Terézváros".
     */
    internal fun shorten(full: String?): String? {
        val text = full?.trim().orEmpty()
        if (text.isBlank()) return null
        val parts = text.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { part -> !part.all { it.isDigit() } }   // irányítószám
            .filter { !it.equals("Magyarország", ignoreCase = true) }
        if (parts.isEmpty()) return null
        return parts.take(3).joinToString(", ").take(120)
    }
}
