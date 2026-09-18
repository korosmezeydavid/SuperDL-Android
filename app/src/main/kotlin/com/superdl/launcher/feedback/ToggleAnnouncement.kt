package com.superdl.launcher.feedback

import android.content.Context
import com.superdl.launcher.battery.BatteryPatrolManager
import com.superdl.launcher.callfilter.CallFilterStore
import com.superdl.launcher.menu.MenuAction
import com.superdl.launcher.patrol.PatrolStore
import com.superdl.launcher.security.LockPinStore
import com.superdl.launcher.system.ConnectivityHelper
import com.superdl.launcher.tools.FlashlightState

object ToggleAnnouncement {

    private data class ToggleSpec(
        val label: String,
        val isEnabled: (Context) -> Boolean
    )

    private val specs: Map<MenuAction, ToggleSpec> = mapOf(
        MenuAction.BATTERY_PATROL_TOGGLE to ToggleSpec("Teljes őrség") { BatteryPatrolManager.isEnabled(it) },
        MenuAction.PATROL_BATTERY_TOGGLE to ToggleSpec("Akkumulátor figyelés") { PatrolStore.isBatteryEnabled(it) },
        MenuAction.PATROL_CALL_ALERT_TOGGLE to ToggleSpec("Hívás értesítés") { PatrolStore.isCallAlertEnabled(it) },
        MenuAction.PATROL_SMS_ALERT_TOGGLE to ToggleSpec("Üzenet értesítés") { PatrolStore.isSmsAlertEnabled(it) },
        MenuAction.PATROL_NOTIFICATION_ALERT_TOGGLE to ToggleSpec("Egyéb értesítés") {
            PatrolStore.isNotificationAlertEnabled(it)
        },
        MenuAction.PATROL_TIME_ANNOUNCE_TOGGLE to ToggleSpec("Idő bemondás") { PatrolStore.isTimeAnnounceEnabled(it) },
        MenuAction.PATROL_NIGHT_MODE_TOGGLE to ToggleSpec("Éjszakai csend") { PatrolStore.isNightModeEnabled(it) },
        MenuAction.PATROL_POWER_BUTTON_TIME_TOGGLE to ToggleSpec("Bekapcsoló gomb idő bemondás") {
            PatrolStore.isPowerButtonTimeEnabled(it)
        },
        MenuAction.WIFI_TOGGLE to ToggleSpec("WiFi") { ConnectivityHelper.isWifiEnabled(it) },
        MenuAction.HOTSPOT_TOGGLE to ToggleSpec("Hotspot") { ConnectivityHelper.isHotspotEnabled(it) },
        MenuAction.BT_TOGGLE to ToggleSpec("Bluetooth") { ConnectivityHelper.isBluetoothEnabled(it) },
        MenuAction.LOCK_PIN_TOGGLE to ToggleSpec("PIN zárolás") { LockPinStore.isEnabled(it) },
        // A REJTETT SZÁMOK KIKERÜLTEK INNEN (2026-09-17). Ez már nem
        // kapcsoló, hanem választó: a menüpont menüt nyit, nem átbillent.
        // Ha itt maradna, a program a fókuszáláskor egy állapotot mondana be,
        // ami után nem az történik, amit ígért.
        MenuAction.FLASHLIGHT to ToggleSpec("Zseblámpa") { FlashlightState.isOn }
    )

    /**
     * A KAPCSOLÓ MEGMONDJA, HOL ÁLL — MIND A 31.
     *
     * Alph kérése (2026-09-18): a kapcsolók maradjanak azonnaliak, DE mondják
     * be az állapotukat. Aki nem látja a képernyőt, annak ez az egyetlen módja
     * megtudni, hogy amire rásöpör, az most be- vagy kikapcsol.
     *
     * A lista alapja a settings/ToggleChoice nyilvántartás (31 kapcsoló); az
     * itteni `specs` csak azokat írja fölül, ahol más szöveg kell.
     */
    private fun stateOf(context: Context, action: MenuAction): Pair<String, Boolean>? {
        specs[action]?.let { spec ->
            return spec.label to runCatching { spec.isEnabled(context) }.getOrDefault(false)
        }
        val generic = com.superdl.launcher.settings.ToggleChoice.specFor(action) ?: return null
        return generic.title to runCatching { generic.isOn(context) }.getOrDefault(false)
    }

    fun isToggle(action: MenuAction): Boolean =
        action in specs || com.superdl.launcher.settings.ToggleChoice.specFor(action) != null

    fun speakFocused(context: Context, itemLabel: String, action: MenuAction): String {
        val (label, state) = stateOf(context, action) ?: return itemLabel
        // AZ OTTHON-FIGYELÉS MÓDJA nem „be" és „ki", hanem ÉLES vagy PRÓBA.
        // Egy „bekapcsolva" itt félrevezetne: az éles mód tényleg SMS-t küld.
        val generic = com.superdl.launcher.settings.ToggleChoice.specFor(action)
        if (generic != null && generic.onState != "bekapcsolva") {
            val word = com.superdl.launcher.settings.ToggleChoice.stateWord(generic, state)
            return "$itemLabel. $label jelenleg ${word.uppercase()}."
        }
        return "$itemLabel. ${speakFocusedState(label, state)}"
    }

    fun speakFocusedState(label: String, enabled: Boolean): String =
        "$label jelenleg ${stateWord(enabled)}."

    fun speakBeforeToggle(context: Context, action: MenuAction): String? {
        val spec = specs[action] ?: return null
        val enabled = spec.isEnabled(context)
        return "${spec.label} jelenleg ${stateWord(enabled)}. ${actionWord(enabled)}"
    }

    fun speakBinaryToggle(label: String, currentlyEnabled: Boolean): String =
        "$label jelenleg ${stateWord(currentlyEnabled)}. ${actionWord(currentlyEnabled)}"

    fun speakAfterToggle(label: String, nowEnabled: Boolean, extra: String = ""): String {
        val suffix = if (extra.isBlank()) {
            if (nowEnabled) "Bekapcsolva." else "Kikapcsolva."
        } else {
            extra
        }
        return "$label jelenleg ${stateWord(nowEnabled)}. $suffix"
    }

    private fun stateWord(enabled: Boolean): String = if (enabled) "BEKAPCSOLVA" else "KIKAPCSOLVA"

    private fun actionWord(currentlyEnabled: Boolean): String =
        if (currentlyEnabled) "Kikapcsolás." else "Bekapcsolás."
}