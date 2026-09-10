## 1.63.7 — A csúszkák, a sebesség, és a rádió, ami nem alszik el

Ez a kiadás négy olyan hibát javít, ami mindegyike ugyanabból fakadt: **rossz
kérdést tettünk fel.**

### A képernyőolvasó és a csúszkák

Ha eddig egy csúszkára léptél — hangerő, fényerő, lejátszó-pozíció —, a
program azt mondta, hogy **„ez az elem nem nyomható meg"**.

Igaza volt, és teljesen félrevezetett. Az Androidban a csúszka nem
kattintható elem, tehát a megnyomás tényleg nem sikerült. Csak épp egy
csúszkát nem megnyomni kell, hanem **állítani**.

Mostantól a jobbra söprés állításra nyit: a felfelé söprés növel, a lefelé
csökkent, és minden lépésnél hallod a százalékot. Jobbra vagy balra: kész.

### A képernyőolvasó saját beszédsebessége

Eddig úgy tűnt, hogy a képernyőolvasó „fölvesz egy automatikus sebességet",
amit nem lehet gyorsítani vagy lassítani. Ez pontosan így is volt, két okból.

A beszédmotorja kiolvasta ugyan a Super DL tempóját — de **csak egyszer,
induláskor**. A képernyőolvasó a bekapcsolástól a telefon újraindításáig él,
tehát ami sebességgel elindult, azzal is maradt. Aki utána átállította a
beszédsebességet, az a menüben hallotta a változást, a képernyőolvasóban
soha. És saját tempója sem volt, pedig kell neki: a menüt máshogy akarod
hallani, mint egy idegen alkalmazás gyors átfutását.

Mostantól van saját sebessége, és a **balra majd le** mozdulattal bárhonnan
állítható. Utána a fel-le gyorsít és lassít — és minden lépést rögtön az új
tempóval hallasz, mert egy sebesség-állításnál nem elolvasni akarod a számot,
hanem hallani, milyen lett.

### A rádió, ami elaludt

Lezárt képernyőn egy idő után elhallgatott az adás. Az ok pontosan az, amit a
neve mond: a telefon elalszik. Az Android felfüggeszti a processzort, és ha a
lejátszó nem szól, hogy neki futnia kell, a hang megszűnik. Nem szakadás, nem
hálózati hiba — a rendszer takarékoskodik.

Most a lejátszó ébren tartja magát, amíg szól, és a wifit is — mert az külön
alszik el.

### m3u és pls: a lista beolvasása a rádióba

Aki a fájlkezelőben m3u fájlt keresett, hogy a rádióba juttassa, nem talált
hozzá menüpontot — mert nem is volt. Egy lejátszási listát nem vágólapon át
kell a rádióba tenni, hanem **beolvasni**.

Mostantól a fájlkezelőben az m3u és pls fájlokon ott az első helyen a
**„Hozzáadás a rádióhoz"** művelet, és a benne lévő ÖSSZES állomás bekerül a
kedvencek közé, a lista saját nevein.

Ugyanez a vágólapról is megy: ha a kimásolt cím lejátszási listára mutat —
`listen.pls` és társai —, mostantól nem egy állomást vesz fel, hanem mindet.
A menüpont neve is ezt mondja már: **„Saját állomás vagy lista a vágólapról"**.

### Kvíz: tízezer új kérdés, és véges kör

A katalógusba **25 új kérdéssor** került, összesen **10 013 kérdéssel** —
történelem, irodalom, magyar nyelv, film, zene, élővilág, földrajz, sport,
konyha, vallás, művészet, gyerekeknek. A nagyobb témák könnyebb és nehezebb
változatban is.

Ehhez nem kell frissítés: a Beállítások, Katalógus, Elérhető modulok pontban
már most letölthetők.

Két dolog viszont a programban javult. Egy kör mostantól **húsz kérdés** —
eddig a modul minden kérdését végigkérdezte, ami egy ezerkérdéses
kérdéssornál azt jelentette, hogy a játék soha nem ér véget. A húsz mindig
másik húsz, tehát ugyanabból a modulból hetekig lehet játszani.

És a kvíz-választó **megmondja, hol találsz többet**. Eddig ezt csak akkor
mondta el, ha egyetlen kvízed sem volt — vagyis pontosan az nem hallotta
soha, akinek már volt egy.

---

**HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!**
