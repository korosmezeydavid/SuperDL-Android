package com.superdl.launcher.settings

import android.content.Context
import com.superdl.launcher.menu.MenuAction

/**
 * KÉT ÁLLÁSÚ KAPCSOLÓK — HELYI MENÜVEL, NEM AZONNALI ÁTBILLENÉSSEL.
 *
 * A HIBA, AMIT EZ JAVÍT (Alph, 2026-09-17):
 *
 * Eddig egyetlen jobbra söprés AZONNAL átbillentett minden kapcsolót. Aki
 * nem látja a képernyőt, annak ez azt jelentette, hogy a menüpontra lépés
 * és a véletlen söprés között nincs semmi — a beállítás már meg is
 * változott, mire meghallotta, mi volt. A hívásszűrőnél ezt már
 * kijavítottuk (ott a körbeforgatás valódi kárt okozott); most ugyanaz az
 * elv jön minden kapcsolóra.
 *
 * MOSTANTÓL: a jobbra söprés MENÜT NYIT. A menü AZON AZ ÁLLÁSON NYÍLIK,
 * amelyik a MOSTANI ELLENTÉTE — vagyis elsőre azt hallod, mi FOG történni
 * („Kikapcsolás"), és egy újabb jobbra söpréssel hagyod jóvá. Két gesztus,
 * nem három. Fel-le söpréssel átváltasz a másik lehetőségre („Marad
 * bekapcsolva"), ami semmit nem változtat.
 *
 * Amíg jobbra nem söpörsz a végén, SEMMI nem lép életbe.
 */
object ToggleChoice {

    /**
     * Egy kapcsoló leírása.
     *
     * @param title       amit a program a menü nyitásakor kimond
     * @param isOn        a JELENLEGI állás lekérdezése
     * @param turnOnLabel a bekapcsolás választható neve
     * @param turnOffLabel a kikapcsolás választható neve
     * @param onState     a bekapcsolt állapot neve („jelenleg ...")
     * @param offState    a kikapcsolt állapot neve
     */
    data class Spec(
        val title: String,
        val isOn: (Context) -> Boolean,
        val turnOnLabel: String = "Bekapcsolás",
        val turnOffLabel: String = "Kikapcsolás",
        val onState: String = "bekapcsolva",
        val offState: String = "kikapcsolva"
    )

    private val specs: Map<MenuAction, Spec> = mapOf(

        // ── Névjegyzék ──────────────────────────────────────────────────
        MenuAction.CONTACT_UI_LETTER_TOGGLE to Spec(
            "Betűindex",
            { com.superdl.launcher.contacts.ContactPrefs.isLetterIndexEnabled(it) }
        ),
        MenuAction.CONTACT_UI_FULL_NUMBER to Spec(
            "Teljes telefonszám",
            { com.superdl.launcher.contacts.ContactPrefs.isFullNumberEnabled(it) }
        ),

        // ── Otthon figyelés ─────────────────────────────────────────────
        MenuAction.HOME_WATCH_TOGGLE to Spec(
            "Otthon figyelés",
            { com.superdl.launcher.home.HomeWatchSettings.isEnabled(it) }
        ),
        // EZ NEM KI-BE, HANEM ÉLES VAGY PRÓBA. Ha „bekapcsolás"-t
        // mondanánk rá, a felhasználó nem tudná, mibe kapcsol bele:
        // az éles mód tényleg SMS-t küld.
        MenuAction.HOME_WATCH_MODE_TOGGLE to Spec(
            "Otthon figyelés módja",
            { com.superdl.launcher.home.HomeWatchSettings.isProbe(it) },
            turnOnLabel = "Próba mód",
            turnOffLabel = "Éles mód",
            onState = "próba mód",
            offState = "éles mód"
        ),
        MenuAction.HOME_WATCH_COUNTDOWN_TOGGLE to Spec(
            "Otthon figyelés visszaszámlálás",
            { com.superdl.launcher.home.HomeWatchSettings.isCountdownEnabled(it) }
        ),
        MenuAction.HOME_WATCH_GREETING_TOGGLE to Spec(
            "Köszönés hazaérkezéskor",
            { com.superdl.launcher.home.HomeWatchSettings.isGreetingEnabled(it) }
        ),

        // ── S.O.S. ──────────────────────────────────────────────────────
        MenuAction.SOS_COUNTDOWN_TOGGLE to Spec(
            "S.O.S. visszaszámlálás",
            { com.superdl.launcher.sos.SosPreferences.isCountdownEnabled(it) }
        ),

        // ── Menü és képernyő ────────────────────────────────────────────
        MenuAction.SIMPLE_MODE_TOGGLE to Spec(
            "Egyszerű mód",
            { com.superdl.launcher.menu.MenuPrefs.isSimpleMode(it) }
        ),
        MenuAction.SCREEN_CURTAIN_TOGGLE to Spec(
            "Sötét mód",
            { com.superdl.launcher.screen.ScreenCurtain.isActive() }
        ),
        MenuAction.SCREEN_READER_TOGGLE to Spec(
            "Képernyőolvasó",
            { com.superdl.launcher.screenreader.ScreenReaderPrefs.isEnabled(it) }
        ),
        MenuAction.SCREEN_READER_SHARE_TOGGLE to Spec(
            "Címke megosztás",
            { com.superdl.launcher.screenreader.LabelSharing.isEnabled(it) }
        ),

        // ── Őrjárat ─────────────────────────────────────────────────────
        MenuAction.BATTERY_PATROL_TOGGLE to Spec(
            "Teljes őrség",
            { com.superdl.launcher.battery.BatteryPatrolManager.isEnabled(it) }
        ),
        MenuAction.PATROL_BATTERY_TOGGLE to Spec(
            "Akkumulátor figyelés",
            { com.superdl.launcher.patrol.PatrolStore.isBatteryEnabled(it) }
        ),
        MenuAction.PATROL_CALL_ALERT_TOGGLE to Spec(
            "Hívás értesítés",
            { com.superdl.launcher.patrol.PatrolStore.isCallAlertEnabled(it) }
        ),
        MenuAction.PATROL_SMS_ALERT_TOGGLE to Spec(
            "Üzenet értesítés",
            { com.superdl.launcher.patrol.PatrolStore.isSmsAlertEnabled(it) }
        ),
        MenuAction.PATROL_NOTIFICATION_ALERT_TOGGLE to Spec(
            "Egyéb értesítés",
            { com.superdl.launcher.patrol.PatrolStore.isNotificationAlertEnabled(it) }
        ),
        MenuAction.PATROL_TIME_ANNOUNCE_TOGGLE to Spec(
            "Idő bemondás",
            { com.superdl.launcher.patrol.PatrolStore.isTimeAnnounceEnabled(it) }
        ),
        MenuAction.PATROL_NIGHT_MODE_TOGGLE to Spec(
            "Éjszakai csend",
            { com.superdl.launcher.patrol.PatrolStore.isNightModeEnabled(it) }
        ),
        MenuAction.PATROL_POWER_BUTTON_TIME_TOGGLE to Spec(
            "Bekapcsoló gomb idő bemondás",
            { com.superdl.launcher.patrol.PatrolStore.isPowerButtonTimeEnabled(it) }
        ),

        // ── Beszédtéma és köszönések ────────────────────────────────────
        MenuAction.VOICE_THEME_TOGGLE to Spec(
            "Beszédtéma",
            { com.superdl.launcher.voicetheme.VoiceThemeStore.isEnabled(it) }
        ),
        MenuAction.VOICE_THEME_MORNING_TOGGLE to Spec(
            "Jó reggelt köszönés",
            { com.superdl.launcher.voicetheme.VoiceThemeStore.isMorningEnabled(it) }
        ),
        MenuAction.VOICE_THEME_MORNING_UNLOCK_TOGGLE to Spec(
            "Jó reggelt csak feloldáskor",
            { com.superdl.launcher.voicetheme.VoiceThemeStore.isMorningOnUnlock(it) }
        ),
        MenuAction.VOICE_THEME_NIGHT_TOGGLE to Spec(
            "Jó éjszakát köszönés",
            { com.superdl.launcher.voicetheme.VoiceThemeStore.isNightEnabled(it) }
        ),

        // ── Hangok ──────────────────────────────────────────────────────
        MenuAction.ALERT_SILENT_MODE_TOGGLE to Spec(
            "Néma mód",
            { com.superdl.launcher.feedback.AlertSoundSettingsStore.isSilentMode(it) }
        ),

        // ── Kapcsolatok ─────────────────────────────────────────────────
        MenuAction.WIFI_TOGGLE to Spec(
            "WiFi",
            { com.superdl.launcher.system.ConnectivityHelper.isWifiEnabled(it) }
        ),
        MenuAction.HOTSPOT_TOGGLE to Spec(
            "Hotspot",
            { com.superdl.launcher.system.ConnectivityHelper.isHotspotEnabled(it) }
        ),
        MenuAction.BT_TOGGLE to Spec(
            "Bluetooth",
            { com.superdl.launcher.system.ConnectivityHelper.isBluetoothEnabled(it) }
        ),

        // ── Segéd és biztonság ──────────────────────────────────────────
        MenuAction.ELENA_WAKE_LISTEN_TOGGLE to Spec(
            "Elena figyelés",
            { com.superdl.launcher.assistant.ElenaWakeStore.isListenEnabled(it) }
        ),
        MenuAction.BT_ASSISTANT_TOGGLE to Spec(
            "Fülhallgató gomb",
            { com.superdl.launcher.assistant.BluetoothAssistantStore.isEnabled(it) }
        ),
        MenuAction.LOCK_PIN_TOGGLE to Spec(
            "PIN zárolás",
            { com.superdl.launcher.security.LockPinStore.isEnabled(it) }
        ),
        MenuAction.KEYGUARD_PIN_ASSIST_TOGGLE to Spec(
            "Rendszer PIN segéd",
            { com.superdl.launcher.lock.keyguard.KeyguardPinSettings.isFeatureEnabled(it) }
        ),

        // ── Diktafon ────────────────────────────────────────────────────
        MenuAction.DICTAPHONE_RAW_TOGGLE to Spec(
            "Teljesen nyers felvétel",
            { com.superdl.launcher.dictaphone.DictaphoneSettingsStore.load(it).rawCapture }
        ),

        // ── Zseblámpa ───────────────────────────────────────────────────
        MenuAction.FLASHLIGHT to Spec(
            "Zseblámpa",
            { com.superdl.launcher.tools.FlashlightState.isOn }
        )
    )

    fun specFor(action: MenuAction): Spec? = specs[action]

    /**
     * A két választható sor. Az ELSŐ mindig a változtatás — azt hallja meg
     * elsőre. A második a maradás, hogy legyen hova „nem"-et mondani anélkül,
     * hogy ki kellene lépni.
     */
    fun labels(spec: Spec, currentlyOn: Boolean): List<String> = if (currentlyOn) {
        listOf(spec.turnOffLabel, "Marad: ${spec.onState}")
    } else {
        listOf(spec.turnOnLabel, "Marad: ${spec.offState}")
    }

    fun stateWord(spec: Spec, currentlyOn: Boolean): String =
        if (currentlyOn) spec.onState else spec.offState
}
