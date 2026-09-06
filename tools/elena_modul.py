"""Elena beszedtema -> katalogus modul (tema-elena.json).

A hat beepitett m4a-t base64-be teszi, es a VoiceThemePackage formatuma
szerint egyetlen JSON-t ir. Ez ugyanaz a fajl, amit a telefon is keszit
a sajat temabol - a katalogus ugyanazt terjeszti, amit a felhasznalo kuld.
"""
import base64
import json
import os

SRC = r"C:\Users\msn\Documents\SuperDL-Android\app\src\main\assets\hangtemak\elena"
OUT = r"C:\Users\msn\Documents\SuperDL-Android\tools\tema-elena.json"

EVENTS = [
    "battery_low",
    "battery_full",
    "charger_in",
    "charger_out_low",
    "morning",
    "night",
]

sounds = {}
total = 0
for name in EVENTS:
    path = os.path.join(SRC, name + ".m4a")
    with open(path, "rb") as f:
        raw = f.read()
    total += len(raw)
    sounds[name] = {
        "formatum": "m4a",
        "adat": base64.b64encode(raw).decode("ascii"),
    }

root = {
    "id": "elena",
    "nev": "Elena",
    "szerzo": "Kőrösmezey Dávid",
    "nyelv": "hu",
    "verzio": 1,
    "leiras": "A telefon megéhezik és jóllakik. Ez a beépített alaptéma — "
              "minden SuperDL-ben ott van, innen csak visszatölteni kell, "
              "ha felülvetted valamelyik hangját.",
    "sajat_hang": True,
    "hangok": sounds,
}

with open(OUT, "w", encoding="utf-8") as f:
    json.dump(root, f, ensure_ascii=False)

print("hangok:", len(sounds), "nyers:", total, "byte")
print("kesz fajl:", os.path.getsize(OUT), "byte ->", OUT)
