## 1.63.8 — A takarékos mód, ami sosem indult el

Két javítás, és mind a kettő tesztelői levélből jött.

### A YouTube takarékos módja összeomlott

Ha bekapcsoltad a takarékos módot — csak a hang, kép nélkül —, a program
bemondta, hogy tölti a hangot, aztán egy idő után azt, hogy nem sikerült.

Nem az internet volt lassú, és nem a te telefonod hibázott. **A lejátszó abban
a pillanatban omlott össze, amikor a hang elindult volna.**

Amikor a takarékos mód lejátszani kezd, a program bejelenti a rendszernek,
hogy „most ez a műsor szól" — ettől tudja kezelni a fülhallgató gombja, az
autórádió és a zárképernyő. Ezen a ponton hiányzott egy alkatrész a kiadási
csomagból, és a szolgáltatás elszállt.

**Miért nem vette észre senki hónapokig:** csak a kiadási változatban
jelentkezett. A kiadás előtt egy tömörítő végigmegy a programon, és kidobálja
belőle, amit nem használtnak lát — ez az alkatrész áldozatul esett. A
fejlesztői változatban, amin a próbák mennek, nincs tömörítés, tehát ott
mindig működött.

Ezt Mezei Géza hibajelentése találta meg. Nem a leírás — a jelentéshez
automatikusan csatolt technikai napló, ami megmutatta a pontos helyet.

### A korlátozott beállítások feloldása: hiányzott egy lépés

Aki áruházon kívülről telepít, annak az Android letiltja a képernyőolvasót és
a PIN segédet, amíg fel nem oldja. Erről eddig is szólt a program — csak épp
**rosszul**.

Azt mondtuk: menj a Beállítások, Alkalmazások, Super DL, három pont menübe, és
ott a „Korlátozott beállítások engedélyezése". Ez igaz, de hiányzott előle a
fele: **ez a menüpont a legtöbb telefonon csak azután jelenik meg, hogy a
rendszer már látta, hogy be akartad kapcsolni a szolgáltatást.**

Aki egyből az alkalmazás oldalára ment, ott nem talált ilyen menüpontot — és
joggal hitte, hogy rossz helyen jár, vagy hogy rosszul mondtuk. Négy tesztelő
akadt el pontosan ugyanitt.

Mostantól a program a teljes sorrendet mondja:

1. **Előbb próbáld meg bekapcsolni**, hadd írja ki a telefon, hogy ez
   korlátozott beállítás — ettől jelenik meg a feloldás.
2. **Utána** Beállítások, Alkalmazások, Super DL, három pont, Korlátozott
   beállítások engedélyezése.
3. **Végül** vissza, és másodszorra bekapcsol.

Ugyanez a három lépés került a hibajelentés naplójába is.

---

**HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!**
