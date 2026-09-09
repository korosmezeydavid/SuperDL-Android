## 1.63.6 — Amit a telefon nem enged, és ami eddig eltűnt

Ez a kiadás két tesztelői levélből született. Mindkettő olyat mutatott meg,
amit magamtól nem találtam volna meg.

### A telefon, ami meg sem kérdezi

Az egyik jelentésben az „alapértelmezett üzenet alkalmazás" **tizenegy
próbálkozás** után is hiányzott. Utánamértem, és kiderült, hogy nem a
felhasználó mondott nemet: a telefonja **meg sem jelenítette a kérdést**,
azonnal, némán visszadobta.

A baj nem is elsősorban ez volt, hanem hogy a napló ezt nem tudta megmutatni.
Egy „próbálkozás: 11" sor két teljesen különböző dolgot jelenthetett — hogy
valaki tizenegyszer nemet mondott, vagy hogy a telefon tizenegyszer meg sem
kérdezte. Vakon ez a különbség minden: az egyik esetben újra kell próbálni, a
másikban az az út egyszerűen nem járható.

Mostantól:

- **Mérjük, mi történt.** Ha a rendszer képernyője fél másodpercen belül
  visszatér, ott nem döntés született, hanem elutasítás.
- **Két sikertelen kör után másik úton megyünk.** A program nem kérdez
  harmadszor is ugyanúgy, hanem megnyitja az alapértelmezett alkalmazások
  listáját, ahol nem kérdés van, hanem választás — és ki is mondja, hogy
  miért váltott.
- **Kimondjuk, hogy nem a te hibád.** „A telefonod meg sem jelenítette a
  kérdést, azonnal visszadobta."
- A hibajelentésbe bekerül, mi volt az utolsó próbálkozás eredménye. A
  következő jelentésnél már nem tippelni kell.

### A podcast, ami letöltött, de nem engedett el

A másik levél három panaszt tett elém, és mind a háromnak más oka volt.

**Nem lehetett hozzáférni.** A letöltött adások olyan mappába kerültek, amit
az Android 11 óta egyetlen fájlkezelő sem nyithat meg. A fájl ott volt, csak
láthatatlanul. Mostantól a közös **Letöltések / Super DL** mappába kerül,
olvasható néven — a műsor és az adás címével, nem egy számsorral.

**Újra letöltötte, ami már megvolt.** A fájl nevét a teljes hivatkozásból
számoltam, a szolgáltatók viszont változó mérőszámot fűznek a végére. Más
hivatkozás, más fájlnév, a program tehát minden alkalommal újnak látta
ugyanazt az adást. Ez most rendben van, és a régi helyen is megkeresi, ami
korábbról megvan — akinek eddig megvolt, annak nem kell újra leszednie.

**Nem lehetett törölni.** Erre egyszerűen nem volt kód. Az adás menüjének a
második sora mostantól attól függ, hogy le van-e már töltve: ha nincs,
„Letöltés offline hallgatáshoz", ha igen, „Törlés a letöltésekből".
Ugyanott, ahol letöltötted. Törlés előtt megkérdezi, mert egy félreértett
söprés ne vigyen el egy adást, amit mobilneten szedtél le.

### Apróság, ami mégis számít

A Letöltéseim bevezetője megmondja, hol vannak a fájlok — de csak akkor
nevezi meg a Letöltések mappát, ha tényleg oda kerülnek. Fájl-engedély
nélkül megmondja, hogy a program saját mappájába mennek, és hogy mit kell
megadni, ha máshogy szeretnéd.

---

**HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!**
