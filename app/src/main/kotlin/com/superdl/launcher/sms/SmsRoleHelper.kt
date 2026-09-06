package com.superdl.launcher.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.superdl.launcher.settings.PermissionGuideSection

object SmsRoleHelper {

    /**
     * MIÉNK-E AZ ÜZENET-SZEREPKÖR — KÉT FORRÁSBÓL.
     *
     * A HIBA, AMIT EZ JAVÍT (Lőrincz Richárd, 2026-09-06, Galaxy S24 Ultra,
     * Android 16). A jelentése szó szerint ez volt:
     *
     *   „amikor a telefon beállításain belül megnyitom az alapértelmezett
     *    alkalmazásokat, akkor az alapértelmezett üzenet alkalmazás a
     *    SuperDL … látom a beérkező üzeneteimet, tudok SMS-t írni, törölni,
     *    MŰKÖDNEK az SMS-ek. De viszont a beállítás varázslóban MÉG MINDIG
     *    azt írja, hogy hiányzik."
     *
     * A varázsló naplójában pedig ez állt: `alapértelmezett üzenet app:
     * nincs` — vagyis a `getDefaultSmsPackage()` ÜRESEN tért vissza, holott
     * a szerepkör a miénk volt.
     *
     * A két réteg nem mindig ért egyet: a szerepkör-kezelő (Android 10 óta ez
     * a mérvadó) odaadta a szerepkört, a régebbi Telephony-lekérdezés viszont
     * nem látta. Ezért mostantól MINDKETTŐT megkérdezzük, és ha bármelyik azt
     * mondja, hogy miénk, akkor miénk. Ugyanez a minta védi az asszisztens
     * szerepkört is (AssistantRoleHelper).
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        // 1) A hagyományos út. Ez a mérvadó ott, ahol működik.
        val byTelephony = try {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        } catch (_: Exception) {
            false
        }
        if (byTelephony) return true
        // 2) A szerepkör-kezelő. Android 10 óta EZ dönti el, ki az
        //    alapértelmezett üzenetküldő.
        return isRoleHeldBySystem(context)
    }

    /** A szerepkör-kezelő szerint miénk-e. Hiba esetén: nem tudjuk, tehát nem. */
    fun isRoleHeldBySystem(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
            roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } catch (_: Exception) {
            false
        }
    }

    /** Elérhető-e egyáltalán a szerepkör ezen a készüléken (a naplóhoz). */
    fun isRoleAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleAvailable(RoleManager.ROLE_SMS) == true
        } catch (_: Exception) {
            false
        }
    }

    fun isSmsRoleHeld(context: Context): Boolean = isDefaultSmsApp(context)

    /**
     * A KÉRÉS ÚTJA.
     *
     * MIÉRT NEM ESHET VISSZA A RÉGI ÚTRA ANDROID 10 FÖLÖTT: a korábbi kód a
     * `!isRoleHeld` feltétel miatt kihagyta a szerepkör-kérést, ha a rendszer
     * szerint már miénk volt a szerepkör — és ilyenkor a RÉGI,
     * `ACTION_CHANGE_DEFAULT` szándékot adta vissza. Az Android 10 óta
     * elavult, és a legtöbb mai rendszeren megnyílik, majd némán bezárul.
     * Richárd naplójában pontosan ez látszott: három próbálkozás, mindegyik
     * ezen a régi úton, mindegyik eredménytelenül.
     *
     * Ugyanez a hibaosztály fogta meg az asszisztens szerepkört az 1.62.1-ben.
     */
    fun createRoleRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && safeRoleAvailable(roleManager)) {
                // Már a miénk: nincs mit kérni.
                if (safeRoleHeld(roleManager)) return null
                return try {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                } catch (_: Exception) {
                    defaultAppsSettings()
                }
            }
            // Android 10 fölött, de a szerepkör nem elérhető: a beállítás-oldal
            // a járható út. A régi szándék itt csak egy néma zsákutca lenne.
            return defaultAppsSettings()
        }
        if (isDefaultSmsApp(context)) return null
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }

    private fun defaultAppsSettings(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    private fun safeRoleAvailable(rm: RoleManager): Boolean = try {
        rm.isRoleAvailable(RoleManager.ROLE_SMS)
    } catch (_: Exception) {
        false
    }

    private fun safeRoleHeld(rm: RoleManager): Boolean = try {
        rm.isRoleHeld(RoleManager.ROLE_SMS)
    } catch (_: Exception) {
        false
    }

    fun speakStatus(context: Context): String =
        if (isDefaultSmsApp(context)) {
            "A Super DL be van állítva alapértelmezett üzenet alkalmazásként. " +
                "Az üzenetek küldése és fogadása a Super DL-ből működik."
        } else {
            "A Super DL még nincs beállítva alapértelmezett üzenet alkalmazásként. " +
                "Beállítás nélkül az üzenetek olvasása és fogadása korlátozott lehet."
        }

    fun smsGuideSections(): List<PermissionGuideSection> = listOf(
        PermissionGuideSection(
            "Alapértelmezett üzenet app – Android 10 vagy újabb",
            "A Super DL megjelenik az SMS és üzenet alkalmazások listájában. " +
                "A menüben válaszd: Üzenetek és E-mail, Alapértelmezett üzenet app beállítása. " +
                "A rendszer kérése után válaszd a Super DL-t."
        ),
        PermissionGuideSection(
            "Alapértelmezett üzenet app – kézi beállítás",
            "Ha a rendszer nem kérdez rá automatikusan: Beállítások, Alkalmazások, " +
                "Alapértelmezett alkalmazások, SMS alkalmazás vagy Üzenet alkalmazás, " +
                "és válaszd a Super DL-t."
        )
    )
}