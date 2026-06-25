package com.superdl.launcher.system

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build

object ConnectivityHelper {

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isWifiEnabled(context: Context): Boolean {
        return try {
            readWifiEnabledViaWifiManager(context)
        } catch (_: Exception) {
            false
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean =
        try {
            bluetoothAdapter(context)?.isEnabled == true
        } catch (_: Exception) {
            false
        }

    fun wifiStatus(context: Context): String =
        if (isWifiEnabled(context)) "WiFi bekapcsolva." else "WiFi kikapcsolva."

    fun bluetoothStatus(context: Context): String {
        val adapter = bluetoothAdapter(context) ?: return "Bluetooth nem elérhető."
        return if (adapter.isEnabled) "Bluetooth bekapcsolva." else "Bluetooth kikapcsolva."
    }

    fun toggleWifi(context: Context): ToggleResult {
        val wasEnabled = isWifiEnabled(context)
        val targetEnabled = !wasEnabled

        val wifiManager = wifiManager(context)
            ?: return ToggleResult(
                success = false,
                nowEnabled = wasEnabled,
                failureMessage = "WiFi szolgáltatás nem elérhető ezen az eszközön."
            )

        val ok = setWifiEnabledDirect(wifiManager, targetEnabled)
        val nowEnabled = if (ok) targetEnabled else isWifiEnabled(context)

        return if (ok || nowEnabled == targetEnabled) {
            ToggleResult(success = true, nowEnabled = nowEnabled)
        } else {
            ToggleResult(
                success = false,
                nowEnabled = wasEnabled,
                failureMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "A WiFi közvetlen kapcsolása nem sikerült ezen az eszközön. " +
                        "Ellenőrizd, hogy a Super DL rendelkezik WiFi módosítási engedéllyel."
                } else {
                    "WiFi kapcsoló nem működött."
                }
            )
        }
    }

    fun toggleBluetooth(context: Context): ToggleResult {
        val adapter = bluetoothAdapter(context)
            ?: return ToggleResult(success = false, nowEnabled = false, failureMessage = "Bluetooth nem elérhető ezen az eszközön.")

        return try {
            if (adapter.isEnabled) {
                @Suppress("DEPRECATION")
                adapter.disable()
                ToggleResult(success = true, nowEnabled = false)
            } else {
                @Suppress("DEPRECATION")
                adapter.enable()
                ToggleResult(success = true, nowEnabled = true)
            }
        } catch (_: SecurityException) {
            ToggleResult(
                success = false,
                nowEnabled = adapter.isEnabled,
                failureMessage = "Bluetooth engedély szükséges. " +
                    "Engedélyezd a Beállítások, Alkalmazások, Super DL, Engedélyek menüben a közeli eszközöket."
            )
        }
    }

    data class ToggleResult(
        val success: Boolean,
        val nowEnabled: Boolean,
        val failureMessage: String? = null,
        val openedPanel: Boolean = false
    )

    @Suppress("DEPRECATION")
    private fun setWifiEnabledDirect(wifiManager: WifiManager, enabled: Boolean): Boolean {
        return try {
            wifiManager.isWifiEnabled = enabled
            true
        } catch (_: Exception) {
            try {
                wifiManager.setWifiEnabled(enabled)
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun wifiManager(context: Context): WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    @Suppress("DEPRECATION")
    private fun readWifiEnabledViaWifiManager(context: Context): Boolean {
        val wifiManager = wifiManager(context) ?: return false
        return wifiManager.isWifiEnabled
    }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }
}