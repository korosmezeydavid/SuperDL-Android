#!/usr/bin/env python3
"""OpenStreetMap telefonszám-index építése a „Szám azonosítása" funkcióhoz.

MIÉRT VAN EZ: a hívásnaplóban egy ismeretlen szám mögött gyakran egy
rendelő, egy bolt vagy egy hivatal áll. Magánszemélyek nevét legálisan,
ingyen semmilyen nyílt forrás nem adja meg — cégekét és intézményekét
viszont az OpenStreetMap igen, szabad licenccel (ODbL 1.0). Ezt a
kivonatot a telefonra csomagoljuk, így a keresés OFFLINE történik: a
hívó száma sosem hagyja el a készüléket.

Bemenet: Overpass JSON (``{"elements": [...]}``), ahol az elemek pont
(``lat``/``lon``) vagy terület/kapcsolat (``center``) formában jönnek.
Kimenet: ``app/src/main/assets/telefon_index_hu.tsv`` — UTF-8, első sora
a forrásmegjelölés, utána ``e164<TAB>név<TAB>típus<TAB>település`` sorok,
e164 szerint rendezve, (e164, név) párra duplikátummentesen.

Csak a standard könyvtárat használja — szándékosan: a magyar számok
normalizálása egyszerű, és így bármelyik gépen lefut, telepítés nélkül.

Használat:
    python3 tools/osm_telefon_index.py kivonat.json [-o kimenet.tsv]
"""

import argparse
import json
import os
import re
import sys

# A telefonszámot hordozó OSM címkék. A mobile/contact:mobile is kell: sok
# kisvállalkozás (fodrász, szerelő) csak mobilszámot ad meg.
PHONE_TAGS = ("phone", "contact:phone", "mobile", "contact:mobile")

# Névforrások sorrendben: a „name" a kiírt név; ha hiányzik, a márka vagy
# az üzemeltető még mindig többet mond a semminél.
NAME_TAGS = ("name", "brand", "operator", "official_name")

# A település forrásai sorrendben.
PLACE_TAGS = ("addr:city", "addr:place", "addr:suburb", "is_in:city")

# A típust ezekből a kulcsokból vesszük, ebben a sorrendben: az amenity és a
# healthcare a legbeszédesebb (rendelő, gyógyszertár), a leisure a legkevésbé.
TYPE_KEYS = ("amenity", "healthcare", "shop", "office", "tourism", "craft", "leisure")

# A leggyakoribb értékek magyarul. Ami nincs itt, abból a nyers értéket
# mondjuk (aláhúzás helyett szóközzel) — angolul, de legalább igazat.
TYPE_HU = {
    # amenity
    "pharmacy": "gyógyszertár",
    "doctors": "orvosi rendelő",
    "dentist": "fogorvos",
    "restaurant": "étterem",
    "cafe": "kávézó",
    "pub": "kocsma",
    "bar": "bár",
    "biergarten": "sörkert",
    "fast_food": "gyorsétterem",
    "ice_cream": "fagyizó",
    "school": "iskola",
    "kindergarten": "óvoda",
    "childcare": "gyermekfelügyelet",
    "college": "főiskola",
    "university": "egyetem",
    "townhall": "polgármesteri hivatal",
    "police": "rendőrség",
    "fire_station": "tűzoltóság",
    "post_office": "posta",
    "bank": "bank",
    "atm": "bankautomata",
    "veterinary": "állatorvos",
    "hospital": "kórház",
    "clinic": "rendelőintézet",
    "library": "könyvtár",
    "place_of_worship": "templom",
    "fuel": "benzinkút",
    "car_wash": "autómosó",
    "car_rental": "autókölcsönző",
    "taxi": "taxi",
    "cinema": "mozi",
    "theatre": "színház",
    "arts_centre": "művelődési ház",
    "community_centre": "közösségi ház",
    "social_facility": "szociális intézmény",
    "nursing_home": "idősek otthona",
    "courthouse": "bíróság",
    "embassy": "nagykövetség",
    "marketplace": "piac",
    "driving_school": "autósiskola",
    "language_school": "nyelviskola",
    "music_school": "zeneiskola",
    "bus_station": "buszpályaudvar",
    "parking": "parkoló",
    "recycling": "hulladékudvar",
    "funeral_hall": "ravatalozó",
    "grave_yard": "temető",
    "public_bath": "fürdő",
    "nightclub": "szórakozóhely",
    "events_venue": "rendezvényhelyszín",
    "casino": "kaszinó",
    "bicycle_rental": "kerékpárkölcsönző",
    # healthcare
    "doctor": "orvos",
    "physiotherapist": "gyógytornász",
    "optometrist": "optometrista",
    "laboratory": "laboratórium",
    "psychotherapist": "pszichoterapeuta",
    "alternative": "természetgyógyász",
    "centre": "egészségközpont",
    # shop
    "bakery": "pékség",
    "supermarket": "szupermarket",
    "convenience": "vegyesbolt",
    "hairdresser": "fodrász",
    "beauty": "szépségszalon",
    "butcher": "hentes",
    "greengrocer": "zöldséges",
    "florist": "virágbolt",
    "clothes": "ruházati bolt",
    "shoes": "cipőbolt",
    "optician": "optika",
    "hardware": "vasbolt",
    "doityourself": "barkácsbolt",
    "car": "autókereskedés",
    "car_parts": "autóalkatrész-bolt",
    "car_repair": "autószerviz",
    "tyres": "gumiszerviz",
    "bicycle": "kerékpárbolt",
    "electronics": "műszaki bolt",
    "mobile_phone": "mobiltelefon-bolt",
    "computer": "számítástechnikai bolt",
    "books": "könyvesbolt",
    "stationery": "papírbolt",
    "gift": "ajándékbolt",
    "jewelry": "ékszerbolt",
    "furniture": "bútorbolt",
    "chemist": "drogéria",
    "cosmetics": "illatszerbolt",
    "perfumery": "parfüméria",
    "pet": "állateledel-bolt",
    "garden_centre": "kertészet",
    "alcohol": "italbolt",
    "beverages": "italbolt",
    "tobacco": "dohánybolt",
    "kiosk": "trafik",
    "department_store": "áruház",
    "mall": "bevásárlóközpont",
    "travel_agency": "utazási iroda",
    "laundry": "mosoda",
    "dry_cleaning": "vegytisztító",
    "funeral_directors": "temetkezési vállalkozás",
    "copyshop": "fénymásoló szalon",
    "massage": "masszázsszalon",
    "tattoo": "tetoválószalon",
    "medical_supply": "gyógyászati segédeszköz bolt",
    "hearing_aids": "hallókészülék-szaküzlet",
    "second_hand": "használtcikk-bolt",
    "variety_store": "vegyes iparcikk",
    "confectionery": "cukrászda",
    "pastry": "cukrászda",
    "deli": "csemegebolt",
    "farm": "termelői bolt",
    "yes": "bolt",
    # office
    "government": "hivatal",
    "company": "cég",
    "lawyer": "ügyvéd",
    "notary": "közjegyző",
    "accountant": "könyvelő",
    "insurance": "biztosító",
    "estate_agent": "ingatlaniroda",
    "it": "informatikai cég",
    "architect": "építész",
    "tax_advisor": "adótanácsadó",
    "employment_agency": "munkaközvetítő",
    "association": "egyesület",
    "ngo": "civil szervezet",
    "educational_institution": "oktatási intézmény",
    "telecommunication": "távközlési cég",
    "energy_supplier": "energiaszolgáltató",
    "water_utility": "vízszolgáltató",
    # tourism
    "hotel": "szálloda",
    "guest_house": "vendégház",
    "apartment": "apartman",
    "hostel": "hostel",
    "motel": "motel",
    "camp_site": "kemping",
    "chalet": "faház",
    "museum": "múzeum",
    "information": "turisztikai információ",
    "attraction": "látnivaló",
    "gallery": "galéria",
    "zoo": "állatkert",
    "wine_cellar": "borpince",
    # craft
    "electrician": "villanyszerelő",
    "plumber": "vízvezeték-szerelő",
    "carpenter": "asztalos",
    "locksmith": "lakatos",
    "shoemaker": "cipész",
    "tailor": "szabó",
    "painter": "festő",
    "photographer": "fényképész",
    "winery": "borászat",
    "brewery": "sörfőzde",
    "beekeeper": "méhész",
    "gardener": "kertész",
    "hvac": "fűtésszerelő",
    "roofer": "tetőfedő",
    "glaziery": "üveges",
    # leisure
    "sports_centre": "sportközpont",
    "fitness_centre": "edzőterem",
    "swimming_pool": "uszoda",
    "stadium": "stadion",
    "playground": "játszótér",
    "park": "park",
    "horse_riding": "lovarda",
    "golf_course": "golfpálya",
    "bowling_alley": "tekepálya",
    "escape_game": "szabadulószoba",
    "water_park": "élményfürdő",
    "marina": "kikötő",
}

# A 9 számjegyű magyar nemzeti számok előtagjai (mobil és internetes
# hívószámok). Minden más magyar szám 8 számjegyű — a budapesti (1) is.
NINE_DIGIT_PREFIXES = ("20", "21", "30", "31", "38", "50", "70")

HEADER_PREFIX = "# forrás: © OpenStreetMap-közreműködők, ODbL 1.0 — letöltve: "


def _valid_hu_nsn(nsn):
    """Magyar nemzeti szám (előhívó nélkül) formailag rendben van-e."""
    if not nsn.isdigit() or nsn.startswith("0"):
        return False
    if nsn.startswith("1"):
        return len(nsn) == 8
    if nsn[:2] in NINE_DIGIT_PREFIXES:
        return len(nsn) == 9
    return len(nsn) == 8


def _normalize_one(value):
    """Egyetlen számjelölt E.164 alakra, vagy None, ha nem értelmezhető."""
    text = value.strip()
    # A mellék („ext. 12", „mellék 3", „/m.12") nem része a hívható számnak:
    # az első betűtől eldobjuk a maradékot.
    m = re.search(r"[A-Za-zÁÉÍÓÖŐÚÜŰáéíóöőúüű]", text)
    if m:
        text = text[: m.start()]
    text = text.strip()
    if not text:
        return None
    international = text.startswith("+")
    if international:
        # „+36 (0)1 …", „+43 (0)1 …": a zárójeles nulla csak belföldről kell.
        text = text.replace("(0)", "")
    digits = re.sub(r"\D", "", text)
    if not digits:
        return None
    if international:
        pass
    elif digits.startswith("00"):
        digits = digits[2:]
        international = True
    elif digits.startswith("06"):
        return "+36" + digits[2:] if _valid_hu_nsn(digits[2:]) else None
    else:
        # Előhívó nélkül („1 234 5678", „30 123 4567"): Magyarországon ez is
        # gyakori, és csak akkor fogadjuk el, ha pontosan magyar szám alakú.
        return "+36" + digits if _valid_hu_nsn(digits) else None
    if digits.startswith("36"):
        nsn = digits[2:]
        # „+36 06 …" — kétszer írt előhívó; a belföldi nullát eldobjuk.
        if nsn.startswith("06"):
            nsn = nsn[2:]
        elif nsn.startswith("0"):
            nsn = nsn[1:]
        return "+36" + nsn if _valid_hu_nsn(nsn) else None
    # Külföldi szám: legalább 8, legfeljebb 15 számjegy (az E.164 határa).
    if 8 <= len(digits) <= 15 and not digits.startswith("0"):
        return "+" + digits
    return None


def normalize_numbers(value):
    """Egy OSM címkeérték összes telefonszáma E.164 alakban (sorrendtartó, egyedi)."""
    result = []
    if not value:
        return result
    for part in re.split(r"[;,]", value):
        if not part.strip():
            continue
        found = _normalize_one(part)
        if found is None and "/" in part:
            # A „/" lehet a körzetszám elválasztója („06-30/123-4567") ÉS két
            # szám közti elválasztó is („+36 1 234 5678 / +36 30 …"). Előbb
            # egészben próbáljuk; ha úgy nem jó, darabonként.
            for piece in part.split("/"):
                one = _normalize_one(piece)
                if one is not None and one not in result:
                    result.append(one)
            continue
        if found is not None and found not in result:
            result.append(found)
    return result


def _clean(text):
    """Tabulátor és sortörés nem kerülhet a TSV mezőbe."""
    return re.sub(r"\s+", " ", text or "").strip()


def type_of(tags):
    for key in TYPE_KEYS:
        raw = tags.get(key)
        if not raw:
            continue
        raw = raw.split(";")[0].strip()
        if raw in ("yes", "no") and key != "shop":
            continue
        if raw in TYPE_HU:
            return TYPE_HU[raw]
        return raw.replace("_", " ")
    return ""


def name_of(tags):
    for key in NAME_TAGS:
        value = _clean(tags.get(key))
        if value:
            return value
    return ""


def place_of(tags):
    for key in PLACE_TAGS:
        value = _clean(tags.get(key))
        if value:
            return value
    return ""


def build(data):
    """Overpass JSON → (rendezett sorok listája, statisztika)."""
    stats = {"objects": 0, "with_name": 0, "numbers": 0}
    rows = {}
    for element in data.get("elements", []):
        tags = element.get("tags") or {}
        numbers = []
        for key in PHONE_TAGS:
            for num in normalize_numbers(tags.get(key, "")):
                if num not in numbers:
                    numbers.append(num)
        if not numbers:
            continue
        stats["objects"] += 1
        name = name_of(tags)
        if not name:
            continue
        stats["with_name"] += 1
        kind = _clean(type_of(tags))
        place = place_of(tags)
        for num in numbers:
            stats["numbers"] += 1
            key = (num, name)
            if key not in rows:
                rows[key] = (num, name, kind, place)
    ordered = sorted(rows.values(), key=lambda r: (r[0], r[1]))
    stats["unique_numbers"] = len({r[0] for r in ordered})
    stats["rows"] = len(ordered)
    return ordered, stats


def write_tsv(rows, timestamp, path):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(HEADER_PREFIX + (timestamp or "ismeretlen") + "\n")
        for row in rows:
            out.write("\t".join(row) + "\n")


def main(argv=None):
    here = os.path.dirname(os.path.abspath(__file__))
    default_out = os.path.join(here, "..", "app", "src", "main", "assets", "telefon_index_hu.tsv")
    parser = argparse.ArgumentParser(description="OSM telefonszám-index építése (ODbL 1.0).")
    parser.add_argument("input", help="Overpass JSON kivonat")
    parser.add_argument("-o", "--output", default=default_out, help="kimeneti TSV")
    args = parser.parse_args(argv)
    with open(args.input, encoding="utf-8") as f:
        data = json.load(f)
    timestamp = (data.get("osm3s") or {}).get("timestamp_osm_base", "")
    rows, stats = build(data)
    write_tsv(rows, timestamp, args.output)
    print(
        "objektum számmal: {objects}, ebből névvel: {with_name}, "
        "számok: {numbers}, egyedi számok: {unique_numbers}, sorok: {rows}".format(**stats)
    )
    print("kimenet: " + os.path.normpath(args.output))
    return 0


if __name__ == "__main__":
    sys.exit(main())
