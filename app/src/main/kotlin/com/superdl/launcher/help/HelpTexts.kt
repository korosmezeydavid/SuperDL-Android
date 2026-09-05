package com.superdl.launcher.help

import com.superdl.launcher.legal.LegalSection

/**
 * A SÚGÓK TÁRA — menüág szerint.
 *
 * ALPH KÉRÉSE (2026-09-03): minden alkalmazás-ág alján legyen egy Súgó, ami
 * az adott alkalmazás EGÉSZÉT elmondja — mert „a menüpontok is súgnak, de
 * duplasúgás a sztereó". A menüpont egyenként megmondja, mit csinál; a
 * RENDSZERT — hogyan kezdj neki, mi mit jelent, mi történik, ha elakadsz —
 * csak a súgó mondja el.
 *
 * MINDEN SÚGÓ UGYANABBÓL AZ ÖT SZAKASZBÓL ÁLL, ugyanabban a sorrendben:
 *   1. Mire való
 *   2. Hogyan kezdd
 *   3. Mozdulatok itt
 *   4. Ha valami nem megy
 *   5. Fejlesztés támogatása   (mindegyik ezzel zárul — lehetőség, nem kérés)
 * Így a felhasználó mindig tudja, hányadik szakaszban mit talál, és a
 * negyedikre ugorhat, ha csak elakadt.
 *
 * A SZÖVEG IGAZ KELL, HOGY LEGYEN. Egy súgó, ami olyan menüpontot említ, ami
 * már nincs, vagy más mozdulatot mond, mint ami történik, rosszabb a
 * semminél — a felhasználó magát hibáztatja. Ezért a SZABÁLY: ha egy
 * menüpont vagy egy mozdulat változik, a súgója ugyanabban a commitban
 * változik. A szövegek a KÓDBÓL vannak kiolvasva (a lejátszók vezérlő-listái,
 * a menüpontok nevei), nem emlékezetből.
 *
 * A TÁJOLÁS: a „söpörj jobbra" típusú mondatokat a beszédmotor elforgatott
 * felületen átfordítja — de csak akkor ismeri fel, ha a „söpr" szó benne
 * van. Ezért itt mindig „söprés" vagy „söpörj", sosem „húzd" vagy „pöccints".
 *
 * A menüág azonosítója az, ami a `MenuTree`-ben van (`"music_group"`,
 * `"podcast"`, `"radio"`). A `buildRoot` ebből tudja, hova szúrjon Súgót.
 */
object HelpTexts {

    /** A súgó-menüpontok azonosítójának előtagja: `help::music_group`. */
    const val MENU_ID_PREFIX = "help::"

    data class HelpTopic(val title: String, val sections: List<LegalSection>)

    /**
     * UGYANAZ A SÚGÓ MÁSIK AZONOSÍTÓRA.
     *
     * A fájlátvitel két helyen szerepel a menüben (Média és Eszközök), és a
     * két példány azonosítója KÖTELEZŐEN különbözik — különben a súgó-beszúró
     * két „Súgó" pontot tenne ugyanabba a listába. A szöveg viszont egy:
     * lemásolva előbb-utóbb szétcsúszna, és a felhasználó két különböző
     * választ kapna ugyanarra a kérdésre.
     *
     * A `topics` térképbe SZÁNDÉKOSAN nem tesszük bele: onnan épül a
     * „Súgó — minden alkalmazás" lista, ott pedig ne szerepeljen kétszer
     * ugyanaz.
     */
    private val ALIASES = mapOf("transfer_group_tools" to "transfer_group")

    private fun resolve(menuId: String): String = ALIASES[menuId] ?: menuId

    /** Van-e súgó ehhez a menüághoz? A menüépítő ezt kérdezi. */
    fun hasTopic(menuId: String): Boolean = topics.containsKey(resolve(menuId))

    fun forMenu(menuId: String): HelpTopic? = topics[resolve(menuId)]

    /** Minden súgó, a Névjegy „Súgó — minden alkalmazás" listájához. */
    fun all(): List<Pair<String, HelpTopic>> = topics.entries.map { it.key to it.value }

    // Segéd: az öt szakasz egy hívásban, a záró szakasz automatikusan.
    private fun topic(
        title: String,
        purpose: String,
        start: String,
        gestures: String,
        trouble: String
    ) = HelpTopic(
        title,
        listOf(
            LegalSection("Mire való", purpose),
            LegalSection("Hogyan kezdd", start),
            LegalSection("Mozdulatok itt", gestures),
            LegalSection("Ha valami nem megy", trouble),
            SupportInfo.helpFooter()
        )
    )

    private val topics: Map<String, HelpTopic> = mapOf(

        // ── ZENE ────────────────────────────────────────────────────────────
        "music_group" to topic(
            title = "Zene súgó",
            purpose = "A telefonon és a felhőben tárolt zenéidet játssza le. A telefonon " +
                "lévő zenét a rendszer zenetárából olvassa; a felhős zenét egy általad " +
                "kiválasztott mappából. Emlékszik, hol hagytad abba, és egy mozdulattal " +
                "folytatja.",
            start = "Először válaszd a Zene a telefonon menüpontot: listát kapsz, fel-le " +
                "söpréssel válogatsz, jobbra söpréssel elindul a lejátszás. Ha felhőben " +
                "tartod a zenét, előbb a Felhős zenemappa kiválasztása menüpontban add " +
                "meg a mappát, utána a Zenék a felhőben menüpont listázza. Az Utoljára " +
                "játszott folytatása menüpont ott folytatja, ahol abbahagytad.",
            gestures = "Lejátszás közben a képernyő egy vezérlő-lista. Fel-le söpréssel " +
                "lépkedsz a vezérlők között, jobbra söpréssel kiválasztod, balra " +
                "söpréssel leállítod és kilépsz. A vezérlők sorban: szünet vagy " +
                "folytatás; következő szám; előző szám; előre tekerés; vissza tekerés; " +
                "hol tartok; zeneszám törlése és a következő lejátszása; lejátszás " +
                "leállítása. A törlés kétlépcsős: az első jobbra söprés csak megkérdezi, " +
                "a második töröl. A tekerés egységét és a lejátszási módot — sorban, " +
                "egy szám ismétlése, mind ismétlése, véletlen — a Zene beállítások " +
                "menüpontban állítod. A Bluetooth fülhallgató gombjai is működnek: " +
                "szünet, következő, előző.",
            trouble = "Ha a lista üres: a telefon zenetára nem lát fájlt. Másold a zenét " +
                "a telefon Music vagy Zene mappájába, vagy használd a Fájlátvitel " +
                "menüpontot. Ha a felhős lista üres: nézd meg, hogy a kiválasztott " +
                "mappában tényleg hangfájlok vannak-e. Ha a zene elhallgat, amikor " +
                "kihúzod a fülhallgatót: ez szándékos, hogy ne üvöltsön a hangszóróból; " +
                "a szünet vagy folytatás vezérlővel indítod újra. Ha beszél a program a " +
                "zene alatt, és ez zavar: Zene beállítások, Beszéd-visszajelzés — ott " +
                "kikapcsolható."
        ),

        // ── PODCAST ─────────────────────────────────────────────────────────
        "podcast" to topic(
            title = "Podcast súgó",
            purpose = "Podcastokat keresel, feliratkozol rájuk, és lejátszod az epizódokat " +
                "— internetről vagy letöltve, hogy térerő nélkül is hallgathasd. " +
                "A feliratkozásaid egy fájlba menthetők és visszatölthetők.",
            start = "A Népszerű podcastok menüpont a legnépszerűbb műsorok listáját adja " +
                "az Ország választása menüpontban beállított országból. A Podcast " +
                "keresése menüpontban bemondod a műsor nevét vagy témáját. Egy műsorra " +
                "jobbra söpréssel lépsz be: ott az epizódok vannak, és fel tudsz " +
                "iratkozni. A Feliratkozásaim menüpont a követett műsoraid, a " +
                "Letöltéseim a telefonra mentett epizódok.",
            gestures = "Lejátszás közben fel-le söpréssel lépkedsz a vezérlők között, " +
                "jobbra söpréssel kiválasztod, balra söpréssel kilépsz. A vezérlők: " +
                "szünet vagy folytatás; előre tekerés; vissza tekerés; hol tartok; " +
                "lejátszási sebesség; alvás időzítő; leírás felolvasása; lejátszás " +
                "leállítása. A sebesség lassítható és gyorsítható, az alvás időzítő " +
                "adott idő után magától megállítja a lejátszást — elalvás előtt " +
                "hasznos.",
            trouble = "Ha a Népszerű lista nem tölt be, vagy a keresés nem ad találatot, " +
                "a program megmondja, mi a hiba — azt a mondatot küldd el " +
                "hibajelentésben. Ha nincs internet, a Letöltéseim menüpontban lévő " +
                "epizódok akkor is mennek. Ha egy másik alkalmazásból hoznád át a " +
                "feliratkozásaidat: mentsd ki ott egy O P M L fájlba, és itt a " +
                "Feliratkozások importálása fájlból menüponttal töltsd be."
        ),

        // ── INTERNETES RÁDIÓ ────────────────────────────────────────────────
        "radio" to topic(
            title = "Internetes rádió súgó",
            purpose = "Interneten sugárzó rádióállomásokat hallgatsz, elmented a " +
                "kedvenceidet, és fel is veszed az adást — azonnal, vagy előre " +
                "időzítve, például egy esti műsort, amíg te alszol.",
            start = "A Magyar állomások menüpont a beépített lista: fel-le söpréssel " +
                "válogatsz, jobbra söpréssel szól. Az Állomás keresése menüpontban " +
                "bemondod az állomás nevét. Ami tetszik, azt lejátszás közben a " +
                "Mentés a kedvencekhez vezérlővel elmented; onnantól a Kedvenc " +
                "állomásaim menüpontban egy mozdulat.",
            gestures = "Lejátszás közben fel-le söpréssel lépkedsz a vezérlők között, " +
                "jobbra söpréssel kiválasztod, balra söpréssel kilépsz. A vezérlők: " +
                "szünet vagy folytatás; következő állomás; előző állomás; mentés a " +
                "kedvencekhez; felvétel indítása vagy leállítása; rádió leállítása. " +
                "A felvétel a Felvételek, Rádió mappába kerül, amit a Rádió felvételek " +
                "megnyitása menüpontból vagy a Fájlkezelőből érsz el. Az Időzített " +
                "felvétel hozzáadása menüpontban megadod az állomást, a kezdést és a " +
                "hosszt — a felvétel akkor is elindul, ha a telefont közben " +
                "újraindítottad.",
            trouble = "Ha egy állomás nem szólal meg: lehet, hogy az adás szünetel, vagy " +
                "az állomás címe megváltozott — próbálj egy másikat, és ha az megy, " +
                "a hiba az állomásnál van. Ha a keresés nem találja, amit mondasz: " +
                "próbáld másképp kimondani, vagy mondd rövidebben, csak a lényeges " +
                "szót. Ha egy kedvencet ki akarsz venni: Kedvenc állomás törlése " +
                "menüpont. Ha a felvétel nem indult el időben: nézd meg az Időzített " +
                "felvételeim menüpontban, hogy be van-e kapcsolva, és hogy a " +
                "Beállítások, Haladó, Korlátlan háttérfutás engedélyezése menüpontban " +
                "megadtad-e az engedélyt — enélkül a telefon alvó állapotban " +
                "leállíthatja a programot."
        ),

        // ── FÁJLÁTVITEL ─────────────────────────────────────────────────────
        "transfer_group" to topic(
            title = "Fájlátvitel és megosztás súgó",
            purpose = "Fájlokat viszel át a számítógépről a telefonra — zenét, könyvet, " +
                "bármit — anélkül, hogy látó segítségre lenne szükséged, és innen " +
                "küldesz is fájlt másnak. Négy út van: USB kábel, WiFi portál a " +
                "böngészőn keresztül, küldés másik programmal, és feltöltés " +
                "ideiglenes tárhelyre, ahonnan linket kapsz. Ez a menü két helyen " +
                "is megvan, ugyanazzal a tartalommal: a Média és az Eszközök alatt.",
            start = "Ha van kábeled, a Fájlátvitel géppel menüpont a gyorsabb: dugd be " +
                "a telefont a géphez, válaszd ezt a pontot, és a program megmondja, " +
                "hogy áll a kapcsolat, majd megnyitja az USB beállításokat, ahol " +
                "fájlátvitelre kell váltani. Kábel nélkül a WiFi fájlportál be és ki " +
                "menüpontot használd: a telefon és a gép legyen ugyanazon a WiFi " +
                "hálózaton, kapcsold be, és a program bemondja a címet és a PIN kódot, " +
                "amit a gép böngészőjébe kell beírni. Ha viszont te akarsz KÜLDENI " +
                "valamit, a Fájl vagy mappa megosztása menüpont a belépő: az a " +
                "Fájlkezelőt nyitja meg, ott kiválasztod a fájlt, jobbra söpörsz, " +
                "és a Megosztás pontot választod. A részletek a Megosztás súgóban.",
            gestures = "Az USB és a portál menüpontja nem nyit külön képernyőt, csak " +
                "beszél — ott nincs külön mozdulat. A portál címét és a PIN kódot a program kétszer " +
                "elmondja; ha nem sikerült megjegyezni, söpörj balra, és válaszd újra " +
                "a menüpontot — de vigyázz, mert az kikapcsolja. A be- és kikapcsolás " +
                "ugyanaz a menüpont: ha már fut, a következő választás leállítja.",
            trouble = "Ha azt mondja, nincs WiFi kapcsolat: a telefon nincs WiFi-n. Mobil " +
                "adaton a portál nem működik, mert a gép nem éri el a telefont. Ha a " +
                "gépen nem jön be a cím: nézd meg, hogy a gép ugyanarra a WiFi-re " +
                "csatlakozik-e, és hogy pontosan írtad-e be a számokat. A géppel " +
                "feltöltött fájlok a SuperDL, Portal mappába kerülnek — onnan a " +
                "Fájlkezelővel mozgathatod tovább, például a zenéket a Music mappába. " +
                "Ha végeztél, kapcsold ki a portált: amíg fut, a hálózaton az érheti " +
                "el, aki tudja a PIN kódot."
        ),

        // ── MEGOSZTÁS ───────────────────────────────────────────────────────
        "share" to topic(
            title = "Megosztás súgó",
            purpose = "Fájlt vagy mappát küldesz el valakinek. Három út van, és a " +
                "különbség köztük NEM technikai, hanem az, hogy ki láthatja a " +
                "fájlt — ezért is állnak ebben a sorrendben, lefelé nő a " +
                "kockázat. A Küldés másik programmal átadja a fájlt egy másik " +
                "alkalmazásnak — levélnek, csevegőprogramnak, Bluetoothnak —, és " +
                "onnantól az dolgozik vele. A Küldés kóddal a másik gépre " +
                "közvetlenül a másik készülékre viszi át, titkosítva, felhő " +
                "nélkül; erről külön súgó szól. A Feltöltés ideiglenes tárhelyre " +
                "felteszi a fájlt az internetre, és linket ad róla; ezt a linket " +
                "bárhova beillesztheted, de FIGYELEM: aki megkapja, le is tudja " +
                "tölteni, mert nincs rajta jelszó. Iratot, orvosi papírt, jelszót " +
                "ne ezen az úton küldj.",
            start = "A megosztás a Fájlkezelőből indul: söpörj a fájlok közt fel-le, " +
                "a kiválasztotton jobbra, és a művelet-listában keresd a Megosztás " +
                "pontot. Ugyanide jutsz a Fájlátvitel és megosztás menü Fájl vagy " +
                "mappa megosztása pontjából is. Mappát is meg lehet osztani: azt a " +
                "program előbb becsomagolja, és a becsomagolt fájl ott marad a " +
                "mappa mellett. Feltöltésnél előbb tárhelyet választasz — a " +
                "program mindegyikről elmondja, mekkora fájt fogad el, meddig él a " +
                "fájl, és hogy csak egyszer lehet-e letölteni —, aztán jön egy " +
                "kérdés, és csak az után indul el bármi.",
            gestures = "Fel-le söpréssel lépkedsz, jobbra söpréssel választasz, balra " +
                "söpréssel lépsz vissza. A megerősítő kérdésnél a jobbra söprés az " +
                "igen; a balra söprés és a fel-le söprés is mégse — aki " +
                "bizonytalanul lépkedni kezd, azt a program nem tekinti igennek. " +
                "Feltöltés közben a jobbra söprés megmondja, hol tart, a balra " +
                "söprés megszakítja. A kész link MAGÁTÓL a vágólapra kerül, tehát " +
                "csak be kell illesztened oda, ahova küldöd.",
            trouble = "Ha azt mondja, a fájl minden tárhelyhez túl nagy: tömöríts, vagy " +
                "küldd USB-vel, illetve a WiFi portállal. Ha egy tárhely hibát ad, " +
                "válassz másikat — ezek ingyenes szolgáltatások, néha nem " +
                "működnek, és ez nem a telefonod hibája. Ha a feltöltés elakad: " +
                "ellenőrizd, hogy van-e internet, és hogy nem fogyott-e el a mobil " +
                "kereted. A feltöltés akkor is fut tovább, ha kilépsz a " +
                "képernyőről; a végeredményt a program akkor is bemondja. Ha nincs " +
                "a telefonon alkalmazás, ami elfogadná a fájlt, azt a program " +
                "megmondja, és akkor az ideiglenes tárhely a járható út."
        ),

        "p2p_share" to topic(
            title = "Küldés kóddal, gépről gépre súgó",
            purpose = "Fájlt viszel át közvetlenül egy másik készülékre — a saját " +
                "gépedre, vagy valakiére, aki szintén SuperDL-t használ. A fájl " +
                "NEM kerül fel semmilyen tárhelyre, és végpontok között " +
                "titkosítva megy: rajtatok kívül senki nem látja a tartalmát. " +
                "Ugyanaz a megoldás, mint a windowsos SuperDL Fájlküldés gépről " +
                "gépre modulja, tehát a telefon és a gép EGYMÁSNAK is tud küldeni. " +
                "Amit viszont nem ígérünk többnek, mint amennyi: ha a két készülék " +
                "nem talál egymásra közvetlenül, az adat egy továbbító szerveren " +
                "folyik át — titkosítva, de nem közvetlenül.",
            start = "Küldéshez válaszd ki a fájlt a Fájlkezelőben, jobbra söprés, " +
                "Megosztás, majd Küldés kóddal a másik gépre. A program kap egy " +
                "kódot: egy szám és két szó, kötőjelekkel. Ezt kell átadnod a " +
                "másik oldalnak, és amint ott beírják, indul az átvitel. " +
                "Fogadáshoz a Fájlátvitel és megosztás menü Fájl fogadása kóddal " +
                "pontja kell, ott pedig vagy beilleszted a vágólapról a kapott " +
                "kódot, vagy beírod billentyűzettel. A fogadott fájl a SuperDL, " +
                "Fogadott mappába kerül.",
            gestures = "A kód képernyőjén fel-le söpréssel lépkedsz a lehetőségek " +
                "közt, jobbra söpréssel indítod: Mondd újra a kódot; Kód betűzve; " +
                "Kód a vágólapra; Kód küldése üzenetben. A kód a vágólapra MAGÁTÓL " +
                "is felkerül, amint megvan. Balra söprés megszakítja az átvitelt. " +
                "Átvitel közben a jobbra söprés megmondja, hol tart.",
            trouble = "A kód szavai ANGOLUL vannak, mert a protokoll nemzetközi — " +
                "ezért a program betűzve is elmondja, és ezért van a Kód küldése " +
                "üzenetben pont: a legbiztosabb, ha a másik fél nem hallás után " +
                "írja be, hanem beilleszti. Ha azt mondja, rossz vagy elgépelt " +
                "kód: pontosan a küldő kódját kell beírni, a kötőjelekkel együtt. " +
                "Ha időtúllépést mond: a kód lejár, tehát indítsátok egyszerre, és " +
                "a fogadó azonnal írja be. Ha azt mondja, a kódot már " +
                "felhasználták: minden kód egyszer használható, kérj újat. Ha a " +
                "közvetítő szerver nem érhető el: valószínűleg a hálózat, a tűzfal " +
                "vagy egy V P N blokkolja — próbáljátok másik hálózatról. Az " +
                "átvitel akkor is fut, ha kilépsz a képernyőről, és a végét a " +
                "program bemondja."
        ),

        "share_history" to topic(
            title = "Megosztási előzmények súgó",
            purpose = "Nyilvántartja, mit töltöttél fel, melyik tárhelyre, mikor, és " +
                "meddig él még a fájl. Ez nem kényelmi funkció: aki elküld egy " +
                "linket, három nap múlva nem emlékszik rá, mit küldött és él-e " +
                "még — és a link maga sem mondja meg.",
            start = "A Fájlátvitel és megosztás menü Megosztási előzmények pontja " +
                "nyitja meg, de a megosztás végén magától is ide érkezel. A lista " +
                "elején az áll, ami HAMARABB lejár. Minden soron elhangzik a " +
                "fájlnév, a mérete, a tárhely neve, és hogy mennyi ideje van még.",
            gestures = "Fel-le söpréssel lépkedsz a tételek közt, jobbra söpréssel " +
                "nyitod a műveleteket, balra söpréssel lépsz vissza. A műveletek: " +
                "Link a vágólapra; Link megosztása, ami átadja egy másik " +
                "alkalmazásnak; Link felolvasása betűzve, ha telefonba kell " +
                "bemondanod; Törlés a tárhelyről, ahol a szolgáltató ezt " +
                "megengedi; Sor törlése a listából; és ha van lejárt tétel, a " +
                "Lejártak eltakarítása.",
            trouble = "A Sor törlése a listából CSAK a nyilvántartásból veszi ki a " +
                "tételt — a fájl a tárhelyen marad, amíg magától le nem jár. Ha " +
                "tényleg meg akarsz szabadulni tőle, a Törlés a tárhelyről az, " +
                "ami számít; ez a nulla iksz nulla pont es té és a filebin pont " +
                "net esetén működik, a többinél meg kell várni a lejáratot, és " +
                "ezt a program meg is mondja. A lejárt tételek még egy napig " +
                "látszanak, hogy legyen válasz arra, miért nem működik egy " +
                "tegnap küldött link."
        ),

        // ── YOUTUBE ─────────────────────────────────────────────────────────
        "youtube_group" to topic(
            title = "YouTube súgó",
            purpose = "YouTube videókat keresel és játszol le hanggal. Van takarékos mód, " +
                "amiben csak a hang megy — kevesebb adat, kevesebb akkumulátor, és " +
                "lezárt képernyővel, zsebben is tovább szól.",
            start = "A Hangos keresés menüpontban bemondod, mit keresel. Húsz találatot " +
                "kapsz: fel-le söpréssel válogatsz, jobbra söpréssel jön a kérdés, " +
                "hogy lejátsszam-e, és még egy jobbra söprés indítja. Az utolsó " +
                "találatnál a lefelé söprés a következő húsz videót tölti be. Az " +
                "Utoljára nézett folytatása ott folytatja, ahol abbahagytad.",
            gestures = "Lejátszás közben négy mozdulat van. Söprés felfelé: szünet vagy " +
                "folytatás. Söprés lefelé: hol tartok a videóban. Söprés balra: " +
                "leállítás és kilépés. Söprés jobbra: a videó adatai — és ha két " +
                "másodpercen belül még egyszer jobbra söpörsz, a videó a kedvencek " +
                "közé kerül, vagy lekerül onnan. Ez a kétszeri söprés azért van, hogy " +
                "egyetlen mozdulattal ne lehessen véletlenül kedvencet állítani. " +
                "A Bluetooth fülhallgató szünet gombja is működik. Takarékos módban " +
                "nincs lejátszó képernyő, a hang a háttérben szól: az értesítés " +
                "gombjaival és a Háttérlejátszás leállítása menüponttal vezérled.",
            trouble = "Ha azt mondja, ezt a videót a YouTube nem adja ki: a feltöltő " +
                "korlátozta, vagy nálunk nem elérhető — próbálj másikat. Ha a videó " +
                "megáll, amint lezárod a képernyőt: kapcsold be a Takarékos módot, " +
                "akkor a hang tovább szól. Ha képet is szeretnél, például mert valaki " +
                "veled nézi, kapcsold ki. Ha a betöltés tizenöt másodpercnél tovább " +
                "tart, a program szól, és megpróbálja közvetlenül lejátszani — ez nem " +
                "fagyás. Ha a kedvenceid listája üres, pedig mentettél: a mentéshez a " +
                "második jobbra söprésnek két másodpercen belül kell jönnie."
        ),

        // ── KÖZLEKEDÉS ──────────────────────────────────────────────────────
        "community" to topic(
            title = "Közlekedés súgó",
            purpose = "Ez az ág arról szól, hogy tudd, hol vagy, mi van körülötted, és " +
                "hogyan jutsz el oda, ahová indulsz. Négy dolgot ad: helymeghatározást, " +
                "a környék felderítését, a tömegközlekedés indulási időit, és a " +
                "korábban bejárt útvonalak visszavezetését.",
            start = "Kezdd a Hol vagyok? menüponttal: a program megkéri, hogy állj egy " +
                "helyben, közben bemondja, hány műholdat lát és hány méter a " +
                "pontosság, végül a címet. Ha innen indulnál valahová, a Gyalogos " +
                "útvonal diktálással menüpontban bemondod a célt. Ha csak azt akarod " +
                "tudni, mi van a közelben, a G P S Kitekintő almenü való rá; a " +
                "buszhoz-villamoshoz a Közeli megállók felolvasása.",
            gestures = "A Hol vagyok? eredményénél jobbra söpréssel elmented a helyet " +
                "egyéni helyként — a program megkérdezi a nevét —, balra söpréssel " +
                "kilépsz, felfelé söpréssel újra hallod, lefelé söpréssel újramérsz. " +
                "A megálló- és állomáslistákban fel-le söpréssel válogatsz, jobbra " +
                "söpréssel megnyílnak a műveletek — részletes felolvasás, mentés a " +
                "kedvencekbe, kedvenc törlése, keresési kör váltása, frissítés —, " +
                "balra söpréssel visszalépsz. A Mi van előttem? Környezeti kitekintő " +
                "egyetlen felolvasás, nem nyit listát.",
            trouble = "Ha a helymeghatározás nem sikerül: menj ki a szabad ég alá, és " +
                "kapcsold be a telefonon a G P S-t — épületben a műholdak nem " +
                "látszanak. Ha azt mondja, a cím internet nélkül nem érhető el: a " +
                "helyzet megvan, csak a címet nem tudja lekérdezni; hálózat nélkül a " +
                "koordináta is használható. Ha nincs internet és van mentett utolsó " +
                "hely, a program azt mondja be. Ha a megállók listája üres: nagyobb " +
                "keresési körre válts a műveletek közt a Keresési kör váltása ponttal. " +
                "A pontosság javítása időbe telik — a program végig beszél, amíg " +
                "dolgozik, tehát nem akadt le."
        ),

        // ── G P S KITEKINTŐ ─────────────────────────────────────────────────
        "gps_radar" to topic(
            title = "G P S Kitekintő súgó",
            purpose = "Megmondja, mi van körülötted háromszáz méteren belül: boltok, " +
                "éttermek, utcák, kereszteződések — és azt is, merre, óra szerinti " +
                "irányban. Egy helyet célba lehet venni, és a program végigkísér hozzá.",
            start = "A Közeli helyek menüpont felderíti a környéket, és felolvassa a " +
                "találatokat. Ha a helyek helyett az irányokra vagy kíváncsi, a " +
                "Hang-iránytű menüpontot indítsd el, és forgasd a telefont — a hang " +
                "követi az irányt; a Hang-iránytű leállítása állítja meg. Az Egyéni " +
                "helyek menüpontban vannak azok a helyek, amiket te mentettél el.",
            gestures = "A helylistában fel-le söpréssel válogatsz, jobbra söpréssel " +
                "megnyílnak a G P S műveletek, balra söpréssel kilépsz. A műveletek " +
                "sorban: Célzárolás, Kereszteződés előre, Hol vagyok?, Utcabemondás " +
                "ki-be, Saját hely mentése, Kiválasztott hely mentése. A műveletek " +
                "közt is fel-le söpréssel lépkedsz, jobbra söpréssel végrehajtod, " +
                "balra söpréssel visszatérsz a helylistába. Célzárolás után a program " +
                "folyamatosan mondja a távolságot és az irányt, és közeledéskor szól " +
                "ötven, húsz, tíz és öt méternél; a zárolást a balra söprés oldja fel. " +
                "A mentett helyeknél a jobbra söprés műveleteket nyit, köztük " +
                "hangjegyzetet is rögzíthetsz a helyhez.",
            trouble = "Ha azt mondja, nincs közeli bolt, étterem, utca vagy kereszteződés " +
                "háromszáz méteren belül: valóban nincs a térképen ilyen a közelben — " +
                "ez nem hiba. Ha a helymeghatározás engedélyt kér, add meg, és próbáld " +
                "újra. Az irányok a telefon tartásához igazodnak: tartsd magad elé, " +
                "vízszintesen, a felső éle előre. Ha a saját helyed mentése nem megy, " +
                "előbb nyisd meg a Közeli helyeket — a mentés csak a kitekintőn belül " +
                "működik. A pontosság a szabad ég alatt a legjobb; épületek között a " +
                "távolságok néhány méterrel tévedhetnek."
        ),

        // ── HELYSZÍN FELISMERŐ ──────────────────────────────────────────────
        "location_watch" to topic(
            title = "Helyszín felismerő súgó",
            purpose = "Megtanít a telefonnak egy helyet a felirata alapján — egy megálló " +
                "tábláját, egy ajtó névtábláját —, és utána szól, amikor a kamera " +
                "megint meglátja. Így G P S nélkül is tudod, hogy odaértél.",
            start = "Előbb tanítani kell: a Helyszín tanítása menüpontban mutasd a " +
                "kamerának a feliratot, és söpörj jobbra a rögzítéshez. Egy helyhez " +
                "több fotó is tartozhat, több szögből — annál biztosabb a felismerés. " +
                "Ha megvan, a Figyelő indítása az összes mentett helyszínt figyeli. " +
                "Ha csak egyetlen szóra vagy feliratra vársz, és nem akarsz tanítani, " +
                "a Figyelő szabad szöveggel menüpontban bemondod, mit keressen — " +
                "legalább négy karaktert.",
            gestures = "A Mentett helyszínek listában fel-le söpréssel válogatsz, jobbra " +
                "söpréssel nyílnak a műveletek: figyelő indítása erre az egy helyre, " +
                "fotók bővítése, fotók törlése. A figyelő addig fut, amíg le nem " +
                "állítod a Figyelő leállítása menüponttal — vagy amíg fel nem ismerte, " +
                "amit keresel.",
            trouble = "Ha nem ismeri fel a helyet: taníts hozzá még néhány fotót más " +
                "szögből és más fényben — egyetlen fotó kevés, ha a tábla máskor " +
                "árnyékban van. A kamerát tartsd nagyjából a felirat magasságában, és " +
                "adj neki egy-két másodpercet. Ha nincs mentett profil, a Figyelő " +
                "indítása megmondja, hogy előbb tanítanod kell. A figyelő a kamerát " +
                "használja, tehát fogyasztja az akkumulátort: ha odaértél, állítsd le."
        ),

        // ── VONAT ───────────────────────────────────────────────────────────
        "train" to topic(
            title = "Vonat súgó",
            purpose = "Vasútállomások indulási idejei — a közeliek, a név szerint " +
                "keresettek, és azok, amiket kedvencnek jelöltél.",
            start = "A Közeli állomások indulási időkkel menüpont a helyzeted alapján " +
                "keres, és rögtön felolvassa az indulásokat. Ha máshol lévő állomás " +
                "érdekel, az Állomás keresése felolvasással menüpontban bemondod a " +
                "nevét. Amit gyakran használsz, azt a listában a műveletek közül a " +
                "Mentés a kedvencekbe ponttal teszed el; onnantól a Kedvenc állomások " +
                "menüpont egy mozdulat.",
            gestures = "Az állomáslistában fel-le söpréssel válogatsz, jobbra söpréssel " +
                "megnyílnak a műveletek, balra söpréssel kilépsz. A műveletek sorban: " +
                "Részletes felolvasás, Állomás mentése kedvencekbe, Kedvenc törlése, " +
                "Keresési kör váltása, Frissítés. A műveletek közt is fel-le söpréssel " +
                "lépkedsz, jobbra söpréssel végrehajtod, balra söpréssel visszatérsz " +
                "az állomáslistába. Az irányt a telefon tartása szerint mondja, ezért " +
                "tartsd magad elé.",
            trouble = "Ha nincs találat: válts nagyobb keresési körre a műveletek közt. " +
                "Ha az indulási idők üresek, azt jelenti, hogy erre az állomásra most " +
                "nincs adat a szolgáltatónál — nem a program romlott el; próbáld " +
                "a Frissítés művelettel. Ha a keresés nem találja az állomást: mondd " +
                "a nevét rövidebben, a fő szót. A lekérdezéshez internet kell."
        ),

        // ── G P S ÚTVONAL ───────────────────────────────────────────────────
        "gps_route" to topic(
            title = "G P S útvonal súgó",
            purpose = "Rögzít egy útvonalat, amit valakivel együtt bejársz, és később " +
                "egyedül is visszavezet rajta. A kanyarokat magától észreveszi, és " +
                "te is tehetsz rá jelölőket ott, ahol fontos valami.",
            start = "Amikor elindulsz azon az úton, amit meg akarsz tanulni, válaszd az " +
                "Útvonal rögzítése menüpontot, és tedd el a telefont. A végén a " +
                "Rögzítés vagy útmutatás leállítása menüponttal zárod le, és a program " +
                "megkérdezi, milyen néven mentse. Legközelebb az Útvonal útmutatás " +
                "menüpontban választod ki, és végigvezet rajta.",
            gestures = "Rögzítés közben: söprés felfelé vagy lefelé az állapotért — hány " +
                "pont, hány jelölő, hány kanyar —, söprés jobbra egy útpont " +
                "rögzítéséhez ott, ahol éppen vagy, söprés balra a rögzítés lezárása " +
                "és mentése. A mentett útvonalak listájában fel-le söpréssel " +
                "válogatsz, jobbra söpréssel felolvassa; az Útvonal útmutatás " +
                "menüpontból nyitva a jobbra söprés indítja az útmutatást, az Útvonal " +
                "törlése menüpontból nyitva pedig a törlést erősíti meg.",
            trouble = "Ha azt mondja, az útvonal túl rövid, nem menthető: túl kevés pont " +
                "gyűlt össze — néhány lépés nem elég, menj végig az úton. Ha nem indul " +
                "új rögzítés, mert épp útmutatás fut, előbb állítsd le. Ha az útpont " +
                "rögzítése azt mondja, még nincs rögzített pont: indulj el pár métert, " +
                "és próbáld újra. A rögzítés a G P S-t használja, ezért fogyasztja az " +
                "akkumulátort, és épületben pontatlan — a szabad ég alatt a legjobb. " +
                "Hosszabb úthoz érdemes a Beállítások, Haladó, Korlátlan háttérfutás " +
                "engedélyezése pontot megadni, hogy a telefon ne állítsa le közben."
        ),

        // ── ESZKÖZÖK (általános) ────────────────────────────────────────────
        "tools" to topic(
            title = "Eszközök súgó",
            purpose = "Ez az ág a mindennapi segédeszközöké: a fájljaid kezelése, a " +
                "zseblámpa, a lépésszámláló, az olvasók és felismerők, a kamera, és a " +
                "mindennapi holmi — hallás erősítő, számológép, bevásárlólista, " +
                "diktafon, gyógyszer-emlékeztető. Az almenüknek külön súgójuk van; " +
                "ez a három olyan pontról szól, amelyik nem almenü.",
            start = "A Fájlkezelő a telefon teljes tárhelyét mutatja: fel-le söpréssel " +
                "lépkedsz a fájlok között, jobbra söpréssel nyílnak a műveletek, " +
                "balra söpréssel a szülőmappába lépsz vissza. A Zseblámpa egy " +
                "kapcsoló. A Műveletsorok azokat a betanított lépéssorokat indítja, " +
                "amiket a képernyőolvasóval vettél fel.",
            gestures = "A Fájlkezelőben a lista végén van egy Menü pont: onnan lehet " +
                "többet egyszerre kijelölni és együtt törölni, másolni vagy " +
                "áthelyezni. A törlés és a beillesztés kétlépcsős: az első jobbra " +
                "söprés csak megkérdezi, a második hajtja végre — közben a fel vagy " +
                "le söprés a mégse. A Műveletsorokban fel-le söpréssel válogatsz, " +
                "jobbra söpréssel nyílnak a műveletek — Indítás, Lépések " +
                "felolvasása, Megosztás, Törlés —, balra söpréssel kilépsz.",
            trouble = "Ha a Fájlkezelő azt mondja, ehhez teljes fájlhozzáférés kell: az " +
                "Android 11 óta egy alkalmazás csak a saját mappáiban törölhet. " +
                "Söpörj jobbra, a program megnyitja a beállítást, és ott a Super " +
                "DL-nél kapcsold be az összes fájl kezelését. Böngészni enélkül is " +
                "tudsz. Ha egy műveletsor nem indul: a képernyőolvasónak futnia kell, " +
                "kapcsold be a Beállítások, Haladó, Képernyőolvasó menüpontban. Ha " +
                "még egy műveletsorod sincs: felvenni a képernyőolvasóban tudsz — " +
                "menj be abba az alkalmazásba, ahol a munka történik, söpörj balra " +
                "majd fel, csináld végig a lépéseket, aztán megint balra majd fel."
        ),

        // ── LÉPÉSSZÁMLÁLÓ ───────────────────────────────────────────────────
        "steps" to topic(
            title = "Lépésszámláló súgó",
            purpose = "A telefon lépésérzékelőjével számolja a napi lépéseidet, becsli a " +
                "megtett távolságot és az elégetett kalóriát, és élő mérésben a " +
                "sebességedet is megmondja. A háttérben magától dolgozik.",
            start = "A Mai összesítő menüpont egyetlen felolvasás: hány lépés, mennyi út, " +
                "hány kalória, és hogy hol tartasz a napi célhoz képest. Az Élő mérés " +
                "és sebesség menüpont egy képernyőt nyit, ami menet közben " +
                "lekérdezhető. Hogy a becslés pontos legyen, add meg egyszer a " +
                "Testmagasság és testsúly menüpontban az adataidat — a program " +
                "bemondja a mostaniakat, aztán bediktálod az újakat.",
            gestures = "Az élő mérésben a fel, a le és a jobbra söprés ugyanazt adja: az " +
                "aktuális lépésszámot, a távolságot, a kalóriát és a sebességet. " +
                "Balra söpréssel lépsz ki — a számlálás attól még megy tovább a " +
                "háttérben. A Napi cél beállítása menüpontnak nincs külön képernyője: " +
                "minden választás ezer lépéssel emeli a célt, és tizenötezernél " +
                "visszaugrik kétezerre.",
            trouble = "Ha azt mondja, ebben a telefonban nincs lépésérzékelő: a készülék " +
                "nem tudja mérni a lépést, ezen a programon nem múlik. Ha a sebesség " +
                "hiányzik, és azt mondja, helymeghatározási engedély kell: add meg — " +
                "a sebesség a G P S-ből jön, a lépés attól függetlenül számol. " +
                "A sebesség épületben pontatlan. Ha a testadatok diktálása nem megy: " +
                "a testmagasságot százhúsz és kétszázhúsz centiméter, a testsúlyt " +
                "harminc és kétszáz kilogramm között fogadja el."
        ),

        // ── OLVASÓK ─────────────────────────────────────────────────────────
        "tools_readers" to topic(
            title = "Olvasók súgó",
            purpose = "Négy olvasó és egy vonalkód-olvasó. Mind a kamerát használja: a " +
                "gyógyszerdoboz feliratát, a termék címkéjét, egy dokumentum " +
                "szövegét olvassa fel, a folyamatos olvasó pedig hosszabb szövegen " +
                "vezet végig részekre bontva. A Q R olvasó kódokat és vonalkódokat " +
                "ismer fel, és a kód tartalmához műveletet is ajánl.",
            start = "Válaszd ki, mit olvasol: a gyógyszerdoboz és a címke olvasó ugyanaz " +
                "a felismerés, csak másra figyel jobban — az egyik a hatóanyagra és " +
                "az adagolásra, a másik az összetételre és az allergénekre. Tartsd a " +
                "feliratot jó fényben a kamera elé, körülbelül olyan távol, hogy " +
                "beleférjen. A program magától olvas, körülbelül másodpercenként; " +
                "nem kell gombot nyomni.",
            gestures = "A három sima olvasóban: söprés felfelé megismétli az utolsó " +
                "felolvasást, söprés lefelé elmondja, hogyan tartsd a kamerát, " +
                "söprés jobbra azonnal olvas, söprés balra kilép. A hangerő gombok " +
                "bármelyike is azonnali felolvasás. A folyamatos olvasóban: söprés " +
                "felfelé az aktuális rész megismétlése, söprés lefelé a következő " +
                "rész, söprés jobbra szünet vagy folytatás, söprés balra kilépés. " +
                "A Q R olvasónál a kód beolvasásáig bármelyik söprés csak annyit " +
                "mond, hogy tartsd a kamerát a kód elé; utána fel-le söpréssel " +
                "válogatsz a műveletek közt — felolvasás ismétlése, hívás, üzenet, " +
                "e-mail, gyalogos útvonal —, jobbra söpréssel végrehajtod.",
            trouble = "Ha azt mondja, nem található olvasható szöveg: több fény kell, " +
                "vagy közelebb a felirat. Ha azt mondja, még nincs kamera kép, várj " +
                "egy pillanatot — a kamera indul. Ha a folyamatos olvasó magától " +
                "megáll túl sok hiba miatt, jobbra söpréssel folytatod, balra " +
                "söpréssel kilépsz. Ha azt mondja, a kamera most nem érhető el: egy " +
                "másik alkalmazás használja, zárd be azt. A kamera engedélyt egyszer " +
                "kell megadni. A felolvasás legfeljebb tíz sor — hosszabb " +
                "dokumentumhoz a folyamatos olvasó való."
        ),

        // ── FELISMERŐK ──────────────────────────────────────────────────────
        "tools_recognizers" to topic(
            title = "Felismerők súgó",
            purpose = "Ezek nem szöveget olvasnak, hanem a világot írják le: a fény " +
                "erősségét hanggal, a felület színét szóval, és azt, hogy milyen " +
                "tárgyak vannak előtted. A Kártya rendszerezőnek külön súgója van.",
            start = "A Fénydetektor a kamerával méri a fényt, és sípol: minél magasabb a " +
                "hang, annál erősebb a fény — így megtalálod, ég-e a villany vagy " +
                "merre van az ablak. A Színfelismerőt tartsd a felülettől húsz-harminc " +
                "centire, és bemondja a domináns színt. A Mi van előttem? egy " +
                "pillanatkép: tartsd a telefont magad elé, és felsorolja, amit lát.",
            gestures = "Fénydetektor: söprés felfelé vagy lefelé elmondja, hogyan " +
                "használd, söprés jobbra megerősíti, hogy fut, söprés balra kilép. " +
                "A hang magától követi a fényt, nem kell semmit nyomni. " +
                "Színfelismerő: söprés felfelé ismétlés, söprés jobbra az aktuális " +
                "szín, söprés lefelé a tartási tanács, söprés balra kilépés. " +
                "Mi van előttem?: söprés jobbra új pillanatkép, söprés felfelé az " +
                "előző megismétlése, söprés lefelé a folyamatos figyelés be- és " +
                "kikapcsolása, söprés balra kilépés; a hangerő gomb is új " +
                "pillanatképet készít.",
            trouble = "Ha a Mi van előttem? azt mondja, nem lát felismerhető tárgyat: " +
                "fordulj kicsit más irányba, és söpörj jobbra egy új pillanatképért — " +
                "a felismerés a gyakori tárgyakat ismeri, nem mindent. Ha a " +
                "színfelismerő azt mondja, még nincs mérés: tartsd a felület felé, és " +
                "adj neki egy pillanatot. Gyenge fényben a szín megbízhatatlan — " +
                "előbb a fénydetektorral nézd meg, van-e elég világos. Ha azt mondja, " +
                "a kamera nem indítható, vagy a memória megtelt: zárd be a többi " +
                "alkalmazást, és indítsd újra."
        ),

        // ── KÁRTYA RENDSZEREZŐ ──────────────────────────────────────────────
        "card_organizer" to topic(
            title = "Kártya rendszerező súgó",
            purpose = "A bankkártyáid, törzsvásárlói és belépőkártyáid megkülönböztetése. " +
                "Egyszer lefotózod mindkét oldalukat és nevet adsz nekik; utána a " +
                "felismerő megmondja, melyiket tartod a kezedben.",
            start = "Az Új kártya hozzáadása menüpontban mutasd a kamerának a kártya " +
                "elejét, és söpörj jobbra — rögzíti. Utána a hátulját, megint jobbra " +
                "söprés. Végül a program megkérdezi a nevét, és bemondod. " +
                "Ha megvan legalább egy kártya, a Kártya felismerése menüpont " +
                "magától figyel: tartsd a kártyát a kamera elé, és bemondja a nevét.",
            gestures = "Hozzáadásnál: söprés jobbra a fotózás, söprés felfelé — ha az " +
                "eleje már megvan — a névadásra ugrás, söprés balra megszakítás. " +
                "A hangerő gombok is fotóznak. A felismerőben nincs mit söpörni: " +
                "magától dolgozik, a balra söprés lép ki. A Mentett kártyák " +
                "listájában fel-le söpréssel válogatsz, jobbra söpréssel újra " +
                "hallod a nevet, balra söpréssel kilépsz. A Kártya törlése " +
                "menüpontból ugyanez a lista nyílik törlés módban: jobbra söprés " +
                "megkérdezi, biztos-e, és még egy jobbra söprés törli.",
            trouble = "Ha a felismerő hallgat, pedig kártyát tartasz elé: nem talált " +
                "elég hasonlót — mozgasd kicsit, adj több fényt, és tartsd a " +
                "kártyát laposan. Nem mond olyat, hogy nem ismerem fel; a csend " +
                "jelenti azt. Ha egy kártyát rendszeresen elvét, vedd fel újra, " +
                "jobb fényben. A névadáshoz hangfelismerés kell: ha azt mondja, " +
                "a hangfelismerés nem elérhető, internet és a Google beszédfelismerő " +
                "szükséges. Legfeljebb száz kártya menthető."
        ),

        // ── KAMERA ──────────────────────────────────────────────────────────
        "tools_camera" to topic(
            title = "Kamera súgó",
            purpose = "Fényképezés és videó úgy, hogy hallod, hol vannak az arcok a " +
                "képen — így magadról és másokról is tudsz úgy képet készíteni, hogy " +
                "tényleg rajta legyenek. A képek a D C I M, Super DL mappába kerülnek.",
            start = "A Kamera és szelfi menüpont indítja. A program folyamatosan mondja, " +
                "hol lát arcot; ha jó a beállás, söpörj jobbra, és elkészül a kép. " +
                "Előlapi kamerára a felfelé söpréssel váltasz. A Kamera minőség " +
                "menüpontban választhatsz alacsony, közepes és magas minőség közül — " +
                "a magas szebb képet ad, de nagyobb fájlt.",
            gestures = "Söprés jobbra: fénykép. Söprés felfelé: váltás a szelfi és a " +
                "hátlapi kamera között. Söprés lefelé: az utolsó fénykép megosztása " +
                "vagy küldése, videó közben pedig a felvétel leállítása. Söprés " +
                "balra: kilépés. Hangerő fel: videó indítása a hátlapi kamerán. " +
                "Hangerő le: videó leállítása. A Kamera minőségnél fel-le söpréssel " +
                "választasz, jobbra söpréssel mented, balra söpréssel kilépsz.",
            trouble = "Ha azt mondja, videófelvétel nem elérhető ezen a kamerán: a " +
                "szelfi kamerán vagy ezen a készüléken nincs videó — fénykép és arc " +
                "jelzés attól még működik. Videó közben nem lehet kamerát váltani. " +
                "Ha azt mondja, még nincs mentett fénykép: előbb söpörj jobbra egy " +
                "képért, utána oszthatod meg. A kamera mellett mikrofon engedélyt is " +
                "kér a videó hangjához; ha nem adod meg, a fényképezés akkor is megy. " +
                "Ha a memória megtelt, zárd be a többi alkalmazást, és indítsd újra."
        ),

        // ── MINDENNAPI ──────────────────────────────────────────────────────
        "tools_daily" to topic(
            title = "Mindennapi súgó",
            purpose = "Négy hétköznapi eszköz. Kettőnek — a Bevásárlólistának és a " +
                "Diktafonnak, meg a Patika Őrangyalnak — saját súgója van; ez a " +
                "Hallás erősítőről és a Számológépről szól.",
            start = "A Hallás erősítő a telefon mikrofonjából a fülhallgatódba viszi a " +
                "hangot, felerősítve — Bluetooth fejhallgatóval is. Kapcsold be, és " +
                "állítsd magadnak. A Számológép kétféleképp megy: bediktálod a " +
                "számolást, vagy egy offline számbillentyűzeten beírod.",
            gestures = "Hallás erősítő: fel-le söpréssel választasz a beállítások közt — " +
                "Bekapcsolás, Mikrofon forrás, Fő erősítés, Mikrofon erősítés, Mély " +
                "hang, Közép hang, Magas hang, Balansz —, jobbra söpréssel növeled " +
                "vagy bekapcsolod, balra söpréssel csökkented vagy kikapcsolod. " +
                "Kilépni a Bekapcsolás beállításról tudsz: ha már ki van kapcsolva, " +
                "a balra söprés zárja be a képernyőt. Számológép: jobbra söprés a " +
                "diktálás, lefelé söprés a számbillentyűzet, balra söprés a mégse. " +
                "A billentyűzeten fel-le söpréssel válogatsz a gombok közt, jobbra " +
                "söpréssel beírsz, balra söpréssel törölsz vissza; a lista végén ott " +
                "a Diktálás és a Kész.",
            trouble = "Ha a hallás erősítő azt mondja, mikrofon engedély nélkül nem " +
                "működik: add meg az engedélyt, és indítsd újra. Ha visítani kezd, " +
                "vegyél vissza a fő erősítésből, vagy tedd távolabb a telefont a " +
                "fülhallgatótól. A számológép diktálásnál a magyar szavakat érti: " +
                "kétszer három, tizenkettő osztva néggyel. Ha azt mondja, nem " +
                "értettem a számolást, mondd tagoltabban, vagy söpörj lefelé, és írd " +
                "be a billentyűzeten. Nullával osztani nem lehet: arra érvénytelen " +
                "számolást mond."
        ),

        // ── BEVÁSÁRLÓLISTA ──────────────────────────────────────────────────
        "shopping" to topic(
            title = "Bevásárlólista súgó",
            purpose = "Több bevásárlólistát vezethetsz, tételekkel és árral. A tételeket " +
                "megvan-jelöléssel pipálod ki, és ha az árakat is megadod, a lista " +
                "végén megkapod az összeget.",
            start = "Az Új lista létrehozása menüpontban bemondod a lista nevét, aztán a " +
                "tételeket egyesével — mindegyik után az árát is megadhatod —, és " +
                "amikor végeztél, azt mondod: kész. A Listáim megnyitása menüpont a " +
                "meglévőket sorolja.",
            gestures = "A listák közt a felfelé söprés lépked, a lefelé söprés viszont a " +
                "lista műveleteit nyitja meg — Lista megnyitása, Új tétel, Lista " +
                "átnevezése, Lista törlése —, a jobbra söprés megnyitja a listát, a " +
                "balra söprés kilép. A listán belül fel-le söpréssel lépkedsz a " +
                "tételek közt, jobbra söpréssel nyílnak a tétel műveletei — Megvan " +
                "jelölés, Új tétel hozzáadása, Név módosítása, Ár módosítása, Tétel " +
                "törlése, Lista törlése —, balra söpréssel kilépsz. Ha bármelyik " +
                "tételnek van ára, a lista végén van még egy összegző pont.",
            trouble = "Ha a diktálás nem érti a tételt, mondd rövidebben, egy szóval. " +
                "Üres nevet nem fogad el. Ha az átnevezés nem sikerül, valószínűleg " +
                "már van ilyen nevű listád — válassz másik nevet. A lista törlése " +
                "megkérdezi, biztos vagy-e: a jobbra söprés töröl, a balra söprés a " +
                "mégse. A tételek diktálásához internet és a Google beszédfelismerő " +
                "kell."
        ),

        // ── PROFI DIKTAFON ──────────────────────────────────────────────────
        "dictaphone" to topic(
            title = "Profi Diktafon súgó",
            purpose = "Hangfelvétel komolyan véve: formátum, mintavétel, bitráta és " +
                "csatorna választható, a zajszűrés ki- és bekapcsolható, és van egy " +
                "teljesen nyers mód is, ami pontosan azt rögzíti, amit a mikrofon " +
                "hall. A felvételek a telefonon maradnak, és onnan küldhetők tovább.",
            start = "A Felvétel indítása menüpont azonnal indul — egy sípszó jelzi, hogy " +
                "megy. Előtte érdemes a Minőség és formátum beállítása menüpontban " +
                "eldönteni, mit akarsz: az alap a W A V, negyvennégy kilohertz, mono, " +
                "zajszűrés nélkül. Ha kíváncsi vagy, mit tud a készüléked, a Mit tud " +
                "a mikrofonom menüpont megvizsgálja és felolvassa.",
            gestures = "Felvétel közben: söprés felfelé vagy lefelé bemondja az eltelt " +
                "időt, söprés jobbra szünetel vagy folytat — ezt sípszó jelzi, nem " +
                "beszéd —, söprés balra pedig LEÁLLÍTJA ÉS MENTI a felvételt. " +
                "A Mentett felvételek listájában fel-le söpréssel válogatsz, jobbra " +
                "söpréssel nyílnak a műveletek — Lejátszás, Megosztás e-mailben, " +
                "Megosztás Bluetooth vagy más alkalmazással, Felvétel törlése —, " +
                "balra söpréssel kilépsz. A beállításoknál fel-le söpréssel " +
                "választasz, jobbra söpréssel lépsz be és mented, balra söpréssel " +
                "visszalépsz mentés nélkül.",
            trouble = "Ha azt mondja, mikrofon engedély szükséges a felvételhez: add meg, " +
                "és indítsd újra. Ha a felvétel indítása nem sikerül, nézd meg a " +
                "tárhelyet is, nem csak a mikrofont. Fontos: a balra söprés MENT, " +
                "nem eldob — nincs olyan mozdulat, ami eldobná a felvételt, ezért " +
                "amit nem akarsz megtartani, a Mentett felvételek közül töröld. " +
                "A bitráta csak M P 3 és A A C formátumnál állítható, W A V-nál nem. " +
                "Ha a teljesen nyers felvételt bekapcsolod, a program megmondja, hogy " +
                "a készüléked támogatja-e; ha nem, a lehető legkevésbé feldolgozott " +
                "forrást használja."
        ),

        // ── PATIKA ŐRANGYAL ─────────────────────────────────────────────────
        "pharmacy_guardian" to topic(
            title = "Patika Őrangyal súgó",
            purpose = "Gyógyszer-emlékeztető és gyógyszerkereső. Napszakhoz kötött " +
                "emlékeztetőket állítasz be, és a telefon szól, amikor be kell venni " +
                "— zárt képernyőn is. A kereső a gyógyszerekről ad tájékoztatást. " +
                "Fontos: ez tájékoztatás, nem gyógyszerészeti tanács; a " +
                "kölcsönhatásokért mindig kérdezd meg a gyógyszerészed.",
            start = "Az Új gyógyszer rögzítése menüpont végigvezet: bemondod a gyógyszer " +
                "nevét, kipipálod a napszakokat — reggel nyolc, dél tizenkettő, este " +
                "hat, lefekvés tíz —, megadod, naponta, hetente vagy egyéni napokon " +
                "kell-e, végül hogy hány napig. A végén összefoglalja, és jobbra " +
                "söpréssel mented. Az Aktuális emlékeztetők felolvasása menüpont " +
                "bármikor felsorolja, mi van beállítva.",
            gestures = "A napszakoknál FIGYELJ, mert itt más a szabály: fel-le söpréssel " +
                "lépkedsz, jobbra söpréssel pipálod ki vagy veszed le — több is " +
                "választható —, és a BALRA SÖPRÉS a végén a tovább, nem a vissza. " +
                "Az egyéni napoknál ugyanez. A gyógyszerkeresőben a szöveg részekre " +
                "van bontva: felfelé vagy jobbra söpréssel ismétled, lefelé " +
                "söpréssel jön a következő rész, balra söpréssel kilépsz. Amikor " +
                "megszólal az emlékeztető, fel-le söpréssel választasz a két " +
                "művelet közül — Emlékeztetés egy óra múlva, Bevétel megerősítése —, " +
                "jobbra söpréssel végrehajtod; a balra söprés vagy bármelyik hangerő " +
                "gomb bezárja.",
            trouble = "Ha a rögzítés el sem indul, mert pontos ébresztő engedélyt kér: " +
                "add meg a program által megnyitott beállításban, enélkül az " +
                "emlékeztető késhet vagy elmaradhat. Ha azt mondja, legalább egy " +
                "napszakot pipálj ki: a jobbra söprés a kipipálás, a balra a tovább. " +
                "Ha a kereső azt mondja, erről a gyógyszerről nem talált megbízható " +
                "leírást: próbáld a hatóanyag nevével, és kérdezd meg a " +
                "gyógyszerészed. A keresőhöz internet kell. Az emlékeztető törlése " +
                "kétlépcsős: jobbra söprés megkérdezi, még egy jobbra söprés töröl."
        ),

        // ── BESZÉDTÉMA ──────────────────────────────────────────────────────
        "voice_theme" to topic(
            title = "Beszédtéma súgó",
            purpose = "A telefon néhány visszatérő pillanatában — merüléskor, töltéskor, " +
                "reggel és este — nem a gépi hang szólal meg, hanem egy FELVETT hang. " +
                "Ettől a program valakivé válik, nem marad a rendszer újabb üzenete. " +
                "Ez NEM azonos a Söpörj hangtémával: az a sípoló gesztus-hangokat " +
                "jelenti, ez pedig kimondott mondatokat. " +
                "Egy dolog viszont mindig marad: a SZÁM. Ha a hang azt mondja, hogy " +
                "éhes vagyok, a program utána akkor is kimondja a töltöttséget — mert " +
                "az önmagában nem árulja el, hogy tíz perc van hátra vagy két óra.",
            start = "Beállítások, Hangok, Beszédtéma. Kapcsold be, majd válaszd a " +
                "Hangok kipróbálása pontot: sorban lejátssza mind a hat eseményt, és " +
                "megmondja, melyikhez nincs még felvett hang. Ha egy hang hiányzik, a " +
                "program a szokásos mondatot mondja — némaság soha nincs. " +
                "A saját hangfájlok helye: Android, data, Super DL, files, elena. " +
                "A fájlnevek ékezet nélkül: battery low, battery full, charger in, " +
                "charger out low, morning, night. A kiterjesztés lehet wav, mp3, m4a " +
                "vagy ogg — nem kell konvertálnod.",
            gestures = "A hat esemény: merüléskor, feltöltve, töltő bedugva, töltő " +
                "kihúzva alacsony töltöttségnél, jó reggelt, jó éjszakát. " +
                "Mindegyik külön ki-be kapcsolható. Az Első figyelmeztetés szintje " +
                "pontban állítod, hány százaléknál szóljon először a merülés — onnantól " +
                "kétszázalékonként ismétel, amíg töltőre nem teszed. " +
                "A Jó reggelt alapból nem órára szólal meg, hanem a beállított idő " +
                "UTÁN az első feloldáskor: egy üres szobának köszönni zaj. Ezt a Jó " +
                "reggelt csak feloldáskor kapcsolóval állítod át pontos időpontra. " +
                "A Napi keret azt mondja meg, hányszor szólalhat meg naponta — a báj " +
                "attól báj, hogy ritka. A merülés figyelmeztetése ebbe nem számít bele.",
            trouble = "Ha nem szólal meg: nézd meg a Hangok kipróbálása pontot, az " +
                "megmondja, van-e egyáltalán felvett hang. Ha a fájl ott van, de nem " +
                "szól, valószínűleg rossz mappába került: a fejlesztői és a kiadási " +
                "változat mappája KÜLÖNBÖZŐ. Ha a jó éjszakát nem jön: ez a köszönés " +
                "az éjszakai csendtől függetlenül szól, tehát nem az némítja el — " +
                "ellenőrizd a kapcsolóját és az időpontját. Ha a jó reggelt elmarad, " +
                "de a telefont csak délben veszed kézbe, az rendben van: az első " +
                "feloldáskor szólal meg, nem korábban."
        ),

        // ── TELEFON ÉS HÍVÁSOK ──────────────────────────────────────────────
        "calls" to topic(
            title = "Telefon és Hívások súgó",
            purpose = "Hívás névjegyből vagy szám tárcsázásával, a névjegyzék kezelése, " +
                "a hívásnapló, és a kedvencek — azok a számok, amiket egy mozdulattal " +
                "hívsz. A telefonszámokat a program számjegyenként mondja, hogy vakon " +
                "is ellenőrizni tudd.",
            start = "A leggyorsabb út a Névjegyből hívás: bemondod a nevet, a program " +
                "megismétli a nevet és a számot, és ha jobbra söpörsz, hív. Több " +
                "találatnál előbb fel-le söpréssel választasz. Ha a névjegyzék üres, " +
                "a Névjegyek szinkronizálása menüpont áthozza a telefon névjegyeit.",
            gestures = "Számbevitelnél — Szám tárcsázása, Új névjegy — egy választó jön: " +
                "jobbra söprés a diktálás, lefelé söprés az offline számbillentyűzet, " +
                "felfelé söprés beilleszti a vágólapon lévő számot, balra söprés a " +
                "mégse. A billentyűzeten fel-le söpréssel válogatsz, jobbra söpréssel " +
                "beírsz, és FIGYELJ: ott a balra söprés visszatörlés, nem kilépés; a " +
                "lista végén van a Kész. A névjegyzékben fel-le söpréssel lépkedsz, " +
                "jobbra söpréssel nyílnak a névjegy műveletei — Hívás indítása, SMS " +
                "küldés, Egyéni csengőhang, Névjegy szerkesztése, Névjegy törlése. " +
                "A hívásnaplóban a jobbra söprés műveleteket ad: hívás, SMS, szám " +
                "másolása, mentés névjegyként, kedvencekhez adás, szám letiltása. " +
                "A KEDVENC HÍVÁSA a leggyorsabb út: fel-le söpréssel megkeresed, és a " +
                "jobbra söprés azonnal tárcsáz — itt szándékosan nincs visszakérdezés, " +
                "mert a kedvenc épp az, akit gondolkodás nélkül akarsz hívni.",
            trouble = "Ha azt mondja, hívás engedély szükséges: a rendszer beállításaiban " +
                "add meg a Super DL-nek a telefon engedélyt. Ugyanígy a névjegyek " +
                "olvasásához és mentéséhez is külön engedély kell. " +
                "Ha a névjegyzék betűk szerint nyílik és ki akarsz lépni, a balra " +
                "söprés előbb a betűkhöz visz vissza, csak onnan kifelé. A betűindexet " +
                "és azt, hogy a teljes számot mondja-e vagy csak az utolsó négy " +
                "számjegyet, a Névjegyzék beállítások almenüben kapcsolod."
        ),

        // ── NÉVJEGYZÉK BEÁLLÍTÁSOK ──────────────────────────────────────────
        "contact_settings" to topic(
            title = "Névjegyzék beállítások súgó",
            purpose = "Itt döntöd el, hogyan viselkedjen a névjegyzék: betűk szerint " +
                "csoportosítson-e, és mennyit mondjon ki a telefonszámból. Innen " +
                "menthetők ki és tölthetők vissza a névjegyek is — költözéskor vagy " +
                "telefonváltáskor ez a mentőöv.",
            start = "A Jelenlegi beállítás felolvasása megmondja, mi van most " +
                "érvényben. A Betűindex és a Telefonszám kimondása menüpont egy-egy " +
                "kapcsoló: minden választás átbillenti, és elmondja, mi lett belőle.",
            gestures = "Ezeknek a pontoknak nincs saját képernyőjük, csak beszélnek. " +
                "A Névjegyek mentése fájlba egy lépés: elkészíti a fájlt, és bemondja " +
                "a nevét meg a helyét. A visszatöltésnél listát kapsz a talált " +
                "fájlokról: fel-le söpréssel választasz, jobbra söpréssel indul a " +
                "visszatöltés, balra söpréssel a mégse.",
            trouble = "Ha a visszatöltés azt mondja, nem talált mentett névjegy fájlt: " +
                "tedd a pont v c f végű fájlt a Letöltések mappába, és próbáld újra — " +
                "oda tudod tenni a Fájlátvitel vagy a WiFi fájlportál segítségével. " +
                "A visszatöltés a telefonszám alapján kihagyja azt, ami már megvan, " +
                "tehát nem lesznek duplikátumok. Ha a mentés nem sikerül, " +
                "valószínűleg nincs menthető névjegy, vagy hiányzik a fájl " +
                "hozzáférés."
        ),

        // ── ÜZENETEK ÉS E-MAIL (a teljes ág) ────────────────────────────────
        "sms" to topic(
            title = "Üzenetek és E-mail súgó",
            purpose = "Három dolog egy helyen: SMS, e-mail és a Csevejcenter. Az SMS-nek " +
                "és az e-mailnek külön almenüje és külön súgója van; ez az ág egésze " +
                "és a Csevejcenter.",
            start = "A Csevejcenter valós idejű csevegő: nyitsz egy szobát, a kódját " +
                "megosztod, és aki beírja, ott van veled. Élő hang is kapcsolható. " +
                "Fejhallgatóval a legjobb, mert különben a hangszóró visszhangzik.",
            gestures = "A Csevejcenter a többitől eltérően lehetőség-kerék: fel-le " +
                "söpréssel forgatod a lehetőségeket, jobbra söpréssel választasz, " +
                "balra söpréssel kilépsz a szobából vagy a Csevejcenterből. Két " +
                "koppintás megismétli az aktuális lehetőséget. A szobában ezek a " +
                "lehetőségek vannak: Üzenet írása és küldése, Beszélgetés " +
                "felolvasása, Résztvevők, Élő hang be vagy ki, Szobakód vágólapra, " +
                "Kilépés a szobából.",
            trouble = "Ha azt mondja, a csevegéshez internet kell: ez az egy funkció " +
                "hálózat nélkül nem megy. Ha az élő hang nem indul, mikrofon engedély " +
                "kell. A szobakódot a program betűzve mondja — ha valakinek átadod, " +
                "a Szobakód lehetőséggel vágólapra másolhatod, és beilleszthetsz " +
                "SMS-be vagy e-mailbe. A diktálás itt a rendszer beszédfelismerő " +
                "ablakát használja, nem a program sajátját."
        ),

        // ── SMS ÜZENETEK ────────────────────────────────────────────────────
        "sms_sub" to topic(
            title = "SMS üzenetek súgó",
            purpose = "A bejövő és a kimenő SMS-eid felolvasása, válasz, továbbítás, " +
                "törlés, és új üzenet írása diktálással. Az írásjeleket ki lehet " +
                "mondani: vessző, pont, kérdőjel.",
            start = "A Bejövő üzenetek menüpont a kapott SMS-eket sorolja: fel-le " +
                "söpréssel lépkedsz, jobbra söpréssel nyílnak a műveletek. Új üzenet " +
                "írásához az Üzenet írása menüpontot válaszd: bemondod a címzettet — " +
                "nevet vagy számot —, a program megerősítteti, aztán bemondod a " +
                "szöveget, és a végén még egyszer megkérdezi, elküldje-e.",
            gestures = "Az üzenetlistában fel-le söpréssel lépkedsz, jobbra söpréssel " +
                "nyílnak a műveletek — Üzenet felolvasása, Válasz küldése, Üzenet " +
                "továbbítása, Üzenet törlése —, a műveletek közt is fel-le söpréssel " +
                "választasz és jobbra söpréssel hajtod végre. Minden megerősítő " +
                "kérdésnél ugyanaz a szabály: jobbra söprés igen, balra söprés mégse, " +
                "fel vagy le söprés megismétli a kérdést. Válasz és továbbítás után " +
                "a program visszavisz oda, ahonnan indultál, nem a menübe.",
            trouble = "Ha azt mondja, SMS küldés vagy olvasás engedély szükséges: add meg " +
                "a rendszerben. Ha épp most kaptál üzenetet, és mégsem látod: várj " +
                "pár másodpercet, és nyisd meg újra a listát. Ha azt akarod, hogy a " +
                "Super DL fogadja és kezelje az üzeneteket, az SMS beállítások " +
                "almenüben állítsd be alapértelmezett üzenet alkalmazásnak — enélkül " +
                "az olvasás korlátozott lehet. A továbbítás nem kérdezi meg a " +
                "szöveget, mert az eredetit viszi tovább."
        ),

        // ── E-MAIL ──────────────────────────────────────────────────────────
        "email_sub" to topic(
            title = "E-mail súgó",
            purpose = "Leveleket olvasol és írsz a telefonról, diktálással. A " +
                "postafiókból válaszolni és továbbítani is tudsz, a feladót pedig " +
                "elmentheted a címjegyzékbe, hogy legközelebb elég legyen a nevét " +
                "bemondani.",
            start = "Először az E-mail fiók beállítása almenüben add meg a fiókot. " +
                "Gmailhez ALKALMAZÁSJELSZÓ kell, nem a sima jelszavad — azt a Google " +
                "fiókod biztonsági beállításaiban tudod létrehozni. Ha a program " +
                "talál e-mail fiókot a telefonon, a címet magától felajánlja. Ha " +
                "megvan, a Postafiók megnyitása hozza a leveleket, az Új levél írása " +
                "pedig végigvezet a küldésen.",
            gestures = "A levéllistában fel-le söpréssel lépkedsz, jobbra söpréssel " +
                "megnyílik a teljes levél, balra söpréssel kilépsz. A megnyitott " +
                "levélben a fel vagy le söprés újra felolvassa, a jobbra söprés a " +
                "levél műveleteit nyitja — Válasz a feladónak, Továbbítás, Levél " +
                "újraolvasása, Feladó mentése a címjegyzékbe, Új levél írása —, a " +
                "balra söprés visszavisz a levélhez. Íráskor minden lépés után " +
                "megerősítés jön: jobbra söprés tovább vagy küldés, balra söprés " +
                "mégse. A címjegyzékben a jobbra söprés csak felolvassa a címet, nem " +
                "kezd levelet.",
            trouble = "Ha azt mondja, előbb állítsd be az e-mail küldőt: a fiók még " +
                "nincs megadva. Ha a küldés sikertelen, először az alkalmazásjelszót " +
                "ellenőrizd — a sima jelszó nem működik. Az E-mail kapcsolat " +
                "vizsgálata menüpont végigpróbálja a kapcsolatot, és elmondja, hol " +
                "akad el; ez fél percig is eltarthat. Ha egy levélre nem tudsz " +
                "válaszolni, mert nincs válaszolható feladó-cím, használd az Új levél " +
                "írása pontot. A jelszó diktálását el lehet kerülni: ha felteszel a " +
                "WiFi fájlportállal egy szövegfájlt, aminek a nevében szerepel az, " +
                "hogy jelszó, a program abból olvassa be."
        ),

        // ── S.O.S. ──────────────────────────────────────────────────────────
        "sos_settings" to topic(
            title = "S.O.S. súgó",
            purpose = "Baj esetén egyetlen menüpont — vagy egyetlen kimondott mondat — " +
                "elindít egy segítségkérő láncot: SMS-t küld a helyzeteddel a megadott " +
                "számokra, aztán sorban hívja őket, és ha senki nem veszi fel, a 112-t. " +
                "Ez a súgó azt is elmondja, hogyan állítod be, és hogyan állítod le.",
            start = "Előbb add meg a számokat: az S.O.S. szám 1-től 4-ig menüpontokban " +
                "vagy bediktálod, vagy a névjegyzékből választasz. Négy szám adható " +
                "meg. Ha egy sincs, az S.O.S. nem indul el, csak szól, hogy nincs " +
                "beállítva szám. Az S.O.S. számok felolvasása bármikor ellenőrizhető. " +
                "Magát a riasztást a főmenüben lévő S.O.S. menüponttal indítod — vagy " +
                "kimondva, ha betanítottál egy hívómondatot.",
            gestures = "HANGGAL IS INDÍTHATÓ. Az S.O.S. hívómondat tanítása menüpontban " +
                "bemondasz egy egész mondatot — például: kérem hívja a segítséget. " +
                "Ha ezt később kimondod, és az Elena figyelő be van kapcsolva, a lánc " +
                "elindul: nem kell hozzá az Elena szó, nem kell parancs, nem kérdez " +
                "vissza semmit. Legfeljebb öt mondat tanítható; a hívómondataidat az " +
                "S.O.S. hívómondataim menüpont sorolja, ott fel-le söpréssel " +
                "válogatsz, és két jobbra söpréssel törölsz. " +
                "Indításkor alapból öt másodperc visszaszámlálás van: ez alatt a " +
                "balra söprés megszakítja, vagy azt is mondhatod: mégse. A jobbra " +
                "söprés ilyenkor NEM indítja el hamarabb, csak emlékeztet, hogy " +
                "balra söpréssel állíthatod le. LEÁLLÍTANI a már futó láncot úgy " +
                "tudod, hogy MÉGEGYSZER elindítod ugyanazt az S.O.S. menüpontot — " +
                "erre a program a lánc alatt többször is figyelmeztet. A szám " +
                "beállításánál fel-le söpréssel választasz szám beírása és " +
                "névjegyből választás között, jobbra söpréssel erősíted meg.",
            trouble = "A lánc szándékosan makacs: minden számot két körben végighív, " +
                "huszonöt másodperces csörgetéssel, és nem tudja megkülönböztetni a " +
                "hangpostát az élő embertől — ezért ha sikerült beszélned valakivel, " +
                "NEKED kell leállítanod az S.O.S. újraindításával. Két kör után a " +
                "112 következik. A Visszaszámlálás ki és be menüpont a védőháló: " +
                "bekapcsolva véd a zsebben indított téves riasztástól, és a 112 " +
                "hívása előtt is ad tíz másodpercet. Kikapcsolva minden azonnal " +
                "indul, megállítási lehetőség nélkül — gondold végig, mert egy " +
                "véletlen indítás is valódi riasztás lesz. " +
                "Ha a betanított mondatot kimondod, és mégsem történik semmi: az " +
                "Elena figyelő valószínűleg ki van kapcsolva — az Asszisztens menüben " +
                "kapcsold be, mert enélkül a telefon nem hallgat. A mondatnak " +
                "legalább két szóból és tizenkét betűből kell állnia: ez véd attól, " +
                "hogy beszélgetés közben véletlenül elhangozzon. A hívómondat is a " +
                "visszaszámlálás szabályát követi, tehát bekapcsolt visszaszámlálásnál " +
                "öt másodperced van meggondolni magad."
        ),

        // ── IDŐ ÉS SZERVEZÉS ────────────────────────────────────────────────
        "time" to topic(
            title = "Idő és Szervezés súgó",
            purpose = "Három almenü: Óra és ébresztés — pontos idő, ébresztők, " +
                "időzítők; Program — a naptárad; Jegyzetek — diktált feljegyzések. " +
                "Mindháromnak külön súgója van, ez az ág egésze.",
            start = "A pontos időt az Óra és ébresztés almenü első pontja mondja meg " +
                "azonnal. A többi három dolog külön világ: az ÉBRESZTŐ időpontra " +
                "szól, az IDŐZÍTŐ időtartamra — például húsz perc múlva —, a " +
                "PROGRAM pedig a telefon naptárába kerül, tehát a gépeden is " +
                "látszik, ha szinkronizáló naptárat választasz.",
            gestures = "Az egész ágban ugyanaz a szabály érvényes: listákban fel-le " +
                "söpréssel válogatsz, jobbra söpréssel lépsz tovább, balra söpréssel " +
                "vissza; a megerősítő kérdéseknél a jobbra söprés az igen, a balra a " +
                "mégse, a fel vagy le söprés pedig megismétli a kérdést. Ahol " +
                "számot kell megadni, mindig ugyanaz a választó jön: jobbra söprés " +
                "diktálás, lefelé söprés offline számbillentyűzet.",
            trouble = "Az ébresztőkhöz és a naptári emlékeztetőkhöz PONTOS ÉBRESZTŐ " +
                "engedély kell — ha a program megnyitja ezt a beállítást, add meg, " +
                "különben az emlékeztető késhet vagy elmaradhat. A naptárhoz külön " +
                "olvasási és írási engedély kell. Ha nem tudod, mit válassz: ami " +
                "óra-perchez kötődik és ismétlődhet, az ébresztő; ami most indul és " +
                "adott idő múlva szól, az időzítő; aminek helye van a napodban, az " +
                "program."
        ),

        // ── ÓRA ÉS ÉBRESZTÉS ────────────────────────────────────────────────
        "clock_sub" to topic(
            title = "Óra és ébresztés súgó",
            purpose = "Pontos idő, ébresztők és időzítők. Az ébresztő adott órára szól " +
                "és ismétlődhet; az időzítő adott időtartam múlva, és közben " +
                "időközönként is jelezhet — előadáshoz, főzéshez hasznos.",
            start = "Az Új ébresztő diktálása menüpontban megadod az időt — például: " +
                "hét harminc —, aztán a nevét, aztán hogy milyen gyakran " +
                "ismétlődjön, és a végén megerősíted. Időzítőhöz az Új időzítő " +
                "menüpont: perc vagy óra, mennyi, milyen gyakran jelezzen, mi a neve.",
            gestures = "Az idő beírásánál a számbillentyűzet NÉGY számjegyet vár — óra, " +
                "óra, perc, perc —, és a negyedik után magától továbblép. " +
                "FIGYELJ az Ébresztők listájára: ott a jobbra söprés nem felolvas, " +
                "hanem az adott ébresztő CSENGŐHANGJÁT engedi kiválasztani. Amikor " +
                "megszólal az ébresztő, a jobbra söprés a szundi — tíz perc —, a " +
                "balra söprés a leállítás, a fel vagy le söprés megismétli. " +
                "Az Ébresztés kihagyása menüpont különleges: ott a jobbra söprés " +
                "PIPÁL, és a BALRA SÖPRÉS A TOVÁBB; a következő lépésben a felfelé " +
                "söprés növeli, a lefelé csökkenti a kihagyások számát.",
            trouble = "Egyszerre csak EGY időzítő futhat: ha újat indítasz, az előzőt a " +
                "program szó nélkül leállítja. Ha az ébresztő beállítása el sem " +
                "indul, a pontos ébresztő engedély hiányzik — a program megnyitja a " +
                "beállítást, add meg. Ha egy ébresztőt csak néhány alkalomra akarsz " +
                "kihagyni — például szabadság alatt —, ne töröld: az Ébresztés " +
                "kihagyása pontban add meg, hány alkalom maradjon ki, utána magától " +
                "visszatér. Az Ébresztés kihagyás állapota menüpont meg is mutatja, " +
                "min van érvényben kihagyás, és egy jobbra söpréssel mindet törli."
        ),

        // ── PROGRAM (NAPTÁR) ────────────────────────────────────────────────
        "program_sub" to topic(
            title = "Program, naptár súgó",
            purpose = "A telefon naptárába ír és onnan olvas: mai, holnapi és heti " +
                "program, új bejegyzés diktálással, szerkesztés, törlés. Ha " +
                "szinkronizáló naptárat választasz, a bejegyzéseid a gépeden és a " +
                "weben is megjelennek.",
            start = "Először a Naptár kiválasztása menüpontban válaszd ki, hova " +
                "kerüljenek a programjaid — a szinkronizáló naptárak vannak elöl, és " +
                "a program megmondja, hogy amit választottál, szinkronizál-e. Utána " +
                "az Új program menüpont végigvezet: név, dátum, kezdés, befejezés, " +
                "ismétlés, végül megerősítés.",
            gestures = "A dátum és az idő megadásánál KÉT út van, és ezt érdemes " +
                "megjegyezni: a jobbra söprés újraindítja a diktálást, a LEFELÉ " +
                "söprés pedig offline számbillentyűzetre vált — a dátum nyolc " +
                "számjegy, év, hónap, nap; az idő négy számjegy. A napi listában " +
                "fel-le söpréssel lépkedsz, jobbra söpréssel nyílnak a műveletek — " +
                "Program felolvasása, Program szerkesztése, Program törlése —, balra " +
                "söpréssel kilépsz. Amikor megszólal az emlékeztető, fel-le " +
                "söpréssel választasz — Emlékeztetés egy óra múlva, Megjelölés " +
                "teljesítettként —, jobbra söpréssel végrehajtod, a balra söprés " +
                "vagy bármelyik hangerő gomb bezárja.",
            trouble = "Ha azt mondja, ma nincs program a naptárban: a heti áttekintőben " +
                "találod a többi napot. Ha a mentés sikertelen, a naptár írási " +
                "engedély hiányzik. Ha a bejegyzéseid nem jelennek meg a gépeden, a " +
                "Naptár állapota menüpont megmondja, hogy a választott naptár " +
                "szinkronizál-e — ha nem, válts a Naptár kiválasztása pontban. " +
                "A befejezési időt el is hagyhatod: akkor egyórás program lesz."
        ),

        // ── JEGYZETEK ───────────────────────────────────────────────────────
        "notes_sub" to topic(
            title = "Jegyzetek súgó",
            purpose = "Diktált feljegyzések: bevásárló ötletek, recept, egy fontos " +
                "telefonszám. A jegyzet bármilyen hosszú lehet, és a felolvasásnál " +
                "részekre bontva kapod, hogy követni lehessen.",
            start = "Az Új jegyzet menüpontban előbb a címet mondod be, aztán a " +
                "szöveget. Az írásjeleket is kimondhatod: vessző, pont, új sor. " +
                "A Jegyzeteim menüpont a meglévőket sorolja.",
            gestures = "A listában fel-le söpréssel válogatsz, jobbra söpréssel " +
                "megnyitod, balra söpréssel kilépsz. A megnyitott jegyzetben a " +
                "LEFELÉ söprés hozza a következő részt, a FELFELÉ söprés " +
                "megismétli az aktuálisat, a balra söprés visszavisz a listához — " +
                "itt a jobbra söprésnek nincs szerepe. A Jegyzet törlése menüpont " +
                "ugyanazt a listát nyitja törlés módban: jobbra söprés megkérdezi, " +
                "még egy jobbra söprés töröl.",
            trouble = "Ha a mentés nem sikerül, vagy üres a szöveg, vagy elérted a " +
                "jegyzet-limitet — ilyenkor törölj a régiekből. Üres címet nem " +
                "fogad el. Ha a diktálás félreért egy szót, a jegyzetet nem lehet " +
                "utólag javítani: töröld, és mondd be újra. Hosszabb szövegnél " +
                "tagolj: mondd ki a pontot és az új sort, mert a felolvasás így " +
                "lesz követhető."
        ),

        // ── KÖNYVEK ─────────────────────────────────────────────────────────
        "books" to topic(
            title = "Könyvek súgó",
            purpose = "A telefonon lévő könyveidet olvassa fel: E P U B, P D F, M O B I, " +
                "T X T, D O C X és még jó néhány formátum. Megjegyzi, hol tartasz, " +
                "könyvjelzőt tehetsz bárhová, és a hangoskönyv-mappákat is lejátssza.",
            start = "Tedd a könyveket a Letöltések, a Dokumentumok vagy a Könyvek " +
                "mappába — a program magától megtalálja őket. Ha máshol tartod, a " +
                "Könyvmappa beállítása menüpontban bemondhatod a mappa nevét vagy " +
                "útvonalát; legfeljebb öt egyéni mappa adható meg. Utána a Könyvtár " +
                "menüpont felsorolja, amit talált. Az Olvasás folytatása a legutóbbi " +
                "könyvet nyitja meg ott, ahol abbahagytad.",
            gestures = "AZ OLVASÓBAN MÁS A SZABÁLY, MINT MÁSHOL, ezt érdemes megjegyezni. " +
                "Lefelé söprés: következő rész. Felfelé söprés: VISSZALAPOZÁS az " +
                "előző részre — itt nincs külön ismétlés, a visszalapozás az. " +
                "Jobbra söprés: KÖNYVJELZŐ mentése oda, ahol tartasz — nem " +
                "továbblapozás. Balra söprés: az olvasás leállítása; a helyet " +
                "megjegyzi. A Bluetooth fülhallgató gombjai is működnek: szünet, " +
                "és a következő-előző gomb lapoz. A listákban a szokásos: fel-le " +
                "söpréssel válogatsz, jobbra söpréssel nyitsz, balra söpréssel " +
                "kilépsz. Hangoskönyvnél viszont a fel-le söprés a VEZÉRLŐK közt " +
                "lépked — szünet, következő sáv, előző sáv, tekerés tizenöt " +
                "másodperccel, hol tartok, könyvjelző, leállítás —, és a jobbra " +
                "söprés hajtja végre a kiválasztottat.",
            trouble = "Ha egy könyv nem nyílik meg: legfeljebb huszonöt megabájt tölthető " +
                "be, és a formátumnak a támogatottak közt kell lennie — ezt a program " +
                "meg is mondja. A zenék mappáját szándékosan kihagyja a keresésből, " +
                "hogy a dalok ne kerüljenek a könyvek közé. A könyv TÖRLÉSÉHEZ teljes " +
                "fájlhozzáférés kell: ha a program erről kezd beszélni, söpörj jobbra, " +
                "és a megnyíló beállításban engedélyezd. A törlés a könyvjelzőket és " +
                "a mentett helyet is elviszi. Ha a felolvasó hangja nem tetszik, a " +
                "Felolvasó hangja almenüben kapcsold be a Saját hangot, és utána " +
                "állítsd a motort, a sebességet és a hangmagasságot — a sebesség és a " +
                "hangmagasság minden választásra egy fokozatot lép, és körbeér."
        ),

        // ── INFORMÁCIÓ ──────────────────────────────────────────────────────
        "info" to topic(
            title = "Információ súgó",
            purpose = "Amit egy nap elején vagy közben tudni akarsz: napi üdvözlés " +
                "névnappal és időjárással, napi összefoglaló, időjárás, hírek több " +
                "mint húsz magyar forrásból, internetes kereső és az akkumulátor " +
                "állapota.",
            start = "A Napi üdvözlés és a Napi összefoglaló egyetlen felolvasás — " +
                "nincs mit kezelni rajta, csak hallgatni. Az összefoglalóban benne " +
                "van a dátum, az akkumulátor, az olvasatlan üzenetek, az időjárás, a " +
                "mai naptári programok és a következő gyógyszer. A Hírek felolvasása " +
                "húsz hírt tölt be a bekapcsolt forrásokból; melyik forrás legyen " +
                "bekapcsolva, azt a Hírforrások kezelése menüpontban döntöd el.",
            gestures = "A hírlistában fel-le söpréssel lépkedsz, jobbra söpréssel a " +
                "program letölti és felolvassa a teljes cikket, balra söpréssel " +
                "kilépsz. Az utolsó hírnél a lefelé söprés a következő húsz hírt " +
                "tölti be. A cikk olvasása közben: lefelé söprés a következő rész, " +
                "felfelé söprés az ismétlés, balra söprés vissza a hírlistához — és " +
                "a cikk végén is a hírlistában találod magad, tehát végig lehet " +
                "hallgatni több hírt egymás után. A Hírforrások kezelésében a jobbra " +
                "söprés helyben KAPCSOLJA ki vagy be a forrást, nem nyit meg semmit. " +
                "Az Internet keresőben a felfelé söprés a KÖVETKEZŐ találat, a " +
                "lefelé söprés jegyzetként menti a találatot, a jobbra söprés " +
                "felolvassa a cikket.",
            trouble = "Ha nincs internet, a hírek, az időjárás és a kereső nem működik — " +
                "a program meg is mondja. Ha egy cikk teljes szövegét nem sikerül " +
                "letölteni, felolvassa helyette a rövid kivonatot, és a listában " +
                "maradsz. Az Időjárás város szerint menüpontban bemondott várost a " +
                "program MEGJEGYZI: onnantól a napi összefoglaló is ezt a várost " +
                "használja a helyzeted helyett. Ha a keresésre a Wikipédiában van " +
                "szócikk, a program rögtön azt olvassa fel, találati lista nélkül."
        ),

        // ── ASSZISZTENS (ELENA) ─────────────────────────────────────────────
        "assistant" to topic(
            title = "Elena súgó",
            purpose = "Elena a program hangos asszisztense: bemondod, mit szeretnél, és " +
                "ő elindítja. Szinte az egész menü elérhető rajta keresztül — idő, " +
                "hívás, üzenet, ébresztő, zene, útvonal, könyv, hírek —, és felel " +
                "kérdésekre is a program működéséről.",
            start = "Az Elena menüpont azonnal hallgatni kezd: mondd ki, mit akarsz. " +
                "Ha nem akarsz a menübe menni érte, kapcsold be az Elena figyelőt: " +
                "onnantól elég kimondanod, hogy Szia Elena, vagy Kérlek Elena. " +
                "A parancsot ugyanabban a lélegzetben is mondhatod: Szia Elena, hány " +
                "óra van. Saját megszólítást az Elena felébresztő tanítása " +
                "menüpontban vehetsz fel.",
            gestures = "Elena képernyőjén két mozdulat van: a jobbra söprés újra " +
                "hallgatni kezd — ez az új parancs —, a balra söprés bezárja. " +
                "A fel-le söprésnek itt nincs szerepe. A Folyamatos beszélgetés " +
                "menüpont azt dönti el, mi legyen a válasz után: bekapcsolva Elena " +
                "tovább hallgat és újabb utasítást vár, kikapcsolva visszalép a " +
                "menübe. Ha az Alapértelmezett asszisztens beállítása menüpontban " +
                "kiválasztod a Super DL-t, az oldalsó gomb hosszú nyomására is Elena " +
                "indul. A Bluetooth gomb asszisztens bekapcsolva a fülhallgató " +
                "gombjának HOSSZÚ nyomására indítja — a rövid nyomás nem elég.",
            trouble = "Ha Elena nem érti: mondd rövidebben, egy-két szóval — idő, " +
                "ébresztő, hívd fel Anyát. Ha azt mondja, nincs stabil internet a " +
                "felismeréshez, a beszédfelismerés hálózatot kér. Zárolt telefonon " +
                "Elena csak a biztonságos parancsokat engedi — idő, akkumulátor, " +
                "üzenetírás, hívás, S.O.S. —, a többihez fel kell oldani a PIN kóddal. " +
                "Ha a figyelő magától leáll, és azt mondja, többször hibába futott: " +
                "ez akkumulátor-védelem, az Asszisztens menüben kapcsold vissza. " +
                "A Bluetooth gomb csak akkor működik, ha a Super DL van előtérben, és " +
                "könyvolvasás közben a fülhallgató gombjai a könyvet vezérlik."
        ),

        // ── KEDVENC ALKALMAZÁSOK ────────────────────────────────────────────
        "favorite_apps" to topic(
            title = "Kedvenc alkalmazások súgó",
            purpose = "Egy rövid, saját listád azokból a dolgokból, amiket sokat " +
                "használsz — akár telepített alkalmazások, akár Super DL funkciók. " +
                "Így nem kell a menüben lefelé keresgélned azt, amit naponta " +
                "négyszer indítasz.",
            start = "A Kedvenc alkalmazás hozzáadása menüpont felsorolja, amiből " +
                "választhatsz: a Super DL saját funkciói és a telepített appok " +
                "egyben. Fel-le söpréssel válogatsz, jobbra söpréssel felveszed — és " +
                "a lista nyitva marad, tehát egymás után többet is felvehetsz. Utána " +
                "a Kedvenc alkalmazás indítása menüpont az, amit naponta használsz.",
            gestures = "Mindhárom listában fel-le söpréssel válogatsz és balra " +
                "söpréssel lépsz ki. A jobbra söprés az indítás, a hozzáadás, " +
                "illetve a törlés listájában a TÖRLÉS — és a törlésnél nincs " +
                "visszakérdezés, egy söprés törli. A kedvenc maga nem vész el " +
                "örökre: bármikor visszaveheted a hozzáadás listából.",
            trouble = "Ha egy kedvenc eltűnik a listából: vagy letörölted az " +
                "alkalmazást a telefonról, vagy a Super DL menüpontja szűnt meg — a " +
                "program ilyenkor csendben kihagyja, nem hibázik. Ha egy külső " +
                "alkalmazás indításakor a képernyőolvasóról kezd beszélni, az azért " +
                "van, mert a Super DL képernyőolvasója kapcsol be, hogy az idegen " +
                "felületet is tudd kezelni; ha ez zavar, a Beállítások, Haladó " +
                "menüben kikapcsolható. Ha azt mondja, az alkalmazás nem indítható, " +
                "az app hibás vagy közben eltűnt."
        ),

        // ── JÁTÉKOK (az ág) ─────────────────────────────────────────────────
        "games" to topic(
            title = "Játékok súgó",
            purpose = "Hét játék, mind hanggal játszható: UNO, Kvíz, Akasztófa, " +
                "Blackjack, Póker, Félkarú rabló és Mille Bornes. Nincs bennük " +
                "valódi pénz, nincs vásárlás, és nincs internet-kapcsolat sem — " +
                "csak a Kvíz kérdéssorait kell egyszer letölteni.",
            start = "Válassz egy játékot, és indul. A kártyajátékok elején eldöntöd, " +
                "hányan játsszatok: kétszemélyes egy géppel, négyszemélyes hárommal. " +
                "A gépi ellenfelek magyar keresztneveket kapnak, és nem csalnak — " +
                "csak abból választanak, amit a szabály megenged. Ha egy játék " +
                "szabályait nem ismered, a Játékszabályok almenüben mindegyikhez " +
                "van külön leírás.",
            gestures = "A közös szabály minden játékban: fel-le söpréssel választasz " +
                "abból, amit épp választani lehet — lap, betű, válasz, tét —, jobbra " +
                "söpréssel lépsz, balra söpréssel kilépsz. A kilépés azonnali, nincs " +
                "megerősítés, de a program elmondja, hol tartottál. A kártyajátékok " +
                "végén a jobbra söprés új kört vagy új játékot indít. " +
                "A pontos szabályokat és az eltéréseket az egyes játékok saját " +
                "súgója írja le — érdemes egyszer végighallgatni, mert néhol a " +
                "fel-le söprés mást jelent, mint amit megszoktál.",
            trouble = "Ha a Kvíz azt mondja, még nincs letöltött kvíz: a kérdéssorok " +
                "külön modulok, a Beállítások, Katalógus, Elérhető modulok pontban " +
                "tölthetők le. Az Akasztófa szavai is bővíthetők onnan, de anélkül " +
                "is van huszonkét beépített szó. Ha egy játékban azt hallod, most " +
                "nem a te köröd, várj — a gépek gondolkodnak, és egy pillanat múlva " +
                "rád kerül a sor. A játékok nem mentenek: kilépés után elölről " +
                "kezdődnek."
        ),

        // ── UNO ─────────────────────────────────────────────────────────────
        "game_uno" to topic(
            title = "UNO szabályok",
            purpose = "Kártyajáték, amiben az nyer, aki elsőként megszabadul az összes " +
                "lapjától. Mindenki hét lappal kezd, és körben haladva egy-egy lapot " +
                "raktok a közös kupacra — de csak olyat, ami illik a legfelsőre.",
            start = "Az elején eldöntöd, hányan játsszatok: kétszemélyes egy géppel, " +
                "négyszemélyes hárommal. Egy lapot akkor rakhatsz le, ha AZONOS A " +
                "SZÍNE az érvényes színnel, vagy AZONOS A SZÁMA, vagy ugyanaz a " +
                "különleges lap. A színválasztó és a színválasztó plusz négy " +
                "bármikor lerakható. Ha nincs játszható lapod, húzol egyet.",
            gestures = "Fel-le söpréssel lépkedsz a kezedben lévő lapok között — a " +
                "program minden lapnál megmondja, játszható-e most. A jobbra " +
                "söprésnek KÉT jelentése van: ha a kijelölt lap játszható, lerakja; " +
                "ha nincs egyetlen játszható lapod sem, húz egyet. Balra söprés a " +
                "kilépés. Ha színválasztót raksz le, a program megkérdezi, milyen " +
                "szín legyen: fel-le söpréssel válogatsz a négy szín közt, jobbra " +
                "söpréssel megerősíted — és itt a balra söprés csak a színválasztást " +
                "szakítja meg, nem lép ki a játékból.",
            trouble = "A leggyakoribb elakadás: egy nem játszható lapon állva jobbra " +
                "söpörsz, mert húzni akarsz, de a program azt mondja, van játszható " +
                "lapod, nem kell húzni. Ilyenkor lépkedj fel-le, amíg egy játszhatót " +
                "nem találsz. A különleges lapok: a kihagyás átugorja a következőt, " +
                "az irányváltás megfordítja a kört — kétszemélyesben ez alig " +
                "számít —, a kettőt húz és a plusz négy pedig húzat a következővel " +
                "és ki is hagyja őt. Ha egy lapod marad, a program kimondja, hogy " +
                "UNO; ezt neked nem kell bemondanod, és nincs érte büntetés."
        ),

        // ── KVÍZ ────────────────────────────────────────────────────────────
        "game_quiz" to topic(
            title = "Kvíz szabályok",
            purpose = "Feleletválasztós kérdések: a program felolvassa a kérdést, majd " +
                "egyesével a válaszokat, és te választasz. Minden helyes válasz egy " +
                "pont, a végén megkapod az eredményt százalékban is.",
            start = "A kérdéssorok LETÖLTHETŐ modulok — a Beállítások, Katalógus, " +
                "Elérhető modulok pontban szerezhetsz be belőlük. Ha csak egy van " +
                "letöltve, a játék rögtön indul; ha több, előbb kiválasztod, melyik " +
                "kérdéssorral játszol. A kérdések sorrendje minden indításnál " +
                "keveredik, tehát ugyanaz a csomag többször is játszható.",
            gestures = "Fel-le söpréssel lépkedsz a válaszok között — a program " +
                "sorszámmal együtt olvassa fel mindet. A jobbra söprés BEADJA az " +
                "éppen felolvasott választ, azonnal, megerősítés nélkül; tehát " +
                "előbb hallgasd végig a lehetőségeket, és csak utána söpörj jobbra. " +
                "A balra söprés kilép, és bemondja, hány helyes válaszod volt.",
            trouble = "Nincs visszalépés és nincs javítás: egy kérdésre egyszer lehet " +
                "válaszolni. Nincs időkorlát, tehát nyugodtan végig lehet " +
                "hallgatni a válaszokat többször is — a fel-le söprés körbeér. " +
                "Ha rossz választ adsz, a program megmondja a helyeset, és ha a " +
                "kérdéshez tartozik magyarázat, azt is felolvassa."
        ),

        // ── AKASZTÓFA ───────────────────────────────────────────────────────
        "game_hangman" to topic(
            title = "Akasztófa szabályok",
            purpose = "A program gondol egy szóra, és csak azt árulja el, hány betűs. " +
                "Te betűket tippelsz. Ha a betű benne van, kiderül, hol áll; ha " +
                "nincs, elveszítesz egy életet. Nyolc életed van.",
            start = "Nincs beállítás, a játék azonnal indul egy véletlen szóval. " +
                "A program felolvassa a szót úgy, hogy a még ki nem talált betűk " +
                "helyén azt mondja: üres. Például egy négybetűs szónál így hangzik: " +
                "a, üres, üres, a. Szavakat a katalógusból is tölthetsz hozzá, de " +
                "huszonkét szó eleve be van építve.",
            gestures = "Fel-le söpréssel lépkedsz a magyar ábécé betűi között — a " +
                "program a már megtippelt betűket magától átugorja, tehát nem tudsz " +
                "kétszer ugyanazt választani. Jobbra söprés a tipp, azonnal. " +
                "Balra söprés a kilépés, és ilyenkor a program elárulja a megoldást.",
            trouble = "Csak egyszerű betűkkel lehet tippelni: a kettős betűket — cs, " +
                "gy, ly, ny, sz, ty, zs — külön betűnként kell megtippelni, tehát a " +
                "csillag szónál a c és az s két külön tipp. Az ékezetes betűk külön " +
                "betűk: az a és az á nem ugyanaz. Minden találat után a program " +
                "újra felolvassa a szót, hogy halld, hol tartasz."
        ),

        // ── BLACKJACK ───────────────────────────────────────────────────────
        "game_blackjack" to topic(
            title = "Blackjack szabályok",
            purpose = "Kártyajáték a krupié ellen. A cél, hogy a lapjaid összege minél " +
                "közelebb legyen a HUSZONEGYHEZ, de ne lépje túl. Aki túllépi, az " +
                "besokallt, és vesztett — akkor is, ha a krupiénak kevesebbje van.",
            start = "Az elején eldöntöd, hányan játsszatok. Mindenki két lapot kap, a " +
                "krupié is, de neki csak az egyik lapját hallod. A számozott lapok " +
                "annyit érnek, amennyi rájuk van írva, a bubi, a dáma és a király " +
                "tízet, az ász tizenegyet — vagy egyet, ha úgy jobb; ezt a program " +
                "magától számolja. Nincs tét és nincs zseton: a nyerés csak " +
                "bejelentés.",
            gestures = "Játék közben csak két dolgot lehet választani, és a fel-le " +
                "söprés ezek közt vált: HÚZÁS — kérsz még egy lapot —, és MEGÁLLÁS " +
                "— befejezed, marad, amid van. A jobbra söprés végrehajtja, amit " +
                "épp kiválasztottál. A balra söprés azonnal kilép. A kör végén a " +
                "jobbra söprés új kört oszt.",
            trouble = "Ha megállsz, utána már nem húzhatsz, tehát előbb gondold végig. " +
                "A krupié kötött szabály szerint játszik: tizenhét alatt húz, " +
                "tizenhéttől megáll — ezért érdemes tudni, mennyije lehet. " +
                "Ebben a változatban a DÖNTETLEN IS A TE GYŐZELMED, tehát ha " +
                "ugyanannyid van, mint a krupiénak, nyertél. Dupla, osztás és " +
                "biztosítás nincs a játékban, és a kétlapos huszonegy sem ér " +
                "többet a többinél."
        ),

        // ── PÓKER ───────────────────────────────────────────────────────────
        "game_poker" to topic(
            title = "Póker szabályok",
            purpose = "Ötlapos húzós póker. Mindenki öt lapot kap, egyszer cserélhetsz " +
                "belőlük, aztán mindenki megmutatja, és a legerősebb kéz nyer. " +
                "Nincs tét, nincs licit és nincs blöff — egyetlen döntésed van.",
            start = "Az elején eldöntöd, hányan játsszatok. A program felolvassa az öt " +
                "lapodat és megmondja, mi van a kezedben — például pár vagy két pár. " +
                "A kezek erőssége növekvő sorrendben: magas lap, pár, két pár, " +
                "drill, sor, flös, full, póker vagyis négy egyforma, színsor, és a " +
                "legerősebb a royal flös.",
            gestures = "Itt a fel-le söprés NEM lapot választ, hanem SZÁMOT ÁLLÍT: azt " +
                "adod meg, hány lapot akarsz eldobni, nulla és öt között. A jobbra " +
                "söprés megerősíti, és utána azonnal jön a mutatás. A balra söprés " +
                "kilép. A kör végén a jobbra söprés új kört oszt.",
            trouble = "Fontos: NEM TE VÁLASZTOD KI, MELYIK lapot dobod el — csak azt, " +
                "hányat. A program mindig a leggyengébb lapjaidat dobja. Ezért " +
                "elég annyit eldöntened, hogy hány lapot érdemes cserélni: párral " +
                "általában hármat, két párral egyet, drillel kettőt, és ha jó a " +
                "kezed, nullát. A kezed csak egyszer, a kör elején hangzik el — ha " +
                "elkerülte a figyelmedet, a kör végén a mutatásnál újra hallod."
        ),

        // ── FÉLKARÚ RABLÓ ───────────────────────────────────────────────────
        "game_slot" to topic(
            title = "Félkarú rabló szabályok",
            purpose = "Háromtárcsás nyerőgép játékzsetonnal. Beállítod a tétet, " +
                "meghúzod a kart, és a három tárcsa egymás után megáll — ha " +
                "egyformák, nyersz. Nincs valódi pénz és nincs vásárlás.",
            start = "Száz zsetonnal kezdesz, az induló tét öt. A tét lehet egy, öt, " +
                "tíz, huszonöt vagy ötven zseton. A nyeremény a tét szorzata: három " +
                "egyforma esetén a szimbólum szorzójával — cseresznye ötszörös, " +
                "citrom nyolcszoros, szilva tizenkétszeres, harang tizennyolcszoros, " +
                "csillag harmincszoros, gyémánt hatvanszoros, hetes " +
                "százhúszszoros —, bármely pár esetén pedig kétszeres.",
            gestures = "Fel-le söpréssel állítod a tétet, és a lista körbeér. A jobbra " +
                "söprés húzza meg a kart. A balra söprés kilép. Pörgetés közben — " +
                "ez körülbelül egy másodperc — a söprések nem érnek semmit, meg " +
                "kell várni az eredményt.",
            trouble = "Nem lehet elveszíteni a játékot: ha elfogy a zsetonod, a " +
                "következő karhúzásnál a gép ad ötven bónusz zsetont. A három hetes " +
                "a jackpot, azt külön hang is jelzi. Az egyenleg nem marad meg: " +
                "kilépés után újra száz zsetonnal kezdesz. Ez játék, nem szerencse" +
                "játék — se pénzt betenni, se kivenni nem lehet belőle."
        ),

        // ── MILLE BORNES ────────────────────────────────────────────────────
        "game_mille_bornes" to topic(
            title = "Mille Bornes szabályok",
            purpose = "Autóverseny kártyával. A cél EZER KILOMÉTER: távolság-lapokkal " +
                "haladsz, az ellenfelek pedig defekttel, balesettel, benzinhiánnyal " +
                "és stoptáblával próbálnak megállítani. Aki elsőként eléri az ezret, " +
                "az nyer.",
            start = "Az elején eldöntöd, hányan játsszatok; itt az alap a " +
                "kétszemélyes. Hat lappal kezdesz, és minden lerakás vagy eldobás " +
                "után húzol egyet. HALADNI CSAK AKKOR TUDSZ, ha zöld lámpád van: " +
                "ezért az első dolgod egy INDULÁS lapot lerakni. Ha megállítanak, a " +
                "megfelelő javító lap hoz vissza — defektre pótkerék, balesetre " +
                "javítás, benzinhiányra benzin, stopra indulás —, és utána megint " +
                "kell egy indulás lap.",
            gestures = "Fel-le söpréssel lépkedsz a lapjaid között, jobbra söpréssel " +
                "lerakod a kijelöltet — vagy ha nem játszható, ELDOBOD. Balra " +
                "söprés a kilépés. FIGYELJ egy dologra négyszemélyesben: ha a " +
                "kijelölt támadó lapnak több lehetséges célpontja van, a fel-le " +
                "söprés a CÉLPONTOT lépteti, nem a lapot — ilyenkor a program " +
                "bemondja, kire irányul. Ebből az állapotból úgy tudsz kilépni, " +
                "hogy lerakod vagy eldobod a lapot.",
            trouble = "Támadó lapot csak olyan ellenfélre lehet játszani, aki éppen " +
                "halad — aki már áll, azt nem lehet még egyszer megállítani. " +
                "Sebességkorlát alatt csak ötven kilométeres vagy kisebb lapot " +
                "játszhatsz, amíg a korlát vége lappal fel nem oldod. A négy " +
                "biztonsági lap — behajtási elsőbbség, defekttűrő, " +
                "üzemanyagtartály, vezető ász — végleges védettséget ad egy-egy baj " +
                "ellen, és ha a kezedben van, a program magától kivédi vele a " +
                "támadást. Ha a pakli elfogy, az nyer, aki a legtöbbet haladta."
        ),

        // ── BEÁLLÍTÁSOK (az ág) ─────────────────────────────────────────────
        "settings" to topic(
            title = "Beállítások súgó",
            purpose = "Itt szabod magadhoz a programot: hogyan kezeld, mennyit " +
                "beszéljen, milyen hangon, mit figyeljen a háttérben, és mi legyen " +
                "biztonságban. A legtöbb almenünek saját súgója van; ez az ág " +
                "egésze és a benne álló önálló pontok.",
            start = "A Program frissítése az első pont: ez magát a Super DL-t " +
                "frissíti, nem a modulokat. A Katalógus a letölthető " +
                "kiegészítőkről szól — kvíz-kérdéssorok, szólisták, elnevezések. " +
                "A Felület elforgatása azoknak való, akik más telefonon máshogy " +
                "szokták meg a söpréseket: háromféle kezelés közül választ, és a " +
                "váltás azonnal él. Ha nem tudod, most melyik van érvényben, a " +
                "Jelenlegi kezelés felolvasása menüpont elmondja a pontos " +
                "szabályt — azt érdemes meghallgatni, mert az mindig a valóságot " +
                "mondja.",
            gestures = "A menüben a szokásos: fel-le söpréssel lépkedsz, jobbra " +
                "söpréssel belépsz vagy végrehajtasz, balra söpréssel visszalépsz. " +
                "A ki-be kapcsolókat egyetlen jobbra söprés billenti át, és a " +
                "program megmondja, mi lett belőle — sőt, ezeknél már akkor is " +
                "hallod az állapotot, amikor rájuk lépsz. A WiFi és a Bluetooth " +
                "pont is ilyen kapcsoló. A Kezdőképernyő váltás almenüben tudsz " +
                "kilépni a Super DL-ből, ha másik kezdőképernyőt szeretnél.",
            trouble = "Ha a frissítés azt mondja, engedélyezned kell, hogy a Super DL " +
                "alkalmazást telepíthessen: ez egyszeri engedély, a program " +
                "megnyitja a beállítást, kapcsold be, és próbáld újra. A program " +
                "magától is keres frissítést naponta kétszer, de egy verzióról " +
                "csak egyszer szól — a menüpontból bármikor rá lehet kérdezni. " +
                "Ha egy modul már le van töltve, a jobbra söprés nem tölti le " +
                "újra, csak elmondja, mi az. Ha elveszted a fonalat a söprések " +
                "irányában, a Jelenlegi kezelés felolvasása mindig kisegít."
        ),

        // ── KATALÓGUS ───────────────────────────────────────────────────────
        "catalog" to topic(
            title = "Katalógus súgó",
            purpose = "A letölthető kiegészítők boltja: kvíz-kérdéssorok, " +
                "szólisták az Akasztófához, elnevezés-csomagok a " +
                "képernyőolvasóhoz és más modulok. Ezek mind ADATOK, nem " +
                "programok — nem futtatnak semmit a telefonodon.",
            start = "Az Elérhető modulok menüpont felsorolja, mi tölthető le. Ha " +
                "többféle van, előbb csoportot választasz, aztán a csoporton " +
                "belül modult. Minden modulnál elhangzik a neve, a típusa, hogy " +
                "le van-e már töltve, és mekkora. A Letöltött modulok menüpont " +
                "azt mondja meg, mi van már nálad.",
            gestures = "A csoportok és a modulok közt fel-le söpréssel válogatsz, és " +
                "a lista körbeér. Csoportnál a jobbra söprés belép, modulnál a " +
                "jobbra söprés LETÖLT. Balra söpréssel lépsz vissza. Ha egy modul " +
                "már megvan, a jobbra söprés nem tölti le újra, csak elmondja, mi az.",
            trouble = "A letöltéshez internet kell. Ha a lista üres vagy nem tölt be, " +
                "előbb az internetkapcsolatot nézd meg. A modulokhoz nem kell " +
                "engedély, mert nem települ semmi: a fájl a program saját " +
                "mappájába kerül. Ha egy letöltött kvíz vagy szólista mégsem " +
                "jelenik meg a játékban, lépj ki a játékból és indítsd újra — " +
                "a játék induláskor olvassa be a modulokat."
        ),

        // ── HALADÓ ÉS TECHNIKAI ─────────────────────────────────────────────
        "advanced" to topic(
            title = "Haladó és technikai súgó",
            purpose = "Ami a program működésén és a rendszerrel való " +
                "kapcsolatán múlik: engedélyek állapota, biztonságos mód, " +
                "diagnosztika, háttérfutás, és innen nyílik a Képernyőolvasó és a " +
                "Billentyűzet almenü is. Ezekhez ritkán kell hozzányúlni, de ha " +
                "valami nem működik, itt derül ki, miért.",
            start = "Ha valami nem szólal meg vagy nem indul el, a Kisegítő " +
                "szolgáltatások állapota menüponttal kezdd: megmondja, be van-e " +
                "kapcsolva a képernyőolvasó és a PIN segéd, és ha nem, meg is " +
                "nyitja a beállítást. A Diagnosztika ennél alaposabb: végigméri, " +
                "mi működik és mi nem, és felolvassa az eredményt. A Beállítás " +
                "varázsló végigvezet azon, ami az első indításkor kell.",
            gestures = "Ezek a pontok többnyire nem nyitnak listát, csak beszélnek " +
                "vagy megnyitnak egy rendszer-beállítást. Ahol kérdés jön, ott a " +
                "szokásos szabály: jobbra söprés igen, balra söprés mégse. " +
                "Az Egyszerű mód ki és be pont azonnal átépíti a menüt — ugyanez " +
                "elérhető rejtett mozdulattal is: öt gyors jobbra söprés a " +
                "főmenüben. Azért öt, hogy zsebben véletlenül ne kapcsoljon át.",
            trouble = "Ha a gyógyszer-emlékeztető vagy az ébresztő késik, két pont " +
                "számít: a Korlátlan háttérfutás engedélyezése, és egyes " +
                "gyártóknál az Automatikus indítás. Mindkettő a rendszer " +
                "beállítását nyitja meg, a program elmondja, mit keress ott. " +
                "Ha valami folyton hibázik, a Biztonságos mód bekapcsolásával " +
                "csak az alapfunkciók indulnak el; a program magától is " +
                "visszakapcsol biztonságos módba, ha ismételten hibába fut, és " +
                "ugyanez a menüpont hozza vissza a kikapcsolt funkciókat. " +
                "A Hibatűrés próbája szándékosan hibát okoz: ha utána a menüben " +
                "találod magad, a védelem működik."
        ),

        // ── KÉPERNYŐOLVASÓ ──────────────────────────────────────────────────
        "screen_reader" to topic(
            title = "Képernyőolvasó súgó",
            purpose = "A Super DL saját képernyőolvasója: KÜLSŐ alkalmazásokban " +
                "olvassa fel, mi van a képernyőn, és ott is söpréssel kezelheted a " +
                "telefont. A Super DL saját menüjében nem működik — ott a program " +
                "maga beszél.",
            start = "Két kapcsoló kell hozzá: a Képernyőolvasó ki és be pont, " +
                "valamint a rendszer engedélye. Ha még nincs engedélyezve, válaszd " +
                "az Engedélyezés a rendszerben pontot: megnyílik a kisegítő " +
                "lehetőségek listája, ott keresd meg a Super DL képernyőolvasót. " +
                "Az Állapot menüpont bármikor megmondja, hol tartasz. Ha a " +
                "mozdulatokat tanulni szeretnéd, a Gesztusok tanulása almenüben " +
                "Elena tanárnő végigvezet rajtuk, és vizsgázni is lehet.",
            gestures = "Egy ujjal: lefelé söprés a következő elem, felfelé az előző, " +
                "jobbra söprés megnyomja, balra söprés a vissza. Kérdésnél a " +
                "jobbra söprés az igen, a balra a nem. Csörgő hívásnál a jobbra " +
                "söprés fogadja, a balra elutasítja; beszélgetés közben a balra " +
                "söprés leteszi. Vannak páros mozdulatok is: le majd fel a " +
                "görgetés lefelé, fel majd le felfelé; fel majd balra a " +
                "kezdőképernyő, fel majd jobbra a legutóbbi alkalmazások, le majd " +
                "balra az értesítések, le majd jobbra a hosszú nyomás; jobbra majd " +
                "fel a hangtérkép. Két ujjal: jobbra vagy balra söprés az olvasási " +
                "mód váltása, lefelé a folyamatos olvasás, felfelé az utolsó " +
                "mondat ismétlése. Három ujjal: jobbra részletesebb, balra durvább " +
                "olvasás, lefelé hol vagyok, felfelé a teljes mozdulat-súgó. " +
                "A teljes lista a Mozdulatok felolvasása menüpontban hangzik el.",
            trouble = "Ha a képernyőolvasó nem szólal meg egy alkalmazásban: nézd meg " +
                "az Állapot pontot — lehet, hogy a rendszerben nincs engedélyezve. " +
                "Ha a telefon érintés-kezelése zavarossá válik, és azonnal " +
                "vissza akarod kapni a megszokottat, ott az AZONNALI leállítás " +
                "pont: attól a pillanattól a rendszer kezeli az érintést, és a " +
                "Képernyőolvasó ki és be ponttal indítod újra. A program magától " +
                "is leáll, ha háromszor egymás után hibába fut. A tenyérrel " +
                "letakarás azonnal elhallgattatja. A többujjas mozdulatokhoz " +
                "Android 11 vagy újabb kell; régebbi telefonon ezek a funkciók a " +
                "menüből érhetők el."
        ),

        // ── MŰVELETSOR-FELVÉTEL ÉS TANULÁS ──────────────────────────────────
        "sr_school" to topic(
            title = "Gesztusok tanulása súgó",
            purpose = "Iskola a képernyőolvasó mozdulataihoz: Elena tanárnő " +
                "óráin egyesével begyakorlod őket, a vizsgán pedig már csak azt " +
                "mondja meg, mit kell elérned — a mozdulatot neked kell tudnod.",
            start = "A tanuláshoz a képernyőolvasónak engedélyezve kell lennie a " +
                "rendszerben; ha a program kapcsolója ki van kapcsolva, a tanulás " +
                "magától bekapcsolja. Az Órák Elena tanárnővel ponttal kezdd: " +
                "csak akkor lépsz tovább, ha a mozdulat sikerült. Ha úgy érzed, " +
                "megy, jöhet a Vizsga.",
            gestures = "Tanulás közben a mozdulatok nem csinálnak semmit a " +
                "telefonon — csak gyakorolsz, semmi nem indul el és nem törlődik. " +
                "Kilépni bármikor lehet: KOPPINTS NÉGYSZER gyorsan. A tanulás a " +
                "fizikai mozdulatot kéri, tehát ha elforgatott kezelést " +
                "használsz, itt az igazi irányt mondja, nem az átfordítottat. " +
                "A Tanulás befejezése menüpont zárja le, utána a képernyőolvasó a " +
                "szokásos módon működik tovább.",
            trouble = "Ha a vizsga megszakad, mert túl sok hiba gyűlt össze, nincs " +
                "semmi baj: kezdd újra az órákkal, azok addig ismételnek, amíg " +
                "sikerül. Ha a tanulás el sem indul, a képernyőolvasót előbb " +
                "engedélyezni kell a kisegítő lehetőségeknél — az Engedélyezés a " +
                "rendszerben pont megnyitja. A négyszeri koppintás akkor is " +
                "kiléptet, ha épp közben tartasz."
        ),

        // ── BILLENTYŰZET (a nem-Braille, nem-mátrix rész) ───────────────────
        "keyboard" to topic(
            title = "Billentyűzet súgó",
            purpose = "Három saját billentyűzet közül választhatsz: a MÁTRIX " +
                "egy ujjal, telefonszám-elrendezésben ír; a BRAILLE hatpontos " +
                "Braille-lal; a DIKTÁLÁS pedig csak hallgat és leírja, amit " +
                "mondasz. A mátrixnak és a Braille-nak saját almenüje és saját " +
                "súgója van — ez a rész a választásról és a szövegtárról szól.",
            start = "Először a Billentyűzetek engedélyezése menüpontot válaszd: " +
                "megnyílik a rendszer billentyűzet-listája, ott kapcsold be a " +
                "Super DL billentyűzeteket. Enélkül nem jelennek meg sehol. " +
                "Utána a Billentyűzet választása menüpontban kipróbálhatod " +
                "mindhármat: kiválasztod, melyiket akarod, a program megnyit egy " +
                "próbamezőt, és megmondja, melyik tételt keresd a rendszer " +
                "listájában.",
            gestures = "A választó listájában fel-le söpréssel válogatsz, jobbra " +
                "söpréssel próbálod ki, balra söpréssel kilépsz. A PRÓBAMEZŐBEN " +
                "más a szabály: a felfelé söprés újra előhozza a rendszer " +
                "billentyűzet-választóját, a lefelé és a jobbra söprés " +
                "felolvassa, amit eddig beírtál, a balra söprés pedig felolvassa " +
                "és visszavisz a listához — nem lép ki. A Diktálás billentyűzeten " +
                "nem kell mozdulat az íráshoz: belépsz a mezőbe, és beszélhetsz; " +
                "az összecsippentés zárja be, egy koppintás újraindítja a " +
                "hallgatást, két ujjal balra söprés törli az utolsó szót, három " +
                "ujjal koppintás a billentyűzet-választó.",
            trouble = "Egy alkalmazás nem tudja helyetted átállítani a " +
                "billentyűzetet — csak megnyitni a rendszer listáját, és " +
                "megmondani, mit válassz. Ha a lista nem nyílik meg, a " +
                "billentyűzeten belül két ujjal felfelé söpörve is előhívható. " +
                "Ha egy billentyűzet nem szerepel a listában, a Billentyűzetek " +
                "engedélyezése pontban még nincs bekapcsolva. A Szövegtár a " +
                "MÁTRIX billentyűzethez tartozik: tizenkét kész szöveg, amit a " +
                "billentyűzeten két ujjal háromszor koppintva tölthetsz fel — a " +
                "menüpont csak felolvassa, ami benne van. A Diktálás jelszó-mezőbe " +
                "szándékosan nem ír, és három néma kör után magától megáll."
        ),

        // ── BIZTONSÁG ───────────────────────────────────────────────────────
        "security" to topic(
            title = "Biztonság súgó",
            purpose = "Négy dolog: a Super DL saját PIN zárolása, a rendszer " +
                "zárolásához adott PIN segéd, a hívás szűrő — hogy ki csöröghet " +
                "be —, és az Időzített fókusz, ami napszakhoz köti a szűrést.",
            start = "Ha a Super DL-t zárni akarod, előbb a PIN kód beállítása " +
                "menüpontban adj meg egy négy-nyolc jegyű kódot, és csak utána " +
                "kapcsold be a PIN zárolást — a bekapcsolás azonnal zárol is. " +
                "A Rendszer PIN segéd más: a telefon SAJÁT zárképernyőjén ad egy " +
                "függőleges, beszélő számbillentyűzetet; ehhez a kapcsolón kívül " +
                "a kisegítő lehetőségeknél is engedélyezni kell a szolgáltatást.",
            gestures = "A PIN billentyűzeten fel-le söpréssel válogatsz a " +
                "számjegyek közt, jobbra söpréssel beírsz, és a BALRA SÖPRÉS EGY " +
                "SZÁMJEGYET TÖRÖL, nem lép ki. A lista alján van a Törlés és a " +
                "Megerősítés. A Hívás szűrő mód menüpont körbeforgat négy mód " +
                "közt, és mindegyiknél elmondja, mit jelent; a Hívás szűrő " +
                "állapota csak felolvassa a mostanit. Az Időzített fókusz " +
                "Szabályaim listájában a jobbra söprés HELYBEN kapcsolja ki-be a " +
                "szabályt. Az Egyéni fókusz létrehozásánál a fel-le söprés az " +
                "ÉRTÉKET állítja, a jobbra söprés lép tovább a következő lépésre.",
            trouble = "A hívás szűrő négy módja közül a Teljes Ne Zavarj mindent " +
                "elutasít, a Részleges csak a kedvenceket és a csillagozott " +
                "névjegyeket engedi, a Laza az ismert névjegyeket, a Mindent " +
                "Fogad pedig — a nevével ellentétben — a REJTETT ÉS ISMERETLEN " +
                "számokat tiltja, mindenki mást átenged. A szűrő működéséhez a " +
                "rendszer engedélye kell, hogy a Super DL legyen a hívás szűrő; " +
                "ezt a program elkéri. A fehérlistán lévő számok minden szűrésen " +
                "átjutnak, még a Teljes Ne Zavarj alatt is — így a fontos " +
                "emberek mindig elérnek. Az Éjszakai nyugalom és a Vasárnapi " +
                "pihenő egy mozdulattal kész szabályt ad."
        ),

        // ── IDŐZÍTETT FÓKUSZ ────────────────────────────────────────────────
        "focus" to topic(
            title = "Időzített fókusz súgó",
            purpose = "Napszakhoz vagy naphoz köti, hogy ki csöröghet be. Nem kell " +
                "kézzel ki-be kapcsolgatni a szűrést: megadod egyszer, mikor " +
                "legyen csend, és onnantól magától él.",
            start = "A leggyorsabb út a két kész szabály: az Éjszakai nyugalom este " +
                "tíztől reggel hatig, minden nap; a Vasárnapi pihenő egész " +
                "vasárnap. Mindkettő teljes Ne Zavarj. Ha ennél pontosabb kell, " +
                "az Egyéni fókusz létrehozása négy lépésben végigvezet: milyen " +
                "erős legyen a szűrés, mely napokon, mikor kezdődjön és mikor " +
                "érjen véget.",
            gestures = "Az Egyéni fókusznál FIGYELJ: a fel-le söprés nem lépked, " +
                "hanem az adott lépés ÉRTÉKÉT állítja — a napokat, az órát, a " +
                "szűrés erősségét —, a jobbra söprés lép a következő lépésre, a " +
                "balra söprés vissza. A végén a jobbra söprés menti. " +
                "A Szabályaim listában fel-le söpréssel válogatsz, és a jobbra " +
                "söprés helyben kapcsolja ki vagy be a kiválasztott szabályt.",
            trouble = "A Mi van most érvényben menüpont bármikor megmondja, hat-e " +
                "épp valamelyik szabály. Ha nem érted, miért nem csörög a " +
                "telefon, ott kezdd. A fehérlista mindent felülír: aki rajta van, " +
                "a legszigorúbb szabály alatt is átcsörög — ezt a program is " +
                "kimondja minden alkalommal. Több szabály is lehet egyszerre " +
                "bekapcsolva; törölni nem kell őket, elég kikapcsolni."
        ),

        // ── HANGOK ──────────────────────────────────────────────────────────
        "sound_settings" to topic(
            title = "Hangok súgó",
            purpose = "Itt dől el, mi hogyan szól: a söprések visszajelző hangja, a " +
                "csengőhang, és külön-külön minden értesítés hangja — ébresztő, " +
                "gyógyszer, program-emlékeztető, SMS, e-mail.",
            start = "A Söpörj hangtéma a program saját visszajelző hangjait " +
                "cseréli. A Csengőhang választása a bejövő hívás hangját. " +
                "A Csengőhang hangerő és a Néma mód az értesítések hangerejét " +
                "szabja meg. A többi pont egy-egy értesítéstípus hangját állítja " +
                "külön, hogy hallás után meg tudd különböztetni, mi szólt.",
            gestures = "Ahol lista nyílik, ott a szokásos: fel-le söpréssel " +
                "válogatsz, jobbra söpréssel kiválasztod, balra söpréssel " +
                "kilépsz. A hangerő és a néma mód körbeforgó kapcsoló: minden " +
                "jobbra söprés egy fokozatot lép, és a program megmondja, mi lett.",
            trouble = "Ha egy értesítést nem hallasz, előbb a Néma módot nézd meg, " +
                "utána a Csengőhang hangerőt. Érdemes az ébresztőnek és a " +
                "gyógyszer-emlékeztetőnek jól megkülönböztethető hangot adni: így " +
                "zsebben is tudod, melyik szólt, anélkül hogy elővennéd a " +
                "telefont. A program saját beszédének hangereje NEM itt van, " +
                "hanem a Hang és beszéd almenüben."
        ),

        // ── ŐRSÉG ───────────────────────────────────────────────────────────
        "patrol_settings" to topic(
            title = "Őrség súgó",
            purpose = "Az őrség az, ami a háttérben figyel és megszólal magától: " +
                "szól, ha fogy az akkumulátor, bemondja, ki hív, ha üzenet " +
                "érkezik, és időnként az időt is. Zárolt képernyőn is működik.",
            start = "A Beállítások almenüjében van egy Teljes őrség ki-be főkapcsoló, " +
                "és itt, az Őrség beállítások almenüben külön-külön minden " +
                "figyelés. Ha csak az időt szeretnéd hallani, kapcsold be az Idő " +
                "bemondást, és a gyakoriságot állítsd magadhoz. Az Éjszakai csend " +
                "megadott két időpont közt elhallgattatja az őrséget.",
            gestures = "Ezek mind egyszerű kapcsolók: egy jobbra söprés billenti " +
                "át, és a program megmondja, mi lett belőle. Sőt, már akkor " +
                "hallod az állapotot, amikor a menüpontra rálépsz — nem kell " +
                "bekapcsolni ahhoz, hogy megtudd, be van-e kapcsolva. " +
                "Az éjszakai csend kezdete és vége a szokásos számbillentyűzeten " +
                "állítható: fel-le söprés választ, jobbra söprés beír, balra " +
                "söprés töröl.",
            trouble = "Ha az őrség elhallgat, nézd meg a Teljes őrség főkapcsolót — " +
                "az mindent felülír. Ha éjszaka is szólna, de nem szól, az " +
                "Éjszakai csend lehet bekapcsolva. Ha a telefon alvó állapotban " +
                "leállítja a figyelést, a Beállítások, Haladó, Korlátlan " +
                "háttérfutás engedélyezése pont segít. A Bekapcsoló gomb idő " +
                "bemondás kényelmes szokás: elég megnyomni a gombot, és hallod, " +
                "hány óra."
        ),

        // ── HANG ÉS BESZÉD ──────────────────────────────────────────────────
        "sound_speech" to topic(
            title = "Hang és beszéd súgó",
            purpose = "A program HANGJA: hangerő, beszédsebesség, melyik beszélő " +
                "hangot használja, és milyen csatornán szóljon. Innen nyílik a " +
                "Kiejtési szótár és a Mennyit magyarázzon almenü is.",
            start = "A hangerőt és a beszédsebességet a négy első pont állítja, " +
                "fokozatonként. A T T S hang választása menüpont felsorolja a " +
                "telefonra telepített beszélő hangokat, és a kiválasztás után " +
                "rögtön hallasz egy próbamondatot az újjal. Ha nincs telepítve " +
                "egy sem, a program szól, hogy a rendszer beállításaiban telepíts " +
                "egyet — például Google beszéd vagy eSpeak.",
            gestures = "A hang- és a szerep-listákban fel-le söpréssel válogatsz, " +
                "jobbra söpréssel választasz, balra söpréssel kilépsz. A hangerő " +
                "és a sebesség pontjai nem nyitnak listát: minden jobbra söprés " +
                "egy fokozat. A Hangszerepek és az Automatikus nyelvváltás " +
                "egyszerű kapcsolók.",
            trouble = "Ha a beszéd elakad vagy elnémul, a Beszéd javítása menüpont " +
                "újraindítja a beszédmotort — és ha a javítás sem sikerül, a " +
                "telefon REZGÉSSEL válaszol, mert épp a beszéd a hibás. " +
                "Ha egy készüléken a program néma marad, a Beszéd hangcsatornája " +
                "ponttal válts: a kisegítő csatorna külön hangerőt kap, de egyes " +
                "telefonokon néma; a média csatorna mindenhol szól, és a " +
                "hangerőgombokkal állítható. Ha váltás után nem hallasz semmit, " +
                "söpörj jobbra UGYANAZON a menüponton — az visszavált. " +
                "A Külön hang a program üzeneteihez ponthoz legalább két " +
                "beszédmotor kell a telefonon, és az első lehetőség mindig a " +
                "Nincs külön hang, tehát egy söpréssel visszavonható."
        ),

        // ── KIEJTÉSI SZÓTÁR ─────────────────────────────────────────────────
        "dict" to topic(
            title = "Kiejtési szótár súgó",
            purpose = "Megtanítod a programnak, hogyan mondjon ki egy szót, amit " +
                "rosszul ejt — rövidítéseket, neveket, idegen szavakat. Ami itt " +
                "be van tanítva, a program minden felolvasásában érvényes.",
            start = "Az Új szabály tanítása két lépés, és mindkettő DIKTÁLÁS, nem " +
                "gépelés: először kimondod azt a szót, amit rosszul mond, aztán " +
                "kimondod, hogyan mondja helyette. A Beépített szabályok kapcsoló " +
                "a gyakori rövidítéseket kezeli — forint, kilométer, körülbelül —, " +
                "ez alapból be van kapcsolva.",
            gestures = "A tanítás közben a program kérdez, te válaszolsz; ha nem " +
                "érti, szól, és a menüből újra lehet kezdeni. A listákat a " +
                "Saját szabályaim és a Jelenlegi állapot menüpont olvassa fel. " +
                "A Beépített szabályok egyszerű kapcsoló: egy jobbra söprés " +
                "billenti át.",
            trouble = "A tanításhoz mikrofon-engedély kell. FIGYELJ a Saját " +
                "szabályok törlése menüpontra: az nem kérdez vissza, azonnal " +
                "törli az összes saját szabályodat — a beépítettek megmaradnak. " +
                "Ha egy szót továbbra is rosszul mond, próbáld meg úgy " +
                "bediktálni a kiejtését, ahogy leírva hangzana; a beszédmotor a " +
                "leírt alakot mondja ki."
        ),

        // ── MENNYIT MAGYARÁZZON ─────────────────────────────────────────────
        "verbosity" to topic(
            title = "Mennyit magyarázzon súgó",
            purpose = "Itt döntöd el, mennyit beszéljen a program. Aki most " +
                "ismerkedik vele, mindent hallani akar; aki már fejből tudja a " +
                "mozdulatokat, annak ugyanaz a magyarázat lassítás. Egyik " +
                "beállítás sem VESZ EL funkciót — csak a szöveget rövidíti.",
            start = "A Jelenlegi beállítás felolvasása megmondja, mi van most " +
                "bekapcsolva. Utána egyesével végigmehetsz a kapcsolókon. " +
                "Ha kezdő vagy, hagyd mindet bekapcsolva; ha gyakorlott, a " +
                "Mozdulat-útmutatók kikapcsolása gyorsítja a legtöbbet.",
            gestures = "Mindegyik pont egyszerű kapcsoló: egy jobbra söprés " +
                "billenti át, és a program megmondja, mi lett belőle. " +
                "A kapcsolók: Mozdulat-útmutatók — a merre söpörj mondatok; " +
                "Alkalmazás-tájékoztató — amit külső alkalmazás indítása előtt " +
                "mond; Darabszámok bemondása — a hány darab a listákban; " +
                "Diktálási tipp — az írásjelekre emlékeztető mondat; " +
                "Billentyűzet-tájékoztató — a billentyűzet megnyitásakor " +
                "elhangzó mozdulat-ismertető; és a Jelszó betűinek kimondása.",
            trouble = "A Jelszó betűinek kimondása külön figyelmet érdemel: ha " +
                "bekapcsolod, a program hangosan kimondja a beírt jelszó betűit — " +
                "ez otthon kényelmes, nyilvános helyen viszont kockázat, ezért " +
                "ott inkább kapcsold ki; kikapcsolva csak egy rövid hang jelzi a " +
                "beírást. Ha valamit túl szűkszavúnak érzel, itt keresd az okát: " +
                "valószínűleg egy kapcsoló ki van kapcsolva. Ezek a beállítások " +
                "csak a beszédet érintik, a mozdulatok mindegyik állásban " +
                "ugyanúgy működnek."
        ),

        // ── A FŐMENÜ, VAGYIS AZ EGÉSZ PROGRAM ───────────────────────────────
        //
        // Ez a súgó nem almenühöz tartozik, hanem a Névjegybe kézzel betett
        // „Súgó — így működik a program" ponthoz (help::main). A főmenü maga
        // nem almenü, oda a menüépítő nem tud Súgót szúrni.
        "main" to topic(
            title = "Így működik a program",
            purpose = "A Super DL egy teljes kezdőképernyő vakoknak: telefonálás, " +
                "üzenet, zene, könyv, navigáció, gyógyszer-emlékeztető, játékok " +
                "és még sok minden — mind hanggal, söprésekkel, képernyő nézése " +
                "nélkül. Nem kell megjegyezned semmit: a program mindig " +
                "megmondja, hol vagy és merre mehetsz.",
            start = "Az egész program EGY menü, amiben ágak vannak. Fel-le " +
                "söpréssel lépkedsz a menüpontok közt — a program felolvassa " +
                "mindegyiket —, jobbra söpréssel belépsz vagy elindítod, balra " +
                "söpréssel visszalépsz. Ennyi az egész. Ha eltévedsz, söpörj " +
                "balra addig, amíg a program azt nem mondja, hogy már a " +
                "főmenüben vagy. Az első időkben érdemes a Névjegy alatti " +
                "Tanuló mód ponttal gyakorolni: ott semmi nem indul el " +
                "élesben.",
            gestures = "A négy söprés jelentése mindenhol ugyanaz: fel-le a " +
                "lépkedés, jobbra a belépés vagy végrehajtás, balra a " +
                "visszalépés vagy a mégse. Ahol a program kérdez, ott a jobbra " +
                "söprés az igen, a balra a nem, és a fel vagy le söprés " +
                "megismétli a kérdést. Ha más telefonon máshogy szoktad meg, a " +
                "Beállítások, Felület elforgatása ponttal átrendezhető; a " +
                "Jelenlegi kezelés felolvasása mindig elmondja, mi van épp " +
                "érvényben. Vannak rejtett mozdulatok is — például öt gyors " +
                "jobbra söprés a főmenüben ki-be kapcsolja az egyszerű módot —, " +
                "ezeket a Névjegy, Rejtett mozdulatok pont sorolja fel.",
            trouble = "Ha kevés menüpontot hallasz, valószínűleg EGYSZERŰ MÓDBAN " +
                "vagy: az csak a legfontosabbakat mutatja, és öt gyors jobbra " +
                "söpréssel a főmenüben kapcsolható át. Semmi nem veszett el, " +
                "csak nem látszik. Az S.O.S. a főmenüben van, egyetlen ponttal " +
                "elérhető; a hozzá tartozó beállítások és a részletes súgó a " +
                "Beállítások, S.O.S. paraméterek almenüben. Ha egy funkció " +
                "engedélyt kér, add meg — a program mindig megmondja, mire kell. " +
                "Minden alkalmazás-ág alján van egy Súgó pont, ami az adott " +
                "részt magyarázza el, és a Névjegyben ezek egy listában is " +
                "elérhetők. Ha valami nem működik, a Névjegy, Hibajelentés " +
                "küldése ponttal írhatsz a fejlesztőnek."
        )
    )
}
