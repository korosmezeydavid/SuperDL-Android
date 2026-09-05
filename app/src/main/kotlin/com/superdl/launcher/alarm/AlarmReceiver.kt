package com.superdl.launcher.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Az ébresztő időpontjában elindítja a valódi ébresztőhangot (AlarmService)
 * és a riasztási képernyőt (AlarmAlertActivity), majd gondoskodik a
 * következő alkalom beütemezéséről (ismétlődő ébresztőnél).
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_TONE_URI = "tone_uri"
        const val EXTRA_SNOOZE_ENABLED = "snooze_enabled"
        const val EXTRA_IS_SNOOZE = "is_snooze"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val label = intent.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() } ?: "Ébresztő"
        val toneUri = intent.getStringExtra(EXTRA_TONE_URI)
        val snoozeEnabled = intent.getBooleanExtra(EXTRA_SNOOZE_ENABLED, true)
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

        // 0) KIHAGYÁS: ha erre az ébresztőre van érvényben kihagyás, most NEM
        // szólalunk meg — csak "elhasználunk" egyet a számlálóból, és
        // beütemezzük a következő alkalmat. Így nem kell kézzel ki-, majd
        // visszakapcsolni az ébresztőt (pl. ha pénteken és hétfőn nem kell
        // dolgozni). A számláló magától elfogy, és utána újra megszólal.
        if (!isSnooze && alarmId >= 0) {
            val entry = AlarmStore.getAll(appContext).firstOrNull { it.id == alarmId }
            if (entry != null && entry.skipRemaining > 0) {
                val left = AlarmStore.consumeSkip(appContext, alarmId)
                android.util.Log.i(
                    "SDL_ALARM",
                    "Ebreszto KIHAGYVA (id=$alarmId), hatralevo kihagyas: $left"
                )
                // SZÁNDÉKOSAN NÉMA: a kihagyás lényege, hogy NE történjen
                // semmi. Egy bemondás pont azt rontaná el, amiért a
                // felhasználó bekapcsolta.
                // A kihagyások ÁLLAPOTA a menüből kérdezhető le
                // ("Kihagyott ébresztők"), hogy vakon is ellenőrizhető legyen.
                AlarmScheduler.scheduleNextOccurrence(
                    appContext,
                    entry.copy(skipRemaining = left)
                )
                return
            }
        }

        // 1) Valódi, fokozódó ébresztőhang indítása (foreground service).
        val soundIntent = Intent(appContext, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_LABEL, label)
            putExtra(AlarmService.EXTRA_TONE_URI, toneUri)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(soundIntent)
            } else {
                appContext.startService(soundIntent)
            }
        } catch (_: Exception) {
        }

        // 2) Riasztási képernyő (bemondja a nevet, szundi/leállítás gesztusok).
        val alertIntent = Intent(appContext, AlarmAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmAlertActivity.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmAlertActivity.EXTRA_LABEL, label)
            putExtra(AlarmAlertActivity.EXTRA_TONE_URI, toneUri)
            putExtra(AlarmAlertActivity.EXTRA_SNOOZE_ENABLED, snoozeEnabled)
        }
        try {
            appContext.startActivity(alertIntent)
        } catch (_: Exception) {
        }

        // 3) A következő alkalom beütemezése (szundi-riasztásnál nem kell).
        if (!isSnooze && alarmId >= 0) {
            AlarmStore.getAll(appContext).firstOrNull { it.id == alarmId }?.let { entry ->
                AlarmScheduler.scheduleNextOccurrence(appContext, entry)
            }
        }
    }
}

/**
 * INDÍTÁS UTÁNI HELYREÁLLÍTÁS — ébresztők, gyógyszer, naptár, időzítő.
 *
 * MIÉRT KAP MINDEN LÉPÉS KÜLÖN VÉDŐHÁLÓT:
 *
 * Ez a nyolc lépés korábban egyetlen blokkban futott. Egy `onReceive`-ben
 * eldobott kivétel nem hiba, hanem AZONNALI PROGRAMHALÁL — és a halál
 * pillanatában a SORBAN HÁTRALÉVŐ LÉPÉSEK IS ELMARADNAK.
 *
 * Élesben ez történt: a névjegy-szinkron `SecurityException`-t dobott
 * (nem volt még meg a névjegy-engedély), a program meghalt bekapcsoláskor,
 * és ezzel EGYÜTT ELMARADT AZ ÉBRESZTŐK ÚJRAÜTEMEZÉSE IS. Vagyis egy
 * névjegy-engedély hiánya el tudta némítani a másnap reggeli ébresztőt.
 * Vakon, egy munkanap előtt ez nem apróság.
 * (Hibajelentés: 2026-09-01, Ulefone Armor 24, Android 13.)
 *
 * Mostantól minden lépés a saját hibájába bukik bele, a többi fut tovább.
 * A sorrend is számít: ami a felhasználó szempontjából a legfontosabb —
 * az ébresztő és a gyógyszer-emlékeztető — az megy elöl.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                step("ebresztok") { AlarmScheduler.rescheduleAll(context) }
                step("gyogyszer") {
                    com.superdl.launcher.medication.MedicationScheduler.rescheduleAll(context)
                }
                step("naptar") {
                    com.superdl.launcher.calendar.CalendarReminderScheduler
                        .rescheduleUpcoming(context)
                }
                step("idozito") {
                    com.superdl.launcher.timer.TimerManager.resumeIfNeeded(context)
                }
                step("akku-orseg") {
                    com.superdl.launcher.battery.BatteryPatrolManager.start(context)
                }
                step("keszulek-hangok") {
                    com.superdl.launcher.feedback.DeviceStateSoundManager.start(context)
                }
                step("nevjegy-utemezes") {
                    com.superdl.launcher.contacts.ContactSyncScheduler.reschedule(context)
                }
                step("nevjegy-szinkron") {
                    com.superdl.launcher.contacts.ContactSyncHelper.syncIfNeeded(context)
                }
                // A köszönések ébresztői újraindításkor elvesznek — enélkül a
                // „jó reggelt" egyszer szólna, aztán soha többé.
                step("beszedtema-koszonesek") {
                    com.superdl.launcher.voicetheme.GreetingScheduler.rescheduleAll(context)
                }
            }
        }
    }

    /**
     * `Throwable`, nem `Exception`: az `OutOfMemoryError` sem `Exception`,
     * és bekapcsoláskor, amikor egyszerre indul minden alkalmazás, épp az a
     * legvalószínűbb pillanat, amikor elfogy a memória.
     */
    private inline fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            android.util.Log.w(
                "BootReceiver",
                "Indulasi lepes '$name' hibara futott: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
    }
}
