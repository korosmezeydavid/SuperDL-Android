package com.superdl.launcher.contacts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A NÉVJEGY-SZINKRON ÜTEMEZETT INDÍTÁSA.
 *
 * A TELJES TÖRZS VÉDŐHÁLÓBAN VAN. Egy `onReceive`-ben eldobott kivétel nem
 * hiba, hanem azonnali programhalál: a rendszer megöli a folyamatot, és a
 * felhasználó annyit lát, hogy „leállt a program" — magyarázat nélkül,
 * abban a pillanatban, amikor épp valami mást csinált. Pontosan ez történt
 * a 2026-09-01-i hibajelentésben.
 *
 * A `?: sync(context)` ág szándékosan maradt: a `syncIfNeeded` akkor is
 * `null`-t ad, ha még nem járt le a napi idő. A `sync` viszont MOSTANTÓL
 * MAGA ELLENŐRZI az engedélyt, tehát engedély nélkül sem dob.
 */
class ContactSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            ContactSyncHelper.syncIfNeeded(context) ?: ContactSyncHelper.sync(context)
        } catch (t: Throwable) {
            android.util.Log.w(
                "ContactSyncReceiver",
                "Szinkron hiba: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
        try {
            ContactSyncScheduler.reschedule(context)
        } catch (t: Throwable) {
            // Az újraütemezésnek akkor is le kell futnia, ha a szinkron
            // elbukott — különben a napi frissítés végleg elmarad.
            android.util.Log.w(
                "ContactSyncReceiver",
                "Ujrautemezes hiba: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }
}