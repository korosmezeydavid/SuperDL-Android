"""Elena felvetele a mobil katalogusba.

Ket dolgot tesz:
  1. feltolti a temak/tema-elena.json fajlt a SuperDL tarolo mobil agara,
  2. beszurja a modul sort a mobil-katalogus.json-ba (UTF-8, BOM nelkul).

A katalogus fajlt NYERS BAJTKENT toltjuk le es NYERS BAJTKENT toltjuk
vissza - igy az ekezetek nem serulhetnek egy kodolas-valtason.
"""
import base64
import json
import os
import subprocess

REPO = "korosmezeydavid/SuperDL"
BRANCH = "mobil"
THEME_LOCAL = r"C:\Users\msn\Documents\SuperDL-Android\tools\tema-elena.json"
THEME_PATH = "temak/tema-elena.json"
CAT_PATH = "mobil-katalogus.json"

MODULE_ID = "tema-elena"


def gh(args, data=None):
    p = subprocess.run(["gh"] + args, capture_output=True, input=data, shell=True)
    if p.returncode != 0:
        raise SystemExit("gh hiba: " + p.stderr.decode("utf-8", "replace"))
    return p.stdout.decode("utf-8", "replace")


def get_file(path):
    """(bajtok, sha) vagy (None, None) ha nincs meg."""
    p = subprocess.run(
        ["gh", "api", f"repos/{REPO}/contents/{path}?ref={BRANCH}"],
        capture_output=True, shell=True,
    )
    if p.returncode != 0:
        return None, None
    o = json.loads(p.stdout.decode("utf-8"))
    return base64.b64decode(o["content"]), o["sha"]


def put_file(path, raw, sha, message):
    """A TORZSET FAJLBOL ADJUK AT.

    A base64-elt tartalom szazezres nagysagrendu karakter; parancssori
    argumentumkent a Windows visszautasitja (WinError 206). A gh a
    --input kapcsoloval fajlbol olvassa a keres torzset, ott nincs
    hosszkorlat.
    """
    body = {
        "message": message,
        "content": base64.b64encode(raw).decode("ascii"),
        "branch": BRANCH,
    }
    if sha:
        body["sha"] = sha
    tmp = os.path.join(os.path.dirname(THEME_LOCAL), "_gh_body.json")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(body, f)
    try:
        out = gh([
            "api", "-X", "PUT", f"repos/{REPO}/contents/{path}",
            "--input", tmp, "--jq", ".commit.sha",
        ])
    finally:
        try:
            os.remove(tmp)
        except OSError:
            pass
    return out.strip()


# ── 1. a tema fajl ───────────────────────────────────────────────────────
with open(THEME_LOCAL, "rb") as f:
    theme_raw = f.read()
_, theme_sha = get_file(THEME_PATH)
c = put_file(THEME_PATH, theme_raw, theme_sha,
             "Elena beszedtema modul (elso tema a katalogusban)")
print("tema feltoltve:", c, f"({len(theme_raw)} byte)")

# ── 2. a katalogus sor ───────────────────────────────────────────────────
cat_raw, cat_sha = get_file(CAT_PATH)
cat = json.loads(cat_raw.decode("utf-8"))

module = {
    "id": MODULE_ID,
    "nev": "Elena",
    "kategoria": "megjelenes",
    "tipus": "soundtheme",
    "szerzo": "Kőrösmezey Dávid",
    "verzio": 1,
    "leiras": "A beépített alaptéma hat hangja. Innen bármikor visszatöltheted, "
              "ha felülvetted valamelyiket. Meghallgatható letöltés előtt.",
    "meret": len(theme_raw),
    "fajl": THEME_PATH,
    "minAlkalmazasVerzio": "1.63.0",
}

mods = cat.get("modules", [])
mods = [m for m in mods if m.get("id") != MODULE_ID]
mods.append(module)
cat["modules"] = mods
cat["frissitve"] = "2026-09-06"

out = json.dumps(cat, ensure_ascii=False, indent=2).encode("utf-8")
c = put_file(CAT_PATH, out, cat_sha, "Elena beszedtema felvetele a katalogusba")
print("katalogus frissitve:", c, f"({len(mods)} modul)")
