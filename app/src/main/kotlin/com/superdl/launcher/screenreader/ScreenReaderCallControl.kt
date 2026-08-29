package com.superdl.launcher.screenreader

import android.content.Context
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * BEJÖVŐ HÍVÁS KEZELÉSE A KÉPERNYŐOLVASÓBÓL.
 *
 * MIÉRT KELL: amikor csörög a telefon, nincs idő elemről elemre navigálni a
 * "Fogadás" gombig — és a hívás közben pont az a legrosszabb, ha keresgélni
 * kell. Ilyenkor a megszokott szabály lép életbe:
 *
 *     JOBBRA söprés = FOGADÁS   |   BALRA söprés = ELUTASÍTÁS
 *
 * MŰKÖDIK MÁS LAUNCHERREL IS: ez nem a SuperDL hívásképernyőjét vezérli,
 * hanem a RENDSZER hívását — tehát akkor is jó, ha a felhasználó megtartotta
 * a saját kezdőképernyőjét és a gyári telefon alkalmazását, és csak a
 * SuperDL képernyőolvasóját használja.
 */
object ScreenReaderCallControl {

    private const val TAG = "SDL_SCREENREADER"

    /** Csörög-e éppen a telefon? */
    fun isRinging(context: Context): Boolean = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        tm.callState == TelephonyManager.CALL_STATE_RINGING
    } catch (e: Exception) {
        Log.w(TAG, "hivas-allapot lekerdezes hiba: ${e.message}")
        false
    }

    /** Folyamatban van-e hívás (fogadott, beszélgetés közben)? */
    fun isInCall(context: Context): Boolean = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        tm.callState == TelephonyManager.CALL_STATE_OFFHOOK
    } catch (_: Exception) {
        false
    }

    /** A csörgő hívás FOGADÁSA. */
    fun answer(context: Context): Boolean = try {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            tm.acceptRingingCall()
            Log.i(TAG, "hivas fogadva a kepernyoolvasobol")
            true
        } else {
            false
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "fogadas engedely hiba: ${e.message}")
        false
    } catch (e: Exception) {
        Log.w(TAG, "fogadas hiba: ${e.message}")
        false
    }

    /**
     * A hívás ELUTASÍTÁSA vagy BEFEJEZÉSE.
     * Csörgésnél elutasít, beszélgetés közben leteszi.
     */
    fun endCall(context: Context): Boolean = try {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val ok = tm.endCall()
            Log.i(TAG, "hivas lezarva a kepernyoolvasobol: $ok")
            ok
        } else {
            false
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "lezaras engedely hiba: ${e.message}")
        false
    } catch (e: Exception) {
        Log.w(TAG, "lezaras hiba: ${e.message}")
        false
    }
}
