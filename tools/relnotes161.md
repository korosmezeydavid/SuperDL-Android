## FONTOS: ez a kiadás egy olyan hibát javít, ami friss telepítésnél megbénította a programot

Aki **frissen telepítette** a SuperDL-t, a beállítás varázslóban zsákutcába
futhatott: az „Alapértelmezett telefon alkalmazás" és az „Alapértelmezett
üzenet alkalmazás" kérdése **nem jött elő**, ezeket viszont alapvetőként nem
lehetett kihagyni sem. Se előre, se hátra.

Samsung és Xiaomi készüléken is előfordult — **nem gyártófüggő, a program
hibája volt.**

### Mi volt az ok

A szerepkör-kérést (alapértelmezett telefon- és üzenetalkalmazás) rossz módon
indítottuk. Az Android szerepkérő ablaka ilyenkor **némán bezárja magát**:
nincs párbeszéd, nincs hibaüzenet, nincs hang. A program ezt nem vette észre.

Ez a hiba nem most keletkezett — **a varázslóból ez a két lépés soha nem
működött.** Csak friss telepítésen látszott, mert akinél ez a két beállítás
már megvolt, annál a varázsló meg sem mutatta őket.

### Mi változott

- A szerepkör-kérés és a varázsló minden rendszerképernyője helyesen indul,
  tehát a kérdések **előjönnek**.
- A rendszerképernyőről visszatérve a program **magától újramér**, és a lista
  ott nyílik ki, ahol abbahagytad.
- **Zsákutca többé nincs:** ha egy alapvető beállítás kétszer nem sikerül, a
  program kimondja, mit veszítesz vele, és **továbbenged**. Később a
  Beállítások menü Beállítás varázsló pontjában bármikor pótolható.
- Ha egy beállítás a visszatérés után is hiányzik, a program **megmondja,
  miért lehet** — nem hallgat.
- **Korlátozott beállítás:** Android 13-tól a rendszer letiltja az
  áruházon kívülről telepített alkalmazásnak az akadálymentesítési kapcsolót
  (nálunk: a képernyőolvasót), az értesítés-hozzáférést és a fölérajzolást. A
  program mostantól kimondja, hogy ez nem a te hibád, és hol lehet feloldani:
  Beállítások, Alkalmazások, Super DL, három pont menü, Korlátozott
  beállítások engedélyezése.
- A beszédhez kötött műveletek nem tudnak elakadni, ha a beszédet valami
  félbeszakítja. Ez az egész programot érinti, nem csak a varázslót.
- Javítva a WiFi fájlportál indulása Android 14 és újabb rendszeren
  (hiányzó rendszerengedély miatt nem indult el).

---

## ÚJ: fájl- és mappamegosztás

A **Fájlkezelőben** minden fájlon és mappán ott az új **Megosztás** pont.
Mappát a program előbb becsomagol, aztán oszt meg.

Három út, és a különbség köztük az, hogy **ki láthatja a fájlt**:

1. **Küldés másik programmal** — a fájl a telefonon marad, egy másik
   alkalmazás viszi tovább (levél, csevegő, Bluetooth).
2. **Küldés kóddal a másik gépre** — közvetlenül a másik készülékre,
   végpontok között titkosítva, felhő nélkül. Ugyanaz a megoldás, mint a
   windowsos SuperDL fájlküldés modulja, tehát **a telefon és a gép egymásnak
   is tud küldeni**. A kód egy szám és két szó; a program kimondja, betűzi,
   vágólapra teszi, és egy mozdulattal üzenetben elküldhető.
3. **Feltöltés ideiglenes tárhelyre** — hat tárhely közül választhatsz, és a
   **link magától a vágólapra kerül**. A program minden feltöltés előtt
   kimondja, hogy **aki megkapja a linket, letöltheti a fájlt** — iratot,
   orvosi papírt, jelszót ne ezen az úton küldj.

**Megosztási előzmények:** mit, hova töltöttél fel, és **meddig él még**
(„még két nap és négy óra"). A link újra vágólapra tehető, elküldhető,
betűzve felolvastatható, és ahol a tárhely engedi, **utólag törölhető is**.

A **Fájlátvitel és megosztás** menü mostantól az **Eszközök** alatt is ott
van — a Média alól nem került ki.

Súgó: két új fejezet (Megosztás, Megosztási előzmények) és egy a kóddal
küldésről.

---

**A megosztás vadonatúj funkció.** Ki van próbálva, de sok a benne dolgozó
mozgó alkatrész — ha hibába futsz, kérlek jelezd, és javítjuk.

**Használat csak tartalék készüléken egyelőre, vagy saját felelősségre!**
