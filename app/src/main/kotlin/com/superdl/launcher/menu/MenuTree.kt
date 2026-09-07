package com.superdl.launcher.menu

// Menüelem típusok
enum class MenuAction {
    SUBMENU,        // Almenübe lép
    CALL_LOG,       // Hívásnapló felolvasása
    CONTACTS,       // Névjegyből hívás diktálással
    CONTACT_BOOK,   // Névjegyzék böngészése
    CONTACT_SYNC,   // Névjegyek szinkronizálása
    CONTACT_UI_STATUS,        // Névjegyzék beállítások felolvasása
    CONTACT_UI_LETTER_TOGGLE, // Betűindex ki és be
    CONTACT_UI_FULL_NUMBER,   // Teljes telefonszám ki és be
    CONTACT_EXPORT,           // Névjegyek mentése fájlba (vCard)
    CONTACT_IMPORT,           // Névjegyek visszatöltése fájlból
    DIAL,           // Számtárcsázás
    SMS_READ,       // SMS olvasás
    SMS_SENT_READ,  // Kimenő SMS olvasás
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
    ASSISTANT_CONTINUOUS,  // Elena: folyamatos beszélgetés (parancs után tovább hallgat)
    KEYBOARD_MATRIX_CELL,   // Mátrix: gombok távolsága (4 fokozat)
    KEYBOARD_MATRIX_SPEED,  // Mátrix: pörgetés sebessége (4 fokozat)
    KEYBOARD_MATRIX_HELP,   // Mátrix: mozdulatok felolvasása
    HELP,            // Alkalmazás-súgó (az azonosító: help::<almenü-id>, lásd HelpTexts)
    HELP_INDEX,      // Névjegy: az összes súgó egy listában
    SUPPORT,         // Névjegy: Fejlesztés támogatása (Revolut, bankszámla)
    CREDITS,         // Névjegy: Köszönet és együttműködők
    MODULE_LAUNCH,   // Telepített bővítmény indítása (az azonosítóban a csomagnév)
    MODULES_BROWSE,  // Telepített program-modulok listája és indítása
    FOCUS_LIST,      // Időzített fókusz: a szabályok felsorolása és kezelése
    FOCUS_ADD_NIGHT, // Gyors: éjszakai nyugalom (22:00-06:00, minden nap)
    FOCUS_ADD_SUNDAY,// Gyors: vasárnapi pihenő (egész nap)
    FOCUS_ADD_CUSTOM,// Egyéni fókusz létrehozása lépésről lépésre
    FOCUS_STATUS,    // Mi van most érvényben?
    CATALOG_BROWSE,      // Katalógus: elérhető modulok böngészése és letöltése
    CATALOG_INSTALLED,   // Katalógus: a letöltött modulok listája
    CATALOG_UPDATE,      // Katalógus: frissítés keresése az alkalmazáshoz
    KEYBOARD_TEXT_BANK,     // Mátrix: a szövegtár tartalmának felolvasása
    KEYBOARD_PICKER,        // Billentyűzet választása (rendszer választó)
    KEYBOARD_SETTINGS,      // Billentyűzetek engedélyezése a rendszerben
    SCREEN_CURTAIN_TOGGLE,  // Sötét mód: a képernyő teljes elfüggönyözése
    SCREEN_READER_TOGGLE,   // Képernyőolvasó ki/be (csak külső appokban)
    SCREEN_READER_SETUP,    // Képernyőolvasó engedélyezése a rendszerben
    SCREEN_READER_STATUS,   // Képernyőolvasó állapota
    SCREEN_READER_HELP,     // Képernyőolvasó mozdulatainak felolvasása
    SCREEN_READER_COUNTER,  // Képernyőolvasó: pozíció bemondása ki/be
    SCREEN_READER_PHONETIC, // Képernyőolvasó: betűző ábécé ki/be
    SCREEN_READER_EXPLORE_HOLD, // Felderítés: indítási idő 1, 2 vagy 3 másodperc
    SCREEN_READER_EXPLORE,  // Képernyőolvasó: felderítés érintéssel ki/be
    SCREEN_READER_NOTIF,    // Képernyőolvasó: érkező értesítések bemondása
    SCREEN_READER_AUTOREAD, // Képernyőolvasó: automatikus felolvasás új képernyőn
    SCREEN_READER_LABELS,   // Saját elnevezések kezelése: lista, átnevezés, törlés
    SCREEN_READER_MAP_STYLE, // Hangtérkép: melyik hangnyelv szóljon
    SCREEN_READER_MAP_TEMPO, // Hangtérkép: nyugodt, normál vagy gyors
    SCREEN_READER_STEREO_TEST, // Bal-jobb próba: hallatszik-e egyáltalán az irány
    TASK_ROUTES,            // Műveletsorok: lista, indítás, törlés
    SCREEN_READER_SHARE_TOGGLE, // Elnevezések megosztása ki/be (alapból KI)
    SCREEN_READER_SHARE_SEND,   // Elnevezések beküldése most
    SCREEN_READER_PANIC,    // Képernyőolvasó AZONNALI leállítása (biztonsági retesz)
    ALARM_DELETE,   // Ébresztő törlése
    ALARM_SKIP,     // Ébresztések kihagyása (N következő alkalom kihagyása)
    ALARM_SKIP_STATUS, // Kihagyott ébresztők: mi van kihagyás alatt, mennyi van hátra
    ALARM_READ_NEXT,// Következő ébresztő
    TIME_NOW,       // Pontos idő
    CALENDAR_READ,  // Mai program felolvasása
    CALENDAR_TOMORROW, // Holnapi program
    CALENDAR_WEEK,  // Heti program áttekintése
    CALENDAR_ADD,   // Új program beállítása
    CALENDAR_CHOOSE_TARGET, // Melyik naptárba kerüljenek a programok
    CALENDAR_STATUS,        // Naptárak állapota (mi szinkronizál, hova írunk)
    CALENDAR_EDIT_PICK,    // Program kiválasztása szerkesztésre (napi listából)
    CALENDAR_DELETE_PICK,  // Program kiválasztása törlésre (napi listából)
    NOTE_LIST,      // Saját jegyzetek listája
    NOTE_CREATE,    // Új jegyzet diktálással
    NOTE_DELETE,    // Jegyzet törlése
    MUSIC,          // Zene a telefonon
    MUSIC_CLOUD,    // Zenék a felhőben (választott mappából, almappákkal)
    MUSIC_CLOUD_FOLDER, // A felhős zenemappa kiválasztása vagy cseréje
    MUSIC_RESUME_LAST,  // Zene: az utoljára hallgatott szám folytatása a mentett pozíciótól
    USB_FILE_TRANSFER, // USB fájlátvitel be/ki (a rendszer USB-képernyőjén)
    FILE_MANAGER,      // Fájlkezelő
    WIFI_PORTAL,       // WiFi fájlportál be/ki (feltöltés gépről böngészővel)
    SHARE_PICK,        // Fájl megosztása: a fájlkezelő nyílik, ott a Megosztás pont
    SHARE_HISTORY,     // Megosztási előzmények: mit hova töltöttünk fel, meddig él
    SHARE_RECEIVE,     // Fájl fogadása kóddal (gépről gépre, magic-wormhole)
    PODCAST_TOP,        // Podcast: népszerű műsorok (ország szerint)
    PODCAST_SEARCH,     // Podcast: keresés
    PODCAST_SUBSCRIPTIONS, // Podcast: feliratkozásaim
    PODCAST_DOWNLOADS,  // Podcast: letöltött adások
    PODCAST_COUNTRY,    // Podcast: ország választása
    PODCAST_OPML_IMPORT, // Podcast: feliratkozások importálása OPML fájlból
    PODCAST_OPML_EXPORT, // Podcast: feliratkozások exportálása OPML fájlba
    MUSIC_PLAY_MODE,   // Zene: lejátszási mód váltása
    MUSIC_SEEK_STEP,   // Zene: tekerés egység váltása
    MUSIC_EQ_PROFILE,  // Zene: hangszínprofil (EQ) váltása
    MUSIC_SPEECH_ENABLED,  // Zene: FŐKAPCSOLÓ — beszéljen-e egyáltalán lejátszás közben
    MUSIC_SPEAK_SKIP,  // Zene: beszéljen-e számváltásnál (ki/be)
    MUSIC_SPEAK_STOP,  // Zene: beszéljen-e leállításnál (ki/be)
    MUSIC_SPEAK_SEEK,  // Zene: beszéljen-e tekerésnél (ki/be)
    YOUTUBE,        // YouTube keresés + lejátszás
    YOUTUBE_SAVER_MODE,      // YouTube: takarékos mód (csak hang, háttérben is szól)
    YOUTUBE_STOP_BACKGROUND, // YouTube: a háttérben szóló hang leállítása
    RADIO_HUNGARIAN,   // Rádió: magyar állomások betöltése és lejátszás
    RADIO_FAVORITES,   // Rádió: mentett kedvenc állomások
    RADIO_SEARCH,      // Rádió: állomás keresése név szerint (hangos)
    RADIO_FAV_DELETE,  // Rádió: megunt kedvenc eltávolítása
    RADIO_ADD_CLIPBOARD, // Rádió: saját stream vagy m3u cím felvétele a vágólapról
    RADIO_RECORDINGS,  // Rádió: elmentett felvételek listája
    RADIO_SCHEDULE,    // Rádió: időzített felvételek kezelése
    NEWS_READ,      // Hírek felolvasása (RSS)
    WEB_SEARCH,     // Internet kereső – felolvasott találatok
    DAY_GREETING,   // Napi üdvözlés (dátum, névnap, időjárás)
    DAY_SUMMARY,    // Napi összefoglaló
    STATUS_REPORT,  // Gyors helyzetjelentés (offline: idő, akku, térerő, hívások, üzenetek, ébresztő, naptár)
    SHOPPING_LIST,  // Bevásárlólista
    SHOPPING_NEW_LIST,  // Bevásárlólista: új lista létrehozása (diktálva)
    EMAIL_IMAP_READ, // Bejövő e-mailek olvasása
    EMAIL_DIAGNOSTICS, // E-mail kapcsolat lépésenkénti naplózása fájlba (hibakereséshez)
    BT_ASSISTANT_TOGGLE, // Bluetooth gomb → asszisztens
    TRANSIT,        // Közeli megállók felolvasása
    TRANSIT_STOP,   // Megálló keresése felolvasással
    TRANSIT_FAVORITES, // Kedvenc megállók (Holabusz-szerű)
    TRANSIT_ROUTE,  // Útvonal tömegközlekedéssel felolvasással
    TRAIN_NEARBY,        // Közeli vasútállomások indulási időkkel
    TRAIN_STATION_SEARCH, // Állomás keresése felolvasással
    TRAIN_FAVORITES,     // Kedvenc állomások indulási időkkel
    NAV_WHERE,      // Hol vagyok?
    NAV_WALK,       // Gyalogos útvonal diktálással
    NAV_SEARCH,     // Cím vagy hely keresése
    GPS_RADAR,      // GPS Kitekintő – közeli POI radar
    COMPASS_SCAN,   // Hang-iránytű - élő tájékozódás forgatással
    COMPASS_SCAN_STOP, // Hang-iránytű leállítása
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
    HOTSPOT_TOGGLE, // Hotspot kapcsoló
    BT_TOGGLE,      // Bluetooth kapcsoló
    VOICE_ASSISTANT, // Elena – hangos asszisztens (helyi parancsok)
    ELENA_WAKE_LISTEN_TOGGLE, // Elena figyelő ki-be
    ELENA_WAKE_LISTEN_ON, // Elena figyelő bekapcsolása
    ELENA_WAKE_LISTEN_OFF, // Elena figyelő kikapcsolása
    ELENA_WAKE_TRAIN, // Saját felébresztő mondat tanítása
    ELENA_WAKE_CUSTOM_LIST, // Mentett felébresztő mondatok felolvasása
    ASSISTANT_DEFAULT_SETUP, // Alapértelmezett digitális asszisztens beállítása
    ASSISTANT_DEFAULT_STATUS, // Alapértelmezett asszisztens állapota
    DIALER_DEFAULT_SETUP, // Alapértelmezett telefon alkalmazás beállítása
    DIALER_DEFAULT_STATUS, // Alapértelmezett telefon alkalmazás állapota
    QR_SCAN,        // QR kód olvasó
    LIGHT_DETECTOR, // Fénydetektor kamerával
    COLOR_DETECTOR, // Színfelismerő kamerával
    ENV_SCANNER,    // Környezeti Kitekintő – kamera objektumfelismerés
    ENV_FIND,       // Keresd meg — hangos ravezetes egy targyra (OOrion-jellegu)
    ENV_SNAPSHOT,   // Mi van előttem? - egy-gombos jelenetleírás
    CURRENCY_RECOGNIZER, // Super DL Pénzfelismerő – offline forint bankjegy
    MEDICATION_READER,   // Gyógyszerdoboz olvasó – kamera OCR
    LABEL_READER,        // Címke olvasó – kamera OCR
    TEXT_READER,         // Szöveg olvasó – általános kamera OCR
    CONTINUOUS_OCR,      // Folyamatos OCR – automatikus szövegváltozás-felismerés
    SOUND_TRAINING, // Program hangjainak megismerése
    SETUP_WIZARD,   // Beállítás varázsló – végigvezet a hiányzó engedélyeken
    SETUP_STATUS,   // Beállítás állapot felolvasása
    SETUP_RESTART,  // Beállítás varázsló elölről (a későbbre hagyottakat is)
    TOUCH_CALIBRATION,        // A kéz megtanulása – a Braille-bevitel alapja
    BRAILLE_PRACTICE,         // Braille próbapad – írás tét nélkül
    BRAILLE_LAYOUT,           // Cella vagy zongora elrendezés
    BRAILLE_ORIENTATION,      // Álló vagy fekvő tartás
    BRAILLE_STATUS,           // A jelenlegi Braille-beállítás felolvasása
    BRAILLE_MODE,             // Írásmód: hat ujj, két menet vagy sín
    BRAILLE_HELP,             // A teljes Braille-súgó felolvasása
    GESTURE_ORIENTATION,      // Felület elforgatása – a söprések jelentésének átrendezése
    GESTURE_ORIENTATION_HELP, // A jelenlegi kezelés szabályainak felolvasása
    SOS_COUNTDOWN_TOGGLE, // S.O.S. visszaszámlálás ki és be
    RADIO_SCHEDULE_ADD,   // Rádió: időzített felvétel hozzáadása
    RADIO_SCHEDULE_LIST,  // Rádió: időzített felvételek listája
    DIAGNOSTICS,    // Diagnosztika – mi nem működik és miért
    BATTERY_OPT_REQUEST, // Korlátlan háttérfutás kérése (akku-optimalizálás alól)
    AUTOSTART_SETUP,     // Gyártói automatikus indítás (Xiaomi, Huawei, Oppo...)
    TRAINING_PLAYGROUND, // Tanuló mód – funkciók bemutatása
    BOOK_LIBRARY,   // Könyvtár – telefonon lévő könyvek
    BOOK_SEARCH,    // Könyv keresése felolvasással
    BOOK_RECENT,    // Nem rég olvasott könyvek
    BOOK_BOOKMARKS, // Mentett könyvjelzők
    BOOK_BOOKMARK_DELETE, // Könyvjelző törlése
    BOOK_DELETE,    // Könyv végleges törlése a telefonról
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
    SOS_READ_ALL,     // S.O.S. számok felolvasása
    SOS_PHRASE_TRAIN, // S.O.S. hívómondat betanítása (hangos vészjelzés)
    SOS_PHRASE_LIST,  // A betanított S.O.S. hívómondatok listája és törlése
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
    KEYGUARD_PIN_ASSIST_TOGGLE, // Rendszer PIN segéd ki-be
    KEYGUARD_PIN_ASSIST_SETUP,  // Rendszer PIN segéd engedélyezése
    KEYGUARD_PIN_ASSIST_STATUS,
    KEYGUARD_VOICE_TEST,   // A beépített zárképernyő-hangok meghallgatása
    TIMER_CREATE,    // Új időzítő mentése
    TIMER_LIST,      // Időzítők listája
    TIMER_START,     // Időzítő indítása
    TIMER_STOP,      // Aktív időzítő leállítása
    TIMER_EDIT,      // Időzítő módosítása
    TIMER_DELETE,    // Időzítő törlése
    DICTAPHONE_RECORD,   // Profi diktafon felvétel
    DICTAPHONE_SETTINGS, // Profi diktafon minőség beállítás
    DICTAPHONE_RAW_TOGGLE, // Profi diktafon: teljesen nyers felvétel ki/be
    DICTAPHONE_CAPABILITIES, // Profi diktafon: mit tud a készülék mikrofonja
    DICTAPHONE_LIBRARY,  // Mentett felvételek
    FAVORITES_ADD,       // Kedvenc hozzáadása
    FAVORITES_CALL,      // Kedvenc hívása
    FAVORITES_DELETE,    // Kedvenc törlése
    SMS_DEFAULT_SETUP,   // Alapértelmezett üzenet app beállítása
    SMS_DEFAULT_STATUS,  // Alapértelmezett üzenet app állapota
    CONTACT_CREATE,      // Új névjegy létrehozása
    CALL_FILTER_BLOCK_PRIVATE_TOGGLE, // Régi – kompatibilitás
    CALL_FILTER_MODE_CYCLE,          // Hívás szűrő mód váltása (4 szint)
    CALL_FILTER_MODE_STATUS,         // Hívás szűrő állapota
    MEDICATION_READ,     // Patika Őrangyal – emlékeztetők felolvasása
    MEDICATION_ADD,      // Patika Őrangyal – új gyógyszer rögzítése
    MEDICATION_SEARCH,   // Gyógyszerkereső - tájékoztató lekérése névből
    MEDICATION_DELETE,   // Patika Őrangyal – emlékeztető törlése
    ALERT_SOUND_CALENDAR,      // Program emlékeztető hang
    ALERT_SOUND_MEDICATION,    // Gyógyszer emlékeztető hang
    ALERT_SOUND_ALARM,         // Ébresztő hang
    ALERT_SOUND_SMS,           // SMS hang
    ALERT_SOUND_EMAIL,         // E-mail hang
    ALERT_SOUND_NOTIFICATION,  // Egyéb értesítés hang
    ALERT_SOUND_VOLUME_CYCLE,  // Csengőhang hangerő
    ALERT_SILENT_MODE_TOGGLE,  // Néma mód ki-be
    SOUND_THEME_SELECT,        // Söpörj hangtéma választás
    // BESZÉDTÉMA — felvett hanggal megszólaló események. NEM azonos a
    // söprés-hangtémával: az síp, ez mondat.
    VOICE_THEME_TOGGLE,        // Beszédtéma ki-be
    VOICE_THEME_PICK,          // Melyik letöltött téma legyen aktív
    VOICE_THEME_TEST,          // Hangok kipróbálása
    VOICE_THEME_STATUS,        // Mihez van már felvett hang
    VOICE_THEME_EVENT_BATTERY_LOW,
    VOICE_THEME_EVENT_BATTERY_FULL,
    VOICE_THEME_EVENT_CHARGER,
    VOICE_THEME_MORNING_TOGGLE,
    VOICE_THEME_MORNING_TIME,
    VOICE_THEME_MORNING_UNLOCK_TOGGLE,
    VOICE_THEME_NIGHT_TOGGLE,
    VOICE_THEME_NIGHT_TIME,
    VOICE_THEME_DAILY_CAP,
    VOICE_THEME_RECORD,        // Saját beszédtéma felvétele a telefonon
    VOICE_THEME_SHARE,         // Csomagolás és megosztás
    VOICE_THEME_SUBMIT,        // Beküldöm a közösbe — feltöltés + kész levél
    VOICE_THEME_INSTALL_FILE,  // Kapott téma-fájl telepítése
    VOICE_THEME_CATALOG,       // Beszédtémák a katalógusban, előhallgatással
    BATTERY_FIRST_ALERT_CYCLE, // Az első akku-figyelmeztetés szintje
    RINGTONE_SELECT,           // Gyári csengőhang választása a híváshoz
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
    CARD_TRAIN,                // Kártya hozzáadása (eleje + hátulja)
    CARD_RECOGNIZE,            // Kártya felismerése kamerával
    CARD_LIST,                 // Mentett kártyák listája
    CARD_DELETE,               // Kártya törlése
    NEWS_FEED_MANAGE,          // Hírforrások kezelése
    NEWS_FEED_IMPORT_OPML,     // Hírforrások OPML import
    HEARING_AID,               // Hallás erősítő – valós idejű hang
    GAME_UNO,                  // UNO kártyajáték
    FAILURE_SELF_TEST,  // Hibatűrés próbája: szándékos hiba a pajzs ellenőrzésére
    VERBOSITY_STATUS,   // Részletesség: jelenlegi beállítás felolvasása
    VERBOSITY_HINTS,    // Részletesség: mozdulat-útmutatók ki/be
    VERBOSITY_APP_INFO, // Részletesség: külső alkalmazás tájékoztató ki/be
    VERBOSITY_COUNTS,   // Részletesség: darabszámok ki/be
    VERBOSITY_PUNCT,    // Részletesség: diktálási tipp ki/be
    VERBOSITY_KEYBOARD, // Részletesség: billentyűzet-tájékoztató ki/be
    KEYBOARD_PASSWORD,  // Billentyűzet: jelszó-karakterek kimondása ki/be
    TTS_ROLE_ENGINE,    // Külön beszédmotor a program saját üzeneteihez
    TTS_VOICE_ROLES,    // Hangszerepek: más hang a program üzeneteihez
    TTS_AUTO_LANGUAGE,  // Automatikus nyelvváltás idegen szövegnél
    DICT_STATUS,        // Kiejtési szótár: állapot
    DICT_LIST,          // Kiejtési szótár: saját szabályok felolvasása
    DICT_ADD,           // Kiejtési szótár: új szabály diktálással
    DICT_BUILTIN,       // Kiejtési szótár: beépített szabályok ki/be
    DICT_CLEAR,         // Kiejtési szótár: saját szabályok törlése
    BUG_REPORT,         // Hibajelentés készítése és elküldése
    TTS_CHANNEL,        // Beszéd hangcsatornája: média vagy kisegítő
    TTS_REPAIR,         // Beszéd javítása (ha elnémult a program)
    SAFE_MODE_STATUS,   // Biztonságos mód: állapot és ki/be kapcsolás
    ACCESSIBILITY_STATUS, // Kisegítő szolgáltatások állapota (PIN segéd, olvasó)
    BOOK_TTS_OWN,     // Könyvolvasó: saját hang ki/be
    BOOK_TTS_ENGINE,  // Könyvolvasó: beszédmotor választása
    BOOK_TTS_RATE,    // Könyvolvasó: beszédsebesség
    BOOK_TTS_PITCH,   // Könyvolvasó: hangmagasság
    BOOK_TTS_STATUS,  // Könyvolvasó: hangbeállítás felolvasása
    SR_TRAIN_FREE,   // Képernyőolvasó: szabad gyakorlás (tét nélkül)
    SR_TRAIN_LESSONS,// Képernyőolvasó: Elena tanárnő órái
    SR_TRAIN_EXAM,   // Képernyőolvasó: vizsga osztályzattal
    SR_TRAIN_STOP,   // Képernyőolvasó: tanulás befejezése
    SR_TRAIN_VOICE_STATUS, // Elena saját hangjai: mi van felvéve
    YOUTUBE_FAVORITES,  // YouTube: kedvenc videóim
    YOUTUBE_CHANNELS,   // YouTube: követett csatornák
    YOUTUBE_RESUME,     // YouTube: az utoljára nézett videó folytatása
    HIDDEN_GESTURES_HELP, // Rejtett mozdulatok felolvasása (pl. teljes mód)
    SIMPLE_MODE_TOGGLE,        // Egyszerű mód: minden funkció mutatása / elrejtése
    GAME_HANGMAN,              // Akasztófa játék (a katalógus szókészleteivel)
    STEPS_TODAY,               // Lépésszámláló: mai összesítő
    STEPS_LIVE,                // Lépésszámláló: élő mérés sebességgel
    STEPS_SETTINGS_GOAL,       // Lépésszámláló: napi cél
    STEPS_SETTINGS_BODY,       // Lépésszámláló: testmagasság és testsúly
    GAME_QUIZ,                 // Kvíz játék (a katalógusból letöltött kérdéssorok)
    GAME_BLACKJACK,            // Blackjack
    GAME_POKER,                // Póker – ötlapos húzás
    GAME_SLOT,                 // Félkarú rabló – nyerőgép
    GAME_MILLE_BORNES,         // Mille Bornes – ezer mérföld
    CHAT_OPEN,                 // Csevejcenter – valós idejű csevegő (Ably-szoba)
}

data class MenuItem(
    val id: String,
    val label: String,          // Magyar TTS szöveg
    val action: MenuAction,
    val children: List<MenuItem> = emptyList()
)

object MenuTree {

    /**
     * EGYSZERŰ MÓD — az első benyomás számít.
     *
     * Egy frissen megvakult ember első nap NEM háromszáz menüpontot akar látni.
     * Egyszerű módban csak a legfontosabb dolgok látszanak; egyetlen ponttal
     * ("Minden funkció mutatása") kinyitható a teljes készlet.
     *
     * FONTOS: semmi nem VÉSZ EL, csak nem látszik. Aki keresi, egy mozdulattal
     * megtalálja — ez sokkal barátságosabb, mint mindent modulokba szórni,
     * amit a felhasználónak külön kellene letöltenie.
     */
    private val SIMPLE_MODE_IDS = setOf(
        "screen_curtain", "calls", "sms", "sos", "time", "media",
        "community", "tools", "assistant", "settings", "about"
    )

    /**
     * A FÁJLÁTVITEL ALMENÜJE — két helyen ugyanaz.
     *
     * A Média alatt marad (ott szokták meg), és az Eszközök alatt is
     * megjelenik (oda való). Egyetlen lista, hogy soha ne csússzon szét a kettő.
     *
     * ⚠️ A [suffix] NEM dísz: az azonosító vezérli a súgót (`help::<id>`), a
     * szűrőket és az egyszerű módot. Két egyforma azonosítótól a súgó-beszúró
     * két „Súgó" pontot tenne ugyanabba a menübe.
     */
    private fun transferChildren(suffix: String): List<MenuItem> = listOf(
        MenuItem("share_pick$suffix", "Fájl vagy mappa megosztása", MenuAction.SHARE_PICK),
        MenuItem("share_receive$suffix", "Fájl fogadása kóddal", MenuAction.SHARE_RECEIVE),
        MenuItem("share_history$suffix", "Megosztási előzmények", MenuAction.SHARE_HISTORY),
        MenuItem("usb_transfer$suffix", "Fájlátvitel géppel", MenuAction.USB_FILE_TRANSFER),
        MenuItem("wifi_portal$suffix", "WiFi fájlportál be és ki", MenuAction.WIFI_PORTAL),
        MenuItem("transfer_back$suffix", "Vissza", MenuAction.SUBMENU)
    )

    /**
     * A menü a beállításoknak megfelelően.
     *
     * Kétféle szűrés fut rajta:
     *  1. EGYSZERŰ MÓD: csak a legfontosabb csoportok látszanak
     *  2. KIADÁSI KAPCSOLÓK: ami még nem érett a nyilvános tesztre, kimarad
     *     (jelenleg: a pénzfelismerő — a fejlesztő döntése alapján)
     */
    fun buildRoot(context: android.content.Context): List<MenuItem> {
        var items = root

        // 1. KIADÁSI KAPCSOLÓ: a pénzfelismerő csak akkor látszik, ha a
        //    fordításkor engedélyezve volt. A téves címlet valódi kárt okoz,
        //    ezért ez a funkció külön engedélyhez kötött.
        if (!com.superdl.launcher.BuildConfig.FEATURE_MONEY_RECOGNIZER) {
            items = items.map { group ->
                if (group.children.isEmpty()) group
                else group.copy(children = removeDeep(group.children, "currency_recognizer"))
            }
        }

        // 1/B. A HIBATŰRÉS PRÓBÁJA csak a fejlesztői és teszt változatban
        //      látszik. Az éles kiadásban fölöslegesen zsúfolná a menüt —
        //      egy átlagos felhasználó soha nem nézné meg.
        if (!com.superdl.launcher.BuildConfig.FEATURE_FAILURE_TEST) {
            items = items.map { group ->
                if (group.children.isEmpty()) group
                else group.copy(children = removeDeep(group.children, "failure_test"))
            }
        }

        // 2. EGYSZERŰ MÓD
        //
        // A teljes készletet NEM menüpont nyitja ki, hanem egy REJTETT MOZDULAT
        // (öt gyors jobbra söprés): egy menüpontra ugyanis véletlenül is rá
        // lehet lépni — például zsebben —, és a felhasználó azt sem értené,
        // honnan lett hirtelen háromszor annyi minden. Az ötszöri söprés
        // viszont szándékos, véletlenül nem jön össze.
        if (com.superdl.launcher.menu.MenuPrefs.isSimpleMode(context)) {
            items = items.filter { it.id in SIMPLE_MODE_IDS }
        }

        // 2/B. SÚGÓ minden almenü aljára, amelyhez van megírt súgószöveg
        //      (HelpTexts). "Duplasúgás a sztereó": a menü maga is súg, de
        //      egy összefüggő, teljes leírás külön menüpontból is elérhető.
        items = withHelp(items)

        // 3. TELEPÍTETT BŐVÍTMÉNYEK beillesztése a saját kategóriájukba
        val modules = try {
            com.superdl.launcher.store.ModuleDiscovery.findModules(context)
        } catch (_: Exception) {
            emptyList()
        }
        if (modules.isEmpty()) return items

        val byMenuId: Map<String, List<MenuItem>> = modules
            .groupBy { it.category.menuId }
            .mapValues { (_, mods) ->
                mods.map { m ->
                    MenuItem(
                        id = "${MODULE_ID_PREFIX}${m.packageName}",
                        label = m.name,
                        action = MenuAction.MODULE_LAUNCH
                    )
                }
            }

        return items.map { item ->
            val extras = byMenuId[item.id] ?: return@map item
            if (item.children.isEmpty()) return@map item
            val backIndex = item.children.indexOfLast { it.label.startsWith("Vissza") }
            val newChildren = if (backIndex >= 0) {
                item.children.toMutableList().apply { addAll(backIndex, extras) }
            } else {
                item.children + extras
            }
            item.copy(children = newChildren)
        }
    }

    /**
     * Súgó-menüpont beszúrása minden olyan almenü aljára (a "Vissza" elé),
     * amelyhez a HelpTexts tartalmaz témát, és amelyben még nincs súgó.
     * Bármilyen mélyen működik; a Braille és a Mátrix almenü saját, kézzel
     * írt súgóját érintetlenül hagyja (ott már van Súgó feliratú pont).
     */
    private fun withHelp(items: List<MenuItem>): List<MenuItem> = items.map { item ->
        if (item.children.isEmpty()) return@map item
        val children = withHelp(item.children)
        val hasHelp = children.any { it.action == MenuAction.HELP || it.label == "Súgó" }
        if (hasHelp || !com.superdl.launcher.help.HelpTexts.hasTopic(item.id)) {
            return@map item.copy(children = children)
        }
        val helpItem = MenuItem(
            id = "${com.superdl.launcher.help.HelpTexts.MENU_ID_PREFIX}${item.id}",
            label = "Súgó",
            action = MenuAction.HELP
        )
        val backIndex = children.indexOfLast { it.label.startsWith("Vissza") }
        val newChildren = if (backIndex >= 0) {
            children.toMutableList().apply { add(backIndex, helpItem) }
        } else {
            children + helpItem
        }
        item.copy(children = newChildren)
    }

    /** Egy menüpont eltávolítása a fából, bármilyen mélyen is van. */
    private fun removeDeep(items: List<MenuItem>, id: String): List<MenuItem> =
        items.filter { it.id != id }
            .map { if (it.children.isEmpty()) it else it.copy(children = removeDeep(it.children, id)) }

    /** A bővítmény-menüpontok azonosítójának előtagja. */
    const val MODULE_ID_PREFIX = "module::"

    val root: List<MenuItem> = listOf(

        // A SÖTÉT MÓD a lista ELEJÉN: egy söpréssel elérhető, mert gyakran és
        // gyorsan kell (magánszféra, akkumulátor-kímélés).
        MenuItem("screen_curtain", "Sötét mód", MenuAction.SCREEN_CURTAIN_TOGGLE),

        MenuItem("calls", "Telefon és Hívások", MenuAction.SUBMENU, listOf(
            MenuItem("call_contacts", "Névjegyből hívás", MenuAction.CONTACTS),
            MenuItem("contact_book", "Névjegyzék", MenuAction.CONTACT_BOOK),
            MenuItem("contact_sync", "Névjegyek szinkronizálása", MenuAction.CONTACT_SYNC),
            MenuItem("call_log", "Hívásnapló felolvasása", MenuAction.CALL_LOG),
            MenuItem("call_dial", "Szám tárcsázása", MenuAction.DIAL),
            MenuItem("fav_add", "Kedvenc hozzáadása", MenuAction.FAVORITES_ADD),
            MenuItem("fav_call", "Kedvenc hívása", MenuAction.FAVORITES_CALL),
            MenuItem("fav_delete", "Kedvenc törlése", MenuAction.FAVORITES_DELETE),
            MenuItem("contact_create", "Új névjegy létrehozása", MenuAction.CONTACT_CREATE),
            MenuItem("contact_settings", "Névjegyzék beállítások", MenuAction.SUBMENU, listOf(
                MenuItem("contact_ui_status", "Jelenlegi beállítások", MenuAction.CONTACT_UI_STATUS),
                MenuItem("contact_ui_letter", "Betűindex ki és be", MenuAction.CONTACT_UI_LETTER_TOGGLE),
                MenuItem("contact_ui_number", "Teljes telefonszám ki és be", MenuAction.CONTACT_UI_FULL_NUMBER),
                MenuItem("contact_export", "Névjegyek mentése fájlba", MenuAction.CONTACT_EXPORT),
                MenuItem("contact_import", "Névjegyek visszatöltése fájlból", MenuAction.CONTACT_IMPORT),
                MenuItem("contact_settings_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("call_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("sms", "Üzenetek és E-mail", MenuAction.SUBMENU, listOf(
            MenuItem("sms_sub", "SMS üzenetek", MenuAction.SUBMENU, listOf(
                MenuItem("sms_read", "Bejövő üzenetek olvasása", MenuAction.SMS_READ),
                MenuItem("sms_sent_read", "Kimenő üzenetek", MenuAction.SMS_SENT_READ),
                MenuItem("sms_write", "Üzenet diktálása és küldése", MenuAction.SMS_WRITE),
                MenuItem("sms_settings_sub", "SMS beállítások", MenuAction.SUBMENU, listOf(
                    MenuItem("sms_default_setup", "Alapértelmezett üzenet app beállítása", MenuAction.SMS_DEFAULT_SETUP),
                    MenuItem("sms_default_status", "Üzenet app állapota", MenuAction.SMS_DEFAULT_STATUS),
                    MenuItem("sms_settings_back", "Vissza", MenuAction.SUBMENU)
                )),
                MenuItem("sms_sub_back", "Vissza", MenuAction.SUBMENU)
            )),
            // AZ E-MAIL MENÜ ÁTRENDEZVE (2026-09-02).
            //
            // A RÉGI SORREND ROSSZ VOLT, és a felhasználó mondta ki, miért:
            // az „E-mail írása" külön menüpont volt, a levelek olvasásától
            // messze — vagyis pont akkor NEM tudtál válaszolni, amikor épp
            // elolvastad a levelet.
            //
            // Az új rend: elöl a POSTAFIÓK (ez a napi művelet, ide lépsz be
            // legtöbbször), utána az ÚJ LEVÉL, és csak azután a ritkán
            // használt címjegyzék és fiókbeállítás. A válasz és a továbbítás
            // pedig NEM menüpont, hanem ott van, ahol kell: a levélnél,
            // egy jobbra söpréssel.
            MenuItem("email_sub", "E-mail", MenuAction.SUBMENU, listOf(
                MenuItem("email_imap_read", "Postafiók megnyitása", MenuAction.EMAIL_IMAP_READ),
                MenuItem("email_write", "Új levél írása", MenuAction.EMAIL_WRITE),
                MenuItem("email_contacts_sub", "E-mail címjegyzék", MenuAction.SUBMENU, listOf(
                    MenuItem("email_list", "Mentett e-mail címek", MenuAction.EMAIL_LIST),
                    MenuItem("email_add", "E-mail cím hozzáadása", MenuAction.EMAIL_ADD),
                    MenuItem("email_import", "E-mail címek importálása", MenuAction.EMAIL_IMPORT),
                    MenuItem("email_contacts_back", "Vissza", MenuAction.SUBMENU)
                )),
                MenuItem("email_account_sub", "E-mail fiók beállítása", MenuAction.SUBMENU, listOf(
                    MenuItem("email_smtp_setup", "Fiók beállítása", MenuAction.EMAIL_SMTP_SETUP),
                    MenuItem("email_smtp_read", "Beállítás felolvasása", MenuAction.EMAIL_SMTP_READ),
                    MenuItem("email_diag", "E-mail kapcsolat naplózása", MenuAction.EMAIL_DIAGNOSTICS),
                    MenuItem("email_smtp_clear", "Beállítás törlése", MenuAction.EMAIL_SMTP_CLEAR),
                    MenuItem("email_account_back", "Vissza", MenuAction.SUBMENU)
                )),
                MenuItem("email_sub_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("csevej", "Csevejcenter – valós idejű csevegő", MenuAction.CHAT_OPEN),
            MenuItem("sms_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("sos", "S.O.S. Vészjelzés", MenuAction.SOS),

        MenuItem("time", "Idő és Szervezés", MenuAction.SUBMENU, listOf(
            MenuItem("clock_sub", "Óra és ébresztés", MenuAction.SUBMENU, listOf(
                MenuItem("time_now", "Pontos idő felolvasása", MenuAction.TIME_NOW),
                MenuItem("alarm_set", "Új ébresztő beállítása", MenuAction.ALARM_SET),
                MenuItem("alarm_next", "Következő ébresztő", MenuAction.ALARM_READ_NEXT),
                MenuItem("alarm_list", "Ébresztők listája", MenuAction.ALARM_LIST),
                MenuItem("alarm_delete", "Ébresztő törlése", MenuAction.ALARM_DELETE),
                MenuItem("alarm_skip", "Ébresztések kihagyása", MenuAction.ALARM_SKIP),
            MenuItem("alarm_skip_status", "Kihagyott ébresztők", MenuAction.ALARM_SKIP_STATUS),
                MenuItem("timer_create", "Új időzítő mentése", MenuAction.TIMER_CREATE),
                MenuItem("timer_list", "Időzítők listája", MenuAction.TIMER_LIST),
                MenuItem("timer_start", "Időzítő indítása", MenuAction.TIMER_START),
                MenuItem("timer_stop", "Aktív időzítő leállítása", MenuAction.TIMER_STOP),
                MenuItem("timer_edit", "Időzítő módosítása", MenuAction.TIMER_EDIT),
                MenuItem("timer_delete", "Időzítő törlése", MenuAction.TIMER_DELETE),
                MenuItem("clock_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("program_sub", "Program (naptár)", MenuAction.SUBMENU, listOf(
                MenuItem("calendar_add", "Program létrehozása", MenuAction.CALENDAR_ADD),
                MenuItem("calendar_edit", "Program szerkesztése", MenuAction.CALENDAR_EDIT_PICK),
                MenuItem("calendar_delete", "Program törlése", MenuAction.CALENDAR_DELETE_PICK),
                MenuItem("calendar_read", "Program napi áttekintő", MenuAction.CALENDAR_READ),
                MenuItem("calendar_tomorrow", "Program holnapi áttekintő", MenuAction.CALENDAR_TOMORROW),
                MenuItem("calendar_week", "Program heti áttekintő", MenuAction.CALENDAR_WEEK),
                MenuItem("calendar_target", "Melyik naptárba írjunk", MenuAction.CALENDAR_CHOOSE_TARGET),
                MenuItem("calendar_status", "Naptárak állapota", MenuAction.CALENDAR_STATUS),
                MenuItem("program_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("notes_sub", "Jegyzetek", MenuAction.SUBMENU, listOf(
                MenuItem("note_list", "Saját jegyzetek", MenuAction.NOTE_LIST),
                MenuItem("note_create", "Új jegyzet", MenuAction.NOTE_CREATE),
                MenuItem("note_delete", "Jegyzet törlése", MenuAction.NOTE_DELETE),
                MenuItem("notes_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("time_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("media", "Zene és Média", MenuAction.SUBMENU, listOf(
            // ZENE: minden zenéhez tartozó pont EGY helyen, ne szétszórva.
            MenuItem("music_group", "Zene", MenuAction.SUBMENU, listOf(
                MenuItem("music_resume", "Utoljára játszott folytatása", MenuAction.MUSIC_RESUME_LAST),
                MenuItem("music", "Zene a telefonon", MenuAction.MUSIC),
                MenuItem("music_cloud", "Zenék a felhőben", MenuAction.MUSIC_CLOUD),
                MenuItem("music_cloud_folder", "Felhős zenemappa kiválasztása", MenuAction.MUSIC_CLOUD_FOLDER),
                MenuItem("music_settings", "Zene beállítások", MenuAction.SUBMENU, listOf(
                    MenuItem("music_play_mode", "Lejátszási mód", MenuAction.MUSIC_PLAY_MODE),
                    MenuItem("music_seek_step", "Tekerés egység", MenuAction.MUSIC_SEEK_STEP),
                    MenuItem("music_eq", "Hangszínprofil", MenuAction.MUSIC_EQ_PROFILE),
                    MenuItem("music_speech", "Beszéd-visszajelzés", MenuAction.SUBMENU, listOf(
                        MenuItem("music_speech_master", "Beszéd a lejátszás alatt", MenuAction.MUSIC_SPEECH_ENABLED),
                        MenuItem("music_speak_skip", "Számváltásnál beszéljen", MenuAction.MUSIC_SPEAK_SKIP),
                        MenuItem("music_speak_stop", "Leállításnál beszéljen", MenuAction.MUSIC_SPEAK_STOP),
                        MenuItem("music_speak_seek", "Tekerésnél beszéljen", MenuAction.MUSIC_SPEAK_SEEK),
                        MenuItem("music_speech_back", "Vissza", MenuAction.SUBMENU)
                    )),
                    MenuItem("music_settings_back", "Vissza", MenuAction.SUBMENU)
                )),
                MenuItem("music_group_back", "Vissza", MenuAction.SUBMENU)
            )),
            // FÁJLÁTVITEL: nem zene, ezért külön csoportban.
            MenuItem("transfer_group", "Fájlátvitel és megosztás", MenuAction.SUBMENU,
                transferChildren("")),
            MenuItem("podcast", "Podcast", MenuAction.SUBMENU, listOf(
                MenuItem("podcast_top", "Népszerű podcastok", MenuAction.PODCAST_TOP),
                MenuItem("podcast_search", "Podcast keresése", MenuAction.PODCAST_SEARCH),
                MenuItem("podcast_subs", "Feliratkozásaim", MenuAction.PODCAST_SUBSCRIPTIONS),
                MenuItem("podcast_downloads", "Letöltéseim", MenuAction.PODCAST_DOWNLOADS),
                MenuItem("podcast_country", "Ország választása", MenuAction.PODCAST_COUNTRY),
                MenuItem("podcast_opml_import", "Feliratkozások importálása fájlból", MenuAction.PODCAST_OPML_IMPORT),
                MenuItem("podcast_opml_export", "Feliratkozások mentése fájlba", MenuAction.PODCAST_OPML_EXPORT),
                MenuItem("podcast_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("youtube_group", "YouTube", MenuAction.SUBMENU, listOf(
                MenuItem("youtube", "Hangos keresés", MenuAction.YOUTUBE),
                MenuItem("yt_favorites", "Kedvenc videóim", MenuAction.YOUTUBE_FAVORITES),
                MenuItem("yt_channels", "Követett csatornák", MenuAction.YOUTUBE_CHANNELS),
                MenuItem("yt_resume", "Utoljára nézett folytatása", MenuAction.YOUTUBE_RESUME),
                MenuItem("yt_saver", "Takarékos mód", MenuAction.YOUTUBE_SAVER_MODE),
                MenuItem("yt_stop_bg", "Háttérlejátszás leállítása", MenuAction.YOUTUBE_STOP_BACKGROUND),
                MenuItem("yt_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("radio", "Internetes rádió", MenuAction.SUBMENU, listOf(
                MenuItem("radio_hungarian", "Magyar állomások", MenuAction.RADIO_HUNGARIAN),
                MenuItem("radio_favorites", "Kedvenc állomásaim", MenuAction.RADIO_FAVORITES),
                MenuItem("radio_search", "Állomás keresése", MenuAction.RADIO_SEARCH),
                MenuItem("radio_fav_delete", "Kedvenc állomás törlése", MenuAction.RADIO_FAV_DELETE),
                MenuItem("radio_add_clip", "Saját állomás a vágólapról", MenuAction.RADIO_ADD_CLIPBOARD),
                MenuItem("radio_recordings", "Rádió felvételek megnyitása", MenuAction.RADIO_RECORDINGS),
                MenuItem("radio_schedule", "Időzített felvétel hozzáadása", MenuAction.RADIO_SCHEDULE_ADD),
                MenuItem("radio_schedule_list", "Időzített felvételeim", MenuAction.RADIO_SCHEDULE_LIST),
                MenuItem("radio_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("media_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("games", "Játékok", MenuAction.SUBMENU, listOf(
            MenuItem("game_uno", "UNO kártyajáték", MenuAction.GAME_UNO),
            MenuItem("game_quiz", "Kvíz játék", MenuAction.GAME_QUIZ),
            MenuItem("game_hangman", "Akasztófa", MenuAction.GAME_HANGMAN),
            MenuItem("game_blackjack", "Blackjack", MenuAction.GAME_BLACKJACK),
            MenuItem("game_poker", "Póker ötlapos húzás", MenuAction.GAME_POKER),
            MenuItem("game_slot", "Félkarú rabló", MenuAction.GAME_SLOT),
            MenuItem("game_mille_bornes", "Mille Bornes", MenuAction.GAME_MILLE_BORNES),
            // JÁTÉKSZABÁLYOK — Alph kérése (2026-09-03): „legyen a játékoknál
            // szabály súgó is, mert lehet hogy van aki nem ismeri de játszaná
            // ha megértené". Ezek nem a menüépítő automatikus Súgó-pontjai:
            // maguk a játékok LEVELEK a menüben, oda nem lehet Súgót szúrni,
            // ezért kapnak egy közös almenüt, játékonként egy leírással.
            MenuItem("game_rules", "Játékszabályok", MenuAction.SUBMENU, listOf(
                MenuItem("help::game_uno", "UNO szabályai", MenuAction.HELP),
                MenuItem("help::game_quiz", "Kvíz szabályai", MenuAction.HELP),
                MenuItem("help::game_hangman", "Akasztófa szabályai", MenuAction.HELP),
                MenuItem("help::game_blackjack", "Blackjack szabályai", MenuAction.HELP),
                MenuItem("help::game_poker", "Póker szabályai", MenuAction.HELP),
                MenuItem("help::game_slot", "Félkarú rabló szabályai", MenuAction.HELP),
                MenuItem("help::game_mille_bornes", "Mille Bornes szabályai", MenuAction.HELP),
                MenuItem("game_rules_back", "Vissza a játékokhoz", MenuAction.SUBMENU)
            )),
            MenuItem("games_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("books", "Könyvek", MenuAction.SUBMENU, listOf(
            MenuItem("book_library", "Könyvtár", MenuAction.BOOK_LIBRARY),
            MenuItem("book_search", "Könyv keresése felolvasással", MenuAction.BOOK_SEARCH),
            MenuItem("book_recent", "Nem rég olvasott könyvek", MenuAction.BOOK_RECENT),
            MenuItem("book_bookmarks", "Könyvjelzők", MenuAction.BOOK_BOOKMARKS),
            MenuItem("book_bookmark_delete", "Könyvjelző törlése", MenuAction.BOOK_BOOKMARK_DELETE),
            MenuItem("book_delete", "Könyv törlése", MenuAction.BOOK_DELETE),
            MenuItem("book_resume", "Olvasás folytatása", MenuAction.BOOK_RESUME),
            MenuItem("book_folder_set", "Könyvmappa beállítása", MenuAction.BOOK_FOLDER_SET),
            MenuItem("book_folder_read", "Könyvmappa felolvasása", MenuAction.BOOK_FOLDER_READ),
            MenuItem("book_folder_clear", "Könyvmappa törlése", MenuAction.BOOK_FOLDER_CLEAR),
            MenuItem("book_voice", "Felolvasó hangja", MenuAction.SUBMENU, listOf(
                MenuItem("book_tts_status", "Jelenlegi beállítás", MenuAction.BOOK_TTS_STATUS),
                MenuItem("book_tts_own", "Saját hang ki és be", MenuAction.BOOK_TTS_OWN),
                MenuItem("book_tts_engine", "Beszédmotor választása", MenuAction.BOOK_TTS_ENGINE),
                MenuItem("book_tts_rate", "Beszédsebesség", MenuAction.BOOK_TTS_RATE),
                MenuItem("book_tts_pitch", "Hangmagasság", MenuAction.BOOK_TTS_PITCH),
                MenuItem("book_voice_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("books_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("info", "Információ", MenuAction.SUBMENU, listOf(
            MenuItem("day_greeting", "Napi üdvözlés", MenuAction.DAY_GREETING),
            MenuItem("day_summary", "Napi összefoglaló", MenuAction.DAY_SUMMARY),
            MenuItem("weather", "Időjárás most", MenuAction.WEATHER),
            MenuItem("weather_city", "Időjárás város szerint", MenuAction.WEATHER_CITY),
            MenuItem("news_read", "Hírek felolvasása", MenuAction.NEWS_READ),
            MenuItem("news_feed_manage", "Hírforrások kezelése", MenuAction.NEWS_FEED_MANAGE),
            MenuItem("news_feed_opml", "Hírforrások OPML import", MenuAction.NEWS_FEED_IMPORT_OPML),
            MenuItem("web_search", "Internet kereső", MenuAction.WEB_SEARCH),
            MenuItem("battery", "Akkumulátor állapot", MenuAction.BATTERY),
            MenuItem("info_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("community", "Közlekedés", MenuAction.SUBMENU, listOf(
            MenuItem("nav_where", "Hol vagyok?", MenuAction.NAV_WHERE),
            MenuItem("nav_walk", "Gyalogos útvonal diktálással", MenuAction.NAV_WALK),
            MenuItem("nav_search", "Cím vagy hely keresése", MenuAction.NAV_SEARCH),
            MenuItem("gps_radar", "G P S Kitekintő", MenuAction.SUBMENU, listOf(
                MenuItem("gps_radar_nearby", "Közeli helyek", MenuAction.GPS_RADAR),
                MenuItem("compass_scan", "Hang-iránytű", MenuAction.COMPASS_SCAN),
                MenuItem("compass_scan_stop", "Hang-iránytű leállítása", MenuAction.COMPASS_SCAN_STOP),
                MenuItem("gps_radar_custom", "Egyéni helyek", MenuAction.GPS_RADAR_SAVED_LIST),
                MenuItem("gps_radar_back", "Vissza a közlekedéshez", MenuAction.SUBMENU)
            )),
            MenuItem("location_watch", "Helyszín felismerő", MenuAction.SUBMENU, listOf(
                MenuItem("location_train", "Helyszín tanítása", MenuAction.LOCATION_TRAIN),
                MenuItem("location_watch_start", "Figyelő indítása", MenuAction.LOCATION_WATCH_START),
                MenuItem("location_watch_text", "Figyelő szabad szöveggel", MenuAction.LOCATION_WATCH_TEXT),
                MenuItem("location_profile_list", "Mentett helyszínek", MenuAction.LOCATION_PROFILE_LIST),
                MenuItem("location_watch_stop", "Figyelő leállítása", MenuAction.LOCATION_WATCH_STOP),
                MenuItem("location_watch_back", "Vissza a közlekedéshez", MenuAction.SUBMENU)
            )),
            MenuItem("env_snapshot", "Mi van előttem? Környezeti kitekintő", MenuAction.ENV_SNAPSHOT),
            MenuItem("env_find", "Keresd meg", MenuAction.ENV_FIND),
            MenuItem("transit_nearby", "Közeli megállók felolvasása", MenuAction.TRANSIT),
            MenuItem("transit_stop", "Megálló keresése felolvasással", MenuAction.TRANSIT_STOP),
            MenuItem("transit_favorites", "Kedvenc megállók indulási időkkel", MenuAction.TRANSIT_FAVORITES),
            MenuItem("transit_route", "Útvonal tömegközlekedéssel felolvasással", MenuAction.TRANSIT_ROUTE),
            MenuItem("train", "Vonat", MenuAction.SUBMENU, listOf(
                MenuItem("train_nearby", "Közeli állomások indulási időkkel", MenuAction.TRAIN_NEARBY),
                MenuItem("train_station", "Állomás keresése felolvasással", MenuAction.TRAIN_STATION_SEARCH),
                MenuItem("train_favorites", "Kedvenc állomások indulási időkkel", MenuAction.TRAIN_FAVORITES),
                MenuItem("train_back", "Vissza a közlekedéshez", MenuAction.SUBMENU)
            )),
            MenuItem("gps_route", "G P S útvonal", MenuAction.SUBMENU, listOf(
                MenuItem("gps_route_record", "Útvonal rögzítése", MenuAction.GPS_ROUTE_RECORD),
                MenuItem("gps_route_stop", "Rögzítés vagy útmutatás leállítása", MenuAction.GPS_ROUTE_STOP),
                MenuItem("gps_route_list", "Mentett útvonalak", MenuAction.GPS_ROUTE_LIST),
                MenuItem("gps_route_guide", "Útvonal útmutatás", MenuAction.GPS_ROUTE_GUIDE),
                MenuItem("gps_route_delete", "Útvonal törlése", MenuAction.GPS_ROUTE_DELETE),
                MenuItem("gps_route_back", "Vissza a közlekedéshez", MenuAction.SUBMENU)
            )),
            MenuItem("community_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("tools", "Eszközök", MenuAction.SUBMENU, listOf(
            // A MŰVELETSOROK ITT IS. A képernyőolvasó almenüjében is ott
            // maradnak (oda tartoznak, mert az veszi fel és az játssza le) —
            // de aki csak EL AKAR INDÍTANI egy betanított műveletsort, annak
            // ne kelljen a képernyőolvasó beállításai közt keresgélnie.
            // Ez nem beállítás, hanem eszköz.
            MenuItem("task_routes_tools", "Műveletsorok", MenuAction.TASK_ROUTES),
            MenuItem("steps", "Lépésszámláló", MenuAction.SUBMENU, listOf(
                MenuItem("steps_today", "Mai összesítő", MenuAction.STEPS_TODAY),
                MenuItem("steps_live", "Élő mérés és sebesség", MenuAction.STEPS_LIVE),
                MenuItem("steps_goal", "Napi cél beállítása", MenuAction.STEPS_SETTINGS_GOAL),
                MenuItem("steps_body", "Testmagasság és testsúly", MenuAction.STEPS_SETTINGS_BODY),
                MenuItem("steps_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("file_manager", "Fájlkezelő", MenuAction.FILE_MANAGER),
            // A FÁJLÁTVITEL ITT IS. A Média alatt marad, ahol eddig volt (aki
            // ott szokta meg, ott találja) — de a fájlátvitel nem média, és
            // aki fájlt akar küldeni, az az Eszközök közt keresi. Ugyanaz a
            // lista, más azonosítóval: az azonosító a súgót és a szűrőket
            // vezérli, két egyforma id-tól két „Súgó" pont keletkezne.
            MenuItem("transfer_group_tools", "Fájlátvitel és megosztás", MenuAction.SUBMENU,
                transferChildren("_t")),
            MenuItem("flashlight", "Zseblámpa", MenuAction.FLASHLIGHT),
            MenuItem("tools_readers", "Olvasók", MenuAction.SUBMENU, listOf(
                MenuItem("qr", "Q R kód olvasó", MenuAction.QR_SCAN),
                MenuItem("medication_reader", "Gyógyszerdoboz olvasó", MenuAction.MEDICATION_READER),
                MenuItem("label_reader", "Címke olvasó", MenuAction.LABEL_READER),
                MenuItem("text_reader", "Szöveg olvasó", MenuAction.TEXT_READER),
                MenuItem("continuous_ocr", "Folyamatos szövegolvasó", MenuAction.CONTINUOUS_OCR),
                MenuItem("tools_readers_back", "Vissza az eszközökhöz", MenuAction.SUBMENU)
            )),
            MenuItem("tools_recognizers", "Felismerők", MenuAction.SUBMENU, listOf(
                MenuItem("light_detector", "Fénydetektor kamerával", MenuAction.LIGHT_DETECTOR),
                MenuItem("color_detector", "Színfelismerő kamerával", MenuAction.COLOR_DETECTOR),
                MenuItem("env_snapshot_tools", "Mi van előttem? Környezeti kitekintő", MenuAction.ENV_SNAPSHOT),
                MenuItem("env_find_tools", "Keresd meg", MenuAction.ENV_FIND),
                MenuItem("currency_recognizer", "Super DL Pénzfelismerő", MenuAction.CURRENCY_RECOGNIZER),
                MenuItem("card_organizer", "Kártya rendszerező", MenuAction.SUBMENU, listOf(
                    MenuItem("card_train", "Új kártya hozzáadása", MenuAction.CARD_TRAIN),
                    MenuItem("card_recognize", "Kártya felismerése", MenuAction.CARD_RECOGNIZE),
                    MenuItem("card_list", "Mentett kártyák", MenuAction.CARD_LIST),
                    MenuItem("card_delete", "Kártya törlése", MenuAction.CARD_DELETE),
                    MenuItem("card_back", "Vissza a felismerőkhöz", MenuAction.SUBMENU)
                )),
                MenuItem("tools_recognizers_back", "Vissza az eszközökhöz", MenuAction.SUBMENU)
            )),
            MenuItem("tools_camera", "Kamera", MenuAction.SUBMENU, listOf(
                MenuItem("face_camera", "Kamera és szelfi", MenuAction.FACE_CAMERA),
                MenuItem("face_camera_quality", "Kamera minőség", MenuAction.FACE_CAMERA_QUALITY),
                MenuItem("tools_camera_back", "Vissza az eszközökhöz", MenuAction.SUBMENU)
            )),
            MenuItem("tools_daily", "Mindennapi", MenuAction.SUBMENU, listOf(
                MenuItem("hearing_aid", "Hallás erősítő", MenuAction.HEARING_AID),
                MenuItem("calculator", "Számológép", MenuAction.CALCULATOR),
                MenuItem("shopping", "Bevásárlólista", MenuAction.SUBMENU, listOf(
                    MenuItem("shopping_open", "Listáim megnyitása", MenuAction.SHOPPING_LIST),
                    MenuItem("shopping_new", "Új lista létrehozása", MenuAction.SHOPPING_NEW_LIST),
                    MenuItem("shopping_back", "Vissza", MenuAction.SUBMENU)
                )),
                MenuItem("dictaphone", "Profi Diktafon", MenuAction.SUBMENU, listOf(
                    MenuItem("dict_record", "Felvétel indítása", MenuAction.DICTAPHONE_RECORD),
                    MenuItem("dict_settings", "Minőség és formátum beállítása", MenuAction.DICTAPHONE_SETTINGS),
                    MenuItem("dict_raw", "Teljesen nyers felvétel", MenuAction.DICTAPHONE_RAW_TOGGLE),
                    MenuItem("dict_caps", "Mit tud a mikrofonom", MenuAction.DICTAPHONE_CAPABILITIES),
                    MenuItem("dict_library", "Mentett felvételek", MenuAction.DICTAPHONE_LIBRARY),
                    MenuItem("dict_back", "Vissza a mindennapihoz", MenuAction.SUBMENU)
                )),
                MenuItem("pharmacy_guardian", "Patika Őrangyal", MenuAction.SUBMENU, listOf(
                    MenuItem("med_read", "Aktuális emlékeztetők felolvasása", MenuAction.MEDICATION_READ),
                    MenuItem("med_add", "Új gyógyszer rögzítése", MenuAction.MEDICATION_ADD),
                    MenuItem("med_search", "Gyógyszerkereső", MenuAction.MEDICATION_SEARCH),
                    MenuItem("med_delete", "Emlékeztető törlése", MenuAction.MEDICATION_DELETE),
                    MenuItem("pharmacy_back", "Vissza a mindennapihoz", MenuAction.SUBMENU)
                )),
                MenuItem("tools_daily_back", "Vissza az eszközökhöz", MenuAction.SUBMENU)
            )),
            MenuItem("tools_back", "Vissza a főmenübe", MenuAction.SUBMENU)
        )),

        MenuItem("assistant", "Asszisztens", MenuAction.SUBMENU, listOf(
            MenuItem("voice_assistant", "Elena", MenuAction.VOICE_ASSISTANT),
            MenuItem("elena_wake_listen", "Elena figyelő", MenuAction.ELENA_WAKE_LISTEN_TOGGLE),
            MenuItem("assistant_continuous", "Folyamatos beszélgetés", MenuAction.ASSISTANT_CONTINUOUS),
            MenuItem("elena_wake_train", "Elena felébresztő tanítása", MenuAction.ELENA_WAKE_TRAIN),
            MenuItem("elena_wake_custom_list", "Saját felébresztő mondatok", MenuAction.ELENA_WAKE_CUSTOM_LIST),
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
            // A PROGRAM FRISSÍTÉSE A BEÁLLÍTÁSOK ELSŐ PONTJA (2026-09-02).
            //
            // MIÉRT KERÜLT IDE: eddig a „Katalógus" almenüben volt, „Frissítés
            // keresése" néven. A Katalógus viszont a LETÖLTHETŐ MODULOKRÓL
            // szól — senki nem ott keresi azt, hogy „frissítsd a programot".
            // Maga a fejlesztő sem találta meg a saját programjában, amikor
            // frissíteni akart. Ha ő nem találja, egy tesztelő biztosan nem.
            //
            // A név is változott: „Program frissítése" — ez mondja meg, hogy
            // MI frissül. A „Frissítés keresése" nem árulta el, hogy a
            // programról vagy a modulokról van-e szó.
            MenuItem("app_update", "Program frissítése", MenuAction.CATALOG_UPDATE),
            MenuItem("catalog", "Katalógus", MenuAction.SUBMENU, listOf(
                MenuItem("catalog_browse", "Elérhető modulok", MenuAction.CATALOG_BROWSE),
                MenuItem("catalog_installed", "Letöltött modulok", MenuAction.CATALOG_INSTALLED),
                MenuItem("catalog_back", "Vissza", MenuAction.SUBMENU)
            )),
            MenuItem("gesture_orientation", "Felület elforgatása", MenuAction.GESTURE_ORIENTATION),
            MenuItem("gesture_orientation_help", "Jelenlegi kezelés felolvasása", MenuAction.GESTURE_ORIENTATION_HELP),
            MenuItem("advanced", "Haladó és technikai", MenuAction.SUBMENU, listOf(
                MenuItem("acc_status", "Kisegítő szolgáltatások állapota", MenuAction.ACCESSIBILITY_STATUS),
                MenuItem("safe_mode", "Biztonságos mód", MenuAction.SAFE_MODE_STATUS),
                MenuItem("failure_test", "Hibatűrés próbája", MenuAction.FAILURE_SELF_TEST),
                MenuItem("simple_mode", "Egyszerű mód ki és be", MenuAction.SIMPLE_MODE_TOGGLE),
                MenuItem("screen_reader", "Képernyőolvasó", MenuAction.SUBMENU, listOf(
                    MenuItem("sr_toggle", "Képernyőolvasó ki és be", MenuAction.SCREEN_READER_TOGGLE),
                    MenuItem("sr_setup", "Engedélyezés a rendszerben", MenuAction.SCREEN_READER_SETUP),
                    MenuItem("sr_status", "Állapot", MenuAction.SCREEN_READER_STATUS),
                    MenuItem("sr_help", "Mozdulatok felolvasása", MenuAction.SCREEN_READER_HELP),
                    MenuItem("sr_school", "Gesztusok tanulása", MenuAction.SUBMENU, listOf(
                        MenuItem("sr_lessons", "Órák Elena tanárnővel", MenuAction.SR_TRAIN_LESSONS),
                        MenuItem("sr_exam", "Vizsga", MenuAction.SR_TRAIN_EXAM),
                        MenuItem("sr_stop", "Tanulás befejezése", MenuAction.SR_TRAIN_STOP),
                        MenuItem("sr_school_back", "Vissza", MenuAction.SUBMENU)
                    )),
                    MenuItem("sr_counter", "Pozíció bemondása", MenuAction.SCREEN_READER_COUNTER),
                    MenuItem("sr_phonetic", "Betűző ábécé", MenuAction.SCREEN_READER_PHONETIC),
                    MenuItem("sr_autoread", "Automatikus felolvasás", MenuAction.SCREEN_READER_AUTOREAD),
                    MenuItem("sr_notif", "Értesítések bemondása", MenuAction.SCREEN_READER_NOTIF),
                    MenuItem("sr_explore", "Felderítés érintéssel", MenuAction.SCREEN_READER_EXPLORE),
                    MenuItem("sr_explore_hold", "Felderítés indítási ideje", MenuAction.SCREEN_READER_EXPLORE_HOLD),
                    MenuItem("sr_labels", "Saját elnevezések", MenuAction.SCREEN_READER_LABELS),
                    MenuItem("sr_map_style", "Hangtérkép hangnyelve", MenuAction.SCREEN_READER_MAP_STYLE),
                    MenuItem("sr_map_tempo", "Hangtérkép tempója", MenuAction.SCREEN_READER_MAP_TEMPO),
                    MenuItem("sr_stereo_test", "Bal-jobb próba", MenuAction.SCREEN_READER_STEREO_TEST),
                    MenuItem("task_routes", "Műveletsorok", MenuAction.TASK_ROUTES),
                    MenuItem("sr_share_toggle", "Elnevezések megosztása", MenuAction.SCREEN_READER_SHARE_TOGGLE),
                    MenuItem("sr_share_send", "Elnevezések beküldése", MenuAction.SCREEN_READER_SHARE_SEND),
                    MenuItem("sr_panic", "AZONNALI leállítás", MenuAction.SCREEN_READER_PANIC),
                    MenuItem("sr_back", "Vissza", MenuAction.SUBMENU)
                )),
                // BILLENTYŰZET — külön ág, mert egyben zsúfolt volt.
                // Ami MINDEN billentyűzetre igaz, az itt van; ami csak
                // egy billentyűzetre, az a saját almenüjében.
                MenuItem("keyboard", "Billentyűzet", MenuAction.SUBMENU, listOf(
                    MenuItem("kb_picker", "Billentyűzet választása", MenuAction.KEYBOARD_PICKER),
                    MenuItem("kb_settings", "Billentyűzetek engedélyezése", MenuAction.KEYBOARD_SETTINGS),
                    MenuItem("kb_text_bank", "Szövegtár tartalma", MenuAction.KEYBOARD_TEXT_BANK),
                    MenuItem("kb_matrix", "Mátrix billentyűzet", MenuAction.SUBMENU, listOf(
                        MenuItem("kb_matrix_help", "Súgó: a mátrix mozdulatai", MenuAction.KEYBOARD_MATRIX_HELP),
                        MenuItem("kb_matrix_cell", "Gombok távolsága", MenuAction.KEYBOARD_MATRIX_CELL),
                        MenuItem("kb_matrix_speed", "Pörgetés sebessége", MenuAction.KEYBOARD_MATRIX_SPEED),
                        MenuItem("kb_matrix_back", "Vissza", MenuAction.SUBMENU)
                    )),
                    // A sorrend a HASZNÁLAT sorrendje: előbb eldöntöd,
                    // HÁNY ujjal írsz, aztán hogyan fogod a telefont és hova
                    // teszed az ujjaidat, aztán megtanítod a kezed, és csak
                    // utána próbálsz írni. Fordítva egyik sem működik.
                    MenuItem("kb_braille", "Braille billentyűzet", MenuAction.SUBMENU, listOf(
                        // A SÚGÓ AZ ELSŐ. „Duplasúgás a sztereó" — a menüpontok
                        // egyenként beszélnek, de a rendszert csak a súgó mondja el.
                        MenuItem("kb_braille_help", "Súgó", MenuAction.BRAILLE_HELP),
                        MenuItem("kb_braille_mode", "Írásmód: hány ujjal írok", MenuAction.BRAILLE_MODE),
                        MenuItem("kb_braille_orient", "Tartás: álló vagy fekvő", MenuAction.BRAILLE_ORIENTATION),
                        MenuItem("kb_braille_layout", "Elrendezés: cella vagy zongora", MenuAction.BRAILLE_LAYOUT),
                        MenuItem("kb_touch_calib", "A kezem megtanítása", MenuAction.TOUCH_CALIBRATION),
                        MenuItem("kb_braille_practice", "Braille próba", MenuAction.BRAILLE_PRACTICE),
                        MenuItem("kb_braille_status", "Beállítás felolvasása", MenuAction.BRAILLE_STATUS),
                        MenuItem("kb_braille_back", "Vissza", MenuAction.SUBMENU)
                    )),
                    MenuItem("kb_back", "Vissza", MenuAction.SUBMENU)
                )),
                MenuItem("setup_wizard", "Beállítás varázsló", MenuAction.SETUP_WIZARD),
                MenuItem("setup_status", "Beállítás állapota", MenuAction.SETUP_STATUS),
                MenuItem("setup_restart", "Beállítás varázsló elölről", MenuAction.SETUP_RESTART),
                MenuItem("diagnostics", "Diagnosztika", MenuAction.DIAGNOSTICS),
                MenuItem("battery_opt", "Korlátlan háttérfutás engedélyezése", MenuAction.BATTERY_OPT_REQUEST),
                MenuItem("autostart", "Automatikus indítás a gyártónál", MenuAction.AUTOSTART_SETUP),
                MenuItem("advanced_back", "Vissza a beállításokhoz", MenuAction.SUBMENU)
            )),
            MenuItem("sos_settings", "S.O.S. paraméterek", MenuAction.SUBMENU, listOf(
                MenuItem("sos_set_1", "S.O.S. szám 1 beállítása", MenuAction.SOS_SET_1),
                MenuItem("sos_set_2", "S.O.S. szám 2 beállítása", MenuAction.SOS_SET_2),
                MenuItem("sos_set_3", "S.O.S. szám 3 beállítása", MenuAction.SOS_SET_3),
                MenuItem("sos_set_4", "S.O.S. szám 4 beállítása", MenuAction.SOS_SET_4),
                MenuItem("sos_read", "S.O.S. számok felolvasása", MenuAction.SOS_READ_ALL),
                MenuItem("sos_countdown", "Visszaszámlálás ki és be", MenuAction.SOS_COUNTDOWN_TOGGLE),
                MenuItem("sos_phrase_train", "S.O.S. hívómondat tanítása", MenuAction.SOS_PHRASE_TRAIN),
                MenuItem("sos_phrase_list", "S.O.S. hívómondataim", MenuAction.SOS_PHRASE_LIST),
                MenuItem("sos_settings_back", "Vissza a beállításokhoz", MenuAction.SUBMENU)
            )),
            MenuItem("patrol_master", "Teljes őrség ki-be", MenuAction.BATTERY_PATROL_TOGGLE),
            MenuItem("security", "Biztonság", MenuAction.SUBMENU, listOf(
                MenuItem("lock_toggle", "PIN zárolás ki-be", MenuAction.LOCK_PIN_TOGGLE),
                MenuItem("lock_set", "PIN kód beállítása", MenuAction.LOCK_PIN_SET),
                MenuItem("lock_status", "PIN zárolás állapota", MenuAction.LOCK_PIN_STATUS),
                MenuItem("keyguard_pin_toggle", "Rendszer PIN segéd ki-be", MenuAction.KEYGUARD_PIN_ASSIST_TOGGLE),
                MenuItem("keyguard_pin_setup", "Rendszer PIN segéd engedélyezése", MenuAction.KEYGUARD_PIN_ASSIST_SETUP),
                MenuItem("keyguard_pin_status", "Rendszer PIN segéd állapota", MenuAction.KEYGUARD_PIN_ASSIST_STATUS),
                // A BEÉPÍTETT ZÁRKÉPERNYŐ-HANGOK PRÓBÁJA. Ezek akkor szólalnak
                // meg, amikor a rendszer beszédmotorja még nem él: bekapcsolás
                // után, az ELSŐ PIN beírásáig. Máskor nem hallhatók — ezért kell
                // egy pont, ahol szándékosan meg lehet hallgatni őket.
                MenuItem("keyguard_voice_test", "Zárképernyő hangjainak próbája", MenuAction.KEYGUARD_VOICE_TEST),
                MenuItem("call_filter_mode", "Hívás szűrő mód", MenuAction.CALL_FILTER_MODE_CYCLE),
                MenuItem("call_filter_status", "Hívás szűrő állapota", MenuAction.CALL_FILTER_MODE_STATUS),
                MenuItem("focus", "Időzített fókusz", MenuAction.SUBMENU, listOf(
                    MenuItem("focus_status", "Mi van most érvényben", MenuAction.FOCUS_STATUS),
                    MenuItem("focus_list", "Szabályaim", MenuAction.FOCUS_LIST),
                    MenuItem("focus_night", "Éjszakai nyugalom bekapcsolása", MenuAction.FOCUS_ADD_NIGHT),
                    MenuItem("focus_sunday", "Vasárnapi pihenő bekapcsolása", MenuAction.FOCUS_ADD_SUNDAY),
                    MenuItem("focus_custom", "Egyéni fókusz létrehozása", MenuAction.FOCUS_ADD_CUSTOM),
                    MenuItem("focus_back", "Vissza", MenuAction.SUBMENU)
                )),
                MenuItem("dialer_default_setup", "Alapértelmezett telefon beállítása", MenuAction.DIALER_DEFAULT_SETUP),
                MenuItem("dialer_default_status", "Telefon alkalmazás állapota", MenuAction.DIALER_DEFAULT_STATUS),
                MenuItem("security_back", "Vissza a beállításokhoz", MenuAction.SUBMENU)
            )),
            MenuItem("sound_settings", "Hangok", MenuAction.SUBMENU, listOf(
                MenuItem("sound_theme", "Söpörj hangtéma", MenuAction.SOUND_THEME_SELECT),
                // BESZÉDTÉMA — külön almenü, mert sok apró kapcsolója van, és
                // a „hangtéma" szó a söprés-hangokra már foglalt.
                MenuItem("voice_theme", "Beszédtéma", MenuAction.SUBMENU, listOf(
                    MenuItem("vt_toggle", "Beszédtéma ki-be", MenuAction.VOICE_THEME_TOGGLE),
                    MenuItem("vt_test", "Hangok kipróbálása", MenuAction.VOICE_THEME_TEST),
                    MenuItem("vt_status", "Mihez van már hang", MenuAction.VOICE_THEME_STATUS),
                    MenuItem("vt_pick", "Téma választása", MenuAction.VOICE_THEME_PICK),
                    MenuItem("vt_ev_low", "Merüléskor ki-be", MenuAction.VOICE_THEME_EVENT_BATTERY_LOW),
                    MenuItem("vt_first_alert", "Első figyelmeztetés szintje", MenuAction.BATTERY_FIRST_ALERT_CYCLE),
                    MenuItem("vt_ev_full", "Feltöltve ki-be", MenuAction.VOICE_THEME_EVENT_BATTERY_FULL),
                    MenuItem("vt_ev_charger", "Töltő be- és kihúzva ki-be", MenuAction.VOICE_THEME_EVENT_CHARGER),
                    MenuItem("vt_morning", "Jó reggelt ki-be", MenuAction.VOICE_THEME_MORNING_TOGGLE),
                    MenuItem("vt_morning_time", "Jó reggelt időpontja", MenuAction.VOICE_THEME_MORNING_TIME),
                    MenuItem("vt_morning_unlock", "Jó reggelt csak feloldáskor", MenuAction.VOICE_THEME_MORNING_UNLOCK_TOGGLE),
                    MenuItem("vt_night", "Jó éjszakát ki-be", MenuAction.VOICE_THEME_NIGHT_TOGGLE),
                    MenuItem("vt_night_time", "Jó éjszakát időpontja", MenuAction.VOICE_THEME_NIGHT_TIME),
                    MenuItem("vt_cap", "Napi keret", MenuAction.VOICE_THEME_DAILY_CAP),
                    // A KÉT LEGFONTOSABB PONT: saját hang felvétele és
                    // megosztása. Ettől lesz a funkció gép nélkül használható.
                    MenuItem("vt_record", "Beszédtéma felvétele", MenuAction.VOICE_THEME_RECORD),
                    MenuItem("vt_share", "Beszédtéma megosztása", MenuAction.VOICE_THEME_SHARE),
                    // A KÖZÖS RÉSZ: innen lehet válogatni mások témái közül,
                    // és ide lehet beküldeni a sajátot. A böngészőben minden
                    // téma MEGHALLGATHATÓ letöltés előtt.
                    MenuItem("vt_catalog", "Beszédtémák a közösből", MenuAction.VOICE_THEME_CATALOG),
                    MenuItem("vt_submit", "Beküldöm a közösbe", MenuAction.VOICE_THEME_SUBMIT),
                    MenuItem("vt_install", "Kapott téma telepítése", MenuAction.VOICE_THEME_INSTALL_FILE),
                    MenuItem("vt_back", "Vissza a hangokhoz", MenuAction.SUBMENU)
                )),
                MenuItem("ringtone_select", "Csengőhang választása", MenuAction.RINGTONE_SELECT),
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
            // HANG ÉS BESZÉD — minden, ami a felolvasást és a hangerőt érinti,
            // EGY helyen. Korábban szétszórtan álltak a beállítások között, és
            // a részletesség-almenü beékelődött a gyorsítás és a lassítás közé.
            MenuItem("sound_speech", "Hang és beszéd", MenuAction.SUBMENU, listOf(
                MenuItem("vol_up", "Hangerő növelése", MenuAction.VOLUME_UP),
                MenuItem("vol_down", "Hangerő csökkentése", MenuAction.VOLUME_DOWN),
                MenuItem("tts_up", "Beszéd gyorsítása", MenuAction.TTS_SPEED_UP),
                MenuItem("tts_down", "Beszéd lassítása", MenuAction.TTS_SPEED_DOWN),
                MenuItem("tts_engine", "T T S hang választása", MenuAction.TTS_ENGINE_SELECT),
                MenuItem("tts_engine_read", "Aktuális T T S hang felolvasása", MenuAction.TTS_ENGINE_READ),
                MenuItem("tts_repair", "Beszéd javítása", MenuAction.TTS_REPAIR),
                MenuItem("tts_channel", "Beszéd hangcsatornája", MenuAction.TTS_CHANNEL),
                MenuItem("tts_auto_lang", "Automatikus nyelvváltás", MenuAction.TTS_AUTO_LANGUAGE),
                MenuItem("tts_roles", "Hangszerepek", MenuAction.TTS_VOICE_ROLES),
                MenuItem("tts_role_engine", "Külön hang a program üzeneteihez", MenuAction.TTS_ROLE_ENGINE),
                MenuItem("dict", "Kiejtési szótár", MenuAction.SUBMENU, listOf(
                    MenuItem("dict_status", "Jelenlegi állapot", MenuAction.DICT_STATUS),
                    MenuItem("dict_add", "Új szabály tanítása", MenuAction.DICT_ADD),
                    MenuItem("dict_list", "Saját szabályaim", MenuAction.DICT_LIST),
                    MenuItem("dict_builtin", "Beépített szabályok", MenuAction.DICT_BUILTIN),
                    MenuItem("dict_clear", "Saját szabályok törlése", MenuAction.DICT_CLEAR),
                    MenuItem("dict_back", "Vissza", MenuAction.SUBMENU)
                )),
                MenuItem("verbosity", "Mennyit magyarázzon", MenuAction.SUBMENU, listOf(
                    MenuItem("verb_status", "Jelenlegi beállítás", MenuAction.VERBOSITY_STATUS),
                    MenuItem("verb_hints", "Mozdulat-útmutatók", MenuAction.VERBOSITY_HINTS),
                    MenuItem("verb_app", "Alkalmazás-tájékoztató", MenuAction.VERBOSITY_APP_INFO),
                    MenuItem("verb_counts", "Darabszámok bemondása", MenuAction.VERBOSITY_COUNTS),
                    MenuItem("verb_punct", "Diktálási tipp", MenuAction.VERBOSITY_PUNCT),
                    MenuItem("verb_keyboard", "Billentyűzet-tájékoztató", MenuAction.VERBOSITY_KEYBOARD),
                    MenuItem("verb_password", "Jelszó betűinek kimondása", MenuAction.KEYBOARD_PASSWORD),
                    MenuItem("verb_back", "Vissza", MenuAction.SUBMENU)
                )),
                MenuItem("sound_speech_back", "Vissza a beállításokhoz", MenuAction.SUBMENU)
            )),
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
            // A KÉT SÚGÓ A NÉVJEGY ELEJÉN. Aki nem tudja, hol keresse a
            // segítséget, a Névjegyben keresi — és itt találja mind a
            // programegész leírását, mind az összes alkalmazás-súgót.
            MenuItem("help::main", "Súgó: így működik a program", MenuAction.HELP),
            MenuItem("help_index", "Súgó — minden alkalmazás", MenuAction.HELP_INDEX),
            MenuItem("sound_training", "Program hangjainak megismerése", MenuAction.SOUND_TRAINING),
            MenuItem("bug_report", "Hibajelentés küldése", MenuAction.BUG_REPORT),
            MenuItem("hidden_gestures", "Rejtett mozdulatok", MenuAction.HIDDEN_GESTURES_HELP),
            MenuItem("training_playground", "Tanuló mód, funkciók bemutatása", MenuAction.TRAINING_PLAYGROUND),
            MenuItem("about_app", "Az alkalmazásról", MenuAction.ABOUT_APP),
            MenuItem("about_dev", "Fejlesztő: Kőrösmezey Dávid", MenuAction.ABOUT_DEVELOPER),
            MenuItem("credits", "Köszönet és együttműködők", MenuAction.CREDITS),
            MenuItem("contact_email", "Kapcsolat e-mailben", MenuAction.CONTACT_EMAIL),
            MenuItem("support", "Fejlesztés támogatása", MenuAction.SUPPORT),
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