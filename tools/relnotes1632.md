# SuperDL 1.63.2

Ez a kiadás egyetlen tesztelői hangfelvételből született. Lőrincz Richárd
tizenegy percben végigment az 1.63.1-en egy Galaxy S24 Ultrán, Android 16-on,
és három dolgot mutatott meg. Kettő itt javítva.

## A LEGSÚLYOSABB: BEKAPCSOLÁS UTÁN NEM BESZÉLT SEMMI

Az ő szavaival:

> „újraindulás után hiába van bekapcsolva a PIN segéd, hiába van bekapcsolva
> a SuperDL képernyőolvasó, amíg be nem írom **először** a PIN kódomat, addig
> **nem beszél egyik sem. Semmi.** És csak a TalkBackkel tudom beírni a PIN
> kódot."

Vagyis a telefon bekapcsolásakor — pont akkor, amikor a legnagyobb szükség
lenne rá — a SuperDL néma volt. A saját telefonod feloldásánál.

**Az ok nem nálunk volt, de a következmény igen.** Az Android az első
feloldásig nem indítja el azokat az alkalmazásokat, amelyek nincsenek
felkészítve a titkosított fázisra. **A rendszer beszédmotorja (Google vagy
Samsung) ilyen** — tehát a SuperDL-nek nem volt mivel beszélnie. A TalkBack
azért szólal meg, mert saját, beépített úton kerüli meg ezt.

A PIN segéd egyébként rendben elindult, a billentyűzet meg is jelent. Csak
némán.

**A javítás:** a zárképernyő szövegkészlete zárt halmaz — tíz számjegy, két
gomb, néhány rögzített mondat. Ezek mostantól **a programba építve** utaznak,
és lejátszásukhoz nem kell beszédmotor. Bekapcsolás után innentől hallod a
számokat, a gombokat, és azt is, hány számjegyet írtál be.

Két dolgot tudni kell róla:

- **A hang gépiesebb a megszokottnál.** Nem a te beszédmotorod szól, hanem
  egy beépített tartalék. A mérce itt nem a szépség volt, hanem hogy
  egyáltalán legyen hang ott, ahol eddig némaság volt.
- **Csak a bekapcsolás utáni első feloldásig szól.** Amint beírtad a PIN-t,
  mindent a megszokott hangod vesz át, a te beállításaiddal.

Meg lehet hallgatni előre: **Beállítások → Biztonság → Zárképernyő hangjainak
próbája.** Érdemes egyszer végighallgatni, hogy éles helyzetben ismerős
legyen.

## AZ ÜZENET-SZEREPKÖR: MŰKÖDÖTT, MÉGIS HIÁNYZÓNAK LÁTSZOTT

Richárd ezt is pontosan leírta:

> „a telefon beállításain belül az alapértelmezett üzenet alkalmazás a
> SuperDL … látom a beérkező üzeneteimet, tudok SMS-t írni, törölni,
> **működnek** az SMS-ek. **De viszont a beállítás varázslóban még mindig azt
> írja, hogy hiányzik.**"

A varázsló naplója megerősítette: a régi lekérdezés üresen tért vissza,
miközben a szerepkör a SuperDL-é volt. A két rendszerréteg nem értett egyet.

Két hiba egyben, mindkettő javítva:

- **A felismerés** mostantól mindkét forrást megkérdezi. Ha bármelyik azt
  mondja, hogy miénk a szerepkör, akkor miénk.
- **A kérés** Android 10 fölött soha nem esik vissza a régi, elavult útra.
  Az megnyílt, majd némán bezárult — Richárd naplójában három ilyen
  eredménytelen próbálkozás látszott. Ugyanez a hibaosztály fogta meg az
  asszisztens szerepkört az 1.62.1-ben.

## A HIBAJELENTÉS TÖBBET MOND

A varázslóból küldhető jelentés mostantól kiírja a szerepkör-kezelő
állapotát is minden szerepkörnél (üzenet, telefon, kezdőképernyő,
asszisztens, hívásszűrő): elérhető-e, és a miénk-e. Az előző jelentésnél
ezekre csak következtetni lehetett.

Ezenkívül kiírja, hogy a készülék fel van-e oldva, milyen beszédmotorok
vannak a rendszerben, és megvannak-e a beépített zárképernyő-hangok.

## AMI MÉG NYITOTT

A SuperDL képernyőolvasója Samsungon továbbra sem kezeli az idegen
alkalmazásokat: bekapcsolva, a TalkBack kikapcsolásával „hiába húzom az
ujjam jobbra-balra, semmi nem történik". Ezt kétszer is jelezte, és nincs
Samsungunk, amin reprodukálni tudnánk. Dolgozunk rajta.

## LETÖLTÉS

https://github.com/korosmezeydavid/SuperDL-Android/releases/latest/download/SuperDL.apk

HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!
