# A közös beszédtéma-katalógus — hogyan működik, és mi a dolgod

**2026-09-06.** Ez az útmutató arról szól, hogy a beküldött beszédtémákból
hogyan lesz olyan téma, amit mindenki lát a telefonján.

---

## ELŐSZÖR: mi megy magától, és mi nem

**A LETÖLTÉS OLDAL MAGÁTÓL MEGY.** Aki felteszi az 1.63.1-et, és megnyitja a
Beszédtémák a közösből pontot, az azt látja, ami a katalógus-fájlban van.
Ehhez semmit nem kell csinálnod: a telefon a GitHubról olvassa a listát,
minden indításkor frissen. Ha ma felteszel oda egy témát, holnap mindenki
telefonján ott van — **alkalmazás-frissítés nélkül.**

**A FELTÖLTÉS OLDAL NEM MEGY MAGÁTÓL, ÉS EZ SZÁNDÉKOS.** A telefonon a
Beküldöm a közösbe gomb feltölti a témát egy ideiglenes tárhelyre, és küld
neked egy levelet a linkkel. A téma ettől **nem kerül be a katalógusba.**

Miért nem: a katalógus egy GitHub tároló, nem kiszolgáló. Egy „bárki
feltölti, és azonnal megjelenik mindenkinél" megoldáshoz saját szerver
kellene fiókokkal — és azzal együtt az is, hogy idegen hang úgy kerül be egy
vak ember telefonjába, hogy azt előtte **senki nem hallgatta meg.** Ő nem
tudja megnézni, mit tölt le. Amíg ez így van, marad az emberi kapu.

**Amit viszont nem kell elviselni: hogy ez neked kézi munka legyen.** Ezért
készült ez a négy parancs.

---

## A NÉGY PARANCS

Mind a négyet a SuperDL-Android mappából futtatod.

### 1. Megnézem, mit küldtek

```
.\tools\temanezd.ps1 <link vagy fájl>
```

Például:

```
.\tools\temanezd.ps1 https://0x0.st/valami.json
.\tools\temanezd.ps1 C:\Users\msn\Downloads\tema-vicces.json
```

Mit csinál:

- letölti,
- **ellenőrzi**, és amit nem ismer, azt eldobja (lásd lentebb),
- kicsomagolja a hangokat egy mappába, **sorszámozott, magyar nevekkel**:
  `1-merules.m4a`, `2-feltoltve.m4a`, `3-tolto-bedugva.m4a`,
  `4-tolto-kihuzva.m4a`, `5-jo-reggelt.m4a`, `6-jo-ejszakat.m4a`,
- kiírja a téma nevét, a szerzőt, a leírást, hogy hány eseményhez van hang,
  és **klipenként a hosszot másodpercben**,
- megnyitja a mappát, hogy egyből tudd hallgatni.

**Közzé NEM tesz semmit.** Ez a lépés csak neked szól.

A hosszra külön figyelmeztet: hat másodperc fölött kiírja, hogy hosszú.
Ezek a hangok naponta többször szólalnak meg, és a klip **után** még
elhangzik a százalék is — ami elsőre kedves, az a huszadik alkalomra teher.

### 2. Meghallgatom

Ez a te dolgod, és ez a lényeg. A mappa a képernyőn megjelenik, a fájlok
sorrendben vannak. Ha jó, mehet tovább. Ha nem, töröld a mappát, és nem
történt semmi — a beküldő fájlja a saját ideiglenes tárhelyén marad, oda
nem nyúltunk.

### 3. Közzéteszem

```
.\tools\temakozze.ps1 <azonosító>
```

Az azonosítót az előző lépés kiírja. Például:

```
.\tools\temakozze.ps1 nagypapa_hangja
```

Mit csinál:

- feltölti a **megtisztított** téma-fájlt a tároló `mobil` ágára
  (`temak/tema-<azonosító>.json`),
- felveszi a sort a `mobil-katalogus.json`-ba: név, szerző, kategória
  (Hangzás és megjelenés), méret, verzió.

Ezután **azonnal él.** Nem kell kiadás, nem kell frissítés, nem kell
semmi. A következő telefonon, ami megnyitja a listát, már ott lesz.

**Ha ugyanazzal az azonosítóval már van fent téma, a parancs MEGÁLL**, és
kiírja, ki a jelenlegi szerzője. Ez a védelem az ellen, hogy valaki — akár
véletlenül — felülírja más munkáját. Ha tudatosan új változatot teszel fel:

```
.\tools\temakozze.ps1 nagypapa_hangja -Felulir
```

Ilyenkor a verziószám magától eggyel nő, és a telefonokon „frissítés érhető
el" lesz belőle.

### 4. Ha meggondolod magad

```
.\tools\temavissza.ps1 <azonosító>
```

Leveszi a katalógusból. A téma **fájlja megmarad** a tárolóban, tehát akinél
már fent van, annál működik tovább — csak új letöltés nem indul rá. Ha a
fájlt is törölnéd:

```
.\tools\temavissza.ps1 <azonosító> -Fajlt
```

### És bármikor: mi van most fent

```
.\tools\temalista.ps1
```

Felsorolja a katalógusban lévő beszédtémákat névvel, szerzővel, verzióval
és mérettel — és a végén azokat is, amiket **megnéztél, de még nem tettél
közzé**, hogy ne felejtsd el őket.

---

## AMIT AZ ELLENŐRZÉS KISZŰR

A beküldött fájl **idegen adat**. A szkript nem teszi közzé változatlanul:
szétszedi, darabonként ellenőrzi, és **újraépíti csak az ismert mezőkből**.

Amit átenged:

| mező | mit csinál vele |
|---|---|
| `id` | ékezet és szóköz nélkülire alakítja, 40 karakterre vágja |
| `nev` | vezérlőkarakterek ki, 60 karakter |
| `szerzo` | vezérlőkarakterek ki, 60 karakter |
| `leiras` | vezérlőkarakterek ki, **240 karakter** — ez a telefonon elhangzik |
| `nyelv` | 8 karakter |
| `hangok` | **csak a hat ismert esemény**, csak ismert formátum |

Amit eldob:

- **minden más mezőt** — ami nem ezen a listán van, az nem kerül a
  katalógusba;
- **az ismeretlen eseményeket** — ha valaki kitalál egy hetediket, kiesik;
- a **1 kB alatti** klipet (csonka másolás) és a **600 kB fölöttit**;
- a nem hangformátumú bejegyzést (csak wav, mp3, m4a, ogg, opus, aac);
- a 4 MB fölötti csomagot egészében.

A jelentésben külön kiírja, ha a beküldött fájl nagyobb volt, mint a
megtisztított — vagyis ha volt benne felesleg.

**Miért fontos ez:** így a katalógusba nem kerülhet be semmi, ami nem
hangfájl és nem a hat esemény valamelyike. Ezt nem bizalmatlanságból
csináltam: egyszerűen nincs értelme megbízni egy fájlban, amit nem te írtál.

---

## AMI SZERKEZETILEG NEM TUD ROSSZ LENNI

Egy beszédtéma **legfeljebb hat eseményhez** szólhat: merüléskor, feltöltve,
töltő bedugva, töltő kihúzva, jó reggelt, jó éjszakát.

Az S.O.S., a gyógyszer emlékeztető és az ébresztő **nincs köztük**, és nem
is lehet. Egy téma tehát lehet ízléstelen, de **veszélyes nem** — nincs hol
az lennie. És a százalék a klip után **mindig elhangzik**, tehát a vicc soha
nem eszi meg az adatot.

Ezért lehet nyugodtan bárkitől csomagot fogadni.

---

## A TELJES ÚT, EGYBEN

1. Valaki felveszi a saját témáját a telefonján (Beszédtéma felvétele).
2. Beküldi: **Beküldöm a közösbe**. A program feltölti, és megnyitja a
   levelezőjét egy kész levéllel — benne a link, a téma adatai, és a
   nyilatkozat, hogy a felvétel az övé.
3. Megérkezik hozzád a levél.
4. `.\tools\temanezd.ps1 <link>` — letölti, szűri, kicsomagolja.
5. **Meghallgatod.**
6. `.\tools\temakozze.ps1 <azonosító>` — és él.
7. Mindenki telefonján megjelenik a Beszédtémák a közösből listában,
   ahol meghallgathatják letöltés előtt.

Négyes és hatos lépés együtt fél perc.

---

## HA KÉSŐBB SOK LESZ

Ha egyszer napi több beküldés jön, akkor jöhet űrlap vagy GitHub-os
beküldés, esetleg automatikus közzététel egy jóváhagyó gombbal. De addig ez
a legolcsóbb működő megoldás — és ami fontosabb: **ebben az emberi kapu nem
plusz munka, hanem maga a minőség.** Te vagy az egyetlen, aki tudja, hogy
egy hang jó-e egy vak ember telefonján.
