package com.superdl.launcher.calendar

import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

/**
 * A HOZZÁRENDELT MŰVELET VÉGREHAJTÁSA — csak megerősítés után.
 *
 * A hívó (CalendarAlertActivity) már megkérdezte a felhasználót, és ő igent
 * mondott. Itt már nem kérdezünk, csak elvégezzük — de MINDEN ágon
 * visszamondjuk, mi történt, mert vakon a néma siker és a néma kudarc
 * megkülönböztethetetlen.
 *
 * @return a felolvasandó visszajelzés.
 */
object CalendarActionRunner {

    fun run(context: Context, action: CalendarAction): String = try {
        when (action) {
            is CalendarAction.OpenMenu -> openMenu(context, action)
            is CalendarAction.RunTaskRoute -> runRoute(context, action)
            is CalendarAction.SendSms -> sendSms(context, action)
            is CalendarAction.OpenApp -> openApp(context, action)
        }
    } catch (e: Exception) {
        "A műveletet nem sikerült végrehajtani: ${e.message ?: "ismeretlen hiba"}."
    }

    /**
     * A SuperDL saját menüpontja. A főképernyőt indítjuk el egy kéréssel,
     * amit a MainActivity az indulásakor kiolvas — így a művelet akkor is
     * működik, ha a program épp nem fut.
     */
    private fun openMenu(context: Context, action: CalendarAction.OpenMenu): String {
        val intent = Intent(context, Class.forName("com.superdl.launcher.MainActivity")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_MENU_ACTION, action.actionName)
        }
        context.startActivity(intent)
        return "Megnyitom: ${action.spoken}."
    }

    private fun runRoute(context: Context, action: CalendarAction.RunTaskRoute): String {
        val intent = Intent(context, Class.forName("com.superdl.launcher.macro.TaskRouteActivity")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_ROUTE_ID, action.routeId)
        }
        context.startActivity(intent)
        return "Indítom a műveletsort: ${action.routeName}."
    }

    /**
     * SMS KÜLDÉSE.
     *
     * Ez az egyetlen művelet, ami visszavonhatatlanul KIFELÉ hat. A hívó
     * ezért a címzettet és a szöveget is felolvasta a megerősítés előtt.
     */
    private fun sendSms(context: Context, action: CalendarAction.SendSms): String {
        val manager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return "Az SMS küldés most nem érhető el."

        val parts = manager.divideMessage(action.text)
        if (parts.size <= 1) {
            manager.sendTextMessage(action.number, null, action.text, null, null)
        } else {
            manager.sendMultipartTextMessage(action.number, null, parts, null, null)
        }
        return "Az üzenet elküldve neki: ${action.who}."
    }

    private fun openApp(context: Context, action: CalendarAction.OpenApp): String {
        val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return "Ez az alkalmazás már nincs a telefonon: ${action.appName}."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Megnyitom: ${action.appName}."
    }

    const val EXTRA_MENU_ACTION = "superdl_naptar_menupont"
    const val EXTRA_ROUTE_ID = "superdl_muveletsor_id"
}
