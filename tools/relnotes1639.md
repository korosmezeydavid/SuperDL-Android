## 1.63.9 — Egy elakadt kérdés, és az egész elhallgatott

Ez a kiadás az előző folytatása, és egy olyan levélből született, ami
elsőre úgy hangzott, mintha a frissítés nem sikerült volna.

Mezei Géza írta az 1.63.8-ról: *„a frissítés nem sikerült, a verziószám
megemelkedett, de a YouTube-nál nem változott semmi."*

**A frissítés sikerült, és az előző javítás is működik.** Ezt maga a
hibajelentés bizonyította: a hozzá csatolt technikai naplóban az utolsó
összeomlás még az 1.63.6-os változatból való. Ha a régi hiba benne maradt
volna, friss nyom állna ott — mert az a fajta összeomlás nem szeszélyes,
mindig ugyanabban a pillanatban történt.

Csak épp a takarékos mód ettől még nem szólalt meg. Kiderült, hogy ott egy
másik hiba is lakott, és ez volt a súlyosabb.

### Egy elakadt kérdés megállította az egész keresést

Amikor a program csak a hangot akarja lejátszani, ki kell kérnie a
hangfolyam címét. Ezt hat különböző úton próbálja meg — több YouTube-kliens,
két fajta tükör-kiszolgáló, végül maga a videó oldala —, mert bármelyik
elakadhat, és elég, ha egy sikerül.

Csak hogy **a hat út egyetlen sorba volt fűzve, védőháló nélkül.** Ha az első
elakadt — például mert nyolc másodperc után sem jött válasz —, az egész
keresés megszakadt ott. A maradék ötöt meg sem kérdezte a program.

Rossz hálózaton ez mindig ugyanígy végződött: nyolc másodperc várakozás,
aztán az, hogy nem sikerült. Pontosan az, amit Géza hallott.

Most minden út külön védőhálóban van. Ha az egyik elakad, jön a következő.
Ugyanaz az elv, ami a lejátszásnál már évek óta megvolt: egy próbálkozás
bukása nem az egész bukása.

### Két különböző baj, ami egyformán hangzott

Eddig két mondat volt erre, és egyetlen szó különbség volt köztük — hallás
után megkülönböztethetetlen. Pedig a két eset teljesen mást jelent, és
teljesen más javítást kíván.

Mostantól:

- **„A YouTube most nem adott ki hangfolyamot ehhez a videóhoz."** Ilyenkor a
  címkeresés nem járt eredménnyel.
- **„Megvan a hang, de a YouTube elutasította a lejátszást."** Ilyenkor a cím
  megvolt, csak a YouTube nem engedte a lejátszást.

Ez nem szépségjavítás. Eddig sem a felhasználó, sem én nem tudtam megmondani,
melyik történt — így a javítás is csak találgatás lehetett volna.

### Csend helyett kijárat

Eddig, ha a takarékos mód nem talált hangot, elhangzott egy mondat, és három
másodperc múlva minden elhallgatott. Ott maradtál csendben, választás nélkül.

Mostantól a program átadja a videót a rendes, képes lejátszónak — az ugyanis
gyakran elindul akkor is, amikor a hangfolyam külön nem érhető el. És meg is
mondja, hogy ez történik.

**A takarékos mód beállítása nem változik ettől.** Ez egyetlen videóra szóló
kerülőút, nem csendes visszakapcsolás a hátad mögött. A következő videónál
megint a takarékos mód próbálkozik először.

### A hibajelentés mostantól elmondja, mi történt a YouTube-bal

A program eddig is feljegyezte magának, hogy melyik út mit válaszolt — csak
ezek a jegyzetek a fejlesztői géphez kötött rendszernaplóba mentek. Onnan egy
tesztelő nem tudja kiszedni, és mire a telefon a kezembe kerülne, felülíródik.
**Az adat létezett, csak soha nem jutott el hozzám.**

Mostantól a hibajelentésben van egy új szakasz: **„UTOLSÓ
YOUTUBE-PRÓBÁLKOZÁS".** Benne, hogy melyik út mit válaszolt, mennyi címet
adott, és min állt el végül a dolog.

**Egy dologról szólni kell:** ez a szakasz tartalmazza a videó azonosítóját
is. Erre azért van szükség, mert enélkül nem lehet újrajátszani az esetet, és
nem derül ki, hogy a baj minden videónál jelentkezik-e, vagy csak a
korhatáros, régiózárt, beágyazás-tiltott darabokon. A hibajelentés továbbra is
**csak akkor indul útnak, ha te magad elküldöd** — és előtte végig
meghallgathatod, mi van benne. Ha nem akarod elküldeni, ne küldd el.

---

**HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!**
