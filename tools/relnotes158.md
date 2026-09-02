## Ha csak egy dolgot olvasol el

**A program összeomlott azoknál, akiknél még nem volt megadva a
névjegy-engedély.** Nem ritka esetben — bekapcsoláskor és a napi
névjegy-frissítéskor, vagyis **minden friss telepítőnél**. A tesztelő, aki
jelezte, azt látta, hogy „az asszisztens beállításakor leáll" — valójában
egy háttéresemény ölte meg a programot ugyanabban a pillanatban.

**Ez a hiba a másnapi ébresztődet is el tudta némítani.** A bekapcsolás utáni
helyreállítás nyolc lépése — ébresztők, gyógyszer-emlékeztető, naptár,
időzítő — egyetlen közös blokkban futott. Amikor a névjegy-lépés elhasalt,
a program meghalt, és **a többi lépés sem futott le**.

Javítva, három szinten: engedély-ellenőrzés, védőháló a lekérdezés körül, és
**minden indulási lépés külön védőhálót kapott** — egy elhasaló lépés többé
nem viheti magával a többit.

---

## E-mail: válasz és továbbítás ott, ahol kell

Eddig az „E-mail írása" külön menüpont volt, a levelek olvasásától messze.
Vagyis pont akkor nem tudtál válaszolni, amikor épp elolvastad a levelet: ki
kellett lépni, megkeresni az írás menüpontot, és ott **újra bediktálni annak
a címét, akinek a levele az imént szólt a füledbe.**

Mostantól az elolvasott levélnél a **jobbra söprés** előhozza a műveleteket:

- **Válasz a feladónak** — a címzett és a tárgy magától kitöltődik, és
  egyenesen a szöveg diktálásához ugrik. **Három diktálásból egy lett.**
- **Továbbítás** — az eredeti levelet idézve viszi tovább
- **Levél újraolvasása**
- **Feladó mentése a címjegyzékbe** — hogy legközelebb ne kelljen diktálni
- **Új levél írása**
- **Vissza a levélhez**

Két részlet, ami számít:

- **Nincs „Re: Re: Re:".** Az előtag csak egyszer kerül fel. Egy hosszú
  levelezésben az a lánc felolvasva elviselhetetlen.
- **Ha a levélnek nincs válaszolható feladó-címe** (hírlevél, no-reply), a
  program ezt **kimondja**, és nem küld. Egy rossz címre elment válasz
  rosszabb, mint a beismert kudarc: azt hinnéd, megérkezett.

**A menü is átrendeződött:** `Postafiók megnyitása` → `Új levél írása` →
címjegyzék → fiókbeállítás. A napi művelet elöl, a ritkák hátul.

## Gesztus-iskola elforgatott felülettel

Ha bekapcsoltad a Felület elforgatását, a gesztus-iskola eddig az ALAP
kezelést tanította — és a helyes mozdulatot **hibának nézte volna a vizsgán**.

Mostantól az órák a jelenlegi kezelésből épülnek fel: a cél marad ugyanaz
(„lépj a következő elemre"), a mozdulat követi az elforgatást.

## Hibajelentés: mostantól megmondja, mikor történt

Az összeomlás-nyom mellé bekerül az **időpont és a verziószám**. Ha a nyom
egy korábbi verzióból való vagy egy hétnél régebbi, a jelentés
figyelmeztet rá.

Miért számít: eddig egy hetekkel korábbi, **már javított** összeomlás úgy
nézett ki, mintha az imént történt volna — és a keresés arra a hibára ment
el, ami már nincs. Egy vak tesztelő nem tudja ellenőrizni a jelentés
tartalmát; azt küldi el, amit a program összerak. Ha a program félrevezet,
a tesztelő is félrevezet, akaratlanul.

## Frissítés

A telefon magától szól — a SuperDL naponta kétszer keres frissítést.
Kézzel: `Beállítások → Katalógus → Frissítés keresése`, vagy töltsd le a
`SuperDL.apk`-t innen.

A beállításaidat, névjegyeidet, könyvjelzőidet és saját elnevezéseidet a
frissítés nem bántja.
