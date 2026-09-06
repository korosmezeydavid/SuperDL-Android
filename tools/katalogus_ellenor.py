"""A katalogus es az Elena tema ellenorzese ONNAN, AHONNAN A TELEFON OLVASSA."""
import json
import urllib.request

BASE = "https://raw.githubusercontent.com/korosmezeydavid/SuperDL/mobil/"


def get(path):
    req = urllib.request.Request(BASE + path, headers={"User-Agent": "SuperDL"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


raw = get("mobil-katalogus.json")
cat = json.loads(raw.decode("utf-8"))
print("katalogus:", len(raw), "byte,", len(cat["modules"]), "modul")
for m in cat["modules"]:
    if m.get("tipus") == "soundtheme":
        print("  beszedtema:", m["id"], "|", m["nev"], "|", m.get("szerzo"),
              "|", m["meret"], "byte |", m["fajl"])

theme_raw = get("temak/tema-elena.json")
theme = json.loads(theme_raw.decode("utf-8"))
print("tema:", len(theme_raw), "byte")
print("  nev:", theme["nev"], "| szerzo:", theme["szerzo"])
print("  hangok:", ", ".join(theme["hangok"].keys()))
for k, v in theme["hangok"].items():
    import base64
    n = len(base64.b64decode(v["adat"]))
    print(f"    {k}: {v['formatum']}, {n} byte")
