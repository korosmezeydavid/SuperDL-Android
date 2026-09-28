package com.superdl.launcher.nettest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthNr
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * A TELEFON HELYI HÁLÓZATI ADATAI — a Windows `halozat_adatok()` párja.
 *
 * A telefon itt TÖBBET tud a gépnél, és ezt ki is használjuk:
 *  • a rendszer MEGMONDJA, forgalomkorlátos-e a kapcsolat (a Windows csak
 *    gyanakodni tud),
 *  • tudja, ha a wifi bejelentkezést kér (szállodai, vonati hálózat),
 *  • és ott a mobilhálózat: szolgáltató, 4G/5G, jelerősség — akkor is, ha
 *    épp wifin vagyunk.
 *
 * MINDEN lépés külön védve van: egy gyártói furcsaság miatt kieső adat ne
 * vigye magával a többit. Jobb egy őszinte „nem ismert", mint egy összeomlás.
 */
object NetInfoCollector {

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun collect(context: Context): NetLocal {
        val h = NetLocal()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active: Network? = try { cm?.activeNetwork } catch (_: Throwable) { null }
        val caps: NetworkCapabilities? = try {
            if (cm != null && active != null) cm.getNetworkCapabilities(active) else null
        } catch (_: Throwable) { null }

        if (cm == null || active == null || caps == null) {
            h.kind = KIND_NONE
        } else {
            h.vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            // VPN ALATT a valódi hálózatot keressük: a router és a wifi-jel
            // attól még ott van, csak a forgalom egy alagúton megy át.
            var physNet: Network = active
            var physCaps: NetworkCapabilities = caps
            if (h.vpn) {
                try {
                    for (n in cm.allNetworks) {
                        val c = cm.getNetworkCapabilities(n) ?: continue
                        if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                        if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                        physNet = n
                        physCaps = c
                        if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) break
                    }
                } catch (_: Throwable) {
                }
            }
            h.kind = when {
                physCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> KIND_WIFI
                physCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> KIND_MOBILE
                physCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> KIND_ETHERNET
                physCaps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> KIND_BLUETOOTH
                else -> KIND_OTHER
            }
            h.metered = try { cm.isActiveNetworkMetered } catch (_: Throwable) { false }
            h.captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) ||
                physCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
            h.linkDownKbps = physCaps.linkDownstreamBandwidthKbps.coerceAtLeast(0)
            h.linkUpKbps = physCaps.linkUpstreamBandwidthKbps.coerceAtLeast(0)

            val lp: LinkProperties? = try { cm.getLinkProperties(physNet) } catch (_: Throwable) { null }
            // a névfeloldás a TÉNYLEGES (esetleg VPN-es) úton megy — onnan kérdezzük
            val lpActive: LinkProperties? = try { cm.getLinkProperties(active) } catch (_: Throwable) { null }
            if (lp != null) fillFromLink(h, lp)
            val dnsSource = lpActive ?: lp
            if (dnsSource != null) {
                try {
                    h.dnsServers = dnsSource.dnsServers.mapNotNull { addr(it) }.distinct().take(4)
                } catch (_: Throwable) {
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        if (dnsSource.isPrivateDnsActive) {
                            val name = dnsSource.privateDnsServerName
                            h.privateDns = if (name.isNullOrBlank()) "bekapcsolva (automatikus mód)" else "bekapcsolva ($name)"
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        if (h.kind == KIND_WIFI) h.wifi = WifiReader.read(context)
        h.mobile = try { readMobile(context) } catch (_: Throwable) { null }
        h.connection = when (h.kind) {
            KIND_WIFI -> "vezeték nélküli (Wi-Fi)"
            KIND_MOBILE -> "mobilnet" + (h.mobile?.generation?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: "")
            KIND_ETHERNET -> "vezetékes (kábel)"
            KIND_BLUETOOTH -> "Bluetooth-megosztás (egy másik készülék netje)"
            KIND_OTHER -> "egyéb"
            else -> "nincs kapcsolat"
        }
        return h
    }

    private fun fillFromLink(h: NetLocal, lp: LinkProperties) {
        try {
            val addrs = lp.linkAddresses.map { it.address }
            val v4 = addrs.firstOrNull { it is Inet4Address }
            val v6 = addrs.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress }
            h.localIp = addr(v4 ?: v6).orEmpty()
        } catch (_: Throwable) {
        }
        try {
            // A mobilnetnek sokszor nincs értelmes átjárója (0.0.0.0) — azt nem írjuk ki.
            val gw = lp.routes.firstOrNull { r ->
                r.isDefaultRoute && r.gateway is Inet4Address && r.gateway?.isAnyLocalAddress == false
            }?.gateway
            h.gateway = addr(gw).orEmpty()
        } catch (_: Throwable) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                h.mtu = lp.mtu.coerceAtLeast(0)
            } catch (_: Throwable) {
            }
        }
    }

    /** IP-cím szövegként, a „%wlan0" zóna-toldalék nélkül. */
    private fun addr(a: InetAddress?): String? = a?.hostAddress?.substringBefore('%')?.takeIf { it.isNotBlank() }

    /**
     * A MOBILHÁLÓZAT. Akkor is kiolvassuk, ha épp wifin vagyunk: ha a wifi
     * lassú, jó tudni, hogy a mobilnet milyen lenne ugyanott.
     *
     * ŐSZINTESÉG: a hálózat típusához (4G/5G) telefon-engedély kell; ha nincs,
     * kimondjuk, és nem találgatunk. A 0–4 fokozat a rendszer besorolása.
     */
    @SuppressLint("MissingPermission")
    private fun readMobile(context: Context): MobileData? {
        var tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        if (tm.simState != TelephonyManager.SIM_STATE_READY) return null
        // Két SIM-nél az ADATFORGALMI SIM kell, nem az alapértelmezett hívó-SIM.
        try {
            val sub = SubscriptionManager.getDefaultDataSubscriptionId()
            if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) tm = tm.createForSubscriptionId(sub)
        } catch (_: Throwable) {
        }
        val operator = try { tm.networkOperatorName.orEmpty() } catch (_: Throwable) { "" }
        val roaming = try { tm.isNetworkRoaming } catch (_: Throwable) { false }
        val phonePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        var type = 0
        var note = ""
        if (phonePerm) {
            try {
                type = tm.dataNetworkType
            } catch (_: Throwable) {
            }
        } else {
            note = "A mobilhálózat típusához (4G, 5G) telefon-engedély kell, és a SuperDL-nek most nincs ilyen engedélye."
        }
        var level = -1
        var dbm = 0
        var nrSeen = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val ss = tm.signalStrength
                if (ss != null) {
                    level = ss.level
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val list: List<CellSignalStrength> = ss.cellSignalStrengths
                        nrSeen = list.any { it is CellSignalStrengthNr }
                        val primary = if (type == 20) list.firstOrNull { it is CellSignalStrengthNr } ?: list.firstOrNull()
                        else list.firstOrNull { it !is CellSignalStrengthNr } ?: list.firstOrNull()
                        val d = primary?.dbm
                        if (d != null && d in -140..-40) dbm = d
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return MobileData(
            operator = operator,
            generation = NetTestText.mobileGeneration(type, nrSeen),
            level = if (level in 0..4) level else -1,
            dbm = dbm,
            roaming = roaming,
            note = note
        )
    }
}
