## 1.63.10 — A letöltés, aminek végre hangja van

Ez a kiadás három szóból lett: **„a játékok nem töltöttek le."** Ennyit írt
egy új tesztelő az első jelentésében.

Utánamértem, mielőtt bármihez hozzányúltam volna: a kiszolgálón mind a 31
modul rendben volt, egyenként megnézve. Tehát nem ott volt a baj — hanem
abban, hogy a program **nem mondta meg, mi történt.**

### A letöltés eddig elveszhetett, némán

A modul-letöltés a katalógus képernyőjén futott. Ha közben kiléptél onnan — 
vagy a telefon leállította a programot, ami Xiaomin a leggyakoribb eset —,
akkor az eredmény **eltűnt**. Nem hibaüzenet jött, nem is siker: semmi. A fájl
akár le is jöhetett, csak épp soha nem tudtad meg.

Ez volt az egyetlen hálózati funkció a programban, ami nem külön
szolgáltatásban futott. A YouTube, az időzítő, a megosztás, a diktafon és a
rádiófelvétel mind úgy készült — a modul-letöltés kimaradt.

Mostantól **külön szolgáltatás tölt le**, ami túléli a képernyőt, és a végén
akkor is megszólal, ha már máshol jársz a programban. Ha hét másodpercnél
tovább tart, közben is szól, hogy még dolgozik — mert vakon a csend és a
lefagyás megkülönböztethetetlen.

### „Van internet?" — akkor is, amikor volt

A program eddig ugyanazt az egy mondatot mondta mindenre: ha a fájl nem volt
fent a kiszolgálón, ha a kiszolgáló hibázott, ha lejárt az idő, ha nem volt
térerő. Emiatt sokan a wifit kapcsolgatták olyan hiba miatt, aminek semmi köze
nem volt a hálózatukhoz.

Mostantól a program megmondja, **mi történt és kin múlik**:

- „Ez a fájl nincs a kiszolgálón. **Ez a mi hibánk, nem a tiéd** — kérlek
  jelezd."
- „A kiszolgáló most nem enged több letöltést. Várj pár percet."
- „A kiszolgáló hibát jelez. **Ez nem a te telefonod**, próbáld később."
- „A kiszolgáló nem válaszolt időben. Gyenge lehet a térerő."
- „A biztonságos kapcsolat nem jött létre. Ellenőrizd a telefon dátumát."

És ha egyáltalán nincs hálózat, azt **rögtön** megmondja, nem húsz másodperc
néma várakozás után.

### A végtelen kör, ami eddig senkinek nem tűnt fel

Ha egy letöltött kérdéssor hibás volt — mert félbeszakadt a letöltés, vagy
mert a fájl sérült —, a katalógus azt mondta rá, hogy „letöltve", a játék
viszont azt, hogy **„még nincs letöltött kvíz"**, és visszaküldött abba a
menübe, ahol már voltál. Onnan újra letöltötted, és kezdődött elölről.

Most a két eset két külön mondat:

- **„Még nincs letöltött kvíz."** — tölts le egyet.
- **„Egy letöltött kérdéssor hibás, ezért nem tudom megnyitni. Töltsd le
  újra."** — ez már más teendő.

Ha több kérdéssorod van és csak az egyik hibás, a játék elindul a többivel, és
szól, mit hagyott ki.

### Egy söprés a katalógusig

Eddig ez hangzott el: *„a Beállítások, Katalógus, Elérhető modulok pontban
tölthetsz le kérdéssorokat."* Ez igaz — de három menülépés, amit vakon fejben
kell tartani, miközben ki kell lépned onnan, ahol épp vagy.

**Mostantól elég egy jobbra söprés, és ott vagy.** Balra: most nem.

Ez a Játékoknál és a „Letöltött modulok" pontnál is működik.

### Két csendes hiba, amit menet közben találtam

**A verzió-korlát.** A katalógusban minden modulnál szerepel, hogy melyik
Super DL kell hozzá — a program ezt beolvasta, és **soha nem nézte meg**. Így
le lehetett tölteni olyat, amit a motor nem tud elolvasni, és utána megint a
fenti végtelen kör jött. Mostantól a lista előre bemondja: „Ehhez újabb Super
DL kell."

**A félbevágott fájl.** A letöltés egyenesen a helyére írt. Ha a telefon
közben leállította a programot, egy csonka fájl maradt ott. Most előbb
ideiglenes helyre ír, és csak a végén kerül a helyére.

---

**A kérdéssorok, ha még nincsenek meg:** Beállítások, Katalógus, Elérhető
modulok. Harmincegy modul van fent, ebből huszonhét kvíz, összesen több mint
tízezer kérdés.

---

**HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!**
