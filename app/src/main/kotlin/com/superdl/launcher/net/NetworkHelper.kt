package com.superdl.launcher.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * VAN-E INTERNETKAPCSOLAT?
 *
 * MIÉRT KELL: hálózat nélkül a címkeresés és a környezetleírás húsz
 * másodpercig vár, majd annyit mond: "ismeretlen cím". A felhasználó — aki
 * épp az utcán áll, és tudni akarja, hol van — azt hiszi, a program romlott
 * el, pedig csak nincs térerő.
 *
 * Ha ELŐRE megnézzük, azonnal és ÉRTHETŐEN válaszolhatunk: megmondjuk, hogy
 * hálózat kell hozzá, és azt is, mi az, ami hálózat NÉLKÜL is működik.
 */
object NetworkHelper {

    fun isOnline(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
        val network = manager?.activeNetwork
        val caps = manager?.getNetworkCapabilities(network)
        caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
        // Ha nem tudjuk eldönteni, INKÁBB PRÓBÁLJUK MEG — jobb egy hosszabb
        // várakozás, mint egy hamis "nincs internet" üzenet.
        true
    }

    /** Mobilneten vagyunk-e (adatforgalom-figyelmeztetéshez)? */
    fun isMobileData(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    } catch (_: Exception) {
        false
    }

    /**
     * Egységes, ÉRTHETŐ üzenet, ha egy hálózatot igénylő funkció nem megy.
     * @param whatFailed mit nem sikerült (pl. "a cím lekérdezése")
     * @param whatStillWorks mi működik nélküle is (nem kötelező)
     */
    fun offlineMessage(whatFailed: String, whatStillWorks: String = ""): String {
        val base = "Nincs internetkapcsolat, ezért $whatFailed most nem lehetséges."
        return if (whatStillWorks.isBlank()) base else "$base $whatStillWorks"
    }
}
