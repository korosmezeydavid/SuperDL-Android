package com.superdl.launcher.contacts

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log

object ContactSyncHelper {

    private const val TAG = "ContactSyncHelper"

    data class SyncResult(
        val count: Int,
        val syncedAtMs: Long,
        val googleAccountsTriggered: Int
    ) {
        fun speakSummary(): String =
            if (count == 0) {
                "Szinkronizálás kész. Nincs telefonszámmal rendelkező névjegy."
            } else {
                "Szinkronizálás kész. $count névjegy frissítve."
            }
    }

    /**
     * VAN-E JOGUNK EGYÁLTALÁN NÉVJEGYET OLVASNI?
     *
     * MIÉRT KELL EZ KÜLÖN: a névjegy-olvasás engedélyhez kötött, és ha nincs
     * meg, a rendszer nem üres listát ad, hanem SecurityException-t DOB.
     * Egy broadcast receiver `onReceive`-jében egy eldobott kivétel nem
     * hiba, hanem AZONNALI PROGRAMHALÁL — a rendszer öli meg a folyamatot.
     *
     * Pontosan ez omlasztotta össze a programot indításkor és
     * névjegy-változáskor, amíg a felhasználó nem adta még meg az
     * engedélyt. A tesztelő azt látta, hogy „a program leáll", és nem is
     * sejthette, hogy a névjegyekhez van köze.
     */
    fun hasPermission(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun sync(context: Context): SyncResult {
        val appContext = context.applicationContext
        if (!hasPermission(appContext)) {
            // Nincs engedély: nem kudarc, csak nincs mit tenni. A meglévő
            // mentett névjegyeket NEM bántjuk — nem írjuk felül üres listával.
            Log.i(TAG, "Nevjegy szinkron kihagyva: nincs READ_CONTACTS engedely")
            return SyncResult(0, System.currentTimeMillis(), 0)
        }
        val googleTriggered = requestGoogleContactsSync(appContext)
        val contacts = ContactHelper.listAllWithPhone(appContext)
        val syncedAt = System.currentTimeMillis()
        ContactStore.save(appContext, contacts, syncedAt)
        Log.i(TAG, "Synced ${contacts.size} contacts, googleAccounts=$googleTriggered")
        return SyncResult(contacts.size, syncedAt, googleTriggered)
    }

    /**
     * A HÁTTÉRBŐL HÍVOTT VÁLTOZAT — indításkor és névjegy-változáskor fut.
     *
     * ITT MINDENT ELKAPUNK, A HIBÁKAT IS. Nem azért, hogy elkendőzzük a
     * bajt, hanem mert ez egy `onReceive`-ben fut: ott egy kivétel az egész
     * programot megöli, a felhasználó szeme láttára, minden magyarázat
     * nélkül. Egy elmaradt névjegy-frissítés bosszantó; egy összeomló
     * kezdőképernyő használhatatlan telefon.
     *
     * `Throwable`, nem `Exception`: az `OutOfMemoryError` sem `Exception`,
     * és pont egy ilyen omlasztotta össze korábban a podcast-importot.
     */
    fun syncIfNeeded(context: Context): SyncResult? = try {
        if (!hasPermission(context)) {
            null
        } else if (!ContactStore.needsDailySync(context)) {
            null
        } else {
            sync(context)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Nevjegy szinkron hiba: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    private fun requestGoogleContactsSync(context: Context): Int {
        var triggered = 0
        try {
            @Suppress("DEPRECATION")
            val accounts = android.accounts.AccountManager.get(context).accounts
            for (account in accounts) {
                if (!account.type.contains("google", ignoreCase = true)) continue
                val authority = ContactsContract.AUTHORITY
                if (!ContentResolver.getSyncAutomatically(account, authority)) continue
                ContentResolver.requestSync(account, authority, Bundle.EMPTY)
                triggered++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google névjegy szinkron kérés sikertelen", e)
        }
        return triggered
    }
}