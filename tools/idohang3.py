# -*- coding: utf-8 -*-
"""A beszelo ora 24 ora-mondata, Leda hangjan.

FOLYTATHATO: ami mar megvan, azt kihagyja. A Gemini ingyenes kerete
kulcsonkent napi 10 keres, ezert a 24 mondat tobb naposra is nyulhat -
ugyanezzel a paranccsal barmikor folytathato.

    python idohang3.py            - a hianyzo ora-mondatok
    python idohang3.py perc       - egyetlen proba: "harmincot perc."
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.request
import urllib.error

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import idohang as I

HANG = "Leda"
MAPPA = os.path.join(I.KIMENET, HANG.lower())
NYERS = os.path.join(MAPPA, "nyers")


def ora_mondat(n):
    """Mit mondjon az adott egesz oraban."""
    if n == 0:
        return "Most " + I.E + "jf" + I.E + "l van."
    if n == 12:
        return "Most d" + I.E + "l van."
    return "Most " + I.szam_szoval(n) + " " + I.SZO_ORA + " van."


def keres(szoveg, kulcsok, allapot, korok=2):
    n = len(kulcsok)
    for kor in range(n * korok):
        i = (allapot["idx"] + kor) % n
        url = ("https://generativelanguage.googleapis.com/v1beta/models/"
               "%s:generateContent" % I.MODELL)
        body = {"contents": [{"parts": [{"text": szoveg}]}],
                "generationConfig": {
                    "responseModalities": ["AUDIO"],
                    "speechConfig": {"voiceConfig": {
                        "prebuiltVoiceConfig": {"voiceName": HANG}}}}}
        req = urllib.request.Request(
            url, data=json.dumps(body).encode("utf-8"),
            headers={"Content-Type": "application/json",
                     "x-goog-api-key": kulcsok[i],
                     "User-Agent": "SuperDL-idohang"})
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                j = json.load(r)
            c = j.get("candidates", [{}])[0]
            if "content" not in c:
                print("   ures valasz (%s), masik kulcs" % c.get("finishReason"))
                time.sleep(2)
                continue
            allapot["idx"] = i
            return base64.b64decode(
                c["content"]["parts"][0]["inlineData"]["data"])
        except urllib.error.HTTPError as e:
            print("   HTTP %d, masik kulcs" % e.code)
            time.sleep(3)
            continue
    raise RuntimeError("minden kulcs elutasitott")


def keszit(nev, szoveg, kulcsok, allapot):
    cel_m4a = os.path.join(MAPPA, nev + ".m4a")
    if os.path.isfile(cel_m4a):
        return "kihagyva"
    nyers = os.path.join(NYERS, nev + ".wav")
    if not os.path.isfile(nyers):
        pcm = keres(szoveg, kulcsok, allapot)
        I.wav_ir(nyers, pcm)
    cel_wav = os.path.join(MAPPA, nev + ".wav")
    subprocess.run([I.FFMPEG, "-y", "-loglevel", "error", "-i", nyers,
                    "-af", I.VAG, "-ar", "24000", "-ac", "1", cel_wav],
                   check=True)
    subprocess.run([I.FFMPEG, "-y", "-loglevel", "error", "-i", cel_wav,
                    "-c:a", "aac", "-b:a", "64k", "-ar", "24000", "-ac", "1",
                    cel_m4a], check=True)
    return "kesz"


def main():
    os.makedirs(NYERS, exist_ok=True)
    kulcsok = I.kulcsok()
    allapot = {"idx": 0}
    print("kulcsok: %d db" % len(kulcsok))

    if len(sys.argv) > 1 and sys.argv[1] == "perc":
        szoveg = "Harminc" + I.OU + "t perc."
        try:
            print(keszit("perc35", szoveg, kulcsok, allapot))
            print("A KETSZAVAS TOREDEK MEGY - johetnek a perc-klipek is.")
        except Exception as ex:
            print("nem ment: %s" % ex)
        return

    kesz = kihagyva = 0
    for n in range(24):
        nev = "ora%02d" % n
        szoveg = ora_mondat(n)
        try:
            allapot_szo = keszit(nev, szoveg, kulcsok, allapot)
        except Exception as ex:
            print("MEGALLTAM a(z) %s-nal: %s" % (nev, ex))
            print("Ugyanezzel a paranccsal barmikor folytathato.")
            break
        if allapot_szo == "kesz":
            kesz += 1
            print("ok: %s  (%s)" % (nev, szoveg))
            time.sleep(1.5)
        else:
            kihagyva += 1
    megvan = len([f for f in os.listdir(MAPPA) if f.startswith("ora") and f.endswith(".m4a")])
    print("most kesz=%d kihagyva=%d | osszesen megvan: %d / 24"
          % (kesz, kihagyva, megvan))


if __name__ == "__main__":
    main()
