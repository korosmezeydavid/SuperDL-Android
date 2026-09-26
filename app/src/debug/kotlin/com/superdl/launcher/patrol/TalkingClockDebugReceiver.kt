package com.superdl.launcher.patrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.superdl.launcher.catalog.CatalogClient
import java.io.File

/**
 * CSAK A FEJLESZTŐI VÁLTOZATBAN. A beszélő óra próbája gépről:
 *   adb shell am broadcast -n com.superdl.launcher.debug/com.superdl.launcher.patrol.TalkingClockDebugReceiver --es mod letolt
 *   ... --es mod wav --es ido 14:30     → files/ora-14-30.wav (run-as-szal lehúzható)
 *   ... --es mod szol --es ido 14:30    → lejátssza
 */
class TalkingClockDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val mod = intent.getStringExtra("mod") ?: "wav"
        val ido = (intent.getStringExtra("ido") ?: "14:30").split(":")
        val h = ido.getOrNull(0)?.toIntOrNull() ?: 14
        val m = ido.getOrNull(1)?.toIntOrNull() ?: 30
        val pending = goAsync()
        Thread {
            try {
                when (mod) {
                    "letolt" -> {
                        val cat = CatalogClient.fetchCatalog(app)
                        val module = cat.modules.firstOrNull { it.id == (intent.getStringExtra("id") ?: "beszelo-ora-leda") }
                        Log.i(TAG, "katalogus: ${cat.modules.size} modul, hiba=${cat.error}, talalat=${module?.id} tipus=${module?.type}")
                        if (module != null) Log.i(TAG, "letoltes: " + (CatalogClient.downloadModule(app, module) ?: "OK"))
                        Log.i(TAG, "canSay 14:30 = ${TalkingClock.canSay(app, 14, 30)}")
                    }
                    "wav" -> {
                        val f = File(app.filesDir, "ora-%02d-%02d.wav".format(h, m))
                        Log.i(TAG, "wav ${f.name}: ${TalkingClock.renderToWav(app, h, m, f)} ${f.length()}")
                    }
                    "szol" -> TalkingClock.play(app, h, m) { ok -> Log.i(TAG, "szol $h:$m -> $ok") }
                }
            } catch (e: Exception) {
                Log.w(TAG, "proba hiba", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object { private const val TAG = "SuperDL.OraProba" }
}
