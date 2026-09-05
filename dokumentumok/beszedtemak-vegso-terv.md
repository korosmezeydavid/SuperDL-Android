# BESZÉDTÉMÁK (ELENA TÉMA) — VÉGSŐ TERV

**2026-09-05.** Alph ötlete és hat felvett hangfájlja. Ez a végleges terv:
mi hol kapcsolódik be, mi hiányzik a mai kódból, és hogyan lesz ebből
megosztható, letölthető téma-ökoszisztéma. **Kód még nincs.**

---

## 0. A hat hangfájl — mit mond Elena

Gépi átirattal ellenőrizve (24 kHz mono WAV, 2,8–6,5 mp):

| fájl | szöveg | esemény |
|---|---|---|
| elenalowbatrtery.wav | „Éhes vagyok! Adj ennem, kérlek!" | akku merül |
| elenabatteryfull.wav | „Most már jóllaktam. Elviheted a kaját. Köszönöm szépen." | feltöltve |
| elenatoltobedugva.wav | „Ah, ez már nagyon kellett." | töltő bedugva |
| elenamegehes.wav | „Kérek szépen még, még nagyon éhes vagyok!" | töltő kihúzva, még alacsony |
| elenamorning.wav | „Jó reggelt, hogy aludtál?" | reggel |
| elenagoogdnight.wav | „Jó éjszakát! Álmodj szépeket!" | este |

A metafora tiszta és következetes: **a telefon egy élőlény, aki megéhezik és
jóllakik.** Ezt kell végigvinni minden további hangon is.

---

## 1. AZ ALAPELV — egyetlen mondat

> **Bármelyik esemény témázható. Az információ viszont éli túl.**

Ahol a semleges változat számot vagy nevet mondott (töltöttség, időpont),
ott a témás változat lejátssza a klipet ÉS kimondja a tényt is. Nem azért,
mert a vicces hang baj — hanem mert az „éhes vagyok" önmagában nem árulja el,
hogy tíz perc van hátra vagy két óra.

Egyetlen működési megkötés, és az sem ízlés kérdése:

> **Kritikus szinten (5% alatt) a szám ELŐBB hangzik el, utána a klip.**

Három százaléknál hat másodperc mondóka után megtudni a számot nem vicces,
hanem késés. Minden más esetben mehet előre a klip.

Nincs más tiltás. A humor, a stílus és az ízlés a téma szerzőjének dolga.

---

## 2. AMI MÁR KÉSZ A KÓDBAN

### a) ElenaVoice — pontosan erre való

`screenreader/ElenaVoice.kt` már ma is fájlból játszik le hangot:

- mappa: `Android/data/<csomagnév>/files/elena/`
- ha a fájl hiányzik vagy 1 kB-nál kisebb, a program a szokásos felolvasóra
  esik vissza — tehát soha nincs némaság;
- új hang cseréjéhez nem kell újrafordítani semmit.

FIGYELEM: az ElenaVoice a hangot fixen USAGE_ASSISTANCE_ACCESSIBILITY-vel
szólaltatja meg. A mai hangerő-javítás óta a beszéd csatornája beállítás
kérdése — a téma hangjának ugyanazon a csatornán kell szólnia, különben megint
lesz olyan hang, amit a hangerő gomb nem fog.

### b) A töltő-események MÁR LÉTEZNEK

`feedback/DeviceStateEvent.kt` ma is ismeri:

    CHARGER_CONNECTED     — "Töltő csatlakoztatva"
    CHARGER_DISCONNECTED  — "Töltő leválasztva"
    BATTERY_FULL          — "Telefon teljesen feltöltve"
    SCREEN_OFF / SCREEN_ON

A DevicePowerReceiver fogja az ACTION_POWER_CONNECTED / DISCONNECTED üzenetet,
a DeviceStateTonePlayer sípol rájuk, és van „egyszer egy töltési ciklusban"
jelző is (DeviceStateStore.battery_full_announced), közös kapcsolóval.

**Nem eseményt kell építeni, hanem hangot tenni a meglévő eseményre.**

### c) A katalógus MÁR TUD hangkészlet-modult

`catalog/CatalogModule.kt`:

    SOUND_THEME("soundtheme", "hangkészlet")   -> CatalogCategory.LOOKS

A letölthető téma fogalma és kategóriája („Hangzás és megjelenés") megvan.
A katalógus alapelve is stimmel: „a modul mindig ADAT, soha nem futtatható
kód" — egy hangcsomag pontosan ilyen.

### d) A megosztás csatornája is kész

Az 1.61.0 fájlmegosztása (kóddal géptől gépig, vagy ideiglenes tárhelyre)
ingyen elviszi a téma-zipet egyik telefonról a másikra.

---

## 3. AMI HIÁNYZIK

### a) A merülési szint NEM állítható

`battery/BatteryPatrolLogic.kt`:

    val THRESHOLDS = listOf(20, 18, 16, 14, 12, 10, 8, 6, 4, 2)

Az első figyelmeztetés mindig 20%-nál jön.

**Terv:** a felhasználó az ELSŐ figyelmeztetés szintjét választja meg
(5, 10, 15, 20, 25, 30, 40, 50), a program onnan lefelé folytatja a
kétszázalékos létrát. A létra maradjon: vakon egyetlen figyelmeztetés kevés,
mert épp lehet a zsebben a telefon.

### b) Reggeli és esti köszönés

Az ütemezéshez van kész eszköz (AlarmScheduler, a pontos ébresztő engedélye
kérve van a varázslóban).

- **Jó éjszakát**: beállított időpontban, pontos ébresztővel. FIGYELEM: az
  éjszakai csend (PatrolStore.isQuietNow) NEM némíthatja el — ez a köszönés
  természeténél fogva éjszakai. Enélkül bekapcsolva is néma marad, és senki
  nem fogja érteni, miért.
- **Jó reggelt**: két üzemmód:
  1. fix időpontban,
  2. az első képernyő-feloldáskor a beállított idő után — ez az
     ALAPÉRTELMEZETT javaslat. Egy üres szobának köszönni zaj; annak
     köszönni, aki most vette kézbe a telefont, kedvesség. A
     BatteryPatrolService már ma is figyeli az ACTION_SCREEN_ON-t.

---

## 4. NÉVZAVAR — előre tisztázni

A „hangtéma" szó ma FOGLALT: a feedback/SoundTheme.kt a söprés-hangokat
jelenti (alapértelmezett, kattintás, suhogás, koppanós, gombnyomás, sci-fi),
beépített erőforrásokból.

- Söprés-hangok (síp, koppanás), beépített: marad, ahogy van.
- **Beszédtéma** (Elena és társai): fájl alapú, cserélhető, megosztható.

Menü: Beállítások → Hangok → Beszédtéma, a meglévő „Söprés hangtéma" mellett.

---

## 5. AZ ESEMÉNYEK ÉS A KAMPÓK

| esemény | hol van ma | mi kell |
|---|---|---|
| merülés | BatteryAlertHelper.alert() | klip + a százalék kimondása |
| kritikus (5% alatt) | ugyanott | **előbb a százalék**, utána klip |
| feltöltve | DeviceStateEvent.BATTERY_FULL | klip |
| töltő bedugva | CHARGER_CONNECTED | klip |
| töltő kihúzva, még alacsony | CHARGER_DISCONNECTED + szint < küszöb | klip, feltételes |
| jó reggelt | nincs | ütemezés vagy első feloldás |
| jó éjszakát | nincs | ütemezés, csend alóli kivétellel |

A „még éhes vagyok" feltételes: csak akkor szóljon, ha a töltő kihúzásakor a
szint még az első figyelmeztetési küszöb alatt van. Ha tele van, ott a
„jóllaktam" — a kettő együtt kerek.

Későbbi bővítés ugyanezzel a mechanizmussal: fejhallgató csatlakoztatva,
kevés a szabad tárhely, sikeres frissítés utáni első indítás, dátumra
nevezett ünnepi klipek (12-24.wav).

---

## 6. MEGOSZTHATÓ BESZÉDTÉMÁK

### 6.1. A csomag alakja

Egy téma = egy mappa (megosztáshoz zip):

    tema.json          — kézirat: azonosító, név, szerző, verzió, nyelv,
                         esemény -> fájlnév hozzárendelés, leírás
    battery_low.wav
    battery_full.wav
    charger_in.wav
    charger_out_low.wav
    morning.wav
    night.wav

Fájlnevek ékezet nélkül, ASCII. A kézirat teszi lehetővé, hogy egy téma
RÉSZLEGES legyen: aki csak két hangot vesz fel, annak a többi eseménynél a
beépített szöveg szól.

### 6.2. Rétegek — és soha nincs némaság

Egy esemény hangja ebben a sorrendben dől el:

1. saját felvétel (files/elena/) — ez mindig nyer,
2. az aktív letöltött téma,
3. a beépített mondat (felolvasóval).

Ettől lesz igazán jó: letöltesz egy vicces témát, de a „jó reggelt"-et az
unokád hangjával hallgatod.

### 6.3. Terjesztés

- Katalógus: soundtheme típus, „Hangzás és megjelenés" kategória.
- Közvetlenül egymásnak: az 1.61.0 fájlmegosztásával.

### 6.4. Téma készítése a telefonon — ez teszi teljessé

Egy „Beszédtéma készítése" pont, ami végigvezet:

1. felveszi a diktafonnal az eseményeket, egyesével, elmondva, mi következik;
2. bármelyiket ki lehet hagyni;
3. végül nevet és szerzőt kér, becsomagol, és felajánlja a megosztást.

Ettől minden felhasználó szerzővé válhat, számítógép és fájlkezelés nélkül.
Vakbarát módon ez az egész ötlet kulcsa.

### 6.5. Amire figyelni kell

- **Hangerő-kiegyenlítés**: a felvételek szintje nagyon eltérő lesz.
  Importáláskor normalizálni, vagy legalább figyelmeztetni.
- **Hossz**: egy hatmásodperces klip minden töltő-bedugásnál sok. Puha felső
  határ (kb. 4 mp), csomagoláskor figyelmeztetés. Kritikus akkuszintnél a klip
  hossza nem számít, mert a szám már elhangzott.
- **Napi keret**: beállítható (alapból 5). A báj attól báj, hogy ritka. Ez nem
  korlátozás, hanem a funkció védelme: enélkül a felhasználó nem a keretet
  kapcsolja ki, hanem az egész témát.
- **Nyelv**: a kéziratban nyelv-mező.
- **Szerzőség**: a csomagolásnál egy mező a szerző nevének — a katalógusban ez
  látszik is, tehát megéri sajátot készíteni.

---

## 7. A BEÁLLÍTÁSI PANEL

Beállítások → Hangok → Beszédtéma:

| tétel | érték |
|---|---|
| Beszédtéma | ki / Elena / (letöltött témák) |
| Merüléskor | be / ki |
| Első figyelmeztetés | 30% (léptethető) |
| Feltöltve | be / ki |
| Töltő be- és kihúzva | be / ki |
| Jó reggelt | ki / 6 óra 30 |
| Jó reggelt csak feloldáskor | be / ki |
| Jó éjszakát | ki / 22 óra 00 |
| Napi keret | 5 alkalom (léptethető, kikapcsolható) |
| Hangok kipróbálása | sorban lejátssza mindet |
| Saját hang felvétele | lásd 6.4. |
| Beszédtéma készítése és megosztása | lásd 6.4. |
| Vissza | |

A kipróbálás nem díszlet: vakon ez az egyetlen mód ellenőrizni, hogy a fájl
megérkezett-e és szól-e.

---

## 8. MUNKA SORRENDJE

1. VoiceTheme réteg (rétegsor a 6.2. szerint) + panel + próbajáték.
2. A hat esemény bekötése — a töltő-eseményeknél a kampó már megvan.
3. Az állítható első figyelmeztetési szint.
4. Reggeli és esti köszönés, az éjszakai csend alóli kivétellel.
5. Súgó-fejezet.
6. Külön kör: téma-készítő, csomagolás, katalógus-támogatás.

Az 1–5. pont egy kiadásba fér, és önmagában is kerek.

FIGYELEM: már az 1. pontnál mappaként tároljuk a témát, ne szétszórt
fájlokként — különben a csomagolást a 6. pontnál újra kell írni.

---

## 9. AMIT A TERVEZÉS KÖZBEN TÉVEDTEM — feljegyezve

1. Azt írtam, hogy a „feltöltődött" esemény nem létezik. De létezik, a
   DeviceStateEvent-ben, sípoló hanggal és ciklusonkénti jelzővel. Csak a
   kimondott változata hiányzott. A tanulság a régiek mellé: mielőtt azt
   mondom, hogy valami nincs, végig kell nézni a szomszédos csomagot is.
2. Előre kerítést húztam az S.O.S. és a gyógyszer-emlékeztető köré, holott
   azok nincsenek is az események között. Alph joggal nevezte túlóvatosnak.
   A valódi szempont nem az, hogy vicces-e a mondat, hanem hogy a szám
   elhangzik-e — és ezt az 1. pont mondja ki, tiltás nélkül.
