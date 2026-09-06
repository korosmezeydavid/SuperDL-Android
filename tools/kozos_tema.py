"""A KOZOS BESZEDTEMA-KATALOGUS KEZELESE — a fejleszto oldala.

A telefon oldala kesz: a "Bekuldom a kozosbe" feltolti a temat egy
ideiglenes tarhelyre, es kuld egy levelet a linkkel. Innentol ez a szkript
veszi at.

NEGY PARANCS:

  nezd <link vagy fajl>   letolti, ELLENORZI, es kicsomagolja a hangokat egy
                          mappaba, hogy meg tudd hallgatni. NEM tesz kozze.
  kozzetesz <azonosito>   a mar megnezett temat felteszi a katalogusba.
  lista                   mi van most a katalogusban.
  visszavon <azonosito>   egy temat levesz a katalogusbol.

MIERT KET LEPES: a "nezd" es a "kozzetesz" kozott te hallgatod meg. Ez az
emberi kapu — enelkul idegen hang kerulne vak emberek telefonjaba anelkul,
hogy azt barki egyszer is meghallgatta volna.

MIERT NEM A BEKULDOTT FAJLT TESSZUK KOZZE VALTOZATLANUL: a bekuldott JSON
idegen adat. A szkript SZETSZEDI, ellenorzi darabonkent, es UJRAEPITI csak
az ismert mezokbol. Igy a katalogusba nem kerulhet be semmi, ami nem
hangfajl es nem a hat esemeny valamelyike.
"""
import argparse
import base64
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request

# ── Beallitasok ──────────────────────────────────────────────────────────
REPO = "korosmezeydavid/SuperDL"
BRANCH = "mobil"
CAT_PATH = "mobil-katalogus.json"
THEME_DIR = "temak"

HERE = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(HERE, "bekuldott")

MAX_PACKAGE = 4 * 1024 * 1024
MAX_CLIP = 600 * 1024
MIN_CLIP = 1000
MAX_LEIRAS = 240

# A hat esemeny — es SEMMI MAS. Ami nem ezen a listan van, kimarad.
EVENTS = [
    ("battery_low", "1-merules"),
    ("battery_full", "2-feltoltve"),
    ("charger_in", "3-tolto-bedugva"),
    ("charger_out_low", "4-tolto-kihuzva"),
    ("morning", "5-jo-reggelt"),
    ("night", "6-jo-ejszakat"),
]
EVENT_LABEL = {
    "battery_low": "merüléskor",
    "battery_full": "feltöltve",
    "charger_in": "töltő bedugva",
    "charger_out_low": "töltő kihúzva alacsony töltöttségnél",
    "morning": "jó reggelt",
    "night": "jó éjszakát",
}
FORMATS = ("wav", "mp3", "m4a", "ogg", "opus", "aac")


# ── Segedek ──────────────────────────────────────────────────────────────

def safe_id(raw):
    """Ekezet es szokoz nelkuli azonosito — ugyanaz a szabaly, mint a telefonon."""
    s = (raw or "").strip().lower()
    for a, b in (("á", "a"), ("é", "e"), ("í", "i"), ("ó", "o"), ("ö", "o"),
                 ("ő", "o"), ("ú", "u"), ("ü", "u"), ("ű", "u")):
        s = s.replace(a, b)
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return s[:40]


def tiszta_szoveg(raw, hatar):
    """Bekuldott szabad szoveg: vezerlokarakterek ki, hossz levagva.

    Ez a szoveg a TELEFONON HANGZIK EL. Egy soremeles vagy egy
    ezerkarakteres leiras ott nem hiba-uzenet, hanem egy vegtelen mondat.
    """
    s = re.sub(r"[\x00-\x1f\x7f]+", " ", (raw or "")).strip()
    s = re.sub(r"\s{2,}", " ", s)
    return s[:hatar]


def letolt(forras):
    """Link vagy helyi fajl -> bajtok."""
    if forras.lower().startswith(("http://", "https://")):
        req = urllib.request.Request(forras, headers={"User-Agent": "SuperDL"})
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.read()
    with open(forras, "rb") as f:
        return f.read()


def gh(args, cimke="gh"):
    p = subprocess.run(["gh"] + args, capture_output=True, shell=True)
    if p.returncode != 0:
        raise SystemExit(f"HIBA ({cimke}): " + p.stderr.decode("utf-8", "replace"))
    return p.stdout.decode("utf-8", "replace")


def gh_get(path):
    """(bajtok, sha) vagy (None, None) ha nincs ilyen fajl."""
    p = subprocess.run(
        ["gh", "api", f"repos/{REPO}/contents/{path}?ref={BRANCH}"],
        capture_output=True, shell=True,
    )
    if p.returncode != 0:
        return None, None
    o = json.loads(p.stdout.decode("utf-8"))
    return base64.b64decode(o["content"]), o["sha"]


def gh_put(path, raw, sha, message):
    """A torzs FAJLBOL megy: a base64 tartalom tul hosszu parancssori
    argumentumnak (Windows: WinError 206)."""
    body = {
        "message": message,
        "content": base64.b64encode(raw).decode("ascii"),
        "branch": BRANCH,
    }
    if sha:
        body["sha"] = sha
    tmp = os.path.join(HERE, "_gh_body.json")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(body, f)
    try:
        return gh(["api", "-X", "PUT", f"repos/{REPO}/contents/{path}",
                   "--input", tmp, "--jq", ".commit.sha"], "feltoltes").strip()
    finally:
        try:
            os.remove(tmp)
        except OSError:
            pass


def gh_delete(path, sha, message):
    body = {"message": message, "sha": sha, "branch": BRANCH}
    tmp = os.path.join(HERE, "_gh_body.json")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(body, f)
    try:
        return gh(["api", "-X", "DELETE", f"repos/{REPO}/contents/{path}",
                   "--input", tmp, "--jq", ".commit.sha"], "torles").strip()
    finally:
        try:
            os.remove(tmp)
        except OSError:
            pass


def hossz_mp(path):
    """A klip hossza masodpercben, ha van ffprobe. Kulonben None."""
    try:
        p = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration",
             "-of", "default=noprint_wrappers=1:nokey=1", path],
            capture_output=True, shell=True, timeout=20,
        )
        if p.returncode != 0:
            return None
        return float(p.stdout.decode().strip())
    except Exception:
        return None


def meret_szoveg(n):
    if n >= 1024 * 1024:
        return f"{n / 1024 / 1024:.1f} MB"
    if n >= 1024:
        return f"{n // 1024} kB"
    return f"{n} bájt"


# ── 1. NEZD — letoltes, ellenorzes, kicsomagolas meghallgatasra ──────────

def parancs_nezd(forras, nyit):
    print("Letöltés…")
    raw = letolt(forras)
    print(f"  {meret_szoveg(len(raw))}")
    if len(raw) > MAX_PACKAGE * 2:
        raise SystemExit("ELUTASÍTVA: a csomag túl nagy, ez nem beszédtéma.")

    try:
        pkg = json.loads(raw.decode("utf-8"))
    except Exception as e:
        raise SystemExit(f"ELUTASÍTVA: ez nem értelmes JSON. ({e})")
    if not isinstance(pkg, dict):
        raise SystemExit("ELUTASÍTVA: a csomag nem objektum.")

    tid = safe_id(pkg.get("id") or pkg.get("nev"))
    if not tid:
        raise SystemExit("ELUTASÍTVA: a csomagnak nincs használható azonosítója.")
    nev = tiszta_szoveg(pkg.get("nev"), 60) or tid
    szerzo = tiszta_szoveg(pkg.get("szerzo"), 60)
    leiras = tiszta_szoveg(pkg.get("leiras"), MAX_LEIRAS)
    nyelv = tiszta_szoveg(pkg.get("nyelv"), 8) or "hu"

    hangok = pkg.get("hangok")
    if not isinstance(hangok, dict):
        raise SystemExit("ELUTASÍTVA: a csomagban nincs hangok szakasz.")

    # A mappa mindig tisztan indul, kulonben egy korabbi bekuldes maradeka
    # osszekeveredne az ujjal.
    mappa = os.path.join(WORK, tid)
    if os.path.isdir(mappa):
        shutil.rmtree(mappa)
    os.makedirs(mappa, exist_ok=True)

    # ÚJRAÉPÍTÉS: csak az ismert mezők és csak a hat esemény kerül át.
    tiszta_hangok = {}
    sorok = []
    osszes = 0
    for kulcs, fajlnev in EVENTS:
        entry = hangok.get(kulcs)
        if not isinstance(entry, dict):
            sorok.append((kulcs, None, "nincs hang"))
            continue
        formatum = str(entry.get("formatum", "m4a")).lower()
        if formatum not in FORMATS:
            sorok.append((kulcs, None, f"ismeretlen formátum: {formatum}"))
            continue
        try:
            adat = base64.b64decode(entry.get("adat", ""), validate=False)
        except Exception:
            sorok.append((kulcs, None, "a hang nem fejthető vissza"))
            continue
        if len(adat) < MIN_CLIP:
            sorok.append((kulcs, None, "túl rövid, valószínűleg csonka"))
            continue
        if len(adat) > MAX_CLIP:
            sorok.append((kulcs, None, f"túl nagy ({meret_szoveg(len(adat))}), kihagyva"))
            continue
        osszes += len(adat)
        if osszes > MAX_PACKAGE:
            sorok.append((kulcs, None, "a csomag mérethatára elfogyott"))
            continue
        ki = os.path.join(mappa, f"{fajlnev}.{formatum}")
        with open(ki, "wb") as f:
            f.write(adat)
        tiszta_hangok[kulcs] = {
            "formatum": formatum,
            "adat": base64.b64encode(adat).decode("ascii"),
        }
        sorok.append((kulcs, ki, None))

    if not tiszta_hangok:
        shutil.rmtree(mappa, ignore_errors=True)
        raise SystemExit("ELUTASÍTVA: egyetlen használható hang sem volt a csomagban.")

    tiszta = {
        "id": tid,
        "nev": nev,
        "szerzo": szerzo,
        "nyelv": nyelv,
        "verzio": 1,
        "leiras": leiras,
        "sajat_hang": True,
        "hangok": tiszta_hangok,
    }
    tiszta_raw = json.dumps(tiszta, ensure_ascii=False).encode("utf-8")
    with open(os.path.join(mappa, "tema.json"), "wb") as f:
        f.write(tiszta_raw)
    with open(os.path.join(mappa, "adatok.json"), "w", encoding="utf-8") as f:
        json.dump({"id": tid, "nev": nev, "szerzo": szerzo, "leiras": leiras,
                   "nyelv": nyelv, "meret": len(tiszta_raw),
                   "hangok": len(tiszta_hangok), "forras": forras},
                  f, ensure_ascii=False, indent=2)

    # ── Jelentes ─────────────────────────────────────────────────────────
    print()
    print("=" * 60)
    print(f"TÉMA: {nev}")
    print(f"  azonosító: {tid}")
    print(f"  szerző:    {szerzo or '(nem adta meg)'}")
    print(f"  nyelv:     {nyelv}")
    if leiras:
        print(f"  leírás:    {leiras}")
    print(f"  hangok:    {len(tiszta_hangok)} / {len(EVENTS)}")
    print(f"  méret:     {meret_szoveg(len(tiszta_raw))} (megtisztítva)")
    if len(tiszta_raw) != len(raw):
        print(f"  FIGYELEM: a bekuldott fajl {meret_szoveg(len(raw))} volt — "
              f"a felesleget eldobtam.")
    print()
    for kulcs, ki, hiba in sorok:
        cimke = EVENT_LABEL[kulcs]
        if hiba:
            print(f"  - {cimke}: {hiba}")
            continue
        mp = hossz_mp(ki)
        hosszszoveg = f"{mp:.1f} mp" if mp else "hossz ismeretlen"
        # HOSSZ-FIGYELMEZTETES. Ezek a hangok NAPONTA TOBBSZOR szolalnak
        # meg. Ami elsore kedves, az hat masodpercben a huszadik alkalomra
        # mar teher — es a klip UTAN meg elhangzik a szazalek is.
        figy = ""
        if mp and mp > 6:
            figy = "  <-- HOSSZÚ, naponta többször hallgatva sok lesz"
        print(f"  - {cimke}: {os.path.basename(ki)}, "
              f"{meret_szoveg(os.path.getsize(ki))}, {hosszszoveg}{figy}")
    print("=" * 60)
    print()
    print("MOST HALLGASD MEG. A hangok itt vannak, sorszámozva:")
    print(f"  {mappa}")
    print()
    print("Ha jónak találod:")
    print(f"  .\\tools\\temakozze.ps1 {tid}")
    print("Ha nem: töröld a mappát, és nem történt semmi.")

    if nyit:
        try:
            subprocess.run(["explorer", mappa], shell=True)
        except Exception:
            pass
    return tid


# ── 2. KOZZETESZ — feltoltes es katalogus-sor ────────────────────────────

def katalogus_be():
    raw, sha = gh_get(CAT_PATH)
    if raw is None:
        raise SystemExit("HIBA: a katalógus-fájlt nem sikerült letölteni.")
    return json.loads(raw.decode("utf-8")), sha


def katalogus_ki(cat, sha, uzenet):
    out = json.dumps(cat, ensure_ascii=False, indent=2).encode("utf-8")
    return gh_put(CAT_PATH, out, sha, uzenet)


def parancs_kozzetesz(tid, felulir):
    tid = safe_id(tid)
    mappa = os.path.join(WORK, tid)
    tema_fajl = os.path.join(mappa, "tema.json")
    adat_fajl = os.path.join(mappa, "adatok.json")
    if not os.path.isfile(tema_fajl) or not os.path.isfile(adat_fajl):
        raise SystemExit(
            f"HIBA: nincs megnézett téma ezzel az azonosítóval: {tid}\n"
            f"Előbb: .\\tools\\temanezd.ps1 <link>"
        )
    with open(adat_fajl, encoding="utf-8") as f:
        meta = json.load(f)
    with open(tema_fajl, "rb") as f:
        tema_raw = f.read()

    modul_id = f"tema-{tid}"
    cat, cat_sha = katalogus_be()
    mods = cat.get("modules", [])
    regi = next((m for m in mods if m.get("id") == modul_id), None)

    if regi and not felulir:
        raise SystemExit(
            f"MEGÁLLTAM: {regi.get('nev')} ({modul_id}) MÁR FENT VAN a "
            f"katalógusban, szerző: {regi.get('szerzo') or '(nincs)'}.\n"
            f"Ha ez ugyanannak a témának az új változata, és tudatosan "
            f"felülírod:\n"
            f"  .\\tools\\temakozze.ps1 {tid} -Felulir"
        )

    verzio = (regi.get("verzio", 1) + 1) if regi else 1

    # 1. a tema fajl
    ut = f"{THEME_DIR}/tema-{tid}.json"
    _, regi_sha = gh_get(ut)
    c1 = gh_put(ut, tema_raw, regi_sha,
                f"Beszedtema: {meta['nev']} ({meta.get('szerzo') or 'ismeretlen szerzo'})")
    print(f"1/2  téma feltöltve: {ut}  ({meret_szoveg(len(tema_raw))})  {c1[:8]}")

    # 2. a katalogus sor
    modul = {
        "id": modul_id,
        "nev": meta["nev"],
        "kategoria": "megjelenes",
        "tipus": "soundtheme",
        "szerzo": meta.get("szerzo", ""),
        "verzio": verzio,
        "leiras": meta.get("leiras") or f"{meta['hangok']} hang, beküldött beszédtéma.",
        "meret": len(tema_raw),
        "fajl": ut,
        "minAlkalmazasVerzio": "1.63.0",
    }
    mods = [m for m in mods if m.get("id") != modul_id]
    mods.append(modul)
    cat["modules"] = mods
    import datetime
    cat["frissitve"] = datetime.date.today().isoformat()
    c2 = katalogus_ki(cat, cat_sha, f"Katalogus: {meta['nev']} felvetele")
    print(f"2/2  katalógus frissítve: {len(mods)} modul  {c2[:8]}")
    print()
    print(f"KÉSZ. A(z) {meta['nev']} mostantól MINDENKI telefonján ott van")
    print("a Beszédtémák a közösből listában. Nem kell hozzá alkalmazás-frissítés.")
    print()
    print("Ha meggondolod magad:")
    print(f"  .\\tools\\temavissza.ps1 {tid}")


# ── 3. LISTA ─────────────────────────────────────────────────────────────

def parancs_lista():
    cat, _ = katalogus_be()
    mods = cat.get("modules", [])
    temak = [m for m in mods if m.get("tipus") == "soundtheme"]
    print(f"A katalógusban {len(mods)} modul van, ebből {len(temak)} beszédtéma.")
    print(f"Utolsó frissítés: {cat.get('frissitve', '(nincs megadva)')}")
    print()
    if not temak:
        print("  (még nincs beszédtéma)")
    for m in temak:
        print(f"  {m['id']}")
        print(f"      név:    {m.get('nev')}")
        print(f"      szerző: {m.get('szerzo') or '(nincs)'}")
        print(f"      verzió: {m.get('verzio')}   méret: {meret_szoveg(m.get('meret', 0))}")
        print(f"      fájl:   {m.get('fajl')}")
    print()
    varo = []
    if os.path.isdir(WORK):
        for n in sorted(os.listdir(WORK)):
            if os.path.isfile(os.path.join(WORK, n, "tema.json")):
                if not any(m["id"] == f"tema-{n}" for m in temak):
                    varo.append(n)
    if varo:
        print("MEGNÉZVE, DE MÉG NEM TETTED KÖZZÉ:")
        for n in varo:
            print(f"  {n}   ->  .\\tools\\temakozze.ps1 {n}")


# ── 4. VISSZAVON ─────────────────────────────────────────────────────────

def parancs_visszavon(tid, fajlt_is):
    tid = safe_id(tid)
    modul_id = f"tema-{tid}"
    cat, cat_sha = katalogus_be()
    mods = cat.get("modules", [])
    regi = next((m for m in mods if m.get("id") == modul_id), None)
    if not regi:
        raise SystemExit(f"Nincs ilyen téma a katalógusban: {modul_id}")
    cat["modules"] = [m for m in mods if m.get("id") != modul_id]
    import datetime
    cat["frissitve"] = datetime.date.today().isoformat()
    katalogus_ki(cat, cat_sha, f"Katalogus: {regi.get('nev')} visszavonasa")
    print(f"Levéve a katalógusból: {regi.get('nev')} ({modul_id})")
    if fajlt_is:
        ut = regi.get("fajl") or f"{THEME_DIR}/tema-{tid}.json"
        _, sha = gh_get(ut)
        if sha:
            gh_delete(ut, sha, f"Beszedtema torlese: {regi.get('nev')}")
            print(f"A téma fájlja is törölve: {ut}")
    else:
        print("A téma fájlja megmaradt a tárolóban — aki már letöltötte, "
              "annál működik tovább.")
        print("Ha a fájlt is törölnéd:  .\\tools\\temavissza.ps1 "
              f"{tid} -Fajlt")


# ── Belepesi pont ────────────────────────────────────────────────────────

def main():
    p = argparse.ArgumentParser(description="SuperDL közös beszédtéma-katalógus")
    sub = p.add_subparsers(dest="parancs", required=True)

    a = sub.add_parser("nezd", help="beküldött téma letöltése és ellenőrzése")
    a.add_argument("forras", help="link vagy helyi fájl")
    a.add_argument("--nenyisd", action="store_true", help="ne nyissa meg a mappát")

    b = sub.add_parser("kozzetesz", help="a megnézett téma közzététele")
    b.add_argument("azonosito")
    b.add_argument("--felulir", action="store_true",
                   help="meglévő azonos azonosítójú téma felülírása")

    sub.add_parser("lista", help="mi van a katalógusban")

    d = sub.add_parser("visszavon", help="téma levétele a katalógusból")
    d.add_argument("azonosito")
    d.add_argument("--fajlt", action="store_true",
                   help="a téma fájlját is törölje a tárolóból")

    args = p.parse_args()
    os.makedirs(WORK, exist_ok=True)
    if args.parancs == "nezd":
        parancs_nezd(args.forras, not args.nenyisd)
    elif args.parancs == "kozzetesz":
        parancs_kozzetesz(args.azonosito, args.felulir)
    elif args.parancs == "lista":
        parancs_lista()
    elif args.parancs == "visszavon":
        parancs_visszavon(args.azonosito, args.fajlt)


if __name__ == "__main__":
    try:
        main()
    except SystemExit as e:
        if isinstance(e.code, str):
            print(e.code)
            sys.exit(1)
        raise
