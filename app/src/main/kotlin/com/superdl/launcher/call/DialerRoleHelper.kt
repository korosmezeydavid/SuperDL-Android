package com.superdl.launcher.call

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import com.superdl.launcher.settings.PermissionGuideSection

object DialerRoleHelper {

    /**
     * MIÉNK-E A TELEFON-SZEREPKÖR — KÉT FORRÁSBÓL.
     *
     * A HIBA, AMIT EZ JAVÍT (Xiaomi M2103K19G, Android 13, 1.62.1-es
     * hibajelentés): a varázsló naplója szerint `alapértelmezett telefon app:
     * com.google.android.dialer`, és a felhasználó KÉTSZER is végigment a
     * kérésen eredménytelenül — a varázsló nem engedte tovább.
     *
     * A korábbi kód EGYETLEN forrásból nézte: `telecom.defaultDialerPackage`.
     * Pontosan ugyanaz a hibaosztály, mint az üzenet-szerepkörnél (lásd
     * SmsRoleHelper): a szerepkör-kezelő és a régebbi rendszerréteg nem
     * mindig ért egyet, és gyártói rendszereken — a MIUI-n különösen —
     * bőven előfordul, hogy az egyik lát valamit, a másik nem.
     *
     * Mostantól mindkettőt megkérdezzük, és ha bármelyik azt mondja, hogy
     * miénk, akkor miénk.
     */
    fun isDefaultDialer(context: Context): Boolean {
        val byTelecom = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(TelecomManager::class.java)
                    ?.defaultDialerPackage == context.packageName
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
        if (byTelecom) return true
        return isRoleHeldBySystem(context)
    }

    /** A szerepkör-kezelő szerint miénk-e. Hiba esetén: nem tudjuk, tehát nem. */
    fun isRoleHeldBySystem(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
            roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } catch (_: Exception) {
            false
        }
    }

    /** Elérhető-e egyáltalán a szerepkör ezen a készüléken (a naplóhoz). */
    fun isRoleAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        } catch (_: Exception) {
            false
        }
    }

    fun isDialerRoleHeld(context: Context): Boolean = isDefaultDialer(context)

    /**
     * A KÉRÉS ÚTJA.
     *
     * MIÉRT NEM ESHET VISSZA A RÉGI ÚTRA ANDROID 10 FÖLÖTT: az
     * `ACTION_CHANGE_DEFAULT_DIALER` Android 10 óta elavult, és a mai
     * rendszereken jellemzően megnyílik, majd némán bezárul — a felhasználó
     * úgy látja, hogy „csinált valamit", közben semmi nem történt. Ez fogta
     * meg az asszisztens szerepkört az 1.62.1-ben és az üzenet-szerepkört az
     * 1.63.2-ben; itt a harmadik előfordulása.
     *
     * Ha a szerepkör-kérés nem hozható létre, akkor NEM adunk vissza néma
     * zsákutcát, hanem az alapértelmezett alkalmazások rendszerképernyőjét —
     * ott a felhasználó kézzel el tudja végezni. Gyártói rendszereken (MIUI)
     * sokszor ez az egyetlen működő út.
     */
    fun createRoleRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = try {
                context.getSystemService(RoleManager::class.java)
            } catch (_: Exception) {
                null
            }
            if (roleManager != null && safeRoleAvailable(roleManager)) {
                if (safeRoleHeld(roleManager)) return null
                return try {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                } catch (_: Exception) {
                    defaultAppsSettings()
                }
            }
            return defaultAppsSettings()
        }
        return createLegacyDialerIntent(context)
    }

    private fun safeRoleAvailable(roleManager: RoleManager): Boolean = try {
        roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
    } catch (_: Exception) {
        false
    }

    private fun safeRoleHeld(roleManager: RoleManager): Boolean = try {
        roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    } catch (_: Exception) {
        false
    }

    /** Az alapértelmezett alkalmazások rendszerképernyője — kézi beállításhoz. */
    private fun defaultAppsSettings(): Intent =
        Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS")

    private fun createLegacyDialerIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return null
        if (telecom.defaultDialerPackage == context.packageName) return null
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        }
    }

    fun speakStatus(context: Context): String =
        if (isDefaultDialer(context)) {
            "A Super DL be van állítva alapértelmezett telefon alkalmazásként. " +
                "A bejövő hívások száma és a hívó neve megjelenik."
        } else {
            "A Super DL még nincs beállítva alapértelmezett telefon alkalmazásként. " +
                "Beállítás nélkül a bejövő hívó száma gyakran ismeretlen marad."
        }

    fun dialerGuideSections(): List<PermissionGuideSection> = listOf(
        PermissionGuideSection(
            "Alapértelmezett telefon – Android 10 vagy újabb",
            "A Super DL megjelenik a telefon alkalmazások listájában. " +
                "A menüben válaszd: Beállítások, Biztonság, Alapértelmezett telefon beállítása. " +
                "A rendszer kérése után válaszd a Super DL-t. " +
                "Ez szükséges ahhoz, hogy a bejövő hívásoknál lásd a hívó számát és nevét."
        ),
        PermissionGuideSection(
            "Alapértelmezett telefon – kézi beállítás",
            "Ha a rendszer nem kérdez rá automatikusan: Beállítások, Alkalmazások, " +
                "Alapértelmezett alkalmazások, Telefon alkalmazás, és válaszd a Super DL-t. " +
                "Egyes telefonokon: Beállítások, Alkalmazások, Speciális hozzáférés, " +
                "Alapértelmezett alkalmazások, Telefon."
        )
    )
}