# -*- coding: utf-8 -*-
"""A beszelo ora vegleges hangjai: modul-JSON a telefonnak + tiszta mappa a Windowsnak.

Kimenet:
  tools/beszelo-ora-leda.json                 - a katalogus-modul (m4a, base64)
  idohang/beszelo-ora-leda/                   - a Windows-oldalnak
      egesz/ora00.wav ... ora23.wav           "Most tizennegy ora van."
      eleje/ora00.wav ... ora23.wav           "Most tizennegy ora"   (00: nulla ora, 12: tizenket ora)
      perc/perc05.wav ... perc55.wav          "harminc perc"
      van.wav                                 "van."
      mondatok/HH-MM.wav                      mind a 288 kesz mondat (egesz ora + 5 perces)
      OLVASS.txt
"""
import base64, json, os, shutil, subprocess, sys, wave
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import idohang as I

GYOKER = r"C:\Users\msn\Documents\SuperDL-Android"
L = os.path.join(GYOKER, "idohang", "leda")
KI = os.path.join(GYOKER, "idohang", "beszelo-ora-leda")
MODUL = os.path.join(GYOKER, "tools", "beszelo-ora-leda.json")
R = 24000
SZUNET_ORA_PERC = 50
SZUNET_PERC_VAN = 15

def forras():
    """kulcs -> forras wav"""
    f = {}
    for h in range(24):
        f["ora%02d" % h] = os.path.join(L, "ora%02d.wav" % h)
        e = "ora%02dn.wav" % h if h in (0, 12) else "ora%02d.wav" % h
        f["eleje%02d" % h] = os.path.join(L, "eleje", e)
    for p in range(5, 60, 5):
        f["perc%02d" % p] = os.path.join(L, "perc%02d.wav" % p)
    f["van"] = os.path.join(L, "veg", "ora16.wav")
    return f

def olv(p):
    w = wave.open(p)
    assert w.getframerate() == R and w.getnchannels() == 1 and w.getsampwidth() == 2, p
    s = w.readframes(w.getnframes()); w.close(); return s

def ir(p, pcm):
    w = wave.open(p, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(R)
    w.writeframes(pcm); w.close()

def cs(ms): return b"\x00\x00" * (R * ms // 1000)

def main():
    f = forras()
    if os.path.isdir(KI):
        shutil.rmtree(KI)
    for d in ("egesz", "eleje", "perc", "mondatok"):
        os.makedirs(os.path.join(KI, d))
    pcm = {k: olv(v) for k, v in f.items()}
    # 1. Windows: a darabok
    for h in range(24):
        ir(os.path.join(KI, "egesz", "ora%02d.wav" % h), pcm["ora%02d" % h])
        ir(os.path.join(KI, "eleje", "ora%02d.wav" % h), pcm["eleje%02d" % h])
    for p in range(5, 60, 5):
        ir(os.path.join(KI, "perc", "perc%02d.wav" % p), pcm["perc%02d" % p])
    ir(os.path.join(KI, "van.wav"), pcm["van"])
    # 2. Windows: a kesz mondatok
    for h in range(24):
        ir(os.path.join(KI, "mondatok", "%02d-00.wav" % h), pcm["ora%02d" % h])
        for p in range(5, 60, 5):
            x = pcm["eleje%02d" % h] + cs(SZUNET_ORA_PERC) + pcm["perc%02d" % p] + cs(SZUNET_PERC_VAN) + pcm["van"]
            ir(os.path.join(KI, "mondatok", "%02d-%02d.wav" % (h, p)), x)
    # 3. Telefon: m4a-k base64-ben
    tmp = os.path.join(KI, "_tmp.m4a")
    hangok = {}
    for k, v in f.items():
        subprocess.run([I.FFMPEG, "-y", "-loglevel", "error", "-i", v, "-c:a", "aac", "-b:a", "64k",
                        "-ar", str(R), "-ac", "1", tmp], check=True)
        with open(tmp, "rb") as fh:
            hangok[k] = base64.b64encode(fh.read()).decode("ascii")
    os.remove(tmp)
    modul = {
        "id": "beszelo-ora-leda",
        "nev": "Beszélő óra — Leda hangja",
        "szerzo": "Kőrösmezey Dávid",
        "leiras": "Élő női hang mondja be az időt: Most tizennégy óra harminc perc van.",
        "nyelv": "hu",
        "formatum": "m4a",
        "szunet_ora_perc_ms": SZUNET_ORA_PERC,
        "szunet_perc_van_ms": SZUNET_PERC_VAN,
        "hangok": hangok,
    }
    with open(MODUL, "w", encoding="utf-8") as fh:
        json.dump(modul, fh, ensure_ascii=False)
    with open(os.path.join(KI, "OLVASS.txt"), "w", encoding="utf-8") as fh:
        fh.write(OLVASS % (SZUNET_ORA_PERC, SZUNET_PERC_VAN))
    print("modul:", MODUL, os.path.getsize(MODUL), "byte,", len(hangok), "hang")
    print("windows mappa:", KI, len(os.listdir(os.path.join(KI, "mondatok"))), "kesz mondat")

OLVASS = """BESZÉLŐ ÓRA — LEDA HANGJA (Gemini TTS, Leda)
Minden fájl: WAV, 24000 Hz, mono, 16 bit, az elején-végén csend nélkül.

KÉSZ MONDATOK (a legegyszerűbb):
  mondatok\\HH-MM.wav   — 24 egész óra + 24 × 11 ötperces = 288 fájl
  pl. mondatok\\14-30.wav = „Most tizennégy óra harminc perc van."
      mondatok\\14-00.wav = „Most tizennégy óra van."
      mondatok\\00-00.wav = „Most éjfél van."   mondatok\\12-00.wav = „Most dél van."

DARABOK (ha magad fűzöd össze):
  egesz\\oraHH.wav   — „Most tizennégy óra van."  (egész órakor ezt játszd)
  eleje\\oraHH.wav   — „Most tizennégy óra"  (00: „Most nulla óra", 12: „Most tizenkét óra")
  perc\\percMM.wav   — „harminc perc"  (05, 10, … 55)
  van.wav            — „van."
  Összefűzés: eleje + %d ms csend + perc + %d ms csend + van

Nem öttel osztható percre nincs hang — ott a gépi felolvasó szól.
"""

if __name__ == "__main__":
    main()
