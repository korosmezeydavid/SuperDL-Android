package com.superdl.launcher.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * BEÁLLÍTÁS-TÁROLÓ, AMI SOHA NEM DOB KIVÉTELT.
 *
 * MIÉRT KELLETT:
 *
 * Az Android az első feloldásig (Direct Boot) titkosítva tartja a szokásos
 * beállítás-tárolót, és a getSharedPreferences() ilyenkor kivételt dob:
 *
 *     IllegalStateException: SharedPreferences in credential encrypted
 *     storage are not available until after user is unlocked
 *
 * Egy elkapatlan kivétel Androidon az EGÉSZ FOLYAMATOT megöli. A SuperDL-nél
 * a két kisegítő szolgáltatás — a képernyőolvasó és a PIN segéd — ugyanabban
 * a folyamatban él, ezért egyetlen ilyen kivétel MINDKETTŐT kiütötte. Két
 * összeomlás után az Android fél órára elhalasztotta az újraindítást, tehát
 * a telefon feloldásáig a SuperDL egyetlen része sem élt.
 *
 * Vak felhasználónál ez azt jelenti, hogy nem tudja feloldani a telefonját.
 *
 * KÉT ÍZBEN IS ÍGY BUKTUNK EL, két különböző helyen:
 *  1. a képernyőolvasó HIBAKEZELŐJE nyúlt a tárolóhoz (a biztonsági háló
 *     maga omlott össze),
 *  2. a beszédmotor ASZINKRON visszahívása (TtsManager.onInit) — azt a
 *     konstruktor köré tett try/catch nem is foghatta el.
 *
 * A második eset a tanulságos: nem elég körülvédeni a hívási helyeket, mert
 * a veszélyes hívás egy KÉSŐBBI, máshonnan érkező visszahívásból is jöhet.
 * Ezért a védelemnek MAGÁBAN A TÁROLÓBAN kell lennie.
 *
 * MIT CSINÁL:
 *
 * Feloldás előtt az ESZKÖZ-VÉDETT tárolót adja (az titkosítás alatt is
 * olvasható és írható), feloldás után a megszokottat. Ha bármi mégis
 * félresikerül, akkor sem dob: legrosszabb esetben egy üres, memóriabeli
 * tárolót ad vissza, tehát a hívó az alapértékekkel dolgozik tovább — némán
 * rosszabbul, de ÉLVE.
 */
object SafePrefs {

    private const val TAG = "SDL_SAFEPREFS"

    /** Végszükség esetére: egy üres tároló, ami mindig működik. */
    private const val FALLBACK_NAME = "superdl_atmeneti_tarolo"

    fun get(context: Context, name: String): SharedPreferences {
        val app = try {
            context.applicationContext ?: context
        } catch (_: Exception) {
            context
        }

        if (!isUserUnlocked(app)) {
            // Titkosított fázis: meg se kíséreljük a szokásos tárolót.
            deviceProtected(app, name)?.let { return it }
        }

        try {
            return app.getSharedPreferences(name, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            // Öv és nadrágtartó: ha az isUserUnlocked() tévedne (gyártói
            // ROM-okon előfordul), itt még mindig nem omlunk össze.
            android.util.Log.w(TAG, "szokasos tarolo nem elerheto ($name): ${e.message}")
        }

        deviceProtected(app, name)?.let { return it }

        // Ide már csak akkor jutunk, ha SEMMI nem működik. Akkor is adunk
        // valamit, amivel a hívó tovább tud dolgozni.
        return try {
            app.createDeviceProtectedStorageContext()
                .getSharedPreferences(FALLBACK_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            throw IllegalStateException("SafePrefs: nincs elerheto tarolo", e)
        }
    }

    private fun deviceProtected(context: Context, name: String): SharedPreferences? = try {
        context.createDeviceProtectedStorageContext()
            ?.getSharedPreferences(name, Context.MODE_PRIVATE)
    } catch (e: Exception) {
        android.util.Log.w(TAG, "eszkoz-vedett tarolo sem elerheto ($name): ${e.message}")
        null
    }

    /**
     * Feloldva van-e a felhasználó.
     *
     * HIBÁS ESETBEN FELOLDOTTNAK VESSZÜK: így a megszokott működés nem
     * sérül, a titkosított eset pedig a fenti try/catch-ben úgyis kiderül.
     */
    fun isUserUnlocked(context: Context): Boolean = try {
        val um = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        um.isUserUnlocked
    } catch (_: Exception) {
        true
    }
}
