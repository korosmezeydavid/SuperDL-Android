package com.superdl.launcher.nettest

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

/**
 * AZ AKTÍV WI-FI KAPCSOLAT ADATAI — a Windows `nettest.wifi()` párja.
 *
 * MIÉRT a WifiManager.connectionInfo, és nem hálózat-keresés (startScan)?
 *  • A startScan-t az Android 9 óta KORLÁTOZZA (két percenként négyszer), a
 *    bejárásnál másfél másodpercenként kell érték — erre a keresés alkalmatlan.
 *  • A connectionInfo a MÁR CSATLAKOZOTT hálózat adatait adja, köztük a VALÓDI,
 *    mért jelerősséget (RSSI, dBm). Ez Android 12-től „elavultnak" van jelölve,
 *    de továbbra is működik, és a jelerősséget engedély nélkül is megadja.
 *  • A hálózat NEVÉT (SSID) viszont csak helymeghatározási engedéllyel ÉS
 *    bekapcsolt helymeghatározással adja ki — különben „<unknown ssid>". Ezt
 *    ilyenkor őszintén kimondjuk, a jelerősséget pedig ugyanúgy mérjük.
 *
 * AMIT TUDNI KELL A FRISSÜLÉSRŐL: a rendszer a jelerősséget maga kérdezi le a
 * wifi-chiptől, bekapcsolt képernyőnél néhány másodpercenként. Egy-egy
 * másfél másodperces lekérdezés ezért ugyanazt az értéket is adhatja, mint az
 * előző — ez nem hiba, a jel azóta nem frissült.
 */
object WifiReader {

    private const val UNKNOWN_SSID = "<unknown ssid>"

    /** null: most nincs aktív Wi-Fi kapcsolat (vagy nem sikerült lekérdezni). */
    @Suppress("DEPRECATION")
    fun read(context: Context): WifiData? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            val info: WifiInfo = wm.connectionInfo ?: return null
            val rssi = info.rssi
            // kapcsolat nélkül az RSSI −127, a frekvencia −1: az nem jelerősség
            if (!NetTestText.validRssi(rssi) || info.frequency <= 0) return null
            val freq = info.frequency
            var rx = 0
            var tx = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                rx = info.rxLinkSpeedMbps.coerceAtLeast(0)
                tx = info.txLinkSpeedMbps.coerceAtLeast(0)
            }
            var standard = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                standard = NetTestText.wifiStandardName(info.wifiStandard, freq)
            }
            WifiData(
                ssid = cleanSsid(info.ssid),
                rssi = rssi,
                frequencyMhz = freq,
                linkMbps = info.linkSpeed.coerceAtLeast(0),
                rxMbps = rx,
                txMbps = tx,
                standard = standard
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** Csak a jelerősség (a bejáráshoz, másfél másodpercenként). 0 = nincs wifi. */
    @Suppress("DEPRECATION")
    fun rssi(context: Context): Int = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = wm?.connectionInfo
        val r = info?.rssi ?: 0
        if (info != null && NetTestText.validRssi(r) && info.frequency > 0) r else 0
    } catch (_: Throwable) {
        0
    }

    /** A rendszer idézőjelben adja a nevet; ha nem adja ki, üres. */
    fun cleanSsid(raw: String?): String {
        val s = (raw ?: "").trim()
        if (s.isEmpty() || s == UNKNOWN_SSID || s == "0x") return ""
        return if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s.substring(1, s.length - 1) else s
    }
}
