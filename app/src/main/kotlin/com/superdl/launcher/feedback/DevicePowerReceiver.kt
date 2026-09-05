package com.superdl.launcher.feedback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.superdl.launcher.patrol.PatrolStore
import com.superdl.launcher.voicetheme.VoiceEvent
import com.superdl.launcher.voicetheme.VoiceThemePlayer

class DevicePowerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // MIÉRT NEM ITT TÉRÜNK VISSZA, HA A SÍPOK KI VANNAK KAPCSOLVA:
        // a beszédtémának SAJÁT kapcsolója van. Aki a sípokat nem kéri, de
        // Elena hangját igen, annak is szólnia kell.
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                DeviceStateStore.setFullAnnounced(context, false)
                if (DeviceStateStore.isEnabled(context)) {
                    DeviceStateTonePlayer.play(DeviceStateEvent.CHARGER_CONNECTED)
                }
                // A síp a gyors visszajelzés, a mondat a hangulat. Vakon
                // amúgy sem mindig tudni, hogy a dugó tényleg érintkezik-e.
                VoiceThemePlayer.announce(context, VoiceEvent.CHARGER_IN)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                DeviceStateStore.setFullAnnounced(context, false)
                if (DeviceStateStore.isEnabled(context)) {
                    DeviceStateTonePlayer.play(DeviceStateEvent.CHARGER_DISCONNECTED)
                }
                // CSAK AKKOR, HA MÉG ALACSONY. Ha tele van, ott a
                // „jóllaktam" — a kettő együtt kerek. Tele telefonnál a
                // „még éhes vagyok" értelmetlen lenne.
                if (isStillLow(context)) {
                    VoiceThemePlayer.announce(context, VoiceEvent.CHARGER_OUT_LOW)
                }
            }
        }
    }

    private fun isStillLow(context: Context): Boolean = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        level in 0..PatrolStore.getFirstAlertPercent(context)
    } catch (_: Exception) {
        false
    }
}