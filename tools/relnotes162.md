# SuperDL Android 1.62.0

## Hibajelentés a beállítás varázslóból — ha elakadsz, tudjuk meg, miért

Ha a beállítás varázslóban valamelyik lépés nem akar menni, a lista **legvégén**
mostantól van egy tétel: **„Nem megy tovább? Hibajelentés küldése."** Jobbra
söpréssel elküldhető.

Miért kellett: az „nem megy" nekünk nem információ. A jelentés megmutatja,
**miért** nem megy — melyik beállítás hiányzik, hányszor próbáltad, és hogy a
rendszer megnyitotta-e egyáltalán a kért képernyőt ezen a készüléken. Benne van
az is, ki birtokolja most az üzenet-, telefon- és kezdőképernyő-szerepkört, és
hogy a program áruházon kívülről lett-e telepítve (mert Android 13 óta ilyenkor
a rendszer letiltja a képernyőolvasó kapcsolóját).

Küldeni a megszokott három módon lehet: saját levelezővel, más alkalmazással,
vagy mentés a telefonra. Küldés után a program **visszavisz a varázslóba**, nem
tesz ki a főmenübe.

**Személyes adat nincs a jelentésben** — se névjegy, se üzenet, se helyzet.

## A beszédet végre le lehet halkítani

Jelezte egy tesztelő, hogy a program „üvölt", és a hangerő gomb nem fog rajta.
Igaza volt, két okból is:

- a beszéd hangján rajta volt egy jelző (`FLAG_AUDIBILITY_ENFORCED`), amivel a
  rendszer **kikényszeríti** a hangot — ez a fényképezőgép zárhangjához való,
  nem beszédhez, és a felhasználó hangereje nem hatott rá;
- a hangerő gombok nem a beszéd csatornájára voltak kötve, ezért csendben a
  csengőhang hangerejét állították.

Mindkettő javítva: a hangerő gombok mostantól mindig a beszédet halkítják.

## A zenelejátszó az összes számot látja

Eddig **300 szám** volt a felső határ, és a vágás a cím szerint rendezett lista
elején történt — akinek nyolcszáz száma volt, az az ábécé első háromszázát látta
csak, visszajelzés nélkül. A korlát megszűnt.

## A könyvek végre törölhetők

Három külön ok játszott össze, és a program mindet sima fájlként kezelte:

- a **hangoskönyv** egy MAPPA, azt a rendszer nem törli egyszerű
  fájltörléssel — a program mégis fájlhozzáférés hiányára panaszkodott;
- a **médiatárból** származó bejegyzés nem fájl, hanem azonosító: a program
  kimondta, hogy törölve, és a könyv a következő listázásnál újra ott volt;
- ugyanaz a könyv **kétszer** szerepelt a listában, így a törlés csak az egyik
  példányt tüntette el.

Mostantól a program a bejegyzés fajtájának megfelelően töröl, és **ellenőrzi,
hogy tényleg eltűnt-e** — csak akkor mond sikert.

---

HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!
