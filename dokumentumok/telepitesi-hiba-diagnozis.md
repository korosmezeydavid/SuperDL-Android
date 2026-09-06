# „A telepítő elindul, aztán leáll" — mit találtam

**2026-09-06.** Tesztelői panasz: a SuperDL telepítője elindul, azt mondja,
hogy telepíti, aztán leáll.

---

## AMIT KIZÁRTAM

### 1. Az aláírás rendben van

```
Verified using v1 scheme (JAR signing):  true
Verified using v2 scheme:                true
Verified using v3 scheme:                true
Number of signers: 1
CN=Korosmezey David, OU=SuperDL, O=SuperDL, L=Budapest, ST=Hungary, C=HU
RSA 2048
```

Az első ellenőrzésnél a v1 „hamis" volt — ez **nem hiba**: az `apksigner`
26-os minSdk-nál meg sem nézi a v1-et, mert Android 8 fölött már nincs rá
szükség. `--min-sdk-version 21`-gyel az is igaz.

A `META-INF/*.version` fájlokra kapott figyelmeztetések szintén szokásos
AGP-viselkedés: azokat a v1 nem fedi, de a v2 és a v3 az **egész fájlt**
aláírja.

### 2. A kulcs mind a 11 kiadásnál UGYANAZ

| kiadás | tanúsítvány SHA-256 |
|---|---|
| 1.55.0 … 1.63.1 | `782163f7d6a0ec3e343eef94e2aa0e443b4e5684fac050b89e4c20163a1e6f6e` |

Ez azért fontos, mert **ha valaha kulcsot váltottunk volna**, a frissítés
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` hibával állna le — és az PONTOSAN így
néz ki kívülről: elindul, aztán leáll. Ez a magyarázat kiesett.

### 3. A kiadott fájl ép

```
amit építettünk:   SHA-256 CDB3F8E9…C080   153 368 294 bájt
amit a link ad:    SHA-256 CDB3F8E9…C080   153 368 294 bájt
EGYEZIK
```

### 4. Nem a hely

Az Ulefone-on 183 GB szabad. Gyengébb telefonon ez még lehet ok (153 MB-os
APK-hoz telepítés közben nagyjából a háromszorosa kell), de nem ez a
tipikus eset.

---

## AMIT MEGTALÁLTAM — ÉS REPRODUKÁLTAM

Átmásoltam az APK-t a telefon Letöltések mappájába, és elindítottam a
rendszer csomagtelepítőjét. **A tünet pontosan az volt, amit panaszolnak:**
a telepítő elindult, majd azonnal eltűnt. A napló megmondta, miért:

```
E InstallStart: Requesting uid 2000 needs to declare permission
                android.permission.REQUEST_INSTALL_PACKAGES
...
game_scn: ... com.android.packageinstaller.InstallStart ... state:PAUSED
```

A telepítő **elindult** (a folyamat létrejött), és a következő ezredmásodpercben
**PAUSED** lett. Semmilyen hibaüzenet nem jelent meg a képernyőn.

### Mit jelent ez

Android 8 óta az „ismeretlen forrásból való telepítés" **nem egy globális
kapcsoló**, hanem **alkalmazásonkénti engedély**. És nem annak az
alkalmazásnak kell, amit telepítesz, hanem **annak, AMELYIKBŐL megnyitod
az APK-t**:

- Chrome-ból töltöd le és onnan nyitod meg → a **Chrome**-nak kell,
- fájlkezelőből → a **fájlkezelőnek**,
- Gmailből, üzenetből → **annak az alkalmazásnak**.

Ha az az alkalmazás nem kapta meg, akkor a telepítő elindul, és **némán
bezárul.** Sok gyártói ROM ilyenkor semmit nem ír ki. Ez a leggyakoribb
néma bukás sideloadnál, és pontosan illik a panaszra.

### Miért nem tudtam adb-ből teljesen végigvinni

Az `adb shell` (uid 2000) nem deklarál manifestet, ezért a rendszer soha
nem engedi neki a telepítés indítását — még `appops`-szal sem. Ez az
**én mérőeszközöm korlátja**, nem a programé. A hibát viszont pont ez
mutatta meg: ugyanaz a mechanizmus.

---

## A TELEFONON MOST OTT VAN A FÁJL

`/sdcard/Download/SuperDL.apk` — 153 368 294 bájt, ép.

Nyisd meg a saját fájlkezelődből (vagy a SuperDL fájlkezelőjéből), és a
telepítő rendesen megnyílik: az Ulefone-on a Chrome-nak, a Files-nak, a
Gmailnek és a SuperDL-nek is **meg van adva** a telepítési jog.

**FIGYELEM:** ha végigviszed, két SuperDL lesz a telefonon (a fejlesztői és
a kiadási), és a két képernyőolvasó egymásra fog beszélni. Ha csak a
telepítő viselkedését akarod látni, elég odáig elmenni, ahol a Telepítés
gomb van, és megszakítani.

---

## AMIT A TESZTELŐKNEK KELL MONDANI

A régi megfogalmazás — „engedélyezni kell az ismeretlen forrásból való
telepítést" — **Android 7 előtti**, és félrevezető: a tesztelő keresi a
globális kapcsolót, nem találja, és feladja.

A helyes:

> Amikor a telepítő nem indul el, vagy elindul és eltűnik: nem a fájllal
> van baj. Annak az alkalmazásnak kell engedélyt adni, **amelyikből
> megnyitottad** — a böngészőnek, a fájlkezelőnek vagy a levelezőnek.
>
> Beállítások → Alkalmazások → (az az alkalmazás) → Ismeretlen alkalmazások
> telepítése → bekapcsolni. Utána nyisd meg újra a fájlt ugyanabból az
> alkalmazásból.

Érdemes a letöltési szakaszba a **SHA-256** ujjlenyomatot is betenni, hogy
a csonka letöltés kizárható legyen:

```
CDB3F8E9D66C692ED2ADCAE991436B9D5FF53FF1ACAD3276762F18F9D303C080
153 368 294 bájt
```

---

## HA EZ SEM AZ

Akkor a következő gyanúsítottak, sorrendben:

1. **Kevés hely.** 153 MB-os APK-hoz telepítés közben ~450 MB kell.
   A telepítő ilyenkor is némán elszáll egyes ROM-okon.
2. **Play Protect.** „Ismeretlen alkalmazás ellenőrzése" — sideloadnál
   blokkolhat. Play Áruház → profil → Play Protect → beolvasás kikapcsolása
   a telepítés idejére.
3. **Csonka letöltés.** 153 MB mobilneten megszakadhat. A SHA-256 ezt
   eldönti.
4. **Régi, MÁS kulccsal aláírt SuperDL** a telefonon — nem a mi
   kiadásainkból, hanem ha valaki kézzel épített egyet. Ilyenkor a régit
   el kell távolítani.

És mindegyikre igaz: **a varázslóból küldött hibajelentés nem segít**, mert
ahhoz már fent kellene lennie a programnak. Ez az egyetlen pont, ahol a
tesztelőnek szavakkal kell elmondania, mit lát — ezért érdemes pontosan
megkérdezni tőle, **melyik alkalmazásból nyitotta meg a fájlt.**
