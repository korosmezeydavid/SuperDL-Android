package com.superdl.launcher.flow

import com.superdl.launcher.alarm.AlarmEntry
import com.superdl.launcher.contacts.ContactMatch
import com.superdl.launcher.sms.Recipient
import com.superdl.launcher.sms.SmsFolder
import com.superdl.launcher.sms.SmsMessage

sealed class AppFlow {
    object Menu : AppFlow()

    object SmsAwaitRecipient : AppFlow()
    data class SmsPickContact(val matches: List<ContactMatch>, val index: Int) : AppFlow()
    data class SmsRecipientConfirm(val recipient: Recipient) : AppFlow()
    data class SmsAwaitMessage(val recipient: Recipient) : AppFlow()
    data class SmsConfirm(val recipient: Recipient, val message: String) : AppFlow()

    data class SmsInbox(
        val messages: List<SmsMessage>,
        val index: Int,
        val folder: SmsFolder = SmsFolder.INBOX
    ) : AppFlow()

    data class SmsContextMenu(
        val messages: List<SmsMessage>,
        val messageIndex: Int,
        val actions: List<com.superdl.launcher.sms.SmsContextAction>,
        val actionIndex: Int,
        val folder: SmsFolder = SmsFolder.INBOX
    ) : AppFlow()

    data class SmsDeleteConfirm(
        val messages: List<SmsMessage>,
        val messageIndex: Int,
        val folder: SmsFolder = SmsFolder.INBOX
    ) : AppFlow()

    object EmailAwaitRecipient : AppFlow()
    data class EmailPickRecipient(val matches: List<com.superdl.launcher.email.EmailRecipient>, val index: Int) : AppFlow()
    data class EmailRecipientConfirm(val recipient: com.superdl.launcher.email.EmailRecipient) : AppFlow()
    data class EmailAwaitSubject(val recipient: com.superdl.launcher.email.EmailRecipient) : AppFlow()
    data class EmailAwaitBody(val recipient: com.superdl.launcher.email.EmailRecipient, val subject: String) : AppFlow()
    data class EmailConfirm(
        val recipient: com.superdl.launcher.email.EmailRecipient,
        val subject: String,
        val body: String
    ) : AppFlow()
    data class EmailBrowseRecipients(val recipients: List<com.superdl.launcher.email.EmailRecipient>, val index: Int) : AppFlow()

    data class CallPickContact(val matches: List<ContactMatch>, val index: Int) : AppFlow()
    data class CallConfirm(val contact: ContactMatch) : AppFlow()

    data class ContactBookBrowse(
        val items: List<com.superdl.launcher.contacts.ContactBookItem>,
        val index: Int
    ) : AppFlow()

    data class ContactLetterBrowse(
        val groups: List<com.superdl.launcher.contacts.ContactLetterIndex.LetterGroup>,
        val index: Int
    ) : AppFlow()

    /** Névjegy visszatöltés: a megtalált .vcf fájlok közötti választás. */
    data class ContactImportBrowse(
        val files: List<java.io.File>,
        val index: Int
    ) : AppFlow()

    /** Rádiófelvételek listája — jobbra lejátszás a beépített lejátszóval. */
    data class RadioRecordingBrowse(
        val files: List<java.io.File>,
        val index: Int
    ) : AppFlow()

    data class ContactContextMenu(
        val items: List<com.superdl.launcher.contacts.ContactBookItem>,
        val contactIndex: Int,
        val actions: List<com.superdl.launcher.contacts.ContactContextAction>,
        val actionIndex: Int
    ) : AppFlow()

    data class ContactEditAwaitName(val contact: ContactMatch) : AppFlow()
    data class ContactEditAwaitPhone(val contact: ContactMatch, val newName: String) : AppFlow()

    data class ContactDeleteConfirm(
        val contact: ContactMatch,
        val items: List<com.superdl.launcher.contacts.ContactBookItem>,
        val index: Int
    ) : AppFlow()

    data class SosCountdown(val secondsLeft: Int) : AppFlow()

    /**
     * A Névjegy „Súgó — minden alkalmazás" listája: minden megírt súgó egy
     * helyen, hogy ne kelljen az adott almenüig eljutni érte.
     */
    data class HelpIndexBrowse(
        val topics: List<Pair<String, com.superdl.launcher.help.HelpTexts.HelpTopic>>,
        val index: Int
    ) : AppFlow()

    /** S.O.S. hívómondat betanítása: a program a mondatot hallgatja. */
    object SosPhraseAwait : AppFlow()

    /** A bemondott hívómondat megerősítése mentés előtt. */
    data class SosPhraseConfirm(val phrase: String) : AppFlow()

    /**
     * A betanított hívómondatok listája. A törlés KÉTLÉPCSŐS: az első jobbra
     * söprés csak megkérdezi (`confirming`), a második töröl — vészjelző
     * mondatot véletlenül elveszíteni rossz lenne.
     */
    data class SosPhraseBrowse(
        val phrases: List<com.superdl.launcher.sos.SosPhraseStore.SosPhrase>,
        val index: Int,
        val confirming: Boolean = false
    ) : AppFlow()

    object AlarmAwaitTime : AppFlow()
    data class AlarmAwaitLabel(val hour: Int, val minute: Int) : AppFlow()
    data class AlarmRepeatBrowse(
        val hour: Int,
        val minute: Int,
        val label: String,
        val options: List<com.superdl.launcher.alarm.AlarmRepeatType>,
        val index: Int
    ) : AppFlow()
    data class AlarmConfirm(val hour: Int, val minute: Int, val label: String) : AppFlow()
    data class AlarmListBrowse(val alarms: List<AlarmEntry>, val index: Int, val deleteMode: Boolean = false) : AppFlow()
    data class AlarmDeleteConfirm(val alarm: AlarmEntry, val alarms: List<AlarmEntry>, val index: Int) : AppFlow()
    /**
     * ÁTJÁRÓ A KATALÓGUSHOZ — egy söprés oda, ahol tartalom van.
     *
     * A HIBA, AMI EZT KIKÉNYSZERÍTETTE: egy friss telepítő ennyit jelentett,
     * hogy „a játékok nem töltöttek le". A program addig annyit mondott, hogy
     * „a Beállítások, Katalógus, Elérhető modulok pontban tölthetsz le
     * kérdéssorokat" — ez igaz, de HÁROM menülépés, amit vakon fejben kell
     * tartani, miközben ki kell lépni onnan, ahol épp vagy.
     *
     * Aki most hallja először, annak nem útbaigazítás kell, hanem ajtó.
     *
     * @param reason amit a felhasználó hallott (miért nincs tartalom)
     */
    data class CatalogGate(val reason: String) : AppFlow()

    /** Kvíz: melyik letöltött kérdéssort játsszuk. */
    data class QuizPick(
        val sets: List<com.superdl.launcher.games.quiz.QuizSet>,
        val index: Int
    ) : AppFlow()

    /**
     * Kvíz játék. Fel-le: válaszok között lépkedés, jobbra: válasz beadása,
     * balra: kilépés.
     */
    data class QuizPlay(
        val set: com.superdl.launcher.games.quiz.QuizSet,
        val questionIndex: Int,
        val answerIndex: Int,
        val score: Int
    ) : AppFlow()

    /**
     * Alkalmazás-kategóriák böngészése ("Zene és hang", "Játékok", ...).
     * Innen lehet belépni egy kategória alkalmazásaiba.
     */
    data class AppCategoryPick(
        val groups: List<Pair<com.superdl.launcher.apps.AppCategory, List<com.superdl.launcher.apps.ExternalApp>>>,
        val index: Int
    ) : AppFlow()

    /**
     * Egyéni fókusz létrehozása lépésről lépésre.
     * Lépések: 0 = szűrési mód, 1 = mely napokon, 2 = kezdés, 3 = vég, 4 = mentés.
     */
    data class FocusWizard(
        val step: Int,
        val mode: com.superdl.launcher.callfilter.CallFilterMode,
        val dayPreset: Int,
        val startMinute: Int,
        val endMinute: Int
    ) : AppFlow()

    /**
     * Hibajelentés: a kész szöveg, és a küldési mód választása.
     * 0 = saját levelező, 1 = megosztás, 2 = mentés fájlba
     */
    data class BugReportSend(val report: String, val index: Int) : AppFlow()

    /**
     * BESZÉDTÉMA FELVÉTELE — végigmegyünk az eseményeken, egyesével.
     *
     * Három szakasz, mert vakon a felvétel csak akkor kezelhető, ha minden
     * lépésnél PONTOSAN egy dolog történhet: felveszem, meghallgatom,
     * megtartom vagy újra. A `index` az esemény sorszáma.
     */
    data class VoiceThemeRecord(
        val index: Int,
        val stage: VoiceRecordStage
    ) : AppFlow()

    /**
     * BESZÉDTÉMA ELŐHALLGATÁSA a katalógusból.
     *
     * MIÉRT NEM „LETÖLT" A JOBBRA SÖPRÉS A TÉMÁKNÁL: egy hangtémát nem lehet
     * leírásból választani. „Vicces" — az mit jelent? Ezért a katalógus a
     * témáknál MEGHALLGATÁST kínál: a csomag (200-400 kilobájt) lejön egy
     * eldobható mappába, végighallgatod eseményenként, és csak akkor kerül a
     * helyére, ha kéred.
     *
     * A `modules` és a `moduleIndex` azért utazik együtt, hogy az elutasítás
     * PONTOSAN oda vigyen vissza, ahonnan indultál — a katalógus ugyanazon
     * tételére.
     */
    data class VoiceThemePreview(
        val themeId: String,
        val name: String,
        val author: String,
        val events: List<com.superdl.launcher.voicetheme.VoiceEvent>,
        val index: Int,
        val modules: List<com.superdl.launcher.catalog.CatalogModule>,
        val moduleIndex: Int
    ) : AppFlow()

    /**
     * A BEKÜLDÉS NYILATKOZATA.
     *
     * Külön lépés, mert ez az egyetlen pont, ahol a felhasználó valami
     * VISSZAVONHATATLANT tesz: a hangja felkerül egy nyilvános címre.
     * Jobbra = vállalom, balra = mégsem.
     */
    data class VoiceThemeSubmitConfirm(
        val themeId: String,
        val name: String,
        val author: String
    ) : AppFlow()

    /** Kihagyások törlésének megerősítése. */
    object AlarmSkipClearConfirm : AppFlow()

    /** Biztonságos mód bekapcsolásának megerősítése. */
    object SafeModeConfirm : AppFlow()

    /**
     * Beszédmotor választása a PROGRAM SAJÁT ÜZENETEIHEZ (név, csomagnév).
     * Az első elem mindig a "nincs külön motor" lehetőség.
     */
    data class RoleEnginePick(
        val engines: List<Pair<String, String>>,
        val index: Int
    ) : AppFlow()

    /** Beszédmotor választása a könyvolvasóhoz (név, csomagnév). */
    data class BookEnginePick(
        val engines: List<Pair<String, String>>,
        val index: Int
    ) : AppFlow()

    /** Lépésszámláló élő mérés: lépések és sebesség. */
    data class StepsLive(val steps: Int, val speedMps: Float) : AppFlow()

    /**
     * Akasztófa játék. Fel-le: betű választása az ábécéből,
     * jobbra: tipp, balra: kilépés.
     */
    data class Hangman(
        val state: com.superdl.launcher.games.hangman.HangmanState,
        val letterIndex: Int
    ) : AppFlow()

    /** Frissítés felajánlása: jobbra letöltés és telepítés, balra mégse. */
    data class UpdateOffer(
        val info: com.superdl.launcher.catalog.UpdateChecker.UpdateInfo
    ) : AppFlow()

    /** Telepített program-modulok böngészése és indítása. */
    data class ModuleBrowse(
        val modules: List<com.superdl.launcher.store.SuperDlModule>,
        val index: Int
    ) : AppFlow()

    /** Időzített fókusz-szabályok böngészése és ki/bekapcsolása. */
    data class FocusListBrowse(
        val schedules: List<com.superdl.launcher.callfilter.FocusSchedule>,
        val index: Int
    ) : AppFlow()

    /** Katalógus: előbb CSOPORTOT választunk, csak utána modult. */
    data class CatalogCategoryPick(
        val groups: List<Pair<com.superdl.launcher.catalog.CatalogCategory, List<com.superdl.launcher.catalog.CatalogModule>>>,
        val index: Int
    ) : AppFlow()

    /** Katalógus: elérhető modulok böngészése és letöltése. */
    data class CatalogBrowse(
        val modules: List<com.superdl.launcher.catalog.CatalogModule>,
        val index: Int
    ) : AppFlow()

    data class CalendarBrowse(val events: List<com.superdl.launcher.calendar.CalendarEvent>, val index: Int) : AppFlow()

    /** Naptár-választó: melyik naptárba kerüljenek a felvett programok. */
    data class CalendarTargetPick(
        val calendars: List<com.superdl.launcher.calendar.CalendarHelper.CalendarInfo>,
        val index: Int
    ) : AppFlow()

    /**
     * Ébresztések kihagyása — 1. lépés: melyik ébresztőket érintse.
     * Fel-le: navigálás, jobbra: kijelölés váltása, balra: tovább / kilépés.
     */
    data class AlarmSkipPick(
        val alarms: List<com.superdl.launcher.alarm.AlarmEntry>,
        val index: Int,
        val selected: Set<Int>
    ) : AppFlow()

    /**
     * Ébresztések kihagyása — 2. lépés: hány következő alkalmat hagyjon ki.
     * Fel-le: darabszám, jobbra: mentés, balra: vissza a kijelöléshez.
     */
    data class AlarmSkipCount(
        val alarms: List<com.superdl.launcher.alarm.AlarmEntry>,
        val selected: Set<Int>,
        val count: Int
    ) : AppFlow()
    data class CalendarPick(
        val events: List<com.superdl.launcher.calendar.CalendarEvent>,
        val index: Int,
        val purpose: com.superdl.launcher.CalendarPickPurpose
    ) : AppFlow()
    data class CalendarWeekBrowse(val days: List<com.superdl.launcher.calendar.CalendarDayGroup>, val index: Int) : AppFlow()
    object CalendarAwaitTitle : AppFlow()
    data class CalendarAwaitDate(val title: String) : AppFlow()
    data class CalendarAwaitStartTime(val title: String, val dayStartMs: Long) : AppFlow()
    data class CalendarAwaitEndTime(
        val title: String,
        val dayStartMs: Long,
        val startHour: Int,
        val startMinute: Int
    ) : AppFlow()
    data class CalendarRecurrenceBrowse(
        val title: String,
        val beginMs: Long,
        val endMs: Long,
        val options: List<com.superdl.launcher.calendar.CalendarRecurrence>,
        val index: Int,
        val editEventId: Long? = null
    ) : AppFlow()

    data class CalendarConfirm(
        val title: String,
        val beginMs: Long,
        val endMs: Long,
        val recurrence: com.superdl.launcher.calendar.CalendarRecurrence,
        val editEventId: Long? = null
    ) : AppFlow()

    data class NoteListBrowse(
        val notes: List<com.superdl.launcher.notes.NoteEntry>,
        val index: Int,
        val deleteMode: Boolean = false
    ) : AppFlow()

    object NoteAwaitTitle : AppFlow()
    data class NoteAwaitBody(val title: String) : AppFlow()
    data class NoteReading(
        val note: com.superdl.launcher.notes.NoteEntry,
        val chunkIndex: Int,
        val totalChunks: Int,
        val percent: Int,
        val notes: List<com.superdl.launcher.notes.NoteEntry>,
        val noteIndex: Int
    ) : AppFlow()

    data class NoteDeleteConfirm(
        val note: com.superdl.launcher.notes.NoteEntry,
        val notes: List<com.superdl.launcher.notes.NoteEntry>,
        val index: Int
    ) : AppFlow()

    data class CalendarContextMenu(
        val events: List<com.superdl.launcher.calendar.CalendarEvent>,
        val eventIndex: Int,
        val actions: List<com.superdl.launcher.calendar.CalendarContextAction>,
        val actionIndex: Int
    ) : AppFlow()

    // ===== NAPTÁRI MŰVELET HOZZÁRENDELÉSE =====
    //
    // Három lépés, mert vakon a „mindent egy képernyőn" nem működik:
    // előbb a FAJTA, aztán a KONKRÉT dolog, végül a VISSZAOLVASÁS. Az utolsó
    // lépés nem elhagyható: a program visszamondja, mit fog csinálni, és csak
    // azután menti. Egy naptári művelet a felhasználó nevében cselekszik —
    // annak, amit nem hallott vissza, nem mondhat igent.

    /** Melyik fajta műveletet rendeljük a programhoz. */
    data class CalendarActionTypePick(
        val events: List<com.superdl.launcher.calendar.CalendarEvent>,
        val eventIndex: Int,
        val index: Int
    ) : AppFlow()

    /** A fajtán belül melyiket: menüpont, műveletsor, alkalmazás vagy címzett. */
    data class CalendarActionOptionPick(
        val events: List<com.superdl.launcher.calendar.CalendarEvent>,
        val eventIndex: Int,
        val cim: String,
        val options: List<CalendarActionOption>,
        val index: Int
    ) : AppFlow()

    /** Az SMS szövegének diktálására várunk. */
    data class CalendarActionSmsAwait(
        val events: List<com.superdl.launcher.calendar.CalendarEvent>,
        val eventIndex: Int,
        val number: String,
        val who: String
    ) : AppFlow()

    /** Visszaolvasás és megerősítés mentés előtt. */
    data class CalendarActionConfirm(
        val events: List<com.superdl.launcher.calendar.CalendarEvent>,
        val eventIndex: Int,
        val action: com.superdl.launcher.calendar.CalendarAction
    ) : AppFlow()

    data class CalendarAlarmContextMenu(
        val event: com.superdl.launcher.calendar.CalendarEvent,
        val actions: List<com.superdl.launcher.calendar.CalendarAlarmAction>,
        val actionIndex: Int
    ) : AppFlow()

    data class CalendarDeleteConfirm(
        val event: com.superdl.launcher.calendar.CalendarEvent,
        val events: List<com.superdl.launcher.calendar.CalendarEvent>,
        val index: Int
    ) : AppFlow()
    data class CallLogBrowse(val entries: List<com.superdl.launcher.calllog.CallLogEntry>, val index: Int) : AppFlow()

    data class CallLogContextMenu(
        val entries: List<com.superdl.launcher.calllog.CallLogEntry>,
        val entryIndex: Int,
        val actions: List<com.superdl.launcher.calllog.CallLogContextAction>,
        val actionIndex: Int
    ) : AppFlow()

    data class CallLogSaveContactAwaitName(
        val entries: List<com.superdl.launcher.calllog.CallLogEntry>,
        val entryIndex: Int
    ) : AppFlow()

    data class ContactCreateAwaitName(val phone: String) : AppFlow()

    data class FavoritesBrowse(
        val favorites: List<com.superdl.launcher.favorites.FavoriteEntry>,
        val index: Int,
        val mode: com.superdl.launcher.favorites.FavoritesListMode
    ) : AppFlow()

    data class FavoriteDeleteConfirm(
        val favorite: com.superdl.launcher.favorites.FavoriteEntry,
        val favorites: List<com.superdl.launcher.favorites.FavoriteEntry>,
        val index: Int
    ) : AppFlow()

    data class FavoriteContactCandidateBrowse(
        val candidates: List<com.superdl.launcher.favorites.FavoriteContactCandidate>,
        val index: Int
    ) : AppFlow()

    data class SosSetupMethodPick(
        val slot: Int,
        val index: Int = 0
    ) : AppFlow()

    data class SosContactCandidateBrowse(
        val contacts: List<ContactMatch>,
        val index: Int,
        val slot: Int
    ) : AppFlow()
    data class MusicBrowse(val tracks: List<com.superdl.launcher.music.MusicTrack>, val index: Int) : AppFlow()
    data class RadioBrowse(
        val stations: List<com.superdl.launcher.radio.RadioStation>,
        val index: Int,
        /** Törlés mód: a jobbra söprés nem elindítja, hanem eltávolítja a kedvencet. */
        val deleteMode: Boolean = false
    ) : AppFlow()

    /**
     * IDŐZÍTETT FELVÉTEL — állomásválasztás. A `forSchedule` jelzi, hogy a
     * lista most nem lejátszásra, hanem időzítésre gyűjt állomást.
     */
    data class RadioScheduleStationPick(
        val stations: List<com.superdl.launcher.radio.RadioStation>,
        val index: Int
    ) : AppFlow()

    /** Időzített felvétel: mennyi ideig vegyen fel. */
    data class RadioScheduleDurationPick(
        val station: com.superdl.launcher.radio.RadioStation,
        val hour: Int,
        val minute: Int,
        val index: Int
    ) : AppFlow()

    /** Időzített felvétel: egyszeri vagy ismétlődő. */
    data class RadioScheduleRepeatPick(
        val station: com.superdl.launcher.radio.RadioStation,
        val hour: Int,
        val minute: Int,
        val durationMinutes: Int,
        val index: Int
    ) : AppFlow()

    /** A beütemezett felvételek listája (jobbra: törlés megerősítése). */
    data class RadioScheduleBrowse(
        val entries: List<com.superdl.launcher.radio.RadioScheduleEntry>,
        val index: Int
    ) : AppFlow()

    data class RadioScheduleDeleteConfirm(
        val entry: com.superdl.launcher.radio.RadioScheduleEntry,
        val entries: List<com.superdl.launcher.radio.RadioScheduleEntry>,
        val index: Int
    ) : AppFlow()

    /** Kedvenc rádió eltávolításának megerősítése. */
    data class RadioFavoriteDeleteConfirm(
        val station: com.superdl.launcher.radio.RadioStation,
        val stations: List<com.superdl.launcher.radio.RadioStation>,
        val index: Int
    ) : AppFlow()
    object CalculatorAwaitInput : AppFlow()
    object WeatherAwaitCity : AppFlow()
    data class EmailSmtpPickAccount(
        val accounts: List<String>,
        val index: Int
    ) : AppFlow()

    object EmailSmtpAwaitUsername : AppFlow()
    object EmailSmtpAwaitPassword : AppFlow()
    object EmailSmtpAwaitFromName : AppFlow()

    object SearchAwaitQuery : AppFlow()
    object SearchLoading : AppFlow()

    data class SearchResultBrowse(
        val results: List<com.superdl.launcher.search.SearchResult>,
        val index: Int,
        val query: String
    ) : AppFlow()

    data class SearchArticleReading(
        val result: com.superdl.launcher.search.SearchResult,
        val chunkIndex: Int,
        val totalChunks: Int,
        val percent: Int,
        val results: List<com.superdl.launcher.search.SearchResult>,
        val resultIndex: Int,
        val query: String,
        val sourceLabel: String = "",
        val articleBody: String = ""
    ) : AppFlow()

    object EmailInboxLoading : AppFlow()

    data class EmailInboxBrowse(
        val mails: List<com.superdl.launcher.email.ImapMail>,
        val index: Int
    ) : AppFlow()

    data class EmailReadBody(
        val mail: com.superdl.launcher.email.ImapMail,
        val mails: List<com.superdl.launcher.email.ImapMail>,
        val index: Int
    ) : AppFlow()

    /**
     * AZ ELOLVASOTT LEVÉL MŰVELETEI — válasz, továbbítás, mentés.
     *
     * Az elolvasott levélnél egy jobbra söprés hozza elő. A `mail` és a
     * `mails` végig vele utazik, hogy a művelet után VISSZA lehessen térni
     * pontosan oda, ahol a felhasználó volt — vakon az elveszett pozíció a
     * legbosszantóbb dolog.
     */
    data class EmailActionMenu(
        val mail: com.superdl.launcher.email.ImapMail,
        val mails: List<com.superdl.launcher.email.ImapMail>,
        val index: Int,
        val actionIndex: Int
    ) : AppFlow()

    data class ShoppingListPick(
        val names: List<String>,
        val index: Int
    ) : AppFlow()

    data class ShoppingListBrowse(
        val listName: String,
        val items: List<com.superdl.launcher.shopping.ShoppingItem>,
        val index: Int,
        val showingSummary: Boolean = false
    ) : AppFlow()

    object ShoppingListAwaitName : AppFlow()
    object ShoppingListAwaitItem : AppFlow()
    data class ShoppingListAwaitMore(
        val listName: String
    ) : AppFlow()

    data class ShoppingItemContextMenu(
        val listName: String,
        val items: List<com.superdl.launcher.shopping.ShoppingItem>,
        val itemIndex: Int,
        val actions: List<com.superdl.launcher.shopping.ShoppingContextAction>,
        val actionIndex: Int
    ) : AppFlow()

    data class ShoppingListContextMenu(
        val names: List<String>,
        val listIndex: Int,
        val actions: List<com.superdl.launcher.shopping.ShoppingListContextAction>,
        val actionIndex: Int
    ) : AppFlow()

    data class ShoppingDeleteItemConfirm(
        val listName: String,
        val item: com.superdl.launcher.shopping.ShoppingItem,
        val items: List<com.superdl.launcher.shopping.ShoppingItem>,
        val index: Int
    ) : AppFlow()

    data class ShoppingDeleteListConfirm(
        val listName: String,
        val names: List<String>,
        val index: Int
    ) : AppFlow()

    data class ShoppingEditItemAwaitName(
        val listName: String,
        val item: com.superdl.launcher.shopping.ShoppingItem,
        val items: List<com.superdl.launcher.shopping.ShoppingItem>,
        val index: Int
    ) : AppFlow()

    data class ShoppingRenameListAwaitName(
        val oldName: String,
        val names: List<String>,
        val index: Int
    ) : AppFlow()
    data class GuideBrowse(val sections: List<com.superdl.launcher.legal.LegalSection>, val index: Int, val title: String) : AppFlow()

    data class NotificationBrowse(val notifications: List<com.superdl.launcher.notifications.NotificationEntry>, val index: Int) : AppFlow()
    data class NewsFeedBrowse(val feeds: List<com.superdl.launcher.news.NewsFeed>, val index: Int) : AppFlow()
    data class NewsBrowse(
        val items: List<com.superdl.launcher.news.RssItem>,
        val index: Int,
        val feedId: String? = null,
        val page: Int = 0,
        val hasMore: Boolean = false
    ) : AppFlow()
    data class NewsArticleReading(
        val newsFlow: NewsBrowse,
        val title: String,
        val body: String
    ) : AppFlow()
    data class NewsFeedManageBrowse(
        val feeds: List<com.superdl.launcher.news.NewsFeed>,
        val index: Int
    ) : AppFlow()
    data class GpsArrivalLocationPrompt(
        val destinationName: String,
        val index: Int = 0
    ) : AppFlow() {
        companion object {
            val OPTIONS = listOf(
                "Helyszín figyelő bekapcsolása",
                "Új helyszín tanítása",
                "Nem kell most"
            )
        }
    }
    object TransitAwaitStop : AppFlow()
    object TransitAwaitDestination : AppFlow()
    object NavAwaitWalkDestination : AppFlow()
    object NavAwaitPlaceQuery : AppFlow()
    data class NavPlaceBrowse(val places: List<com.superdl.launcher.navigation.NavPlace>, val index: Int) : AppFlow()
    data class TransitBrowse(
        val places: List<com.superdl.launcher.transit.TransitPlace>,
        val index: Int,
        val title: String = "Megállók",
        val radiusMode: com.superdl.launcher.transit.TransitHelper.StopRadiusMode =
            com.superdl.launcher.transit.TransitHelper.StopRadiusMode.NEAR
    ) : AppFlow()
    data class TransitContextMenu(
        val places: List<com.superdl.launcher.transit.TransitPlace>,
        val placeIndex: Int,
        val actions: List<com.superdl.launcher.transit.TransitContextAction>,
        val actionIndex: Int,
        val title: String = "Megállók",
        val radiusMode: com.superdl.launcher.transit.TransitHelper.StopRadiusMode =
            com.superdl.launcher.transit.TransitHelper.StopRadiusMode.NEAR
    ) : AppFlow()
    data class TransitRouteBrowse(
        val route: com.superdl.launcher.transit.TransitRoute,
        val index: Int
    ) : AppFlow()

    object TrainAwaitStation : AppFlow()
    data class TrainBrowse(
        val stations: List<com.superdl.launcher.train.TrainStation>,
        val index: Int,
        val title: String = "Állomások",
        val radiusMode: com.superdl.launcher.train.TrainHelper.StationRadiusMode =
            com.superdl.launcher.train.TrainHelper.StationRadiusMode.NEAR
    ) : AppFlow()
    data class TrainContextMenu(
        val stations: List<com.superdl.launcher.train.TrainStation>,
        val stationIndex: Int,
        val actions: List<com.superdl.launcher.train.TrainContextAction>,
        val actionIndex: Int,
        val title: String = "Állomások",
        val radiusMode: com.superdl.launcher.train.TrainHelper.StationRadiusMode =
            com.superdl.launcher.train.TrainHelper.StationRadiusMode.NEAR
    ) : AppFlow()

    object VoiceAssistantAwaitQuestion : AppFlow()
    object VoiceAssistantChat : AppFlow()
    object ElenaWakeTrainAwaitPhrase : AppFlow()

    object YoutubeAwaitQuery : AppFlow()
    data class YoutubeBrowse(
        val videos: List<com.superdl.launcher.youtube.YoutubeVideo>,
        val index: Int,
        val query: String = "",
        val page: Int = 0,
        val hasMore: Boolean = false
    ) : AppFlow()
    data class YoutubePlayConfirm(
        val video: com.superdl.launcher.youtube.YoutubeVideo,
        val videos: List<com.superdl.launcher.youtube.YoutubeVideo>,
        val index: Int
    ) : AppFlow()

    /**
     * Szakaszos szövegböngésző. A jogi szövegeké volt; 2026-09-03-tól a
     * SÚGÓ, a TÁMOGATÁS és az EGYÜTTMŰKÖDŐK is ezen mennek — ezért van cím.
     */
    data class LegalBrowse(
        val sections: List<com.superdl.launcher.legal.LegalSection>,
        val index: Int,
        val title: String = "Jogi információ"
    ) : AppFlow()

    data class BookLibraryBrowse(
        val books: List<com.superdl.launcher.book.BookEntry>,
        val index: Int,
        val deleteMode: Boolean = false
    ) : AppFlow()
    /** Könyv végleges törlésének megerősítése (jobbra: törlés, balra: mégse). */
    data class BookDeleteConfirm(
        val book: com.superdl.launcher.book.BookEntry,
        val books: List<com.superdl.launcher.book.BookEntry>,
        val index: Int
    ) : AppFlow()
    data class BookRecentBrowse(val books: List<com.superdl.launcher.book.BookEntry>, val index: Int) : AppFlow()
    data class BookBookmarkBrowse(
        val bookmarks: List<com.superdl.launcher.book.BookBookmark>,
        val index: Int,
        val deleteMode: Boolean = false
    ) : AppFlow()
    data class BookBookmarkDeleteConfirm(
        val bookmark: com.superdl.launcher.book.BookBookmark,
        val bookmarks: List<com.superdl.launcher.book.BookBookmark>,
        val index: Int
    ) : AppFlow()
    object BookSearchAwaitQuery : AppFlow()
    data class BookReading(
        val book: com.superdl.launcher.book.BookEntry,
        val chunkIndex: Int,
        val totalChunks: Int,
        val percent: Int,
        val paused: Boolean = false
    ) : AppFlow()

    object BookLoading : AppFlow()

    data class TtsVoiceBrowse(
        val options: List<com.superdl.launcher.tts.TtsVoiceOption>,
        val index: Int
    ) : AppFlow()

    enum class FavoriteAppsMode {
        LAUNCH,
        REMOVE,
        ADD
    }

    data class FavoriteAppsBrowse(
        val favorites: List<com.superdl.launcher.apps.FavoriteAppEntry>,
        val index: Int,
        val mode: FavoriteAppsMode
    ) : AppFlow()

    data class FavoriteAppsCandidateBrowse(
        val candidates: List<com.superdl.launcher.apps.FavoriteAppCandidate>,
        val index: Int
    ) : AppFlow()

    data class SoundTrainingBrowse(
        val items: List<com.superdl.launcher.feedback.SoundType>,
        val index: Int
    ) : AppFlow()

    data class AlertSoundPresetBrowse(
        val category: com.superdl.launcher.feedback.AlertSoundCategory,
        val presets: List<com.superdl.launcher.feedback.AlertSoundPreset>,
        val index: Int
    ) : AppFlow()

    data class SoundThemeBrowse(
        val themes: List<com.superdl.launcher.feedback.SoundTheme>,
        val index: Int
    ) : AppFlow()

    data class TrainingPlayground(
        val steps: List<com.superdl.launcher.training.TrainingStep>,
        val stepIndex: Int,
        val choiceIndex: Int = 0,
        val awaitingAdvance: Boolean = false
    ) : AppFlow()

    object PatrolNightAwaitStart : AppFlow()
    object PatrolNightAwaitEnd : AppFlow()

    data class NumericDictationAwait(
        val purpose: com.superdl.launcher.input.NumberPadPurpose,
        val sosSlot: Int? = null,
        val alarmDraft: Boolean = false,
        val timerUnit: com.superdl.launcher.timer.TimerUnitOption? = null,
        val editTimerId: Int? = null,
        val calendarTitle: String? = null,
        val calendarDayStartMs: Long? = null,
        val calendarAwaitEnd: Boolean = false,
        val calendarStartHour: Int? = null,
        val calendarStartMinute: Int? = null,
        val shoppingListName: String? = null,
        val shoppingItemName: String? = null,
        val shoppingEditItemId: Int? = null,
        val shoppingEditPriceOnly: Boolean = false
    ) : AppFlow()

    data class NumberPadInput(
        val purpose: com.superdl.launcher.input.NumberPadPurpose,
        val items: List<com.superdl.launcher.input.NumberPadItem>,
        val index: Int,
        val buffer: String,
        val sosSlot: Int? = null,
        val pinMode: com.superdl.launcher.input.PinPadMode? = null,
        val setupPin: String? = null,
        val alarmDraft: Boolean = false,
        val timerUnit: com.superdl.launcher.timer.TimerUnitOption? = null,
        val editTimerId: Int? = null,
        val calendarTitle: String? = null,
        val calendarDayStartMs: Long? = null,
        val calendarAwaitEnd: Boolean = false,
        val calendarStartHour: Int? = null,
        val calendarStartMinute: Int? = null,
        val shoppingListName: String? = null,
        val shoppingItemName: String? = null,
        val shoppingEditItemId: Int? = null,
        val shoppingEditPriceOnly: Boolean = false
    ) : AppFlow()

    object CalculatorVoiceInput : AppFlow()

    data class ExternalAppBrowse(
        val apps: List<com.superdl.launcher.apps.ExternalApp>,
        val index: Int
    ) : AppFlow()

    data class TimerUnitBrowse(
        val units: List<com.superdl.launcher.timer.TimerUnitOption>,
        val index: Int,
        val editTimerId: Int? = null
    ) : AppFlow()

    data class TimerAwaitAmount(
        val unit: com.superdl.launcher.timer.TimerUnitOption,
        val editTimerId: Int? = null
    ) : AppFlow()

    data class TimerIntervalBrowse(
        val durationMinutes: Int,
        val intervals: List<Int>,
        val index: Int,
        val editTimerId: Int? = null
    ) : AppFlow()

    data class TimerAwaitLabel(
        val durationMinutes: Int,
        val announceIntervalMinutes: Int,
        val editTimerId: Int? = null
    ) : AppFlow()

    data class TimerConfirm(
        val durationMinutes: Int,
        val announceIntervalMinutes: Int,
        val label: String,
        val editTimerId: Int? = null
    ) : AppFlow()

    data class TimerListBrowse(
        val timers: List<com.superdl.launcher.timer.TimerEntry>,
        val index: Int,
        val mode: com.superdl.launcher.timer.TimerListMode
    ) : AppFlow()

    data class TimerDeleteConfirm(
        val timer: com.superdl.launcher.timer.TimerEntry,
        val timers: List<com.superdl.launcher.timer.TimerEntry>,
        val index: Int
    ) : AppFlow()

    object GpsRadarLoading : AppFlow()

    data class GpsRadarBrowse(
        val pois: List<com.superdl.launcher.gps.GpsPoi>,
        val index: Int
    ) : AppFlow()

    data class GpsRadarGuiding(
        val pois: List<com.superdl.launcher.gps.GpsPoi>,
        val index: Int
    ) : AppFlow()

    data class GpsRadarContextMenu(
        val pois: List<com.superdl.launcher.gps.GpsPoi>,
        val poiIndex: Int,
        val actions: List<com.superdl.launcher.gps.GpsRadarContextAction>,
        val actionIndex: Int,
        val fromGuiding: Boolean = false
    ) : AppFlow()

    data class GpsRadarAwaitSaveName(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Int? = null,
        val category: String = "egyéni",
        val returnBrowse: GpsRadarBrowse? = null,
        val returnGuiding: GpsRadarGuiding? = null,
        val returnNavWhere: NavWhereResult? = null
    ) : AppFlow()

    object NavWhereLoading : AppFlow()

    data class NavWhereResult(
        val latitude: Double,
        val longitude: Double,
        val address: String,
        val accuracyMeters: Int
    ) : AppFlow()

    data class GpsSaveRefining(
        val returnBrowse: GpsRadarBrowse? = null,
        val returnGuiding: GpsRadarGuiding? = null,
        val fromAssistant: Boolean = false
    ) : AppFlow()

    data class GpsSavedPoiBrowse(
        val saved: List<com.superdl.launcher.gps.SavedPoi>,
        val index: Int
    ) : AppFlow()

    data class SavedPoiContextMenu(
        val saved: List<com.superdl.launcher.gps.SavedPoi>,
        val poiIndex: Int,
        val actions: List<com.superdl.launcher.gps.SavedPoiContextAction>,
        val actionIndex: Int
    ) : AppFlow()

    data class SavedPoiVoiceRecording(
        val saved: List<com.superdl.launcher.gps.SavedPoi>,
        val poiIndex: Int
    ) : AppFlow()

    object DictaphoneRecording : AppFlow()

    data class DictaphoneSettingsBrowse(
        val options: List<com.superdl.launcher.dictaphone.DictaphoneSettingsOption>,
        val index: Int
    ) : AppFlow()

    data class DictaphoneFormatBrowse(
        val formats: List<com.superdl.launcher.dictaphone.DictaphoneFormat>,
        val index: Int
    ) : AppFlow()

    data class DictaphoneSampleRateBrowse(
        val rates: List<com.superdl.launcher.dictaphone.DictaphoneSampleRate>,
        val index: Int
    ) : AppFlow()

    data class DictaphoneBitrateBrowse(
        val bitrates: List<com.superdl.launcher.dictaphone.DictaphoneBitrate>,
        val index: Int
    ) : AppFlow()

    data class DictaphoneChannelsBrowse(
        val channels: List<com.superdl.launcher.dictaphone.DictaphoneChannels>,
        val index: Int
    ) : AppFlow()



    data class DictaphoneRecordingsBrowse(
        val recordings: List<com.superdl.launcher.dictaphone.DictaphoneRecordingEntry>,
        val index: Int
    ) : AppFlow()

    data class DictaphoneRecordingContextMenu(
        val recordings: List<com.superdl.launcher.dictaphone.DictaphoneRecordingEntry>,
        val recordingIndex: Int,
        val actions: List<com.superdl.launcher.dictaphone.DictaphoneRecordingContextAction>,
        val actionIndex: Int
    ) : AppFlow()

    data class DictaphoneShareEmailAwaitRecipient(
        val entry: com.superdl.launcher.dictaphone.DictaphoneRecordingEntry
    ) : AppFlow()

    data class DictaphoneShareEmailPickRecipient(
        val entry: com.superdl.launcher.dictaphone.DictaphoneRecordingEntry,
        val matches: List<com.superdl.launcher.email.EmailRecipient>,
        val index: Int
    ) : AppFlow()

    data class DictaphoneShareEmailConfirm(
        val entry: com.superdl.launcher.dictaphone.DictaphoneRecordingEntry,
        val recipient: com.superdl.launcher.email.EmailRecipient
    ) : AppFlow()

    data class DictaphoneRecordingDeleteConfirm(
        val recordings: List<com.superdl.launcher.dictaphone.DictaphoneRecordingEntry>,
        val recordingIndex: Int
    ) : AppFlow()

    object MedicationAwaitName : AppFlow()

    data class MedicationTimeOfDayBrowse(
        val name: String,
        val options: List<com.superdl.launcher.medication.MedicationTimeOfDay>,
        val selected: Set<com.superdl.launcher.medication.MedicationTimeOfDay>,
        val index: Int
    ) : AppFlow()

    object MedicationAwaitCourseDays : AppFlow()

    object MedicationSearchAwaitName : AppFlow()

    // ==================== Podcast ====================
    object PodcastLoading : AppFlow()
    object PodcastSearchAwaitQuery : AppFlow()
    data class PodcastListBrowse(
        val podcasts: List<com.superdl.launcher.podcast.Podcast>,
        val index: Int,
        val title: String
    ) : AppFlow()
    data class PodcastEpisodeBrowse(
        val podcast: com.superdl.launcher.podcast.Podcast,
        val episodes: List<com.superdl.launcher.podcast.PodcastEpisode>,
        val index: Int
    ) : AppFlow()
    data class PodcastEpisodeMenu(
        val podcast: com.superdl.launcher.podcast.Podcast,
        val episodes: List<com.superdl.launcher.podcast.PodcastEpisode>,
        val episodeIndex: Int,
        val actionIndex: Int
    ) : AppFlow()
    /**
     * LETÖLTÖTT ADÁS TÖRLÉSÉNEK MEGERŐSÍTÉSE.
     *
     * Ugyanazt a három adatot viszi tovább, amit az adás-menü, hogy törlés
     * UTÁN vissza lehessen állni a helyére a listában. Aki a nyolcadik
     * adásnál törölt, ne a lista elején találja magát.
     */
    data class PodcastDeleteConfirm(
        val podcast: com.superdl.launcher.podcast.Podcast,
        val episodes: List<com.superdl.launcher.podcast.PodcastEpisode>,
        val episodeIndex: Int
    ) : AppFlow()
    data class PodcastCountryBrowse(val index: Int) : AppFlow()
    object MedicationSearchLoading : AppFlow()
    data class MedicationSearchResult(
        val title: String,
        val fullText: String
    ) : AppFlow()

    data class MedicationCycleBrowse(
        val name: String,
        val hour: Int,
        val minute: Int,
        val options: List<com.superdl.launcher.medication.MedicationCycleType>,
        val index: Int
    ) : AppFlow()

    data class MedicationWeekdayBrowse(
        val name: String,
        val hour: Int,
        val minute: Int,
        val cycleType: com.superdl.launcher.medication.MedicationCycleType,
        val selectedDays: Set<Int>,
        val index: Int
    ) : AppFlow()

    data class MedicationConfirm(
        val name: String,
        val hour: Int,
        val minute: Int,
        val cycleType: com.superdl.launcher.medication.MedicationCycleType,
        val weekDays: Set<Int>
    ) : AppFlow()

    data class MedicationListBrowse(
        val reminders: List<com.superdl.launcher.medication.MedicationReminder>,
        val index: Int,
        val deleteMode: Boolean = false
    ) : AppFlow()

    data class MedicationDeleteConfirm(
        val reminder: com.superdl.launcher.medication.MedicationReminder,
        val reminders: List<com.superdl.launcher.medication.MedicationReminder>,
        val index: Int
    ) : AppFlow()

    object LauncherExitConfirm : AppFlow()

    /**
     * Beállítás varázsló: végigvezet a hiányzó engedélyeken.
     *
     * MIÉRT LISTA + INDEX: ugyanaz a minta, mint a MedicationListBrowse-nál —
     * fel-le söprés a tételek között, jobbra a megadás. A `requirements` a
     * HIÁNYZÓ tételek listája, a felmérés pillanatában.
     */
    data class SetupWizardBrowse(
        val requirements: List<com.superdl.launcher.setup.SetupRequirements.Requirement>,
        val index: Int,
        /**
         * ELSŐ INDÍTÁS: ilyenkor a varázsló nem enged tovább, amíg az
         * alapvető tételek hiányoznak. A menüből indított varázslónál
         * ez hamis — ott bármikor ki lehet lépni.
         */
        val firstRun: Boolean = false
    ) : AppFlow()

    /**
     * A varázsló megvárja, hogy a felhasználó visszatérjen a rendszerképernyőről.
     *
     * MIÉRT KELL KÜLÖN ÁLLAPOT: a szerepkör- és rendszerbeállítás-kérések másik
     * Activity-ben futnak. Amikor visszatérünk, újra kell mérni az állapotot —
     * enélkül a varázsló azt hinné, hogy még mindig hiányzik.
     */
    data class SetupWizardAwaitReturn(
        val requirement: com.superdl.launcher.setup.SetupRequirements.Requirement,
        val firstRun: Boolean = false
    ) : AppFlow()

    /**
     * NEM LEKÉRDEZHETŐ TÉTEL megerősítése (gyártói automatikus indítás).
     * A program nem tudja megmérni, ezért megkérdezi — és ezt ki is mondja.
     */
    data class SetupWizardConfirmManual(
        val requirement: com.superdl.launcher.setup.SetupRequirements.Requirement,
        val firstRun: Boolean = false
    ) : AppFlow()


    object GpsRouteRecordingActive : AppFlow()

    data class GpsRouteAwaitName(val route: com.superdl.launcher.route.GpsRouteRecording) : AppFlow()

    data class GpsRouteBrowse(
        val routes: List<com.superdl.launcher.route.GpsRouteRecording>,
        val index: Int,
        val deleteMode: Boolean = false,
        val guideMode: Boolean = false
    ) : AppFlow()

    data class GpsRouteDeleteConfirm(
        val route: com.superdl.launcher.route.GpsRouteRecording,
        val routes: List<com.superdl.launcher.route.GpsRouteRecording>,
        val index: Int
    ) : AppFlow()

    data class GpsRouteGuidingActive(val route: com.superdl.launcher.route.GpsRouteRecording) : AppFlow()

    data class LocationProfileBrowse(
        val profiles: List<com.superdl.launcher.locationwatch.LocationProfile>,
        val index: Int,
        val deleteMode: Boolean = false
    ) : AppFlow()

    data class LocationProfileActions(
        val profile: com.superdl.launcher.locationwatch.LocationProfile,
        val profiles: List<com.superdl.launcher.locationwatch.LocationProfile>,
        val profileIndex: Int,
        val actionIndex: Int = 0
    ) : AppFlow() {
        companion object {
            val OPTIONS = listOf(
                "Figyelő indítása",
                "Fotók bővítése",
                "Fotók törlése"
            )
        }
    }

    data class CardBrowse(
        val cards: List<com.superdl.launcher.cardorganizer.CardProfile>,
        val index: Int,
        val deleteMode: Boolean = false
    ) : AppFlow()

    data class CardDeleteConfirm(
        val card: com.superdl.launcher.cardorganizer.CardProfile,
        val cards: List<com.superdl.launcher.cardorganizer.CardProfile>,
        val index: Int
    ) : AppFlow()

    data class LocationProfileDeleteConfirm(
        val profile: com.superdl.launcher.locationwatch.LocationProfile,
        val profiles: List<com.superdl.launcher.locationwatch.LocationProfile>,
        val index: Int
    ) : AppFlow()

    data class CameraQualityBrowse(
        val profiles: List<com.superdl.launcher.camera.CameraQualityProfile>,
        val index: Int
    ) : AppFlow()
}

/** A beszédtéma-felvétel három szakasza. Lásd `AppFlow.VoiceThemeRecord`. */
enum class VoiceRecordStage {
    /** Készen állunk: jobbra indul a felvétel. */
    READY,

    /** Épp veszünk fel: jobbra leáll és ment. */
    RECORDING,

    /** Felvéve, visszajátszva: jobbra megtartom, balra újra. */
    REVIEW
}