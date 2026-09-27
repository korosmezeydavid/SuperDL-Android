package com.superdl.launcher.offers

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Közös termékcsoportok minden bolthoz — a Windows-oldali `csoport.py` pontos
 * átirata (ugyanazok a szabályok, ugyanabban a sorrendben, ugyanaz az eredmény).
 *
 * A PDF-újságos boltoknál (Lidl, Tesco, Spar, Auchan) nincs termékcsoport – a
 * „kategória" ott az újság neve. Ezért egy KÖZÖS szűrő kell, ami a
 * „Tejtermék"-et minden boltban ugyanúgy érti.
 *
 * A besorolás KULCSSZAVAS (nem AI, nem hálózat): a termék nevéből, ha abból
 * nem derül ki, a bolt saját kategóriájából, végül a bolt jellegéből. A
 * szabályok SORRENDJE számít – az első találat nyer: „Tejszelet" az
 * édességekhez kerül, mielőtt a „tej" a tejtermékekhez vinné; „Almás pite" a
 * pékáruhoz, mielőtt az „alma" a gyümölcshöz. Ami bizonytalan, az „Egyéb" –
 * inkább oda, mint rossz helyre.
 *
 * A kulcsszavak ékezet nélkül, a szó ELEJÉRE illesztve (a magyar összetett
 * szavak miatt: „csirkemellfilé" → „csirke…"). Az `=` előtag teljes szót
 * jelent (pl. „=bor", hogy a „borsó" ne ital legyen), a `~` a szó belsejét is
 * („lecsókolbász" → „~kolbasz"). A kulcsszavakat szóközök választják el – a
 * „konyhai papir" tehát KÉT kulcsszó, ahogy a Pythonban is.
 */
object OfferGroups {

    const val OTHER = "Egyéb"

    // Az ékezettelenítés (plain) táblái ELÖL állnak: az object mezői sorrendben
    // kapnak értéket, és a szabályminták fordítása már használja őket.
    // A kanonikus kombináló osztály 1 (átfedő jelek) – ezt az isCombining()
    // sorrendezési próbája nem mutatja ki, ezért felsoroljuk (Unicode-adatbázis).
    private val CCC_ONE = intArrayOf(
        0x334, 0x335, 0x336, 0x337, 0x338, 0x1CD4, 0x1CE2, 0x1CE3, 0x1CE4, 0x1CE5,
        0x1CE6, 0x1CE7, 0x1CE8, 0x20D2, 0x20D3, 0x20D8, 0x20D9, 0x20DA, 0x20E5, 0x20E6,
        0x20EA, 0x20EB, 0x10A39, 0x16AF0, 0x16AF1, 0x16AF2, 0x16AF3, 0x16AF4, 0x1BC9E,
        0x1D167, 0x1D168, 0x1D169
    )
    private val combiningCache = ConcurrentHashMap<Int, Boolean>()

    // (csoport, kulcsszavak) – a sorrend a szabály! Szóról szóra a csoport.py-ból.
    private val RULES: List<Pair<String, String>> = listOf(
        "Baba" to
            "~pelenka babakozmetik bebietel bebiital babaetel babafurdet popsi cumi " +
            "cumisuveg babatorl babahinto babaolaj =baby kubu hipp gerber babydream " +
            "babylove pampers huggies bebivita tejpep babaapol",
        "Állateledel" to
            "macska kutya allateledel eledel alom =whiskas pedigree prevital felix =perfect " +
            "friskies kitekat jutalomfalat ragocsont",
        // a parfüm KÜLÖN csoport; csak teljes szóra: a „parfümös tusfürdő"
        // maradjon drogéria
        "Parfüm és illat" to
            "=parfum =parfumje =eau =edp =edt =edc =kolni parfumviz illatpermet =testpermet",
        "Drogéria és szépségápolás" to
            "sampon hajbalzsam =balzsam hajpakolas hajmaszk hajfest hajlakk hajhab hajzsele " +
            "hajolaj tusfurd tusolo habfurd dezodor deo =izzadsag fogkrem fogkefe fogselyem " +
            "fogkoz mosdato szajviz szajvi arckrem kezkrem labkrem testapol testvaj " +
            "testolaj arcszerum szerum arctisztit arclemos sminklemos micellas napozo " +
            "naptej fenyvedo parfum kolni rúzs ruzs ajak szempilla szemhej szemceruza " +
            "szemfest alapozo puder pirosito korom =smink highlighter borotv borotva intim " +
            "tampon egeszsegugyi =betet tisztasagi vatta fultiszt szappan kezmos ~szappan " +
            "~sampon kontaktlencse =vitamin etrend magnezium hajcsat hajgumi hajpant " +
            "hajdisz ~hajszinez hajspray fejbor ovszer",
        "Háztartás és tisztítószer" to
            "mososzer moso =mosopor mosogel mosokapszula mosogat oblito tisztito " +
            "tisztitoszer fertotlenit =wc wc- toalettpapir toalett papirtorlo konyhai papir " +
            "zsebkendo szemeteszsak szemetes folia alufolia szivacs ~mososzer ~oblito " +
            "~tisztito illatgyongy penesz ~kendo ~torlokendo felmoso legfrissit " +
            "illatgyertya gyertya =elem elemek izzo villanykorte vizkoold vizkooldo " +
            "zsiroldo ablaktisztit padlotisztit finish domestos ariel persil lenor =jar " +
            "sanytol calgon vanish perwoll silan bref cillit mosogepi mosogatogep tabletta",
        "Fagyasztott" to
            "gyorsfagyaszt fagyaszt mirelit jegkrem fagylalt =jegkocka hasab halrud",
        "Ital" to
            "asvanyviz =viz =vize szensavas udito =cola =kola limonade gyumolcsle =le =leve " +
            "nektar szorp =sor =sore =sorok =bor =bora vorosbor feherbor roze pezsgo " +
            "palinka whisky vodka likor rum =gin konyak brandy energiaital =ital itala " +
            "jegestea =tea teafilter =kave kaveszemes kavekapszula kavespecial =cappuccino " +
            "latte shot fuzetea sorpack ~buzasor =pils ~bier weissbier =jagermeister " +
            "prosecco ~likor sportital ~aperitif ~szorp ~kave gyumolcsital ~italpor " +
            "kaveital ~szirup gyogyviz =icetea nestea =smoothie =kombucha =nektar",
        "Édesség és snack" to
            "tejszelet =rudi csoki csokolade =cukorka cukorka kemenycukor nyaloka bonbon " +
            "desszert keksz puszedli fondant ~csokolad ostya napolyi piskota gumicukor " +
            "zselecukor =szelet muzliszelet proteinszelet chips =ropi perec popcorn " +
            "=mogyoro pattogatott =nasi rago rágó marcipan halva praline =torta ~torta " +
            "~sutemeny ~snack ~mogyoro ~kraker ~kreker tortak =pite =suti sutemeny linzer",
        "Tejtermék és tojás" to
            "=tej =tejes tejfol ~joghurt kefir =turo turos =sajt sajtos sajtkrem =vaj " +
            "=vajas tejszin habtejszin mascarpone mozzarella habalap =trapista =gouda " +
            "=edami =cheddar =feta =parmezan =camembert =brie =tojas =tojast =puding " +
            "tejbegriz tejberizs =ayran =skyr ~pudding tejital tejdesszert zabital sojaital " +
            "=margarin =rama",
        "Hús, hal, felvágott" to
            "=hus husos csirke diszno toka ~kolbasz ~szalami ~sonka ~virsli ~szalonna " +
            "~felvagott ~pastetom =csirkemell pulyka sertes marha =borju =barany =kacsa " +
            "=liba karaj =comb combfile =mell mellfile =daralt kolbasz szalami sonka virsli " +
            "felvagott szalonna tepertő toperto pastetom mahony parizsi =fasirt =hamburger " +
            "=hal =halfile lazac tonhal harcsa ponty pisztrang tokehal hekk garnela =rak " +
            "rakpalca szardinia makrela hering csulok oldalas tarja =nyul nyulfel krinolin " +
            "szafalade =maj =lecso",
        "Pékáru" to
            "kenyer zsemle kifli =bucka =buci bagett kalacs pogacsa =fank retes =csiga " +
            "muffin =toast toastkenyer tortilla croissant ~kenyer ~kifli ~zsemle =pita " +
            "=lepeny pekaru =briós brios kornspitz ciabatta =vekni =kalacs =bejgli =pogi",
        "Alapvető élelmiszer" to
            "=liszt =cukor =kristalycukor porcukor =so =olaj napraforgo olivaolaj =ecet " +
            "=rizs teszta spagetti makaroni =penne fusilli =orso szarvacska =metelt galuska " +
            "konzerv befott lekvar =mez szosz =ketchup majonez mustar fuszer =bors =orolt " +
            "=leves levespor =muzli zabpehely gabonapehely corn flakes =kakao =bab =lencse " +
            "csicseri =kukorica =zab tarkabab feherbab vorosbab mogyorovaj puree pure " +
            "=ivolé savanyusag csemege ecetes ~teszta =chilis =pesto =humusz =tahini dzsem " +
            "kakaopor pudingpor sutopor eleszto ~morzsa =maggi zselatin =tészta taco",
        "Zöldség és gyümölcs" to
            "=alma =almat banan =korte szolo narancs citrom mandarin =lime dinnye " +
            "gorogdinnye sargadinnye =eper malna afonya szeder szilva =barack oszibarack " +
            "kajszi =kiwi ananasz mango avokado grapefruit =dio =mandula paradicsom " +
            "=paprika ~uborka burgonya =krumpli hagyma lilahagyma fokhagyma =repa sargarepa " +
            "cekla =retek kaposzta =kel karfiol brokkoli cukkini sutotok =padlizsan salata " +
            "jegsalata rukkola spenot =gomba csiperke =zoldseg zoldsegek =gyumolcs " +
            "=petrezselyem =zeller =kapor karalabe =sosk =cekla",
        // NEM ÉLELMISZER (Pepco, Müller-játékok, Libri) – lásd NON_FOOD
        "Ruházat és cipő" to
            "=polo polot =ruha =ruhat szoknya leggings pizsama zokni harisnya pulover " +
            "pulcsi kardigan kabat dzseki nadrag farmer =ing =inget bluz =cipo cipot csizma " +
            "papucs sapka kesztyu sal =sal melltarto bugyi alsonadrag boxer overal jelmez " +
            "tunika melegito =mez fehernemu",
        "Otthon és dekoráció" to
            "parna parnahuzat takaro pled agynemu lepedo torolkozo furdolepedo fuggony " +
            "szonyeg labtorlo dekor dekoracio bogre tanyer =pohar poharak evoeszkoz =tal " +
            "=talka kosar =doboz tarolo lampa koszoru =vaza gyertyatarto kepkeret =ora " +
            "faliora =serpenyo =labas edeny",
        "Játék" to
            "jatek jatekfigura pluss plussfigura =lego kirako puzzle tarsasjatek =baba " +
            "babak jatekauto =auto kisauto labda szinezo gyurma =kocka",
        "Könyv" to
            "=konyv konyvek regeny"
    )

    // a NEM ÉLELMISZER csoportok (Ruházat, Otthon, Játék, Könyv) a lista végén
    // állnak, hogy az élelmiszer- és drogériaszabályok előbb döntsenek; a
    // vegyes áru boltjainál (Pepco) viszont CSAK ezek jöhetnek szóba – egy
    // „macskamintás ruha" ott ne legyen állateledel.
    private val NON_FOOD = setOf(
        "Ruházat és cipő", "Otthon és dekoráció", "Játék",
        "Könyv", "Parfüm és illat", "Drogéria és szépségápolás",
        "Háztartás és tisztítószer"
    )
    private val ONLY_NON_FOOD = setOf("Pepco")

    // a bolt SAJÁT kategóriájának szavai (Rossmann, dm, Penny) – ha a névből
    // nem derül ki
    private val CATEGORY_RULES: List<Pair<String, String>> = listOf(
        "Játék" to
            "jatek jatekok",
        "Könyv" to
            "=konyv konyvek",
        "Baba" to
            "=baba pelenka",
        "Állateledel" to
            "=allat allateledel =kisallat",
        "Háztartás és tisztítószer" to
            "haztartas tisztit mosas",
        "Drogéria és szépségápolás" to
            "dekorkozmetika arcapolas =haj szepsegapolas szajapolas parfum egeszseg " +
            "kozmetik testapolas hajapolas napozo",
        "Fagyasztott" to
            "fagyaszt mirelit",
        "Ital" to
            "=ital =italok =bor =sor",
        "Édesség és snack" to
            "edesseg snack nasi",
        "Tejtermék és tojás" to
            "tejtermek tojas",
        "Hús, hal, felvágott" to
            "=hus husok =hal felvagott",
        "Pékáru" to
            "pekaru =kenyer",
        "Zöldség és gyümölcs" to
            "zoldseg gyumolcs",
        "Alapvető élelmiszer" to
            "=elelmiszer alapveto konzerv"
    )

    // ha semmi nem illik: a bolt jellege
    private val STORE_DEFAULT = mapOf(
        "Rossmann" to "Drogéria és szépségápolás",
        "dm" to "Drogéria és szépségápolás"
    )
    // vegyes kínálatú drogéria (édességet, kávét, italt is árul): a NÉV dönt,
    // és csak ha semmi nem illik, akkor drogéria
    private val DEFAULT_IF_NONE = mapOf("Müller" to "Drogéria és szépségápolás")

    // drogérialáncnál a névből csak ezek a csoportok jöhetnek
    private val DRUGSTORE = setOf(
        "Parfüm és illat", "Drogéria és szépségápolás",
        "Háztartás és tisztítószer", "Baba", "Állateledel"
    )

    private const val BASIC_FOOD = "Alapvető élelmiszer"

    /** Az összes csoport, a szűrő sorrendjében; az utolsó az „Egyéb". */
    val GROUPS: List<String> = RULES.map { it.first } + OTHER

    // A szabályokat EGYSZER dolgozzuk fel. (A Python csoportonként egy
    // vagylagos regexet fordít; itt a kulcsszavakat az első betűjük szerint
    // tároljuk – ugyanaz az eredmény, de telefonon is gyors tízezer termékre.)
    private val NAME_TABLE = Table(RULES)
    private val CATEGORY_TABLE = Table(CATEGORY_RULES)
    private val NON_FOOD_IDX: BooleanArray = BooleanArray(RULES.size) { RULES[it].first in NON_FOOD }

    /**
     * A termék közös csoportja: név → bolti kategória → bolt jellege →
     * „Egyéb". (A Python `besorol()` párja.)
     */
    fun classify(name: String, category: String = "", store: String = ""): String {
        if (store in ONLY_NON_FOOD) {
            return NAME_TABLE.first(name, NON_FOOD_IDX) ?: OTHER
        }
        STORE_DEFAULT[store]?.let { storeDefault ->
            // drogérialánc: a SAJÁT kategóriája megbízhatóbb, mint a név (egy
            // „citromos tusfürdő" ne legyen gyümölcs); élelmiszernél a név dönt
            val k = CATEGORY_TABLE.first(category)
            if (k != null && k != BASIC_FOOD) return k
            if (k != null) return NAME_TABLE.first(name) ?: k
            val fromName = NAME_TABLE.first(name)
            return if (fromName != null && fromName in DRUGSTORE) fromName else storeDefault
        }
        return NAME_TABLE.first(name)
            ?: CATEGORY_TABLE.first(category)
            ?: DEFAULT_IF_NONE[store]
            ?: OTHER
    }

    /** A termék csoportja: amit a gyűjtő megadott, különben a besorolás. */
    fun of(item: OfferItem): String = item.group.ifBlank { classify(item.name, item.category, item.store) }

    /** A listában előforduló csoportok a [GROUPS] sorrendjében, darabszámmal. */
    fun counts(items: List<OfferItem>): List<Pair<String, Int>> {
        val n = HashMap<String, Int>()
        for (it in items) {
            val g = of(it)
            n[g] = (n[g] ?: 0) + 1
        }
        val known = GROUPS.filter { it in n }.map { it to n.getValue(it) }
        // a gyűjtő adhat a listán kívüli csoportnevet is – az a végére kerül
        val extra = n.keys.filter { it !in GROUPS }.sorted().map { it to n.getValue(it) }
        return known + extra
    }

    // ---- kulcsszavak ----

    /**
     * Python `\w` (a `re` modul str-en): betű, szám vagy aláhúzás. A `\b`
     * szóhatárt ezzel számoljuk KÉZZEL – így nem függ attól, hogy az adott
     * Android regex-motorja Unicode-szerint érti-e a szóhatárt („ő", „ø").
     */
    private fun isWordChar(cp: Int): Boolean =
        cp == '_'.code || Character.isLetter(cp) || Character.isDigit(cp) ||
            Character.getType(cp).let {
                it == Character.LETTER_NUMBER.toInt() || it == Character.OTHER_NUMBER.toInt()
            }

    private const val PREFIX = 0    // `\bszo`   – a szó ELEJÉRE illesztve
    private const val WHOLE = 1     // `\bszo\b` – `=` előtag: teljes szó
    private const val INSIDE = 2    // `szo`     – `~` előtag: a szó belsejében is

    private class Keyword(val group: Int, val text: String, val kind: Int) {
        private val firstIsWord = isWordChar(text.codePointAt(0))
        private val lastIsWord = isWordChar(text.codePointBefore(text.length))

        /** Illeszkedik-e a (már ékezettelenített) szövegben az `i`. helyen. */
        fun matchesAt(t: String, i: Int): Boolean {
            if (!t.startsWith(text, i)) return false
            if (kind == INSIDE) return true
            // `\b` a kulcsszó előtt: az előző karakter „szó-sága" eltér az elsőétől
            val prevWord = i > 0 && isWordChar(t.codePointBefore(i))
            if (prevWord == firstIsWord) return false
            if (kind == PREFIX) return true
            val e = i + text.length
            val nextWord = e < t.length && isWordChar(t.codePointAt(e))
            return nextWord != lastIsWord
        }
    }

    /**
     * Egy szabálylista (csoport → kulcsszavak), a Python `_minta()` szerint
     * feldolgozva: minden kulcsszó ékezet nélkül, `split()` szerint szavakra
     * bontva. A kulcsszavak az első karakterük szerint vannak szétosztva, azon
     * belül csoportsorrendben – a keresés csak a legkorábbi csoportot keresi.
     */
    private class Table(rules: List<Pair<String, String>>) {
        private val groups: List<String> = rules.map { it.first }
        private val byFirst: Map<Char, Array<Keyword>>

        init {
            val all = ArrayList<Keyword>()
            rules.forEachIndexed { g, (_, words) ->
                for (raw in words.trim().split(Regex("\\s+"))) {
                    val s = plain(raw)
                    all += when {
                        s.startsWith("=") -> Keyword(g, s.substring(1), WHOLE)
                        s.startsWith("~") -> Keyword(g, s.substring(1), INSIDE)
                        else -> Keyword(g, s, PREFIX)
                    }
                }
            }
            byFirst = all.groupBy { it.text[0] }
                .mapValues { (_, l) -> l.sortedBy { it.group }.toTypedArray() }
        }

        /**
         * Az első (szabálysorrend szerinti) csoport, amelynek valamelyik
         * kulcsszava illeszkedik – a Python `_elso()` párja. [allowed]: csak
         * ezek a csoportok jöhetnek szóba (Pepco).
         */
        fun first(text: String?, allowed: BooleanArray? = null): String? {
            val t = plain(text)
            var best = Int.MAX_VALUE
            for (i in t.indices) {
                val cands = byFirst[t[i]] ?: continue
                for (k in cands) {
                    if (k.group >= best) break
                    if (allowed != null && !allowed[k.group]) continue
                    if (k.matchesAt(t, i)) { best = k.group; break }
                }
                if (best == 0) break
            }
            return if (best == Int.MAX_VALUE) null else groups[best]
        }
    }

    // ---- ékezettelenítés ----

    /**
     * A Python `ekezet_nelkul()` pontos párja: kisbetű, NFKD, és a
     * kombináló jelek (`unicodedata.combining(c) != 0`) nélkül. (Az
     * [OfferText.plain] NFD-t és minden Mn-jelet használ – az nem ugyanaz.)
     */
    fun plain(s: String?): String {
        val n = Normalizer.normalize(s.orEmpty().lowercase(), Normalizer.Form.NFKD)
        var i = 0
        var sb: StringBuilder? = null
        while (i < n.length) {
            val cp = n.codePointAt(i)
            val len = Character.charCount(cp)
            if (isCombining(cp)) {
                if (sb == null) sb = StringBuilder(n.length).append(n, 0, i)
            } else {
                sb?.appendCodePoint(cp)
            }
            i += len
        }
        return sb?.toString() ?: n
    }

    /**
     * `unicodedata.combining(c) != 0`: a Java nem adja ki a kombináló
     * osztályt, de az NFD a jeleket osztály szerint sorba rendezi – ha a jel
     * egy 1-es osztályú jel (U+0334) ELÉ kerül, akkor az osztálya > 1.
     * Csak jelekre (M*) számoljuk, és megjegyezzük.
     */
    private fun isCombining(cp: Int): Boolean {
        when (Character.getType(cp)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> Unit
            else -> return false
        }
        return combiningCache.getOrPut(cp) {
            if (CCC_ONE.contains(cp)) true
            else {
                val c = String(Character.toChars(cp))
                Normalizer.normalize("a$c\u0334", Normalizer.Form.NFD) == "a\u0334$c"
            }
        }
    }
}
