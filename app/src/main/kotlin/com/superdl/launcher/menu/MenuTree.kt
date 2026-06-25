package com.superdl.launcher.menu

// Menüelem típusok
enum class MenuAction {
    SUBMENU,        // Almenübe lép
    CALL_LOG,       // Hívásnapló felolvasása
    CONTACTS,       // Névjegyek
    DIAL,           // Számtárcsázás
    SMS_READ,       // SMS olvasás
    SMS_WRITE,      // SMS írás diktálással
    EMAIL_WRITE,    // E-mail diktálása és küldése
    EMAIL_IMPORT,   // E-mail címek importálása
    EMAIL_ADD,      // E-mail cím hozzáadása
    EMAIL_LIST,     // Mentett e-mail címek
    EMAIL_SMTP_SETUP,  // E-mail küldő beállítása
    EMAIL_SMTP_READ,   // E-mail küldő felolvasása
    EMAIL_SMTP_CLEAR,  // E-mail küldő törlése
    SOS,            // S.O.S. hívás
    ALARM_SET,      // Új ébresztő diktálása
    ALARM_LIST,     // Ébresztők listája
    ALARM_DELETE,   // Ébresztő törlése
    ALARM_READ_NEXT,// Következő ébresztő
    TIME_NOW,       // Pontos idő
    CALENDAR_READ,  // Mai program felolvasása
    CALENDAR_TOMORROW, // Holnapi program
    CALENDAR_WEEK,  // Heti program áttekintése
    CALENDAR_ADD,   // Új program beállítása
    NOTE_LIST,      // Saját jegyzetek listája
    NOTE_CREATE,    // Új jegyzet diktálással
    NOTE_DELETE,    // Jegyzet törlése
    MUSIC,          // Zene a telefonon
    YOUTUBE,        // YouTube keresés + lejátszás
    NEWS_READ,      // Hírek felolvasása (RSS)
    WEB_SEARCH,     // Internet kereső – felolvasott találatok
    DAY_GREETING,   // Napi üdvözlés (dátum, névnap, időjárás)
    DAY_SUMMARY,    // Napi összefoglaló
    SHOPPING_LIST,  // Bevásárlólista
    EMAIL_IMAP_READ, // Bejövő e-mailek olvasása
    BT_ASSISTANT_TOGGLE, // Bluetooth gomb → asszisztens
    TRANSIT,        // Közeli megállók felolvasása
    TRANSIT_STOP,   // Megálló keresése felolvasással
    TRANSIT_ROUTE,  // Útvonal tömegközlekedéssel felolvasással
    NAV_WHERE,      // Hol vagyok?
    NAV_WALK,       // Gyalogos útvonal diktálással
    NAV_SEARCH,     // Cím vagy hely keresése
    GPS_RADAR,      // GPS Kitekintő – közeli POI radar
    GPS_RADAR_SAVED_LIST, // Mentett helyek listája
    GPS_RADAR_SAVE_OWN,   // Saját hely mentése (GPS folyamatban)
    GPS_RADAR_SAVE_POI,   // Aktuális POI mentése (GPS folyamatban)
    NOTIFICATIONS_READ, // Értesítések olvasása
    WEATHER,        // Időjárás most
    WEATHER_CITY,   // Időjárás város szerint
    FLASHLIGHT,     // Zseblámpa
    BATTERY,        // Akkumulátor állapot
    BATTERY_PATROL_TOGGLE, // Teljes őrség ki-be
    PATROL_BATTERY_TOGGLE, // Akkumulátor figyelés ki-be
    PATROL_CALL_ALERT_TOGGLE, // Hívás értesítés ki-be
    PATROL_SMS_ALERT_TOGGLE, // Üzenet értesítés ki-be
    PATROL_NOTIFICATION_ALERT_TOGGLE, // Egyéb értesítés ki-be
    PATROL_TIME_ANNOUNCE_TOGGLE, // Idő bemondás ki-be
    PATROL_TIME_INTERVAL_CYCLE, // Idő bemondás gyakorisága
    PATROL_NIGHT_MODE_TOGGLE, // Éjszakai csend ki-be
    PATROL_NIGHT_START_SET, // Éjszakai csend kezdete
    PATROL_NIGHT_END_SET, // Éjszakai csend vége
    PATROL_POWER_BUTTON_TIME_TOGGLE, // Bekapcsoló gomb idő bemondás
    WIFI_TOGGLE,    // WiFi kapcsoló
    BT_TOGGLE,      // Bluetooth kapcsoló
    VOICE_ASSISTANT, // Hangos asszisztens (helyi parancsok)
    ASSISTANT_DEFAULT_SETUP, // Alapértelmezett digitális asszisztens beállítása
    ASSISTANT_DEFAULT_STATUS, // Alapértelmezett asszisztens állapota
    DIALER_DEFAULT_SETUP, // Alapértelmezett telefon alkalmazás beállítása
    DIALER_DEFAULT_STATUS, // Alapértelmezett telefon alkalmazás állapota
    QR_SCAN,        // QR kód olvasó
    LIGHT_DETECTOR, // Fénydetektor kamerával
    COLOR_DETECTOR, // Színfelismerő kamerával
    ENV_SCANNER,    // Környezeti Kitekintő – kamera objektumfelismerés
    CURRENCY_RECOGNIZER, // Super DL Pénzfelismerő – offline forint bankjegy
    MEDICATION_READER,   // Gyógyszerdoboz olvasó – kamera OCR
    LABEL_READER,        // Címke olvasó – kamera OCR
    TEXT_READER,         // Szöveg olvasó – általános kamera OCR
    CONTINUOUS_OCR,      // Folyamatos OCR – automatikus szövegváltozás-felismerés
    SOUND_TRAINING, // Program hangjainak megismerése
    TRAINING_PLAYGROUND, // Tanuló mód – funkciók bemutatása
    BOOK_LIBRARY,   // Könyvtár – telefonon lévő könyvek
    BOOK_SEARCH,    // Könyv keresése felolvasással
    BOOK_RECENT,    // Nem rég olvasott könyvek
    BOOK_BOOKMARKS, // Mentett könyvjelzők
    BOOK_BOOKMARK_DELETE, // Könyvjelző törlése
    BOOK_RESUME,    // Utoljára olvasott könyv folytatása
    BOOK_FOLDER_SET,   // Egyéni könyvmappa beállítása
    BOOK_FOLDER_READ,  // Egyéni könyvmappa felolvasása
    BOOK_FOLDER_CLEAR, // Egyéni könyvmappák törlése
    CALCULATOR,     // Számológép diktálással
    VOLUME_UP,      // Hangerő fel
    VOLUME_DOWN,    // Hangerő le
    TTS_SPEED_UP,   // TTS gyorsabb
    TTS_SPEED_DOWN, // TTS lassabb
    TTS_ENGINE_SELECT, // TTS motor választása
    TTS_ENGINE_READ,   // Aktuális TTS motor felolvasása
    EXIT_LAUNCHER,  // Kilépés a launcherből
    SOS_SET_1,      // S.O.S. szám 1 beállítása
    SOS_SET_2,      // S.O.S. szám 2 beállítása
    SOS_SET_3,      // S.O.S. szám 3 beállítása
    SOS_SET_4,      // S.O.S. szám 4 beállítása
    SOS_READ_ALL,   // S.O.S. számok felolvasása
    ABOUT_APP,      // Az alkalmazásról
    ABOUT_DEVELOPER,// Fejlesztő
    CONTACT_EMAIL,  // Fejlesztői e-mail
    PRIVACY_POLICY, // Adatvédelmi tájékoztató
    TERMS_OF_USE,   // Felhasználási feltételek
    LEGAL_NOTICE,   // Jogi nyilatkozat
    EXTERNAL_APPS,  // Külső alkalmazások listája
    FAVORITE_APPS_LAUNCH, // Kedvenc alkalmazás indítása
    FAVORITE_APPS_ADD,    // Kedvenc alkalmazás hozzáadása
    FAVORITE_APPS_REMOVE, // Kedvenc alkalmazás törlése
    LOCK_PIN_TOGGLE, // PIN zárolás ki-be
    LOCK_PIN_SET,    // PIN kód beállítása / módosítása
    LOCK_PIN_STATUS, // PIN zárolás állapota
    TIMER_CREATE,    // Új időzítő mentése
    TIMER_LIST,      // Időzítők listája
    TIMER_START,     // Időzítő indítása
    TIMER_STOP,      // Aktív időzítő leállítása
    TIMER_EDIT,      // Időzítő módosítása
    TIMER_DELETE,    // Időzítő törlése
    DICTAPHONE_RECORD,   // Profi diktafon felvétel
    DICTAPHONE_SETTINGS, // Profi diktafon minőség beállítás
    DICTAPHONE_LIBRARY,  // Mentett felvételek
    FAVORITES_ADD,       // Kedvenc hozzáadása
    FAVORITES_CALL,      // Kedvenc hívása
    FAVORITES_DELETE,    // Kedvenc törlése
    SMS_DEFAULT_SETUP,   // Alapértelmezett üzenet app beállítása
    SMS_DEFAULT_STATUS,  // Alapértelmezett üzenet app állapota
    CONTACT_CREATE,      // Új névjegy létrehozása
    CALL_FILTER_BLOCK_PRIVATE_TOGGLE, // Rejtett számok tiltása
    MEDICATION_READ,     // Patika Őrangyal – emlékeztetők felolvasása
    MEDICATION_ADD,      // Patika Őrangyal – új gyógyszer rögzítése
    MEDICATION_DELETE,   // Patika Őrangyal – emlékeztető törlése
    ALERT_SOUND_CALENDAR,      // Program emlékeztető hang
    ALERT_SOUND_MEDICATION,    // Gyógyszer emlékeztető hang
    ALERT_SOUND_ALARM,         // Ébresztő hang
    ALERT_SOUND_SMS,           // SMS hang
    ALERT_SOUND_EMAIL,         // E-mail hang
    ALERT_SOUND_NOTIFICATION,  // Egyéb értesítés hang
    ALERT_SOUND_VOLUME_CYCLE,  // Csengőhang hangerő
    ALERT_SILENT_MODE_TOGGLE,  // Néma mód ki-be
    LOCATION_TRAIN,            // Helyszín profil tanítása
    LOCATION_WATCH_START,      // Helyszín figyelő – mentett profilok
    LOCATION_WATCH_TEXT,       // Helyszín figyelő – szabad szöveg
    LOCATION_PROFILE_LIST,     // Mentett helyszín profilok listája
    LOCATION_WATCH_STOP,       // Helyszín figyelő leállítása
    FACE_CAMERA,               // Arc kamera – hátlapi
    FACE_CAMERA_SELFIE,        // Arc kamera – szelfi
    FACE_CAMERA_QUALITY,       // Kamera minőség beállítás
    GPS_ROUTE_RECORD,          // GPS útvonal rögzítése
    GPS_ROUTE_STOP,            // GPS útvonal rögzítés / útmutatás leállítása
    GPS_ROUTE_LIST,            // Mentett GPS útvonalak listája
    GPS_ROUTE_GUIDE,           // GPS útvonal útmutatás
    GPS_ROUTE_DELETE,          // GPS útvonal törlése
}

data class MenuItem(
    val id: String,
    val label: String,          // Magyar TTS szöveg
    val action: MenuAction,
    val children: List<MenuItem> = emptyList()
)

object MenuTree {

    val root: List<MenuItem> = listOf(

        MenuItem("calls", "Telefon és Hívások", MenuAction.SUBMENU, listOf(
            MenuItem("call_contacts", "Névjegyből hívás", MenuAction.CONTACTS),
            MenuItem("call_log", "Hívásnapló felolvasása", MenuAction.CALL_LOG),
            MenuItem("call_dial", "Szám tárcsázása", MenuAction.DIAL),
            MenuItem("fav_add", "Kedvenc hozzáadása", MenuAction.FAVORITES_ADD),
            MenuItem("fav_call", "Kedvenc hívása", MenuAction.FAVORITES_CALL),
            MenuItem("fav_delete", "Kedvenc törlése", MenuAction.FAVORITES_DELETE),
            MenuItem("contact_create", "Új névjegy létrehozása", MenuAction.CONTACT_CREATE),
            MenuItem("call_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("sms", "Üzenetek és E-mail", MenuAction.SUBMENU, listOf(
            MenuItem("sms_read", "Üzenetek olvasása", MenuAction.SMS_READ),
            MenuItem("sms_write", "Üzenet diktálása és küldése", MenuAction.SMS_WRITE),
            MenuItem("sms_default_setup", "Alapértelmezett üzenet app beállítása", MenuAction.SMS_DEFAULT_SETUP),
            MenuItem("sms_default_status", "Üzenet app állapota", MenuAction.SMS_DEFAULT_STATUS),
            MenuItem("email_write", "E-mail diktálása és küldése", MenuAction.EMAIL_WRITE),
            MenuItem("email_import", "E-mail címek importálása", MenuAction.EMAIL_IMPORT),
            MenuItem("email_add", "E-mail cím hozzáadása", MenuAction.EMAIL_ADD),
            MenuItem("email_list", "Mentett e-mail címek", MenuAction.EMAIL_LIST),
            MenuItem("email_smtp_setup", "E-mail küldő beállítása", MenuAction.EMAIL_SMTP_SETUP),
            MenuItem("email_smtp_read", "E-mail küldő felolvasása", MenuAction.EMAIL_SMTP_READ),
            MenuItem("email_smtp_clear", "E-mail küldő törlése", MenuAction.EMAIL_SMTP_CLEAR),
            MenuItem("email_imap_read", "E-mailek olvasása", MenuAction.EMAIL_IMAP_READ),
            MenuItem("sms_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("sos", "S.O.S. Vészjelzés", MenuAction.SOS),

        MenuItem("time", "Idő és Szervezés", MenuAction.SUBMENU, listOf(
            MenuItem("time_now", "Pontos idő felolvasása", MenuAction.TIME_NOW),
            MenuItem("alarm_set", "Új ébresztő beállítása", MenuAction.ALARM_SET),
            MenuItem("alarm_next", "Következő ébresztő", MenuAction.ALARM_READ_NEXT),
            MenuItem("alarm_list", "Ébresztők listája", MenuAction.ALARM_LIST),
            MenuItem("alarm_delete", "Ébresztő törlése", MenuAction.ALARM_DELETE),
            MenuItem("calendar_read", "Mai program felolvasása", MenuAction.CALENDAR_READ),
            MenuItem("calendar_tomorrow", "Holnapi program felolvasása", MenuAction.CALENDAR_TOMORROW),
            MenuItem("calendar_week", "Heti program áttekintése", MenuAction.CALENDAR_WEEK),
            MenuItem("calendar_add", "Új program beállítása", MenuAction.CALENDAR_ADD),
            MenuItem("note_list", "Saját jegyzetek", MenuAction.NOTE_LIST),
            MenuItem("note_create", "Új jegyzet", MenuAction.NOTE_CREATE),
            MenuItem("note_delete", "Jegyzet törlése", MenuAction.NOTE_DELETE),
            MenuItem("timer_create", "Új időzítő mentése", MenuAction.TIMER_CREATE),
            MenuItem("timer_list", "Időzítők listája", MenuAction.TIMER_LIST),
            MenuItem("timer_start", "Időzítő indítása", MenuAction.TIMER_START),
            MenuItem("timer_stop", "Aktív időzítő leállítása", MenuAction.TIMER_STOP),
            MenuItem("timer_edit", "Időzítő módosítása", MenuAction.TIMER_EDIT),
            MenuItem("timer_delete", "Időzítő törlése", MenuAction.TIMER_DELETE),
            MenuItem("time_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("media", "Zene és Média", MenuAction.SUBMENU, listOf(
            MenuItem("music", "Zene a telefonon", MenuAction.MUSIC),
            MenuItem("youtube", "YouTube hangos keresés", MenuAction.YOUTUBE),
            MenuItem("media_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("books", "Könyvek", MenuAction.SUBMENU, listOf(
            MenuItem("book_library", "Könyvtár", MenuAction.BOOK_LIBRARY),
            MenuItem("book_search", "Könyv keresése felolvasással", MenuAction.BOOK_SEARCH),
            MenuItem("book_recent", "Nem rég olvasott könyvek", MenuAction.BOOK_RECENT),
            MenuItem("book_bookmarks", "Könyvjelzők", MenuAction.BOOK_BOOKMARKS),
            MenuItem("book_bookmark_delete", "Könyvjelző törlése", MenuAction.BOOK_BOOKMARK_DELETE),
            MenuItem("book_resume", "Olvasás folytatása", MenuAction.BOOK_RESUME),
            MenuItem("book_folder_set", "Könyvmappa beállítása", MenuAction.BOOK_FOLDER_SET),
            MenuItem("book_folder_read", "Könyvmappa felolvasása", MenuAction.BOOK_FOLDER_READ),
            MenuItem("book_folder_clear", "Könyvmappa törlése", MenuAction.BOOK_FOLDER_CLEAR),
            MenuItem("books_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("info", "Információ", MenuAction.SUBMENU, listOf(
            MenuItem("day_greeting", "Napi üdvözlés", MenuAction.DAY_GREETING),
            MenuItem("day_summary", "Napi összefoglaló", MenuAction.DAY_SUMMARY),
            MenuItem("weather", "Időjárás most", MenuAction.WEATHER),
            MenuItem("weather_city", "Időjárás város szerint", MenuAction.WEATHER_CITY),
            MenuItem("news_read", "Hírek felolvasása", MenuAction.NEWS_READ),
            MenuItem("web_search", "Internet kereső", MenuAction.WEB_SEARCH),
            MenuItem("battery", "Akkumulátor állapot", MenuAction.BATTERY),
            MenuItem("info_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("community", "Közösség", MenuAction.SUBMENU, listOf(
            MenuItem("nav_where", "Hol vagyok?", MenuAction.NAV_WHERE),
            MenuItem("nav_walk", "Gyalogos útvonal diktálással", MenuAction.NAV_WALK),
            MenuItem("nav_search", "Cím vagy hely keresése", MenuAction.NAV_SEARCH),
            MenuItem("gps_radar", "G P S Kitekintő", MenuAction.SUBMENU, listOf(
                MenuItem("gps_radar_nearby", "Közeli helyek", MenuAction.GPS_RADAR),
                MenuItem("gps_radar_custom", "Egyéni helyek", MenuAction.GPS_RADAR_SAVED_LIST),
                MenuItem("gps_radar_back", "Vissza a közösséghez", MenuAction.SUBMENU)
            )),
            MenuItem("env_scanner", "Környezeti Kitekintő kamerával", MenuAction.ENV_SCANNER),
            MenuItem("transit_nearby", "Közeli megállók felolvasása", MenuAction.TRANSIT),
            MenuItem("transit_stop", "Megálló keresése felolvasással", MenuAction.TRANSIT_STOP),
            MenuItem("transit_route", "Útvonal tömegközlekedéssel felolvasással", MenuAction.TRANSIT_ROUTE),
            MenuItem("gps_route", "G P S útvonal", MenuAction.SUBMENU, listOf(
                MenuItem("gps_route_record", "Útvonal rögzítése", MenuAction.GPS_ROUTE_RECORD),
                MenuItem("gps_route_stop", "Rögzítés vagy útmutatás leállítása", MenuAction.GPS_ROUTE_STOP),
                MenuItem("gps_route_list", "Mentett útvonalak", MenuAction.GPS_ROUTE_LIST),
                MenuItem("gps_route_guide", "Útvonal útmutatás", MenuAction.GPS_ROUTE_GUIDE),
                MenuItem("gps_route_delete", "Útvonal törlése", MenuAction.GPS_ROUTE_DELETE),
                MenuItem("gps_route_back", "Vissza a közösséghez", MenuAction.SUBMENU)
            )),
            MenuItem("community_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("tools", "Eszközök", MenuAction.SUBMENU, listOf(
            MenuItem("flashlight", "Zseblámpa", MenuAction.FLASHLIGHT),
            MenuItem("qr", "Q R kód olvasó", MenuAction.QR_SCAN),
            MenuItem("light_detector", "Fénydetektor kamerával", MenuAction.LIGHT_DETECTOR),
            MenuItem("color_detector", "Színfelismerő kamerával", MenuAction.COLOR_DETECTOR),
            MenuItem("env_scanner_tools", "Környezeti Kitekintő kamerával", MenuAction.ENV_SCANNER),
            MenuItem("currency_recognizer", "Super DL Pénzfelismerő", MenuAction.CURRENCY_RECOGNIZER),
            MenuItem("medication_reader", "Gyógyszerdoboz olvasó", MenuAction.MEDICATION_READER),
            MenuItem("label_reader", "Címke olvasó", MenuAction.LABEL_READER),
            MenuItem("text_reader", "Szöveg olvasó", MenuAction.TEXT_READER),
            MenuItem("continuous_ocr", "Folyamatos szövegolvasó", MenuAction.CONTINUOUS_OCR),
            MenuItem("calculator", "Számológép", MenuAction.CALCULATOR),
            MenuItem("shopping_list", "Bevásárlólista", MenuAction.SHOPPING_LIST),
            MenuItem("dictaphone", "Profi Diktafon", MenuAction.SUBMENU, listOf(
                MenuItem("dict_record", "Felvétel indítása", MenuAction.DICTAPHONE_RECORD),
                MenuItem("dict_settings", "Minőség és formátum beállítása", MenuAction.DICTAPHONE_SETTINGS),
                MenuItem("dict_library", "Mentett felvételek", MenuAction.DICTAPHONE_LIBRARY),
                MenuItem("dict_back", "Vissza az eszközökhöz", MenuAction.SUBMENU)
            )),
            MenuItem("pharmacy_guardian", "Patika Őrangyal", MenuAction.SUBMENU, listOf(
                MenuItem("med_read", "Aktuális emlékeztetők felolvasása", MenuAction.MEDICATION_READ),
                MenuItem("med_add", "Új gyógyszer rögzítése", MenuAction.MEDICATION_ADD),
                MenuItem("med_delete", "Emlékeztető törlése", MenuAction.MEDICATION_DELETE),
                MenuItem("pharmacy_back", "Vissza az eszközökhöz", MenuAction.SUBMENU)
            )),
            MenuItem("location_watch", "Helyszín felismerő", MenuAction.SUBMENU, listOf(
                MenuItem("location_train", "Helyszín tanítása", MenuAction.LOCATION_TRAIN),
                MenuItem("location_watch_start", "Figyelő indítása", MenuAction.LOCATION_WATCH_START),
                MenuItem("location_watch_text", "Figyelő szabad szöveggel", MenuAction.LOCATION_WATCH_TEXT),
                MenuItem("location_profile_list", "Mentett helyszínek", MenuAction.LOCATION_PROFILE_LIST),
                MenuItem("location_watch_stop", "Figyelő leállítása", MenuAction.LOCATION_WATCH_STOP),
                MenuItem("location_watch_back", "Vissza az eszközökhöz", MenuAction.SUBMENU)
            )),
            MenuItem("face_camera", "Kamera és szelfi", MenuAction.FACE_CAMERA),
            MenuItem("face_camera_quality", "Kamera minőség", MenuAction.FACE_CAMERA_QUALITY),
            MenuItem("tools_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("assistant", "Asszisztens", MenuAction.SUBMENU, listOf(
            MenuItem("voice_assistant", "Hangos asszisztens", MenuAction.VOICE_ASSISTANT),
            MenuItem("assistant_default_setup", "Alapértelmezett asszisztens beállítása", MenuAction.ASSISTANT_DEFAULT_SETUP),
            MenuItem("assistant_default_status", "Asszisztens állapota", MenuAction.ASSISTANT_DEFAULT_STATUS),
            MenuItem("bt_assistant", "Bluetooth gomb asszisztens", MenuAction.BT_ASSISTANT_TOGGLE),
            MenuItem("assistant_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("external_apps", "Minden alkalmazás", MenuAction.EXTERNAL_APPS),

        MenuItem("favorite_apps", "Kedvenc alkalmazások", MenuAction.SUBMENU, listOf(
            MenuItem("fav_apps_launch", "Kedvenc alkalmazás indítása", MenuAction.FAVORITE_APPS_LAUNCH),
            MenuItem("fav_apps_add", "Kedvenc alkalmazás hozzáadása", MenuAction.FAVORITE_APPS_ADD),
            MenuItem("fav_apps_remove", "Kedvenc alkalmazás törlése", MenuAction.FAVORITE_APPS_REMOVE),
            MenuItem("fav_apps_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("settings", "Beállítások", MenuAction.SUBMENU, listOf(
            MenuItem("sos_settings", "S.O.S. paraméterek", MenuAction.SUBMENU, listOf(
                MenuItem("sos_set_1", "S.O.S. szám 1 beállítása", MenuAction.SOS_SET_1),
                MenuItem("sos_set_2", "S.O.S. szám 2 beállítása", MenuAction.SOS_SET_2),
                MenuItem("sos_set_3", "S.O.S. szám 3 beállítása", MenuAction.SOS_SET_3),
                MenuItem("sos_set_4", "S.O.S. szám 4 beállítása", MenuAction.SOS_SET_4),
                MenuItem("sos_read", "S.O.S. számok felolvasása", MenuAction.SOS_READ_ALL),
                MenuItem("sos_settings_back", "Vissza a beállításokhoz", MenuAction.SUBMENU)
            )),
            MenuItem("patrol_master", "Teljes őrség ki-be", MenuAction.BATTERY_PATROL_TOGGLE),
            MenuItem("security", "Biztonság", MenuAction.SUBMENU, listOf(
                MenuItem("lock_toggle", "PIN zárolás ki-be", MenuAction.LOCK_PIN_TOGGLE),
                MenuItem("lock_set", "PIN kód beállítása", MenuAction.LOCK_PIN_SET),
                MenuItem("lock_status", "PIN zárolás állapota", MenuAction.LOCK_PIN_STATUS),
                MenuItem("call_filter_private", "Rejtett számok tiltása", MenuAction.CALL_FILTER_BLOCK_PRIVATE_TOGGLE),
                MenuItem("dialer_default_setup", "Alapértelmezett telefon beállítása", MenuAction.DIALER_DEFAULT_SETUP),
                MenuItem("dialer_default_status", "Telefon alkalmazás állapota", MenuAction.DIALER_DEFAULT_STATUS),
                MenuItem("security_back", "Vissza a beállításokhoz", MenuAction.SUBMENU)
            )),
            MenuItem("sound_settings", "Hangok", MenuAction.SUBMENU, listOf(
                MenuItem("sound_volume", "Csengőhang hangerő", MenuAction.ALERT_SOUND_VOLUME_CYCLE),
                MenuItem("sound_silent", "Néma mód ki-be", MenuAction.ALERT_SILENT_MODE_TOGGLE),
                MenuItem("sound_calendar", "Program emlékeztető hang", MenuAction.ALERT_SOUND_CALENDAR),
                MenuItem("sound_medication", "Gyógyszer emlékeztető hang", MenuAction.ALERT_SOUND_MEDICATION),
                MenuItem("sound_alarm", "Ébresztő hang", MenuAction.ALERT_SOUND_ALARM),
                MenuItem("sound_sms", "SMS hang", MenuAction.ALERT_SOUND_SMS),
                MenuItem("sound_email", "E-mail hang", MenuAction.ALERT_SOUND_EMAIL),
                MenuItem("sound_notification", "Egyéb értesítés hang", MenuAction.ALERT_SOUND_NOTIFICATION),
                MenuItem("sound_settings_back", "Vissza a beállításokhoz", MenuAction.SUBMENU)
            )),
            MenuItem("patrol_settings", "Őrség beállítások", MenuAction.SUBMENU, listOf(
                MenuItem("patrol_battery", "Akkumulátor figyelés ki-be", MenuAction.PATROL_BATTERY_TOGGLE),
                MenuItem("patrol_call", "Hívás értesítés ki-be", MenuAction.PATROL_CALL_ALERT_TOGGLE),
                MenuItem("patrol_sms", "Üzenet értesítés ki-be", MenuAction.PATROL_SMS_ALERT_TOGGLE),
                MenuItem("patrol_notification", "Egyéb értesítés ki-be", MenuAction.PATROL_NOTIFICATION_ALERT_TOGGLE),
                MenuItem("patrol_time", "Idő bemondás ki-be", MenuAction.PATROL_TIME_ANNOUNCE_TOGGLE),
                MenuItem("patrol_interval", "Idő bemondás gyakorisága", MenuAction.PATROL_TIME_INTERVAL_CYCLE),
                MenuItem("patrol_night", "Éjszakai csend ki-be", MenuAction.PATROL_NIGHT_MODE_TOGGLE),
                MenuItem("patrol_night_start", "Éjszakai csend kezdete", MenuAction.PATROL_NIGHT_START_SET),
                MenuItem("patrol_night_end", "Éjszakai csend vége", MenuAction.PATROL_NIGHT_END_SET),
                MenuItem("patrol_power", "Bekapcsoló gomb idő bemondás", MenuAction.PATROL_POWER_BUTTON_TIME_TOGGLE),
                MenuItem("patrol_settings_back", "Vissza a beállításokhoz", MenuAction.SUBMENU)
            )),
            MenuItem("vol_up", "Hangerő növelése", MenuAction.VOLUME_UP),
            MenuItem("vol_down", "Hangerő csökkentése", MenuAction.VOLUME_DOWN),
            MenuItem("tts_up", "Beszéd gyorsítása", MenuAction.TTS_SPEED_UP),
            MenuItem("tts_down", "Beszéd lassítása", MenuAction.TTS_SPEED_DOWN),
            MenuItem("tts_engine", "T T S hang választása", MenuAction.TTS_ENGINE_SELECT),
            MenuItem("tts_engine_read", "Aktuális T T S hang felolvasása", MenuAction.TTS_ENGINE_READ),
            MenuItem("notifications", "Értesítések olvasása", MenuAction.NOTIFICATIONS_READ),
            MenuItem("wifi", "WiFi be- és kikapcsolás", MenuAction.WIFI_TOGGLE),
            MenuItem("bt", "Bluetooth be- és kikapcsolás", MenuAction.BT_TOGGLE),
            MenuItem("launcher_switch", "Kezdőképernyő váltás", MenuAction.SUBMENU, listOf(
                MenuItem("exit", "Kilépés a Super DL launcherből", MenuAction.EXIT_LAUNCHER),
                MenuItem("launcher_switch_back", "Vissza a beállításokhoz", MenuAction.SUBMENU)
            )),
            MenuItem("settings_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("about", "Névjegy és jogi információk", MenuAction.SUBMENU, listOf(
            MenuItem("sound_training", "Program hangjainak megismerése", MenuAction.SOUND_TRAINING),
            MenuItem("training_playground", "Tanuló mód, funkciók bemutatása", MenuAction.TRAINING_PLAYGROUND),
            MenuItem("about_app", "Az alkalmazásról", MenuAction.ABOUT_APP),
            MenuItem("about_dev", "Fejlesztő: Kőrösmezey Dávid", MenuAction.ABOUT_DEVELOPER),
            MenuItem("contact_email", "Kapcsolat e-mailben", MenuAction.CONTACT_EMAIL),
            MenuItem("privacy", "Adatvédelmi tájékoztató", MenuAction.PRIVACY_POLICY),
            MenuItem("terms", "Felhasználási feltételek", MenuAction.TERMS_OF_USE),
            MenuItem("legal", "Jogi nyilatkozat", MenuAction.LEGAL_NOTICE),
            MenuItem("about_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        ))
    )

    fun allItems(): List<MenuItem> {
        fun flatten(items: List<MenuItem>): List<MenuItem> =
            items.flatMap { item -> listOf(item) + flatten(item.children) }
        return flatten(root)
    }
}