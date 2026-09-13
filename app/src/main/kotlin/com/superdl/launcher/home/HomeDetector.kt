package com.superdl.launcher.home

import android.content.Context
import android.location.Location

enum class HomeVerdict {
    /** Biztosan otthon van. */
    HOME,

    /** Biztosan nincs otthon. */
    AWAY,

    /** Nem lehet eldönteni. ITT NEM RIASZTUNK — lásd lent, miért. */
    UNKNOWN
}

data class HomeCheck(val verdict: HomeVerdict, val reason: String)

/**
 * OTTHON VAGYOK-E?
 *
 * A HÁROM JELZÉS, EBBEN A SORRENDBEN:
 *
 * 1. A KAPCSOLÓDÓ WIFI. Nulla akkumulátor, azonnali, és beltéren a
 *    legpontosabb — épp ott, ahol a GPS a legrosszabb.
 * 2. A LÁTOTT MOBILCELLÁK. Beltéren is megvannak, olcsók, és nem koordináták,
 *    hanem azonosságok: „az otthoni cellák egyikét látom?"
 * 3. A GPS. Csak döntetlennél, és csak az ellenőrzés pillanatában.
 *
 * MIÉRT NEM RIASZTUNK DÖNTETLENNÉL:
 *
 * Ez a program legfontosabb szabálya ebben a funkcióban. Egy HAMIS riasztás
 * rosszabb, mint egy elmaradt — mert az első hamis riasztás után a
 * felhasználó (és a riasztott ismerős) kikapcsolja az egészet, és onnantól
 * nincs védőháló. Ezért ha nem lehet eldönteni, a program NEM küld üzenetet,
 * hanem KIMONDJA, hogy nem tudta eldönteni. Ez az egyetlen becsületes
 * megoldás: nem hazudik védelmet, és nem is riaszt vaktában.
 *
 * A GPS KÜSZÖBEI SZÁNDÉKOSAN TÁGAK. A városi GPS húsz-ötven métert simán
 * téved, egy panelházban többet is. A 80 méteren belül „otthon", a 250
 * méteren túl „nincs otthon", a kettő között „nem tudom" — a kettő közötti
 * sáv maga a hiszterézis, ami megakadályozza, hogy a határon billegve
 * oda-vissza váltogasson a döntés.
 */
object HomeDetector {

    /** Ezen belül biztosan otthon. */
    private const val HOME_RADIUS_M = 80f

    /** Ezen túl biztosan nincs otthon. */
    private const val AWAY_RADIUS_M = 250f

    /** Ennél pontatlanabb mérésre nem építünk döntést. */
    private const val MAX_USABLE_ACCURACY_M = 120f

    fun check(context: Context, sample: HomeSample): HomeCheck {
        val home = HomeSignatureStore.get(context)
        if (home.isEmpty) {
            return HomeCheck(HomeVerdict.UNKNOWN, "az otthon nincs betanítva")
        }

        // 1. WIFI — a legerősebb jel. Ha az otthoni hálózaton vagy, otthon vagy.
        val bssid = sample.bssid
        if (bssid != null && home.bssids.contains(bssid)) {
            return HomeCheck(HomeVerdict.HOME, "az otthoni wifi hálózaton vagy")
        }

        // 2. MOBILCELLA.
        val matching = sample.cells.count { home.cells.contains(it) }
        if (matching > 0) {
            return HomeCheck(HomeVerdict.HOME, "az otthoni mobilcellát látod")
        }

        // 3. GPS — csak most, és csak ha van mihez mérni.
        val loc = sample.location
        val lat = home.lat
        val lon = home.lon
        if (loc != null && lat != null && lon != null && usable(loc)) {
            val d = distanceTo(loc, lat, lon)
            if (d <= HOME_RADIUS_M) {
                return HomeCheck(HomeVerdict.HOME, "a mentett otthon közelében vagy")
            }
            if (d >= AWAY_RADIUS_M) {
                val km = (d / 1000f)
                val tav = if (km >= 1f) {
                    String.format(java.util.Locale("hu"), "%.1f kilométerre", km)
                } else {
                    "${d.toInt()} méterre"
                }
                return HomeCheck(HomeVerdict.AWAY, "$tav vagy az otthonodtól")
            }
            return HomeCheck(HomeVerdict.UNKNOWN, "a helyzet a határon van")
        }

        // 4. NINCS DÖNTŐ JEL.
        //
        // Ha egyáltalán semmit nem látunk — se wifi, se cella, se helyzet —,
        // az nem azt jelenti, hogy nincs otthon, hanem hogy a telefon nem
        // tudott körülnézni (repülő mód, kikapcsolt rádiók). Ez döntetlen.
        if (sample.isBlind) {
            return HomeCheck(HomeVerdict.UNKNOWN, "a telefon nem látott se wifit, se mobilhálózatot")
        }

        // Ha viszont LÁTUNK környezetet, és az NEM az otthoni, akkor tényleg
        // máshol van. Ez a funkció valódi esete.
        return HomeCheck(HomeVerdict.AWAY, "nem az otthoni környezetet látom")
    }

    private fun usable(loc: Location): Boolean =
        !loc.hasAccuracy() || loc.accuracy <= MAX_USABLE_ACCURACY_M

    private fun distanceTo(loc: Location, lat: Double, lon: Double): Float {
        val out = FloatArray(2)
        Location.distanceBetween(loc.latitude, loc.longitude, lat, lon, out)
        return out[0]
    }
}
