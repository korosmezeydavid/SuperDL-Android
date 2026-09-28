package com.superdl.launcher.nettest

import java.util.Collections

// ════════════════════════════════════════════════════════════════════════
//  INTERNET-TESZT — ADATOK
// ════════════════════════════════════════════════════════════════════════
//
// A windowsos SuperDL `nettest.py` adatosztályainak (Sebesseg, Halozat,
// Publikus, Eredmeny) telefonos párja. SZÁNDÉKOSAN nincs benne egyetlen
// android.* import sem: így a szövegek és a számítások a felhőben, sima
// JVM-en is tesztelhetők, a Windows-eredetivel összevetve.
//
// A Halozat itt NetLocal: a telefon mást tud, mint egy PC. Nincs „adapter"
// és nincs Windows-féle „mért kapcsolat gyanúja" — helyette a rendszer
// TÉNYLEGESEN megmondja, forgalomkorlátos-e a hálózat, és van mobilhálózat
// (szolgáltató, 4G/5G, jelerősség), amit a gép nem lát.

/** Mérési módok — ugyanazok a kódok, mint Windowson, hogy a napló egyezzen. */
const val MODE_FULL = "teljes"
const val MODE_SAVER = "takarekos"
const val MODE_QUICK = "gyors"

/** A kapcsolat fajtája (NetLocal.kind). */
const val KIND_WIFI = "wifi"
const val KIND_MOBILE = "mobile"
const val KIND_ETHERNET = "ethernet"
const val KIND_BLUETOOTH = "bluetooth"
const val KIND_OTHER = "other"
const val KIND_NONE = "none"

/** = nettest.Sebesseg */
data class NetSpeed(
    var downMbps: Double = 0.0,
    var upMbps: Double = 0.0,
    var downPeakMbps: Double = 0.0,
    var upPeakMbps: Double = 0.0,
    /** fél másodpercenkénti minták, Mbit/s — ebből derül ki az akadozás */
    var downSamples: List<Double> = emptyList(),
    var upSamples: List<Double> = emptyList(),
    /** a legkisebb TCP-kapcsolatnyitási idő (NEM ICMP-ping) */
    var latencyMinMs: Double = 0.0,
    var latencyAvgMs: Double = 0.0,
    /** jitter: az egymást követő minták eltérésének átlaga */
    var jitterMs: Double = 0.0,
    /** a sikeres kapcsolat-próbák aránya, százalék (csomagvesztést NEM mérünk) */
    var successPct: Double = 0.0,
    /** névfeloldás ideje — a gyorsítótár is beleszámít, ezt ki is mondjuk */
    var dnsMs: Double = 0.0,
    var downBytes: Long = 0L,
    var upBytes: Long = 0L
)

/**
 * Az aktív Wi-Fi kapcsolat. A 0 (vagy üres) érték mindenhol azt jelenti:
 * NEM TUDJUK — és akkor nem is írunk ki róla semmit, nem találgatunk.
 */
data class WifiData(
    /** a hálózat neve; üres, ha a rendszer nem adja ki (engedély, helymeghatározás) */
    val ssid: String = "",
    /** VALÓDI, mért jelerősség dBm-ben (a telefon ezt méri, nem százalékból számoljuk) */
    val rssi: Int = 0,
    val frequencyMhz: Int = 0,
    /** a rendszer által jelzett kapcsolati sebesség (Mbit/s) */
    val linkMbps: Int = 0,
    /** Android 10-től külön a letöltési és a feltöltési irány */
    val rxMbps: Int = 0,
    val txMbps: Int = 0,
    /** felolvasható szabvány-név („Wi-Fi 6 (802.11ax)"); Android 11-től */
    val standard: String = ""
) {
    val band: String get() = NetTestText.bandFromFrequency(frequencyMhz)
    val channel: Int get() = NetTestText.channelFromFrequency(frequencyMhz)
}

/** A mobilhálózat — ezt a telefon tudja, a gép nem. */
data class MobileData(
    val operator: String = "",
    /** „4G (LTE)", „5G" … vagy magyarázat, ha nem látjuk */
    val generation: String = "",
    /** a rendszer 0–4 fokozatú besorolása; -1 = nem ismert */
    val level: Int = -1,
    /** mért jelszint dBm-ben (LTE-nél az RSRP); 0 = nem ismert */
    val dbm: Int = 0,
    val roaming: Boolean = false,
    /** kiegészítő, őszinte megjegyzés („a hálózat típusához telefon-engedély kell") */
    val note: String = ""
)

/** = nettest.Halozat, telefonra szabva */
data class NetLocal(
    var kind: String = "",
    /** felolvasható: „vezeték nélküli (Wi-Fi)", „mobilnet, 4G (LTE)" */
    var connection: String = "",
    var localIp: String = "",
    var gateway: String = "",
    var dnsServers: List<String> = emptyList(),
    /** „bekapcsolva (dns.google)" / „automatikus mód" — üres, ha nem ismert */
    var privateDns: String = "",
    var mtu: Int = 0,
    var ipv6: Boolean = false,
    var vpn: Boolean = false,
    /** a RENDSZER szerint forgalomkorlátos (mobilnet, mért wifi, hotspot) */
    var metered: Boolean = false,
    /** a hálózat bejelentkezést kér (szállodai, vonati wifi) */
    var captivePortal: Boolean = false,
    /** a rendszer sávszélesség-BECSLÉSE (nem mérés!), kbit/s */
    var linkDownKbps: Int = 0,
    var linkUpKbps: Int = 0,
    var wifi: WifiData? = null,
    var mobile: MobileData? = null
)

/** = nettest.Publikus */
data class NetPublic(
    var ip: String = "",
    var host: String = "",
    var provider: String = "",
    var asn: String = "",
    var city: String = "",
    var country: String = "",
    /** melyik Cloudflare-központ szolgált ki (colo) */
    var colo: String = ""
)

/** Egy szolgáltatás elérhetősége: TCP-kapcsolatnyitás, ms-ban mérve. */
data class ServiceProbe(val name: String, val ok: Boolean, val ms: Double)

/** Egy megszólítandó szolgáltatás (név, gép, port). */
data class ServiceTarget(val name: String, val host: String, val port: Int)

/** Mire elég a net: (funkció, kell letöltés, kell feltöltés, legfeljebb ennyi késleltetés). */
data class NetNeed(val name: String, val downMbps: Double, val upMbps: Double, val maxLatencyMs: Double)

/** Egy „mire elég" sor eredménye. */
data class NetQualification(val name: String, val ok: Boolean, val reason: String)

/** = nettest.Eredmeny */
data class NetTestResult(
    var time: String = "",
    var mode: String = MODE_FULL,
    val speed: NetSpeed = NetSpeed(),
    var local: NetLocal = NetLocal(),
    var pub: NetPublic = NetPublic(),
    var services: List<ServiceProbe> = emptyList(),
    /** mérési figyelmeztetések — több szál is írhatja, ezért szinkronizált */
    val errors: MutableList<String> = Collections.synchronizedList(mutableListOf()),
    var cancelled: Boolean = false
)
