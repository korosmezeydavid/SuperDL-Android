package com.superdl.launcher.history

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log

/**
 * A TELJES HÍVÁSNAPLÓ ÉS AZ ÖSSZES ÜZENET TÖRLÉSE.
 *
 * MIÉRT VESZÉLYES, ÉS MIÉRT VAN MÉGIS: ez visszavonhatatlan, és vakon egy
 * elsöpört mozdulat is elindíthatná. Ezért a program KÉTSZER kérdez rá, és
 * a második kérdésnél KIMONDJA, hány tételről van szó — egy szám sokkal
 * jobban megállítja az embert, mint egy általános „biztos vagy benne".
 *
 * MIÉRT SZÁMOLUNK TÖRLÉS UTÁN IS: a rendszer nem dob hibát, ha nem volt
 * jogunk törölni — csak nulla sort töröl. A néma kudarc a legrosszabb
 * kimenetel, ezért utána megnézzük, tényleg eltűnt-e.
 */
object HistoryWipe {

    private const val TAG = "SDL_TORLES"

    // ── Hívásnapló ───────────────────────────────────────────────────────

    fun countCalls(context: Context): Int = try {
        var count = 0
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID), null, null, null
        )?.use { count = it.count }
        count
    } catch (e: Exception) {
        Log.w(TAG, "hivasnaplo szamlalas hiba: ${e.message}")
        -1
    }

    /**
     * @return hány tétel maradt; -1 ha nem lehetett megállapítani
     */
    fun wipeCalls(context: Context): Int {
        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "hivasnaplo torles hiba: ${e.message}")
        }
        return countCalls(context)
    }

    // ── Üzenetek ─────────────────────────────────────────────────────────

    fun countMessages(context: Context): Int = try {
        var count = 0
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), null, null, null
        )?.use { count = it.count }
        count
    } catch (e: Exception) {
        Log.w(TAG, "uzenet szamlalas hiba: ${e.message}")
        -1
    }

    /**
     * AZ ÖSSZES ÜZENET — bejövő és kimenő EGYBEN.
     *
     * CSAK AKKOR MŰKÖDIK, HA A SUPER DL AZ ALAPÉRTELMEZETT ÜZENET APP.
     * Android 4.4 óta más program nem törölhet SMS-t; a rendszer nem szól,
     * csak nem történik semmi. Ezért a hívó előbb megkérdezi
     * `canDeleteMessages`-szel, és ha nem mi vagyunk, azt MEGMONDJA —
     * különben a felhasználó azt hinné, törölt, pedig minden megvan.
     *
     * @return hány üzenet maradt; -1 ha nem lehetett megállapítani
     */
    fun wipeMessages(context: Context): Int {
        try {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "uzenet torles hiba: ${e.message}")
        }
        return countMessages(context)
    }

    /** Mi vagyunk-e az alapértelmezett üzenet alkalmazás. */
    fun canDeleteMessages(context: Context): Boolean = try {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    } catch (e: Exception) {
        Log.w(TAG, "alapertelmezett sms app lekerdezes hiba: ${e.message}")
        false
    }
}
