# SuperDL 1.63.3

Az 1.63.2 azt ígérte, hogy bekapcsolás után végre lesz hang a zárképernyőn.
A saját telefonomon **nem volt.** Ez a kiadás annak az okát javítja — és
kiderült, hogy az ok sokkal súlyosabb volt, mint a némaság.

## AZ ÖSSZEOMLÁS, AMI MINDKÉT SZOLGÁLTATÁST KIÜTÖTTE

A bekapcsolási napló ezt mutatta:

```
Start proc 2060: com.superdl.launcher ... KeyguardPinAccessibilityService
E AndroidRuntime: java.lang.IllegalStateException:
  SharedPreferences in credential encrypted storage are not available
  until after user is unlocked
    at ScreenReaderPrefs.prefs(ScreenReaderPrefs.kt:32)
    at ScreenReaderPrefs.reportFailure(ScreenReaderPrefs.kt:74)
    at ScreenReaderService.onServiceConnected(ScreenReaderService.kt:104)
Process com.superdl.launcher (pid 2060) has died
Scheduling restart ... in 1810000ms
```

Sorrendben: a rendszer bekapcsoláskor rendben elindította a PIN segédet.
Ugyanabban a folyamatban indul a képernyőolvasó is. Az megpróbálta felépíteni
a beszédmotort — ami feloldás előtt nem létezik —, mire lefutott a hibakezelő,
és **az** nyúlt a titkosított beállítás-tárolóhoz. Kivétel, és mivel Androidon
egy elkapatlan kivétel az egész folyamatot megöli, a PIN segéd is vele ment.
Két összeomlás után az Android **fél órára** halasztotta az újraindítást.

**A biztonsági háló ölte meg a programot** — pont ott, ahol a felhasználónak
a legnagyobb szüksége lett volna rá: a saját telefonja feloldásánál.

Az irónia teljes: a hibaszámláló három hiba után magától vészleállítja a
képernyőolvasót. Ha nem omlott volna össze, **három újraindítás után
kikapcsolta volna magát** annál, aki minden reggel újraindítja a telefonját.

## A JAVÍTÁS HÁROM RÉTEGBEN

**1. `SafePrefs` — új, közös beállítás-tároló, ami soha nem dob kivételt.**

Az első javítás után a program egy réteggel mélyebben omlott össze ugyanígy:
a beszédmotor `onInit` visszahívásában. Ez a tanulságos eset — azt a
visszahívást nem mi hívjuk, hanem a rendszer, később, a főszálon, tehát a
létrehozás köré tett `try/catch` **semmit nem ér**. Ezért a védelem magába a
tárolóba került: feloldás előtt az eszköz-védett tárolót adja, és ha minden
kötél szakad, egy üres tárolót — a hívó az alapértékekkel dolgozik tovább,
némán rosszabbul, de **élve**. Tizenkét tároló használja: beszéd,
hangvisszajelzés, gesztusok, zárolás, hangtémák, varázsló.

**2. A képernyőolvasó feloldás előtt el sem indul.** Ebben a fázisban nincs
dolga: a zárolt képernyőt szándékosan nem olvassuk fel, a PIN képernyő pedig
a segéd dolga. A feloldás pillanatában indul el magától.

**3. A segéd bekapcsolás után a „húzd fel" alapállapotban is megjelenik.**
A zárképernyő induláskor nem PIN-mezőt mutat, és a korábbi feltétel szerint a
segéd ilyenkor elrejtőzött. Vakon nem kitalálható, hogy előbb fel kell húzni a
képernyőt. Mivel az Android az első feloldásig úgyis csak a kódot fogadja el —
ujjlenyomatot nem —, ilyenkor nyugodtan vehetjük PIN-nek.

**Négy újraindításból négy** a mérés eredménye: összeomlás nulla, a segéd
minden alkalommal megjelent és megszólalt a kód beírása előtt.

## A ZÁRKÉPERNYŐ HANGJAI TARTÓS HELYRE KERÜLTEK

A 38 klip eddig a gyorsítótárba csomagolódott ki. Kiderült, hogy ezt a
készülék takarítója minden bekapcsoláskor kiüríti, tehát a klipek **minden
indulásnál** újra kimásolódtak — pont akkor, amikor a felhasználó a
zárképernyő előtt áll és beszédre vár. Mostantól tartós mappában vannak, és
feloldás után, nyugodt körülmények között csomagolódnak ki előre.

## A VARÁZSLÓ ZSÁKUTCÁJA — MOST MÁR HANGOSAN VAN KIJÁRAT

Egy Xiaomi Redmi Note 10 Pro tulajdonosa húsz perc alatt **három**
hibajelentést küldött ugyanarról: elakadt az alapértelmezett telefon
alkalmazásnál. A három jelentést idősorként olvasva látszik, hogy közben
mindent megoldott — a billentyűzetet, a kihagyható tételeket. Egyedül ezen
nem jutott át.

Két hiba tartotta bent:

- **A kijárat néma volt.** A varázsló két sikertelen próbálkozás után engedi
  kihagyni az alapvető tételeket is. Ő viszont csak annyit hallott, hogy „ezt
  nem lehet későbbre hagyni" — egy szót sem arról, hogy mikortól lehet. Egy
  kijárat, amiről a felhasználó nem tud, nem kijárat. Mostantól elhangzik.
- **A próbálkozás-számláló nem élte túl a program leállítását.** Memóriában
  tartottuk. A MIUI viszont leállítja a háttérben lévő alkalmazásokat — a
  jelentései szerint ugyanezen a telefonon a „korlátlan háttérfutás"
  engedélyt is **magától visszavonta** két jelentés között. Minden leállítás
  nullázta a számlálót, tehát a felhasználó soha nem érhette el a kettőt.
  Mostantól a tárolóba megy.

## A TELEFON-SZEREPKÖR FELISMERÉSE

Ugyanaz a hibaosztály, ami az asszisztensnél az 1.62.1-ben és az üzenetnél az
1.63.2-ben kijött; ez a harmadik előfordulása. A `DialerRoleHelper` egyetlen
forrásból nézte, kié a szerepkör, és Android 10 fölött vissza tudott esni az
elavult `ACTION_CHANGE_DEFAULT_DIALER`-re, ami megnyílik, majd némán bezárul.
Mostantól két forrásból néz, és néma zsákutca helyett az alapértelmezett
alkalmazások rendszerképernyőjét nyitja meg — gyártói rendszereken sokszor ez
az egyetlen működő út.

## HELYESBÍTÉS AZ 1.63.2-HÖZ

Az 1.63.2 jegyzetében azt írtam, hogy a javítást a saját készülékemen nem
tudom ellenőrizni, mert nincs rajta képernyőzár. **Ez téves volt.** A
`dumpsys device_policy` → `Password quality` sort olvastam félre: az az
eszközkezelői követelményt mutatja, nem azt, hogy van-e zár. A helyes
lekérdezés a `dumpsys lock_settings` → `CredentialType`, és az PIN kódot
mutat. A hírlevelet, a Richárdnak írt levelet és a kiadási jegyzetet
javítottam.

## AMI MÉG NYITOTT

A SuperDL képernyőolvasója Samsungon továbbra sem kezeli az idegen
alkalmazásokat. Nincs Samsungunk, amin reprodukálni tudnánk. Dolgozunk rajta.

## LETÖLTÉS

https://github.com/korosmezeydavid/SuperDL-Android/releases/latest/download/SuperDL.apk

HASZNÁLAT CSAK TARTALÉK KÉSZÜLÉKEN EGYELŐRE, VAGY SAJÁT FELELŐSSÉGRE!
