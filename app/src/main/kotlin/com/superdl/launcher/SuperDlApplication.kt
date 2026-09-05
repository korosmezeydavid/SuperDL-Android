package com.superdl.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.superdl.launcher.crash.CrashLogHandler
import com.superdl.launcher.system.QuietModeHelper
import org.lsposed.hiddenapibypass.HiddenApiBypass

class SuperDlApplication : Application() {

    /**
     * FONTOS (Direct Boot / első bekapcsolás):
     * Az első feloldásig a szokásos beállítás-tároló TITKOSÍTVA van. Ha ilyenkor
     * SharedPreferences-hez nyúlunk, a rendszer IllegalStateException-t dob
     * ("SharedPreferences in credential encrypted storage are not available
     * until after user is unlocked"), és az EGÉSZ alkalmazás összeomlik —
     * emiatt a PIN segéd kisegítő szolgáltatás sem tudott létrejönni
     * (a rendszerben "Binding" állapotban ragadt).
     *
     * Ezért induláskor csak a titkosítás alatt is biztonságos részt végezzük el,
     * a többit a feloldás pillanatában pótoljuk.
     */
    private var pendingUnlockInit = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                initAfterUnlock()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Ez nem használ beállítás-tárolót, tehát mindig biztonságos.
        ensureHiddenApiAccess()

        // A HÁTTÉRSZÁL-VÉDELEM A LEHETŐ LEGKORÁBBAN.
        //
        // MIÉRT ITT, ÉS NEM A FELOLDÁS UTÁN: eddig csak a feloldást követően
        // települt, így egy KORÁBBAN hibázó háttérszál védtelenül maradt — és
        // egy háttérhiba Androidon az EGÉSZ folyamatot megöli.
        //
        // Ez élesben ki is derült: friss telepítésnél a névjegy-szinkron
        // engedély nélkül futott, kivételt dobott, és a launcher eltűnt.
        // A naplózás továbbra is a feloldás után indul (fájlt ír, ahhoz kell
        // a tároló) — de az ELSZIGETELÉS már itt működik.
        try {
            CrashLogHandler.install(this)
        } catch (e: Exception) {
            Log.w(TAG, "korai CrashLogHandler.install hiba: ${e.message}")
        }

        if (isUserUnlocked()) {
            initAfterUnlock()
        } else {
            // Első bekapcsolás, még titkosított állapot: megvárjuk a feloldást.
            Log.i(TAG, "Direct Boot: a teljes indulas a feloldasig var.")
            pendingUnlockInit = true
            try {
                registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            } catch (e: Exception) {
                Log.w(TAG, "unlock receiver regisztralas hiba: ${e.message}")
            }
        }
    }

    /** A beállítás-tárolót igénylő indulási lépések (feloldás után). */
    private fun initAfterUnlock() {
        try {
            CrashLogHandler.install(this)
        } catch (e: Exception) {
            Log.w(TAG, "CrashLogHandler.install hiba: ${e.message}")
        }
        // INDULÁSI ŐR: feljegyezzük, hogy elkezdődött az indulás. Ha a
        // program itt összeomlik, a következő induláskor ez a jelzés fogad
        // minket — három félbemaradt indulás után BIZTONSÁGOS MÓDBAN indulunk.
        try {
            com.superdl.launcher.crash.StartupGuard.onStartupBegin(this)
            // Ha a program eddig eljutott és még mindig fut, az indulás
            // sikeresnek tekinthető — de adunk időt a korai összeomlásoknak is.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                com.superdl.launcher.crash.StartupGuard.onStartupSuccess(this)
            }, com.superdl.launcher.crash.StartupGuard.SUCCESS_DELAY_MS)
        } catch (e: Exception) {
            Log.w(TAG, "StartupGuard hiba: ${e.message}")
        }
        // A névjegyzék megjelenítési beállításai a memóriába: a maskPhone()
        // olyan helyeken is fut (csengetés, hívás közbeni képernyő), ahol
        // már nincs mód beállítást olvasni.
        try {
            com.superdl.launcher.contacts.ContactPrefs.warm(this)
        } catch (e: Exception) {
            Log.w(TAG, "ContactPrefs.warm hiba: ${e.message}")
        }
        // A gesztus-irányítás módja: a söprések értelmezése minden képernyőn
        // és az ernyőolvasóban is ebből dolgozik, ezért még indulás előtt kell.
        try {
            com.superdl.launcher.gestures.GestureOrientation.warm(this)
        } catch (e: Exception) {
            Log.w(TAG, "GestureOrientation.warm hiba: ${e.message}")
        }
        try {
            QuietModeHelper.reconcileOnStartup(this)
        } catch (e: Exception) {
            Log.w(TAG, "QuietModeHelper hiba: ${e.message}")
        }
        // BEÉPÍTETT BESZÉDTÉMA (Elena): a hangfájlok a programcsomagban
        // utaznak, de lejátszani csak fájlból tudjuk, ezért az első indulásnál
        // kicsomagoljuk őket. Háttérszálon, mert fájlmásolás; és csak azt
        // másoljuk, ami hiányzik, tehát a további indulások ingyenesek.
        try {
            Thread {
                try {
                    com.superdl.launcher.voicetheme.VoiceThemeAssets.ensureInstalled(this)
                } catch (e: Exception) {
                    Log.w(TAG, "VoiceThemeAssets.ensureInstalled hiba: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.w(TAG, "VoiceThemeAssets szal hiba: ${e.message}")
        }
        if (pendingUnlockInit) {
            pendingUnlockInit = false
            try {
                unregisterReceiver(unlockReceiver)
            } catch (_: Exception) {
            }
            Log.i(TAG, "Feloldas utani indulas kesz.")
        }
    }

    private fun isUserUnlocked(): Boolean = try {
        val um = getSystemService(Context.USER_SERVICE) as UserManager
        um.isUserUnlocked
    } catch (_: Exception) {
        // Ha nem tudjuk megállapítani, óvatosak vagyunk: feloldottnak vesszük,
        // hogy a megszokott működés ne sérüljön.
        true
    }

    companion object {
        private const val TAG = "SDL_APP"

        @Volatile
        private var hiddenApiReady = false

        fun ensureHiddenApiAccess() {
            if (hiddenApiReady) return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                hiddenApiReady = true
                return
            }
            synchronized(this) {
                if (hiddenApiReady) return
                try {
                    HiddenApiBypass.addHiddenApiExemptions("")
                    hiddenApiReady = true
                } catch (_: Exception) {
                }
            }
        }
    }
}
