## Ha csak egy dolgot olvasol el

**Mostantól Braille-lel írhatsz a telefonodon.** Hatpontos magyar irodalmi
Braille, a magyar `q`-val és `z`-vel, mind a hét összetett betűvel (cs, gy,
ly, ny, sz, ty, zs), ékezetekkel, számokkal, írásjelekkel és egy külön
jel-réteggel a ritkább jeleknek. Nem próbapad: **valódi billentyűzet**, ami
bármelyik alkalmazás szövegmezőjébe ír — e-mailbe, keresőbe, üzenetbe.

És **három módon** írhatsz vele, a telefonodtól és a kezedtől függően:

- **Igazi Braille** — hat ujjal, egyszerre. A leggyorsabb.
- **Két menet** — három ujjal: előbb a bal oszlop, aztán a jobb. Ehhez
  gyakorlatilag minden érintőkijelző elég.
- **Sín** — egyetlen ujjal. Leteszed, fel-le sort választasz, oldalra lépve
  ütöd le a pontot. **Bármilyen telefonon működik, kalibrálás nélkül.**

---

## Hogyan kezdj neki

1. `Beállítások → Haladó és technikai → Billentyűzet → Billentyűzetek
   engedélyezése` — a rendszer listájában kapcsold be a **Super DL Braille
   billentyűzetet**.
2. `Billentyűzet → Braille billentyűzet → Súgó` — egyben elmondja az egészet.
3. `A kezem megtanítása` — **tedd rá mind a hat ujjadat egyszerre**, úgy,
   ahogy írni fogsz. A program magától felismeri, hogy két oszlopba (cella)
   vagy egy sorba (zongora) tetted-e őket. Ha kevesebbet érez, újrapróbálhatod,
   vagy lefelé söpörve két menetben mérhetsz.
4. `Braille próba` — itt gyakorolhatsz tét nélkül.
5. Egy szövegmezőben `Billentyűzet választása`, és írj.

**A billentyűzet a teljes kijelző.** Nincs „a billentyűzet területe" — az
egész képernyő az, mert a kezed helyét a kalibrálásból ismeri.

## A mozdulatok

- **Üres cella** (letétel pont nélkül): szóköz — és **kimondja a szót**, amit
  betűnként írtál. Így derül ki az elírás, mielőtt továbbmennél.
- **Egy ujj balra:** törlés — és megmondja, **mi tűnt el, és mi maradt**.
- **Három ujj balra:** az egész szó törlése.
- **Egy ujj lefelé:** hol vagyok — az utolsó szavak, a mód és a réteg.
- **Két ujj lefelé:** új sor. **Két ujj felfelé:** billentyűzet-váltás.
- **Összecsippentés:** bezárás — ugyanaz, mint a mátrixnál.

Sín módban: két ujj koppintás = törlés, három ujj = felolvasás.

## Jelzők és a jel-réteg

- **Szám:** 3-4-5-6, utána az a–j az 1–0. A vessző, kettőspont, pont és
  kötőjel bent tartja — a `3,14` és a `2026-09-02` egy jelzővel megy.
- **Nagybetű:** 4-6 egyszer a következő betűre; **kétszer az egész szóra**.
- **Jel-réteg: az 5-ös pont** — a magyar Braille szabványa. Utána a következő
  cella nem betű, hanem jel: 5 + d = dollár, 5 + vessző = per jel, 5 + nyitó
  zárójel = kapcsos zárójel. **Kétszer: zárolva**, amíg vissza nem kapcsolod.

Ugyanaz a szabály mindenhol: **egyszer egy cellára, kétszer amíg vissza nem
kapcsolod.**

## A kéz követése

Írás közben a kéz elcsúszik. A program **minden betű után utánamegy** az
ujjaidnak — így pár perc után sem kell újrakalibrálni. Ha a képernyő más
állásban van, mint amiben kalibráltál (mert az alkalmazás nem forog), a
pontokat átszámolja, és szól.

## Billentyűhang

Minden ujjnál egy halk kattanás, betűnél, szóköznél, törlésnél a maga hangja
— a rendszer saját billentyűhangjai, tehát ha kikapcsoltad a beállításokban,
itt is csendben marad.

---

## Super DL Diktálás — a legegyszerűbb bevitel

Egy **harmadik választható billentyűzet**, annak, aki se mátrixot, se
Braille-t nem akar. **Belépsz a szövegmezőbe, és a telefon hallgat.** Mondod,
beírja, újra hallgat — semmilyen mozdulat nem kell. Írásjeleket is mondhatsz.

Ha kész vagy, **összecsippentesz** — ez az egyetlen mozdulat. Ha háromszor
nem ért semmit, **megáll**, és megmondja, koppintásra folytatja: a mikrofon
nem marad nyitva a végtelenségig. Két ujjal balra az utolsó szót törli.
Jelszómezőben nem diktál.

## Apróbb változások

- **Menü:** a Billentyűzet külön ág lett, benne Mátrix és Braille almenü.
  A képernyőolvasó almenü megkönnyebbült.
- **Súgó** a Braille almenü élén — a menüpontok egyenként beszélnek, de a
  rendszert csak a súgó mondja el egyben.
- **Mennyit magyarázzon → Billentyűzet-tájékoztató:** a billentyűzetek
  megnyitáskor elmondott bemutatója kikapcsolható. A neve mindig elhangzik.
- **Fekvő tartásnál** a töltő csatlakozója jobbra esik — az utasítás most
  ezt mondja.
- **Billentyűzet választása:** a lista mostantól mind a három SuperDL
  billentyűzetet ismeri, és megnyitja a rendszer választóját.

## Ami még nem kész

Őszintén: a tábla két forrásból egyezik (a tesztelő és a LibLouis), de
tételről tételre végig nem hallgatott. Ha egy betű rosszat ad, **írd meg,
melyik** — a hibajelentőben vagy levélben. A jel-rétegen néhány jel (`=`,
`&`, `€`, `%`) helye még nincs eldöntve.

## Frissítés — EZT OLVASD EL, ha 1.58.0 vagy régebbi van a telefonodon

**A programon belüli frissítés eddig senkinél nem működött.** A telepítő
„nem indítható" üzenettel kilépett — a program a letöltött fájlt nem tudta
átadni a rendszernek, mert egyetlen mappát engedett megosztani, és nem azt,
amelyikbe a frissítés került. Ez a hiba a kezdetek óta bent volt. **Javítva.**

De a javítás csak ebben a verzióban van, tehát **erre a verzióra még kézzel
kell frissíteni:** töltsd le innen a `SuperDL.apk`-t, és telepítsd. Ezután
a következő frissítéseket már a program maga elintézi — naponta kétszer
keres, és szól, ha van újabb. Kézzel: `Beállítások → Program frissítése`.

A beállításaidat, névjegyeidet, könyvjelzőidet és saját elnevezéseidet a
frissítés nem bántja.
