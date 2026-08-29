package com.superdl.launcher.legal

data class LegalSection(
    val title: String,
    val body: String
) {
    fun speakPreview(): String = title

    fun speakFull(): String = "$title. $body"
}

object LegalTexts {

    /**
     * A VERZIÓSZÁM a build.gradle-ből jön, NEM kézzel írjuk ide.
     *
     * JAVÍTVA (2026-08-16): itt korábban egy KÉZZEL beírt "1.54.9" állt,
     * miközben a program már 1.55.0 volt. A felhasználó tehát ROSSZ
     * verziószámot hallott a Névjegyben — és pont ez az az adat, amit egy
     * hibajelentésnél elsőként kérdeznénk tőle.
     */
    val APP_VERSION: String get() = com.superdl.launcher.BuildConfig.VERSION_NAME

    const val DEVELOPER_NAME = "Kőrösmezey Dávid"
    const val DEVELOPER_EMAIL = "korosmezey.david.richard@gmail.com"
    const val APP_FULL_NAME = "Super Digital Launcher"
    const val APP_SHORT_NAME = "Super DL"

    fun speakEmail(): String =
        "korosmezey pont dávid pont richard kukac gmail pont com"

    fun aboutApp(): String = """
        $APP_FULL_NAME, röviden $APP_SHORT_NAME.
        Vak és gyengénlátó felhasználók számára készült Android kezdőképernyő.
        Négy mozdulattal vezérelhető: fel és le a választáshoz, jobbra a megnyitáshoz,
        balra a visszalépéshez.
        Telefonálás, üzenetek, e-mail, névjegyek, naptár, ébresztő, időzítő,
        gyógyszer-emlékeztető és bevásárlólista.
        Saját képernyőolvasó, ami a többi alkalmazásban is felolvas, saját
        billentyűzet, és Elena hangasszisztens.
        Zene, rádió, podcast, YouTube, könyvolvasás és hangoskönyv.
        Felismerők: szöveg, szín, tárgyak, Q R kód.
        Navigáció, tömegközlekedés, iránytű és környezetleírás.
        Profi diktafon, S.O.S. hívás, akkumulátor őrség, PIN segéd és WiFi fájlportál.
        Verzió: $APP_VERSION.
    """.trim().replace(Regex("\\s+"), " ")

    fun aboutDeveloper(): String = """
        Fejlesztő: $DEVELOPER_NAME.
        A $APP_SHORT_NAME alkalmazást $DEVELOPER_NAME készítette, hogy a mindennapi telefonhasználat
        önállóbb és biztonságosabb legyen látássérült felhasználók számára.
        Elérhetőség e-mailben: ${speakEmail()}.
    """.trim().replace(Regex("\\s+"), " ")

    fun contactDeveloper(): String =
        "Kapcsolat a fejlesztővel: $DEVELOPER_NAME. E-mail cím: ${speakEmail()}."

    fun privacyPolicy(): List<LegalSection> = listOf(
        LegalSection(
            "Röviden",
            "A $APP_SHORT_NAME a telefonodon tárolja az adataidat. Nem küld rólad " +
                "semmit sehova, nem figyeli a szokásaidat, és nem tartalmaz reklámot " +
                "vagy követőkódot. Ami kimegy a telefonról, az csak az, ami egy adott " +
                "funkció működéséhez kell — például egy címkereséshez a helyzeted. " +
                "A következő szakaszokban ezt pontosan elmondom."
        ),
        LegalSection(
            "Mit tárol a telefonodon",
            "Névjegyeket, üzeneteket, jegyzeteket, ébresztőket, naptárt, " +
                "gyógyszer-emlékeztetőket, mentett helyeket, bevásárlólistát, " +
                "hangfelvételeket és fényképeket. Ezek mind a telefonodon maradnak. " +
                "Ha törlöd a programot, ezek is törlődnek, ezért érdemes időnként " +
                "mentened őket a WiFi portálon keresztül."
        ),
        LegalSection(
            "Jelszavak",
            "Ha e-mail fiókot állítasz be, a jelszó titkosítva tárolódik, a " +
                "telefonhoz kötött kulccsal. Más alkalmazás nem fér hozzá, és a " +
                "WiFi portálon sem érhető el. A hibakereső napló csak a jelszó " +
                "hosszát jegyzi fel, a tartalmát soha."
        ),
        LegalSection(
            "Mi megy ki a telefonról",
            "Öt dolog, mindegyik csak akkor, ha te kéred. Egy: a helyzeted a " +
                "térkép-szolgáltatásnak, amikor címet vagy megállót keresel — enélkül " +
                "nem tudná megmondani, hol vagy. Kettő: a keresett szöveg a " +
                "YouTube-nak, a rádiónak, a podcastnak. Három: az e-mail fiókod adatai " +
                "a saját levelezőszolgáltatódnak. Négy: a diktált szöveg a telefon " +
                "beszédfelismerőjének. Öt: frissítés-ellenőrzéskor a program " +
                "verziószáma. Ezekre a szolgáltatásokra a saját adatvédelmi szabályaik " +
                "vonatkoznak."
        ),
        LegalSection(
            "Mi nem megy ki soha",
            "Az üzeneteid, a névjegyeid, a jegyzeteid, a fényképeid, a " +
                "hangfelvételeid és a jelszavaid. A képernyőolvasó látja minden " +
                "alkalmazásod tartalmát — a banki alkalmazást, a magánüzeneteket is —, " +
                "de ebből semmit nem jegyez fel és nem küld el. Csak felolvassa neked. " +
                "Ugyanígy az értesítéseket is helyben dolgozza fel."
        ),
        LegalSection(
            "Nincs követés",
            "A program nem tartalmaz semmilyen elemző, hirdetési vagy követőkódot. " +
                "Nem tudjuk, hányan használják, mit használsz belőle, vagy mikor. " +
                "Ha a program hibába fut, a hibajelentés a telefonodon marad — csak " +
                "akkor jut el hozzánk, ha te magad elküldöd."
        ),
        LegalSection(
            "A WiFi portál",
            "Amikor bekapcsolod, a telefonod adatai elérhetők ugyanarról a " +
                "wifi-hálózatról, négyjegyű kóddal védve. Tíz hibás próbálkozás után " +
                "lezár. Fontos: idegen hálózaton — kávézóban, munkahelyen — csak addig " +
                "hagyd bekapcsolva, amíg tényleg használod."
        ),
        LegalSection(
            "Engedélyek",
            "A program sok engedélyt kér, mert sok mindent tud. Mindegyik egy konkrét " +
                "funkcióhoz kell: a mikrofon a diktáláshoz és Elenához, a kamera a " +
                "felismerőkhöz, a helyzet a navigációhoz, a névjegyek és az üzenetek a " +
                "telefonáláshoz. Bármelyiket megtagadhatod vagy visszavonhatod az " +
                "Android beállításaiban — akkor csak az adott funkció nem működik, a " +
                "többi igen. Az alkalmazás eltávolításával a helyi adatok is törlődnek."
        ),
        LegalSection(
            "Kapcsolat",
            "Adatvédelmi kérdés esetén fordulj a fejlesztőhöz: $DEVELOPER_NAME. " +
                "E-mail: $DEVELOPER_EMAIL, felolvashatóan: ${speakEmail()}."
        )
    )

    fun termsOfUse(): List<LegalSection> = listOf(
        LegalSection(
            "Ez egy TESZTVERZIÓ",
            "A $APP_SHORT_NAME jelenleg nyilvános tesztelés alatt áll. Ez azt jelenti, hogy " +
                "előfordulhatnak hibák, és egyes funkciók még változhatnak. " +
                "NE HASZNÁLD EGYETLEN ESZKÖZKÉNT olyasmire, ahol a hiba komoly bajt okozna: " +
                "életmentő gyógyszer bevételéhez, fontos találkozóhoz vagy vészhelyzeti " +
                "híváshoz tarts meg egy második megoldást is. " +
                "Ha hibát találsz, a Névjegy menü Hibajelentés küldése pontjával jelezheted — " +
                "ez a legtöbb segítség, amit adhatsz."
        ),
        LegalSection(
            "Általános feltételek",
            "A $APP_SHORT_NAME használatával elfogadod, hogy az alkalmazást saját felelősségedre használod. " +
                "Az alkalmazás célja az akadálymentesített telefonhasználat megkönnyítése."
        ),
        LegalSection(
            "S.O.S. és vészhelyzet",
            "Az S.O.S. funkció segít gyors hívásindításban, de nem garantál hálózati lefedettséget, " +
                "és nem helyettesíti a hivatalos segélyszolgálati rendszereket. Vészhelyzetben mindig a helyi segélyhívó számot is használd."
        ),
        LegalSection(
            "Pontosság",
            "Az időjárás, hírek, tömegközlekedés, YouTube-találatok és egyéb külső tartalmak pontosságáért " +
                "harmadik fél szolgáltatók felelnek. Az alkalmazás ezeket csak elérhetővé teszi."
        ),
        LegalSection(
            "Felelősségkorlátozás",
            "A fejlesztő mindent megtesz a megbízható működésért, de az alkalmazást jelen formájában, garancia nélkül biztosítja. " +
                "A felhasználó felelős a telefon, SIM-kártya, engedélyek és hálózati kapcsolat rendben tartásáért."
        ),
        LegalSection(
            "Módosítások",
            "A fejlesztő jogosult az alkalmazás és ezen feltételek frissítésére. A telepített verzió az aktuális buildben látható."
        )
    )

    fun legalNotice(): List<LegalSection> = listOf(
        LegalSection(
            "Szerzői jog",
            "A $APP_FULL_NAME ($APP_SHORT_NAME) szoftver és felhasználói felület szerzői joga $DEVELOPER_NAME-t illeti meg."
        ),
        LegalSection(
            "Védjegy",
            "A Super Digital Launcher és Super DL megnevezések az alkalmazás védjegyei. " +
                "Harmadik fél védjegyei, például a Google, YouTube és Google Térkép a megfelelő tulajdonosoké."
        ),
        LegalSection(
            "Nyílt forrású komponensek",
            "Az alkalmazás Android és Kotlin nyílt ökoszisztéma komponenseit használja. " +
                "A külső könyvtárakra a saját licenceik vonatkoznak."
        ),
        LegalSection(
            "Irányadó jog",
            "A jogvitákra a felhasználó lakóhelye szerinti irányadó jog az elsődleges, " +
                "a vonatkozó fogyasztóvédelmi szabályok figyelembevételével."
        )
    )
}