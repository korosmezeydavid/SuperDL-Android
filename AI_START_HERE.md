# SuperDL — OLVASD EL ELŐSZÖR (AI asszisztensnek)

**Utolsó frissítés:** 2026-09-01 | **Verzió:** 1.57.0 (versionCode 103)

> **2026-09-01 este: KIADVA — 1.57.0. AZ S.O.S. TELJES LÁNCA ÉS A FELÜLET
> ELFORGATÁSA.**
>
> **A legfontosabb: JAVÍTVA EGY SÚLYOS S.O.S. HIBA.** Az 1.56.0-ig a lánc nem
> nézte a hívás állapotát: húsz másodperc után rátárcsázott a következő számra
> **akkor is, ha az elsőt felvették** — vagyis el tudta vágni pont azt a
> hívást, ami sikerült. Mostantól a SuperDL alapértelmezett telefon
> alkalmazásként látja a hívásállapotot (`SosCallWatcher`).
>
> **Új: az S.O.S. teljes lánca.** Négy SMS AZONNAL kimegy a helyzettel (akkor
> is, ha felveszik a hívást — a hívás azt intézi, hogy tudjanak rólad, az SMS
> azt, hogy megtaláljanak), aztán két kör hívás, végül 112. A hangpostát nem
> heurisztikával szűrjük: a lánc a hívás vége után mindig folytatódik, a
> felhasználó állítja le az S.O.S. újraindításával. **Élesben tesztelve.**
>
> **Új: Felület elforgatása** (`Beállítások → Felület elforgatása`) — a négy
> söprés jelentése átrendezhető iPhone-szokásra, mind a 41 képernyőn és a
> képernyőolvasóban. Két hibával, amit a tesztelés hozott elő: a szabály
> kimondása kétszeresen fordult (a program lefordította a saját tanítását), és
> a szófordító elrontotta volna a navigációs útvonal-leírásokat is („fordulj
> jobbra a saroknál"). Mindkettő javítva — lásd `gestures/GestureWords.kt`.
>
> Továbbá: varázsló billentyűzet-választó javítás, S.O.S. visszaszámlálás
> ki-be kapcsolható, fájlkezelő átnevezés, rádió időzített felvétel.
> GitHub kiadás: `v1.57.0`, a `verzio.json` frissítve a `mobil` ágon.

> **2026-09-01: KIADVA — 1.56.0.** Beállítás varázsló friss telepítéshez,
> csoportos fájlműveletek, felvételek a `/Recordings/` mappában, névjegyzék
> beállítások, könyv/zene/rádió törlés. Javítva: OPML-import összeomlás
> (OutOfMemoryError), podcast toplista és keresés. Az alapértelmezett telefon
> alkalmazás FONTOS helyett **ALAPVETŐ** lett — az S.O.S. lánc a hívásállapotra
> épül. GitHub kiadás: `v1.56.0`, a `verzio.json` frissítve a `mobil` ágon.

> **2026-08-30: NAGY KÖR — a képernyőolvasó öt új képessége.** Elem-ujjlenyomat,
> címkekezelő, címkecsomagok, hangtérkép, műveletsorok, beküldés. Részletek a
> 7. szakasz „2026-08-30" részében. **A verziószám NEM emelkedett** — a munka a
> debug variánsban van a telefonon, kiadás nem készült.

## GITHUB (2026-08-29-től)

**Repó: https://github.com/korosmezeydavid/SuperDL-Android — NYILVÁNOS.**
A 2026-07-08 óta felgyűlt 434 fájlnyi munka egy commitban rögzítve és felpusholva
(`26fb93c`). Ág: `master`. A `.git` addig **remote nélkül** állt: a képernyőolvasó,
a gesztus-iskola és minden más 7 hétig csak ezen a merevlemezen létezett.

**Tesztelői letöltő-link (mindig a legfrissebb kiadásra visz):**
`https://github.com/korosmezeydavid/SuperDL-Android/releases/latest/download/SuperDL.apk`

Ez azért működik, mert minden kiadáshoz feltöltünk egy **verzió nélküli**
`SuperDL.apk` másolatot is a verziózott `SuperDL-<verzió>.apk` mellé.
**Új kiadásnál MINDKETTŐT fel kell tölteni, különben a link elromlik.**

> ⚠️ **A REPÓ NYILVÁNOS — KÖTELEZŐ TITOK-SZKEN MINDEN PUSH ELŐTT.**
> A `.gitignore`-ban 2026-08-29-ig hiányzott egy sorvégtörés
> (`*.keystoreapp/src/main/assets/ably_key.txt` egyetlen sorban), ezért **sem az aláíró
> kulcs, sem az Ably-kulcs nem volt kizárva**. Javítva, de ne ronts vissza rajta.
> Amit soha nem szabad kiküldeni: `app/superdl-release.keystore`, `keystore.properties`,
> `app/src/main/assets/ably_key.txt`, `local.properties`.
> Ha az aláíró kulcs kiszivárog, bárki aláírhat hamis „frissítést" a program nevében.
>
> A `.gitignore` kizárja továbbá a build-kimeneteket, az alprojektek `build/` mappáit
> és a bankjegy-tanító képeket (`tools/banknote_yolo/`, `banknote_dataset*` — kb. 1,25 GB).
> **Ezek a képek NINCSENEK mentve sehova** — ha fontosak, külön mentést igényelnek.

Ha te egy AI vagy (Claude, Grok, egyéb), és most kapcsolódtál be a SuperDL
fejlesztésébe: ez a fájl a belépőpont. Olvasd el végig, mielőtt bármit
módosítanál. A részletek a `dokumentumok/fejlesztesi-naplo.txt`-ben vannak.

---

## 1. Mi ez a projekt

Magyar nyelvű Android **launcher vak és gyengénlátó felhasználóknak**.
Gesztusokkal (fel/le/bal/jobb swipe) és hanggal (TTS + diktálás) vezérelhető.
Natív Android app, Kotlin, minSdk 26, targetSdk 34.

A fejlesztő maga is vak, TalkBack képernyőolvasót használ. **Ez nem egy
absztrakt "accessibility feature" — ez a napi használatban lévő telefonja.**
Ebből következik minden alábbi szabály.

## 2. Tervezési alapelvek (ezeket ne sértsd meg)

- **Minden funkciónak hangosan meg kell szólalnia.** Néma sikerre/hibára
  nincs visszajelzés. Ha egy művelet nem megy, mondd ki, miért.
- **Inkább hallgat, mint téveszt.** A pénzfelismerő filozófiája az egész
  appra igaz: a téves információ rosszabb, mint a "nem tudom".
- **Egy menüpont, egy művelet, oda-vissza.** Pl. az egyéni csengőhangnál az
  "Alapértelmezett" választása = törlés. Ne csinálj külön "törlés" menüpontot,
  amit meg kell keresni.
- **Gesztusok:** fel = előző/ismétlés, le = következő, jobbra = belépés/
  megerősítés, balra = vissza/megszakítás (a diktálás is leáll).
- A kód kommentjei magyarul, gyakran a **MIÉRT**-tel kezdve. Tartsd ezt a
  szokást — a "miért" a hasznos, a "mit" látszik a kódból.

## 3. !! A LEGFONTOSABB: DEBUG vagy RELEASE?

**A telefonon a DEBUG variáns fut** (`com.superdl.launcher.debug`).
A release (`com.superdl.launcher`) **NINCS telepítve**.

Ez két külön csomagnév, külön SharedPrefs-szel. Ha release APK-t telepítesz:
- NEM frissíti a meglévőt → **második app** kerül fel, üres beállításokkal
- két launcher közül kellene választani, vakon, TalkBackkel — kellemetlen
- a beállítások (csengőhangok, ébresztők, kedvencek) nem látszanának át

**=> Fejlesztés után mindig: `assembleDebug` + `adb install -r` a debug APK-val.**
Az `install -r` megtartja az összes beállítást.

Ellenőrzés, hogy tényleg frissítés történt-e:
`firstInstallTime` régi + `lastUpdateTime` friss = valódi frissítés, adatok megvannak.

## 4. Környezet

```powershell
cd C:\Users\msn\Documents\SuperDL-Android
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'   # kötelező
$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"  # NINCS a PATH-on
```

Telefon: Ulefone Armor 24, serial `3116TF1010002416`

Szokásos kör:
```powershell
.\gradlew.bat assembleDebug *> build_log_debug.txt; "EXIT=$LASTEXITCODE"
& $adb -s 3116TF1010002416 install -r app\build\outputs\apk\debug\SuperDL-<verzió>-debug.apk
```

## 5. Build buktatók (ezekbe bele fogsz futni)

**TTS HUROK-BUG: gesztus-kiváltott kilépésnél NE `speakThen { finish() }`!**
A `speakThen(msg) { finish() }` a finish()-t csak a TTS BEFEJEZÉSE UTÁN hívja.
Ha a felhasználó közben újra söpör (mert nem történt semmi láthatóan), a még
élő Activity ÚJRA lefuttatja, és a mondat 3-4-szer elhangzik, mielőtt tényleg
kilép. Vakon ez kritikus: a felület "elszalad" a felhasználó tempója alatt.
HELYES minta gesztus-kilépésnél: `tts.speak(msg); finish()` — a beszéd elindul,
a kilépés AZONNALI (a QUEUE_FLUSH miatt a következő képernyő beszéde úgyis
felülírja). 2026-07-18-án 13 helyen javítva (music, podcast, youtube, light,
color, hearingaid, filemanager, textreader, 2 trainer, 5 játék).
KIVÉTEL: egyszeri hibaüzenet + kilépés (pl. "nincs kamera-engedély"), ami
NEM ismételhető söpréssel — ott a speakThen maradhat, mert nem hurkol.

**A PORTÁL ROUTINGJA A QUERY-T LEVÁGVA NÉZI (route, nem path).**
A `handleClient` a nyers `path`-ból csinál egy `route = path.substringBefore("?")`
változót, és a `when` ágak ezt használják (`route == "/backup"`). MIÉRT: a
GET-formok a query-be teszik a PIN-t (`?pin=1234`), és a pontos egyezés
(`path == "/x"`) elbukna, a kérés a 404-es főoldalra esne. Tünet: a gomb
"visszaugrik a főoldalra" letöltés helyett. Az AUTH viszont a nyers `path`-ot
kapja (`isAuthorized(headers, path)`), mert annak kell a `pin=`.

**SOHA ne szerkeszd a .kt fájlokat PowerShell Get-Content/WriteAllLines-szal!**
2026-07-17-én ez tönkretette a WifiPortalServer.kt teljes ékezetes tartalmát
(mojibake: minden magyar karakter elromlott, 160+ helyen). A PowerShell a
rendszer OEM-kódlapján olvas és ír, nem UTF-8-on. Helyreállítás CP1250-es
Python-dekódolással sikerült, de ez szerencse volt. HASZNÁLD a Desktop Commander
edit_block/write_file eszközét, ami helyesen kezeli az UTF-8-at.
Új portál-oldal felvételekor a fül KÉT helyre kell:
1. `PortalControlPages.header()` — a vezérlő-oldalak (SMS, Névjegyek, ...)
   fülsávja, a `tab()` segédfüggvénnyel
2. `WifiPortalServer.buildIndexPage()` (~687. sor) — a FŐOLDAL (`/`) saját,
   kézzel írt `<a class="tab">` sávja
A kettő független! A főoldal címe „SuperDL fájlportál", a vezérlő-oldalaké
„SuperDL vezérlő" — ebből lehet felismerni, melyiket látod. 2026-07-17-én a
Patika Őrangyal fül csak az 1-esbe került be, ezért a főoldalon nem látszott,
és úgy tűnt, mintha a régi kód futna.

**A PORTÁL SZERVERE TÚLÉLI A TELEPÍTÉST.** A `WifiPortalService` foreground
service. Ha a portál BE volt kapcsolva telepítéskor, a régi példány tovább fut,
és a RÉGI oldalt szolgálja ki — az új fül/útvonal nem jelenik meg, hiába jó az
APK. Megoldás: a telefonon Zene és Média → WiFi fájlportál **ki, majd be**
(új PIN-t mond be). Ellenőrzés:
`adb shell "dumpsys activity services com.superdl.launcher.debug"` — fut-e a
WifiPortalService, és mióta.

**APK-tartalom ellenőrzése: a nyers bájtokban KERESNI FÉLREVEZET.** A dex-ek
tömörítve vannak az APK-ban, így a `ReadAllBytes` + `Contains("valami")` **False**-t
ad akkor is, ha a kód benne van. Ki kell csomagolni (`ZipFile::ExtractToDirectory`),
és a `classes*.dex` fájlokban keresni. 2026-07-17-én ez a hamis negatív majdnem
oda vezetett, hogy rossz helyen keressük a hibát.

**MCP timeout ≠ a build meghalt.** A teljes release build ~6 perc, ez túllépi
az MCP hívás időkorlátját. A `start_process` timeout hibát ad, **de a build
tovább fut**. Ilyenkor: `list_sessions` → `read_process_output` a PID-del.
**Ne indíts újat.** A kimenetet fájlba irányítsd (`*> build_log.txt`) — a
`| Select-Object -Last 40` a pipeline végéig pufferel, közben nem látsz semmit.

**R8 / ProGuard hibák release buildnél.** Az R8 elhasal a hiányzó opcionális
osztályokon. A megoldás mindig ugyanaz: az R8 legenerálja a kellő sorokat ide:
`app\build\outputs\mapping\release\missing_rules.txt` → bemásolni a
`app\proguard-rules.pro` végére. Eddig így kezelve: PDFBox JPEG2000,
BouncyCastle LDAP, Tink KeysDownloader.

## 6. Architektúra — amit tudnod kell

Feature-alapú monolit, **nem** Clean Architecture. Ne próbáld annak tekinteni.

- `MainActivity.kt` — **13000+ sor**, menü-állapotgép, TTS, gesztusok
- **495 Kotlin fájl** (~75 400 sor, 26 deklarált szolgáltatás – mérve 2026-08-10),
  lapos csomagstruktúra (`alarm`, `contacts`, `currency`, ...)
- **1 db ViewModel** az egész projektben (`CurrencyRecognizerViewModel`)
- **Nincs** DI, Room, DataStore, Retrofit, Repository, UseCase
- Adatréteg: **41 db `*Store.kt`** — SharedPrefs + JSON
- Build: Groovy `build.gradle` (nem `.kts`), single-module

**Minta-követés:** ha új funkciót kötsz be, keress egy meglévő hasonlót és
kövesd. Pl. az `alarmTonePickerLauncher` volt a minta a névjegy-csengőhanghoz.

## 7. Hol tartunk most (2026-08-29)

**A PROJEKT ÁTLÉPETT A TESZTELŐI FÁZISBA.** Ez a legfontosabb változás: a fejlesztés
már nem csak a fejlesztő telefonján zajlik, hanem külső, vak tesztelők kezében is.
Ebből következik: **a regresszió most drágább, mint az új funkció.**

**Verzió:** forrás 1.55.0 (101). A telefonon a DEBUG variáns naprakész.
Buildek: `SuperDL-1.55.0-debug.apk` (2026-08-23, 183 MB),
`SuperDL-1.55.0-release.apk` (2026-08-16, 132 MB).
A bankjegy-modellek `app/src/debug/assets/`-be kerültek → csak a fejlesztői buildben
utaznak; a felismerő a release-ben KI van kapcsolva (külön, egy hónapos pontosság-mérést kap).

### Ami 2026-07-18 óta elkészült

- **Képernyőolvasó (saját, `screenreader`)** — párbeszédeknél jobbra söprés = igen/OK,
  balra = nem/mégse; bejövő hívás fogadása/elutasítása bármely launcherrel; olvassa az
  első indítás rendszer-engedélykéréseit is; a kapcsolók/jelölőnégyzetek állapotát külön
  be/ki hang jelzi (nem kell elsöpörni és visszajönni).
- **Gesztus-iskola** — 26 vezetett lecke, majd 34 feladatos vizsga (26 gesztus-feladat,
  ami CSAK a célt mondja meg, + 8 életszerű többlépéses feladat). „Elena tanárnő" osztályoz,
  a jegy-kommentárok felvett WAV-ok (`Android/data/<pkg>/files/elena/`).
- **Mátrix-billentyűzet** — egyujjas, telefon-billentyűzet elrendezés, oda kalibrál, ahová
  az ujj esik; csippentés zár, ujjszétnyitás diktálást indít, három ujj rendszer-billentyűzetet vált.
- **Hangoskönyv-lejátszó + PC↔telefon könyvjelző-szinkron** (1.55.0, `/sync/bookmarks` a portálon).
- **Hibajelentés a programból** (2026-08-16): Névjegy → „Hibajelentés küldése". A tesztelő
  bediktálja, mi történt; az app hozzárakja az állapot-pillanatképet (készülék, verzió, futó
  kisegítő szolgáltatások, beszédcsatorna, összeomlás-napló vége), és három utat kínál:
  a telefon saját levelezője előre kitöltve, megosztás bármely appba (WhatsApp/Messenger),
  vagy fájlba mentés és letöltés a WiFi-portálról. Személyes adatot nem tartalmaz.
- **MÁSODIK KÉSZÜLÉKEN VALIDÁLVA (2026-08-15):** Cat/Doogee S62 Pro, Android 11, tiszta
  telepítés. Első próbálkozás azonnal összeomlott (a névjegyek engedély előtt, háttérszálon;
  és a háttérszál-izoláció túl későn épült be) — mindkettő javítva, az újratelepítés tiszta
  volt. Az első engedélyeket TalkBackkel adta meg.
- **`nagytakaritas` QA-kör:** 1–16. mérföldkő lefutott (a 15. a tesztelői fázisra halasztva).
  Eredmények: `dokumentumok/hibalista.txt`, kézi ellenőrzések: `dokumentumok/kezi-teendok.txt`.
- **`realarm` LEZÁRVA (2026-08-14):** az „ébresztő nem szólalt meg" nem hiba volt — két hatórás
  ébresztő létezett, és az egyiken érvényes kihagyás állt. Tanulság a UI-ra: a kihagyás
  beállítása több ébresztőt is érintett, és ez nem volt hallható.

### 2026-08-30 — a képernyőolvasó öt új képessége (MK0–MK9)

Egy nap alatt hét mérföldkő. Minden a `mobil`-ágas tervdokumentum
(`android-uj-funkciok-merfoldkovek.md`, a claude.ai Projektben) szerint.
**A telefonon fut, de MÉRÉSSEL MÉG NINCS LEZÁRVA — lásd lentebb, mi vár rá.**

- **Elem-ujjlenyomat (`ElementFingerprint.kt`).** Egy képernyő-elem azonosítása
  NEM egy jelre épül, hanem tízre, súlyozva: belső azonosító, osztály, szülő
  osztálya, testvér-index, arányos méret és helyzet, kattintható, kapcsoló.
  Az egyezés PONTSZÁM. **A program tudatában van a bizonytalanságának:** 0,86
  fölött kimondja a nevet, 0,68 és 0,86 közt azt mondja, „valószínűleg X", az
  alatt hallgat. ⚠️ **A küszöbök TIPPEK, nem mérésből valók** — a terv szerint
  mérésből kellene jönniük (20 elem, 5 app, frissítés előtt-után).
- **Címkekezelő (`LabelManagerActivity`).** Eddig elnevezni lehetett, de
  listázni, átnevezni, törölni nem — a címke zsákutca volt. Most: lista,
  Átnevezés, Részletek (mire épül a név, mennyire bízhatsz benne), Törlés.
- **Elnevezés billentyűzettel (`LabelInputActivity`).** Eddig CSAK hanggal ment;
  ha nem volt beszédfelismerés, a gomb örökre névtelen maradt. Most a hangos út
  hibájánál is átadja a szót a billentyűzetnek.
- **⚠️ HIBAJAVÍTÁS: a címkék nem voltak benne a mentésben.** A
  `BackupManager.INCLUDED_PREFS` nem sorolta fel a
  `superdl_screenreader_labels`-t → telefoncsere = minden elnevezés elveszett.
- **Címkecsomagok (`LabelPackStore`).** Új modul-típus: `labelpack`. Formátum:
  `dokumentumok/cimkecsomag-formatum.txt`. **A saját címke MINDIG veri a
  közösét** — nem szabályként, hanem SORRENDKÉNT: saját pontos kulcs → saját
  ujjlenyomat → csak azután a csomag. Elvethető, és az elvetés túléli a
  frissítést. Nincs még kiadott csomag a katalógusban.
- **Hangtérkép (`ScreenMap`, `ScreenMapPlayer`).** Jobbra-majd-fel: a képernyő
  SZERKEZETE hangban. Nem leltár — sávokra bont ott, ahol a függőleges hézag
  nagyobb a szokásosnál. **Négy hangnyelv** (Csoportos, Számláló, Pásztázó,
  Beszédes), mert ez hallásélmény, nem logika; jobbra-majd-le vált és rögtön
  le is játssza. Tempó: Nyugodt (alap) / Normál / Gyors — az első próba
  visszajelzése az volt, hogy „ledarálja".
- **Műveletsorok (`macro` csomag).** Balra-majd-fel indítja és állítja le a
  felvételt. **A lépés nem koordináta, hanem ujjlenyomat + címke.** Négy
  megállási pont: nem találja / bizonytalan / veszélyes szó / balra söprés.
  Kapcsolóknál a KÍVÁNT ÁLLAPOTOT jegyzi meg, nem a megnyomást — ha már jó
  állapotban van, kihagyja. Elérhető: Eszközök → Műveletsorok, ÉS a
  képernyőolvasó almenüjéből. **Elena is érti:** „műveletsor <név>".
  Megosztás: a lépésneveket felolvassa küldés előtt, mert azok a képernyőről
  származnak és lehet bennük személyes adat.
- **Beküldés (`LabelSharing`, `LabelSubmitActivity`).** **ALAPBÓL KIKAPCSOLVA**,
  a terv szerint. Egy készülék egy szavazat (véletlen azonosító). A veszélyes
  szavak (töröl, fizet, elküld…) jelölve mennek, és hárman sem élesednek.
- **⚠️ HIBAJAVÍTÁS: duplán bemondott menüpontok.** A Beállításokban minden sor
  kétszer hangzott el. Ok: a megnyomható sor a gyerekei szövegéből kapja a
  nevét, aztán a bejárás a gyerekbe lépve ugyanazt megtalálta. A `walk` most
  magával viszi, mit mondott már el a sor — de az önállóan működő elemek
  (kapcsoló, mező, gomb) SOHA nem esnek ki.

**Amit a felderítésről (`felderites`) tudni kell: MÁR MEG VAN ÍRVA.** A
`TouchExplorer.kt` be van kötve, és **alapból BE van kapcsolva** — vagyis a
tesztelőknél 1.55.0 óta fut. Döntés 2026-08-30: marad bekapcsolva.

**Ami MÉRÉSRE VÁR a telefonon:** az MK1 küszöbei, a hangtérkép hangnyelv-
választása (ez tisztán a fejlesztő füle), a címkecsomag kész-kritériuma
(nincs kiadott csomag), és a műveletsorok viselkedése app-frissítés után.

**Ami külső dologra vár:** MK10 (segítség egy gesztussal) — a super-dl.com-on
futnia kellene valaminek, mert az Ably-kulcs nem mehet böngészőbe;
lásd `dokumentumok/segitseg-terv.txt`. MK11 (élő kísérő) — YOLO + szemüveg.
**MK7 (közös hangnyelv a Windowsszal) 2026-08-30-án ELVETVE.**

### Tesztelői fázis — anyagok készen

| Fájl | Mi ez |
|------|-------|
| `dokumentumok/tezteloifelhivas.txt` | Facebook-poszt a nyilvános teszt meghirdetésére (hosszú + rövid változat), link: `super-dl.com/android/tesztelo` |
| `dokumentumok/gratulalunk.txt` | levél a felvett tesztelőknek (apró lépésekre bontott telepítés) |
| `dokumentumok/herculesdroidosszefoglalo.txt` | összefoglaló Herculesnek a `super-dl.com/#android` oldalrészhez |
| `dokumentumok/teszt-naplozas-terv.txt` | teszt-naplózási terv (jelszó: „naplócska") |

**KÉRDEZD MEG A FEJLESZTŐT:** kiment-e már a felhívás és a levél, és jött-e vissza
tesztelői visszajelzés — ez dönti el, min kell dolgozni.

### Nyitott szálak

- ~~**`felderites`**~~ — **MEGÉPÜLT, és alapból BE van kapcsolva** (2026-08-30-i
  lemez-ellenőrzés derítette ki: a `TouchExplorer.kt` régóta be van kötve).
  A terv `dokumentumok/felderites-terv.txt`-ben megmaradt.
- **`domino` (2026-08-10, TERV)** — hibatűrés: „egy funkció meghibásodhat, a SuperDL nem".
  `dokumentumok/domino-terv.txt`.
- **`bolt` (döntés 2026-08-04)** — a SuperDL NEM megy a Google Play-re, saját modulboltot kap,
  hogy valódi programmodulok legyenek letölthetők. Indoklás: „inkább kizárok egyet, hogy
  beengedjek tizet". Az adat-modul katalógus már él a `mobil` ágon, appon belüli böngészővel,
  kategóriákkal és napi kétszeri frissítés-ellenőrzéssel. `dokumentumok/bolt-terv.txt`.
- **`jajdejolo`** — saját YOLO objektumfelismerő tanító-folyamat (Colab + Drive), ajtófelismeréssel
  kezdve, a bankjegy-felismerő mintájára.
- **`metanetta`** — Meta okosszemüveg-integráció, hogy a kamerás felismerők a szemüveg kameráját
  használhassák. Technikailag lehetséges (Meta Wearables Device Access Toolkit), nem indult el.
  `dokumentumok/meta-szemuveg-terv.txt`.
- **`braille`** — a Braille-billentyűzet kódja 2026-07-31-én eltávolítva (a tesztkészülék nem
  reagált, valószínűleg a hat egyidejű érintés korlátja miatt). A teljes terv megmaradt:
  `dokumentumok/braille-billentyuzet-terv.txt`, egy alkalmasabb készülékhez.
- **Portál: névjegy-szerkesztés** — a 4 lépéses csengőhang-tervből ez az egy maradt, el sem kezdődött.
- **`huf_banknote_detector.tflite`** — a YOLO tanítás lefutott
  (`tools/runs/banknote/huf_detect-2/best.pt`), de a TFLite export nincs bemásolva → a kétlépcsős
  pénzfelismerő ki van kapcsolva, csak a tartalék fut.

A teljes lista: `dokumentumok/fejlesztesi-naplo.txt` 9. szakasz.
⚠️ A napló 2. és 10. szakasza 2026-08-09-nél áll — a fenti 08-10 → 08-29 közti munka
oda még nincs bevezetve.

## 8. Munkamódszer

- **A hosszú session betelik, és mindig rossz helyen.** Ez a projekt sok
  fájl-tartalmat és buildnaplót termel, ami megtölti a kontextusablakot.
  2026-07-17 előtt pontosan így szakadt meg a névjegy-csengőhang munka: négy
  fájl készen állt, csak a MainActivity-bekötés hiányzott — és semmi nem
  őrizte meg, hogy hol tartunk. **Ezért van ez a fájl.** Írj bele, mielőtt
  betelik, ne utána.
- **2026-08-29-től a fejlesztés CSAK Claude-dal folyik** (korábban Grokkal is, eszköz
  szerint váltva). A terminálos munkát Claude + Desktop Commander viszi, mert a
  PowerShell kézi használata vakon kényelmetlen.
- **Van egy claude.ai Projekt: „super dl windows and android".** Ez a közös emlékezet
  a beszélgetések között: minden új téma új beszélgetés, a projekt-dokumentumok viszik
  tovább az állapotot. Fejlesztési kör után a projektet is frissíteni kell, nem csak ezt
  a fájlt.
- **Van egy TESTVÉRPROJEKT:** a SuperDL Windows-változata (akadálymentes letöltő és
  médiaközpont, Python/wxPython) — `C:\Users\msn\Documents\Audacity\SuperDownloader`,
  belépő doksi ott: `HANDOFF.md`. Külön termék, de közös filozófia (lean mag + modulbolt),
  és van közös felület is (Átjáró / könyvjelző-szinkron telefon és PC között).
  ⚠️ A Windows-projektnek MÁS a munkarendje: ott a „create maxima" jelszó indítja a
  kódolást, és a „publikálás" a feltöltést. Ne keverd a kettőt.
- **Nézd meg, mi van a lemezen, mielőtt hiszel a leírásnak.** A kód az igazság.
  Új funkció előtt keress rá: létezik-e már a `*Store.kt`, be van-e kötve
  (`start_search` a HASZNÁLATRA, ne csak a fájl létére).
- **Átvételkor a bekötést ellenőrizd először.** A leggyakoribb félbemaradás:
  az osztály kész, az enum-érték kész, de a `when` ág hiányzik. Vakon ez a
  legrosszabb hibafajta: a menüpont kimondja magát, aztán néma.
- **Terminál parancsokat futtasd le magad** Desktop Commanderrel, ne add oda
  kézi futtatásra.
- **Minden fejlesztési kör után frissítsd a naplót** (`fejlesztesi-naplo.txt`
  2., 9., 10. szakasz) **és ezt a fájlt**, ha a fentiek bármelyike változott.
  Ez nem formalitás: enélkül a következő session vakon indul.

## 9. Fájlok

| Fájl | Mit tartalmaz |
|------|---------------|
| `AI_START_HERE.md` | **ez a fájl** — belépőpont, mindig ezzel kezdd |
| `dokumentumok/fejlesztesi-naplo.txt` | fő napló: állapot, tervek, fejlesztési körök (⚠️ 2026-08-09-nél áll) |
| `SuperDL_Project_State.md` | mély technikai riport (2026-07-03, részben elavult) |
| `CHANGELOG.md` | verzió-változások |
| `dokumentumok/javitas.txt` | pénzfelismerő javítás napló |
| `dokumentumok/osszefoglalom.txt` | korábbi összefoglalók (1.36.x–1.37.x) |
| `app/src/main/assets/elena_tudas_superdl.txt` | Elena asszisztens tudásbázis |

### 9.1 TERVEK ÉS NYOMOZÁSOK — ELŐHÍVÓ JELSZAVAKKAL

A fejlesztő kulcsszóval hívja elő ezeket. Ha egy jelszót mond, EZT a fájlt kell megnyitni.

| Jelszó | Fájl | Miről szól |
|--------|------|------------|
| `felderites` | `dokumentumok/felderites-terv.txt` | érintéses felderítés (MEGÉPÜLT, alapból BE) |
| `segitseg` | `dokumentumok/segitseg-terv.txt` | MK10: mi akadályozza, és mit kell eldönteni |
| `cimkecsomag` | `dokumentumok/cimkecsomag-formatum.txt` | a címkecsomagok formátuma (elsőre kell eltalálni) |
| `domino` | `dokumentumok/domino-terv.txt` | hibatűrés, izolált meghibásodás (TERV) |
| `bolt` | `dokumentumok/bolt-terv.txt` | saját modulbolt a Google Play helyett |
| `braille` | `dokumentumok/braille-billentyuzet-terv.txt` | Braille-billentyűzet (kód eltávolítva, terv megmaradt) |
| `screenreader` | `dokumentumok/screenreader-terv.txt` + `screenreader-tuning-terv.txt` | a saját képernyőolvasó |
| `naplócska` | `dokumentumok/teszt-naplozas-terv.txt` | teszt-naplózás és hibabejelentés |
| `metanetta` | `dokumentumok/meta-szemuveg-terv.txt` | Meta okosszemüveg-integráció |
| `kezi` | `dokumentumok/kezi-teendok.txt` | kézzel elvégzendő ellenőrzések |
| `realarm` | `dokumentumok/realarm.txt` | ébresztő-nyomozás (LEZÁRVA, 2026-08-14) |
| — | `dokumentumok/hibalista.txt` | a `nagytakaritas` QA-kör találatai |
| — | `dokumentumok/katalogus-terv.txt`, `nitaplay.txt`, `hibakereses-terv.txt`, `mobilinfora.txt`, `spdlosszegzo.txt` | további tervek és összegzők |
