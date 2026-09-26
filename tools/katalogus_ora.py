# -*- coding: utf-8 -*-
"""A beszelo ora modul felvetele a mobil katalogusba (a katalogus_elena.py mintajara).
  1. modulok/beszelo-ora-leda.json fel a SuperDL tarolo mobil agara,
  2. a modul sora a mobil-katalogus.json-ba (nyers bajtkent le es fel)."""
import base64, json, os, subprocess, datetime

REPO = "korosmezeydavid/SuperDL"
BRANCH = "mobil"
LOCAL = r"C:\Users\msn\Documents\SuperDL-Android\tools\beszelo-ora-leda.json"
PATH = "modulok/beszelo-ora-leda.json"
CAT_PATH = "mobil-katalogus.json"
MODULE_ID = "beszelo-ora-leda"

def gh(args):
    p = subprocess.run(["gh"] + args, capture_output=True, shell=True)
    if p.returncode != 0:
        raise SystemExit("gh hiba: " + p.stderr.decode("utf-8", "replace"))
    return p.stdout.decode("utf-8", "replace")

def get_file(path):
    p = subprocess.run(["gh", "api", f"repos/{REPO}/contents/{path}?ref={BRANCH}"], capture_output=True, shell=True)
    if p.returncode != 0:
        return None, None
    o = json.loads(p.stdout.decode("utf-8"))
    raw = base64.b64decode(o["content"]) if o.get("content") else None
    if raw is None or len(raw) < o.get("size", 0):
        # 1 MB folott a contents API nem adja vissza a tartalmat - csak a sha kell
        raw = None
    return raw, o["sha"]

def put_file(path, raw, sha, message):
    body = {"message": message, "content": base64.b64encode(raw).decode("ascii"), "branch": BRANCH}
    if sha:
        body["sha"] = sha
    tmp = os.path.join(os.path.dirname(LOCAL), "_gh_body.json")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(body, f)
    try:
        return gh(["api", "-X", "PUT", f"repos/{REPO}/contents/{path}", "--input", tmp, "--jq", ".commit.sha"]).strip()
    finally:
        try: os.remove(tmp)
        except OSError: pass

with open(LOCAL, "rb") as f:
    raw = f.read()
_, sha = get_file(PATH)
print("modul feltoltve:", put_file(PATH, raw, sha, "Beszelo ora modul: Leda hangja"), len(raw), "byte")

cat_raw, cat_sha = get_file(CAT_PATH)
cat = json.loads(cat_raw.decode("utf-8"))
regi = next((m for m in cat.get("modules", []) if m.get("id") == MODULE_ID), None)
verzio = (regi.get("verzio", 0) + 1) if regi else 1
module = {
    "id": MODULE_ID,
    "nev": "Beszélő óra — Leda hangja",
    "kategoria": "megjelenes",
    "tipus": "talkingclock",
    "szerzo": "Kőrösmezey Dávid",
    "verzio": verzio,
    "leiras": "Az időbemondás élő női hangon, Elena hangján: „Most tizennégy óra harminc perc van.\" "
              "Egész órakor és ötpercenként szól; a köztes percekben marad a felolvasó. "
              "A Teljes őrség időbemondásával működik.",
    "meret": len(raw),
    "fajl": PATH,
    "minAlkalmazasVerzio": "1.86.0",
}
mods = [m for m in cat.get("modules", []) if m.get("id") != MODULE_ID]
mods.append(module)
cat["modules"] = mods
cat["frissitve"] = datetime.date.today().isoformat()
out = json.dumps(cat, ensure_ascii=False, indent=2).encode("utf-8")
print("katalogus frissitve:", put_file(CAT_PATH, out, cat_sha, "Beszelo ora felvetele a katalogusba"), len(mods), "modul, verzio", verzio)
