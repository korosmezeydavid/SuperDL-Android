package com.superdl.launcher.screenrecord

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * AZ EGYETLEN DOLOG, AMIT NEM LEHET MEGKERÜLNI.
 *
 * A képernyő tükrözéséhez a rendszer SAJÁT párbeszéde kell — ezt nem
 * lehet programból jóváhagyni, és jól is van így: enélkül bármelyik
 * alkalmazás némán figyelhetné a képernyőt.
 *
 * VAK FELHASZNÁLÓNAK EZ NEHÉZ PILLANAT: a rendszer párbeszéde nem a
 * SuperDL felülete, ott a söprések nem működnek. Ezért a program a
 * megnyitás ELŐTT elmondja, mi fog történni és mit kell keresni. A
 * párbeszédben a jóváhagyó gomb általában "Indítás most" vagy "Kezdés".
 *
 * Ez az ablak szándékosan üres és átlátszó: csak átveszi a választ, és
 * azonnal eltűnik.
 */
class ScreenRecordPermissionActivity : Activity() {

    companion object {
        private const val REQ = 4411

        fun start(context: android.content.Context) {
            val intent = Intent(context, ScreenRecordPermissionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            finish()
            return
        }
        try {
            startActivityForResult(manager.createScreenCaptureIntent(), REQ)
        } catch (_: Exception) {
            finish()
        }
    }

    @Deprecated("A rendszer párbeszédéhez ez a régi út a megbízható.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ) {
            finish()
            return
        }
        if (resultCode == RESULT_OK && data != null) {
            val intent = ScreenRecordService.startIntent(this, resultCode, data)
            try {
                startForegroundService(intent)
            } catch (e: Exception) {
                try {
                    startService(intent)
                } catch (e2: Exception) {
                    say("A felvételt tartó szolgáltatás nem indult el.")
                }
            }
        } else {
            // A VISSZAUTASÍTÁST IS KI KELL MONDANI.
            //
            // Eddig ez NÉMÁN végződött: ha a felhasználó nem találta meg a
            // rendszer párbeszédében a jóváhagyó gombot, vagy elsöpörte,
            // semmi nem történt — és ő abban a hiszemben maradt, hogy fut a
            // felvétel. Vakon ez a leggyakoribb kimenet, nem a kivétel.
            say(
                "A telefon nem adta meg a képernyőt. A rendszer kérdésénél az " +
                    "Indítás most gombot kell jóváhagyni."
            )
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun say(text: String) {
        try {
            com.superdl.launcher.patrol.PatrolAnnouncer.announce(
                applicationContext, text, critical = true
            )
        } catch (_: Throwable) {
        }
    }
}
