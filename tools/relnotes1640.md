## 1.64.0 — Minden kedden öt óra, és az üzenet, ami megmondja az igazat

Ez az első kiadás a Védőháló nevű munkából, ami az ébresztőt, az SMS-t és a
biztonságot köti össze. Két dolog van benne.

### Ébresztő, ami csak a kiválasztott napokon szól

Eddig négy lehetőség közül lehetett választani: egyszeri, minden nap,
hétköznap, hétvégén. Ami hiányzott: **„csak kedden"**, vagy „hétfőn és
csütörtökön".

Mostantól az ismétlésnél az ötödik lehetőség az **„egyéni napokon"**. Ha ezt
választod, jön egy képernyő a hét napjaival:

- **fel-le** — lépkedés a napok között, hétfőtől vasárnapig
- **jobbra** — a nap kijelölése vagy a kijelölés törlése
- **balra** — kész

Minden lépésnél megmondja, hol tartasz: *„Kedd, kijelölve. Összesen 2 nap."*
Így nem kell fejben számolni, mit jelöltél be.

Ha egy napot sem jelölsz ki, a program nem enged tovább — és megmondja, miért:
egy ilyen ébresztő soha nem szólalna meg.

**Két hibát is javítottunk hozzá**, amik eddig rejtve maradtak, mert nem volt
mivel előhozni őket:

A **„Következő ébresztő"** eddig csak az időpontot nézte, az ismétlést nem. Egy
csak szombatra beállított ébresztőt szerdán is következőként mondott be.

A **napok felsorolása** a rendszer számozását követte, ahol a vasárnap az első.
Így azt mondta volna, hogy „vasárnap, hétfő, kedd". Most hétfővel kezd, ahogy
magyarul illik — és vakon a felolvasott sorrend az egyetlen fogódzó.

### Az üzenet, ami eddig hazudott

Ez a fontosabbik.

Amikor elküldtél egy SMS-t, a program azt mondta: **„Üzenet elküldve."** Csakhogy
ezt abban a pillanatban mondta, amikor átadta az üzenetet a rendszernek — a
hálózat válasza ekkor még meg sem érkezett.

Ha a hálózat elutasította a küldést, a program **akkor is azt mondta, hogy
elment.**

A legbosszantóbb ebben az, hogy a program TUDTA az igazat. A hibát megkapta,
emberi nyelvre is lefordította — „nincs hálózat", „a rádió ki van kapcsolva" —,
és aztán **senki nem olvasta el.** Ez a kód évek óta ott van, és soha nem jutott
el a füledig.

Mostantól:

- Ha a hálózat pár másodperc múlva elutasítja, a program **külön szól**:
  „Figyelem: az üzenet Katinak mégsem ment el: nincs hálózat. Próbáld újra."
- Új menüpont az SMS üzenetek alatt: **„Utolsó üzenet sorsa"**. Ez megmondja,
  hogy az utolsó üzenet elment-e, és hogy **megérkezett-e a címzetthez**.

Ez utóbbi külön magyarázatot érdemel. Egy SMS-nek három állapota van, és ezek
időben szétesnek: a program átadja a rendszernek (azonnal), a hálózat átveszi
(pár másodperc), és a címzett készüléke megkapja (**akár percek**). A
kézbesítést a küldés pillanatában nem lehet bemondani, mert még nem tudjuk.
Ezért van külön menüpont: a választ akkor kérdezheted meg, amikor már
megérkezett.

És ha azt mondja, hogy a címzett készüléke még nem jelezte vissza, az **nem
baj**: nem minden szolgáltató küld ilyen visszajelzést.

### Két csendes hiba, amit menet közben találtunk

**Két SMS-küldő volt a programban.** A naptári emlékeztetőkhöz tartozó küldés
nem a közös küldőt használta, hanem egy saját másolatot — és abból hiányzott a
szám tisztítása, a visszajelzés és a mentés a kimenő üzenetek közé. Most már
mind a kettő ugyanazon az úton megy. Két küldő két igazságot jelent.

**A több címzettes hivatkozás.** Ha egy weboldal vagy egy alkalmazás olyan
üzenet-linket adott át, amiben két szám szerepelt, a program a kettőt
összeragasztotta egyetlen, nem létező számmá — és az üzenet csendben rossz
helyre ment. Most az elsőt veszi. A valódi többcímzettes küldés a következő
kiadásban jön.

---

**HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!**
