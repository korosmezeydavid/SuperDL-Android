package com.superdl.launcher.screen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * A SÖTÉT MÓD VÉSZKIJÁRATA.
 *
 * MIÉRT KELL (Tamás Bálint, 2026-09-18):
 *
 * A sötét mód vakon tökéletes: a képernyő fekete, senki nem lát bele, a
 * telefon alatta ugyanúgy kezelhető. LÁTÓ embernek viszont csapda. Egy
 * tesztelőnk így írta le: „teljesen használhatatlanná teszi a telefont, és
 * mivel kínszenvedés árán sikerült a régi telóról levakarnom, az
 * elsődleges telefonomon nem merem elindítani."
 *
 * Igaza volt. Kikapcsolni eddig csak ugyanabból a menüpontból lehetett,
 * amit épp nem lát. Vakon ez rendben van — de az appot bárki letöltheti,
 * és aki kíváncsiságból bekapcsolja, gyakorlatilag megbénítja a telefonját.
 *
 * MIÉRT ÉRTESÍTÉS, ÉS MIÉRT NEM GOMBKOMBINÁCIÓ:
 *
 * Kézenfekvő lett volna a két hangerőgomb — CSAKHOGY az a TalkBack saját
 * gyorsbillentyűje, és mi magunk kérjük a felhasználókat, hogy azt
 * állítsák be előre (lásd „ha elakadsz"). Két dolgot ugyanarra a
 * mozdulatra tenni pont a menekülőutat rontaná el.
 *
 * Az értesítési sáv viszont RENDSZERABLAK: az Android a program fölé
 * rajzolt rétegek FÖLÉ teszi. Vagyis a függöny alatt is lehúzható, és
 * látszik rajta a kikapcsoló gomb. Nem kell hozzá új engedély, nem ütközik
 * semmilyen gesztussal, és a vak felhasználót sem zavarja: neki egy néma,
 * csendes értesítés, amit soha nem kell megnéznie.
 */
object ScreenCurtainExit {

    private const val CHANNEL_ID = "SOTET_MOD_CHANNEL"
    private const val NOTIFICATION_ID = 7710
    const val ACTION_TURN_OFF = "com.superdl.launcher.action.SOTET_MOD_KI"

    fun show(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        createChannel(manager)

        val off = Intent(ACTION_TURN_OFF).setPackage(app.packageName)
        val pending = PendingIntent.getBroadcast(
            app,
            0,
            off,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setContentTitle("Sötét mód bekapcsolva")
            .setContentText("A képernyő fekete. Koppints a kikapcsoláshoz.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Sötét mód kikapcsolása", pending)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun hide(context: Context) {
        val manager = context.applicationContext
            .getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    /**
     * INDULÁSKOR TAKARÍTUNK.
     *
     * A függönyt a futó folyamat tartja: ha a rendszer megölte a programot,
     * a fekete réteg eltűnt — az értesítés viszont ottmaradhatott. Egy
     * kikapcsoló gomb olyasmihez, ami nincs is bekapcsolva, csak zavar.
     */
    fun cleanUpIfNotActive(context: Context) {
        if (!ScreenCurtain.isActive()) hide(context)
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sötét mód",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Kikapcsoló gomb, amíg a sötét mód be van kapcsolva"
            setShowBadge(false)
        }
        runCatching { manager.createNotificationChannel(channel) }
    }
}

/** A kikapcsoló gomb fogadója. */
class ScreenCurtainOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScreenCurtainExit.ACTION_TURN_OFF) return
        ScreenCurtain.hide(context)
        ScreenCurtainExit.hide(context)
        // A vak felhasználó is hallja, ha véletlenül ide nyúlt.
        runCatching {
            com.superdl.launcher.patrol.PatrolAnnouncer.announce(
                context,
                "Sötét mód kikapcsolva.",
                withBeep = false,
                critical = false
            )
        }
    }
}
