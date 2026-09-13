package com.superdl.launcher.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.WifiManager
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * EGY PILLANATKÉP A KÖRNYEZETRŐL.
 *
 * @param bssid    a KAPCSOLÓDÓ wifi hálózat azonosítója, ha van
 * @param cells    a most látott mobilcellák azonosítói
 * @param location helyzet, ha épp van — a betanításnál és döntetlennél számít
 */
data class HomeSample(
    val bssid: String?,
    val cells: Set<String>,
    val location: Location?
) {
    /** Van-e egyáltalán bármi, amiből dönteni lehet. */
    val isBlind: Boolean get() = bssid == null && cells.isEmpty() && location == null

    /** Elég pontos-e a helyzet ahhoz, hogy felülírja a korábban mentettet. */
    val accurateEnough: Boolean
        get() = location?.hasAccuracy() == true && location.accuracy <= 30f

    fun speakSummary(): String {
        val parts = mutableListOf<String>()
        parts.add(if (bssid != null) "wifi hálózaton vagy" else "wifi nélkül")
        parts.add(
            when (cells.size) {
                0 -> "mobilcellát nem látok"
                1 -> "egy mobilcellát látok"
                else -> "${cells.size} mobilcellát látok"
            }
        )
        val loc = location
        if (loc != null) {
            val acc = if (loc.hasAccuracy()) "kb. ${loc.accuracy.toInt()} méter pontossággal" else ""
            parts.add("a helyzet megvan $acc".trim())
        } else {
            parts.add("helyzet nélkül")
        }
        return parts.joinToString(", ") + "."
    }
}

/**
 * A KÖRNYEZET LEOLVASÁSA.
 *
 * CSAK A KAPCSOLÓDÓ HÁLÓZATOT KÉRDEZZÜK, PÁSZTÁZNI NEM PÁSZTÁZUNK. Így a
 * manifest `neverForLocation` jelzőjét nem kell visszavonni, és nem gyűjtünk
 * be semmit a szomszéd hálózatairól.
 *
 * MINDEN LÉPÉS KÜLÖN VÉDŐHÁLÓBAN: a gyártói ROM-ok ezeken a felületeken
 * szeretnek váratlan kivételt dobni, és egy hibára futott környezet-olvasás
 * nem viheti magával az egész ellenőrzést.
 */
object HomeEnvironment {

    private const val TAG = "SDL_OTTHON"

    /** Amikor nincs helyzet-engedély, a rendszer ezt a hamis címet adja vissza. */
    private const val FAKE_BSSID = "02:00:00:00:00:00"

    fun sample(context: Context, location: Location? = null): HomeSample =
        HomeSample(
            bssid = connectedBssid(context),
            cells = visibleCells(context),
            location = location
        )

    @SuppressLint("MissingPermission")
    fun connectedBssid(context: Context): String? = try {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val info = wifi?.connectionInfo
        @Suppress("DEPRECATION")
        val bssid = info?.bssid
        when {
            bssid.isNullOrBlank() -> null
            bssid == FAKE_BSSID -> null
            bssid == "<none>" -> null
            else -> bssid.lowercase()
        }
    } catch (t: Throwable) {
        Log.w(TAG, "wifi olvasas hiba: ${t.javaClass.simpleName}")
        null
    }

    @SuppressLint("MissingPermission")
    fun visibleCells(context: Context): Set<String> {
        if (!hasLocationPermission(context)) return emptySet()
        return try {
            val tm = context.applicationContext
                .getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return emptySet()
            val out = linkedSetOf<String>()
            tm.allCellInfo?.forEach { info ->
                cellId(info)?.let { out.add(it) }
            }
            out
        } catch (t: Throwable) {
            Log.w(TAG, "cella olvasas hiba: ${t.javaClass.simpleName}")
            emptySet()
        }
    }

    private fun cellId(info: Any?): String? = try {
        when (info) {
            is CellInfoLte -> {
                val id = info.cellIdentity
                if (id.ci == Int.MAX_VALUE) null else "lte:${id.ci}:${id.tac}"
            }
            is CellInfoGsm -> {
                val id = info.cellIdentity
                if (id.cid == Int.MAX_VALUE) null else "gsm:${id.cid}:${id.lac}"
            }
            is CellInfoWcdma -> {
                val id = info.cellIdentity
                if (id.cid == Int.MAX_VALUE) null else "wcdma:${id.cid}:${id.lac}"
            }
            is CellInfoNr -> {
                val id = info.cellIdentity as? CellIdentityNr
                if (id == null || id.nci == Long.MAX_VALUE) null else "nr:${id.nci}"
            }
            else -> null
        }
    } catch (t: Throwable) {
        null
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}
