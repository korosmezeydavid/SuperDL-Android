# -*- coding: utf-8 -*-
"""Idobemondo klipek gyartasa a GemOlvaso (Gemini TTS) hangjaval.

A kulcsokat a gemolvaso melletti kulcsok.txt-bol olvassa, ugyanugy rotalva,
ahogy a GemOlvaso teszi. A kulcs SOHA nem kerul kiirasra.

Hasznalat:
    python idohang.py proba          - 4 klip + egy osszefuzott elohallgatas
    python idohang.py mind           - minden hianyzo klip legyartasa
    python idohang.py proba Kore     - mas hanggal
"""
import base64
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
import urllib.error
import wave

MODELL = "gemini-2.5-flash-preview-tts"
ALAP_HANG = "Leda"
KULCS_FAJL = r"C:\Users\msn\Documents\gemolvaso\kulcsok.txt"
FFMPEG = (r"C:\Users\msn\AppData\Local\Microsoft\WinGet\Packages"
          r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
          r"\ffmpeg-8.1.1-full_build\bin\ffmpeg.exe")
KIMENET = r"C:\Users\msn\Documents\SuperDL-Android\idohang"

# --- magyar szavak, ekezetek unicode-kodokkal (a fajl maradjon ASCII) -------
E = "\u00e9"   # e'
A = "\u00e1"   # a'
OU = "\u00f6"  # o:
OH = "\u0151"  # o"
IH = "\u00ed"  # i'
UH = "\u00fa"  # u'
OA = "\u00f3"  # o'

EGYESEK = ["", "egy", "kett" + OH, "h" + A + "rom", "n" + E + "gy", OU + "t",
           "hat", "h" + E + "t", "nyolc", "kilenc"]
TIZESEK = {10: "t" + IH + "z", 20: "h" + UH + "sz", 30: "harminc",
           40: "negyven", 50: OU + "tven"}
ELOTAG = {10: "tizen", 20: "huszon", 30: "harminc", 40: "negyven",
          50: OU + "tven"}

SZO_ORA = OA + "ra"
SZO_PERC = "perc"
SZO_PONT = "pont"


def szam_szoval(n):
    if n == 0:
        return "nulla"
    if n < 10:
        return EGYESEK[n]
    tizes = (n // 10) * 10
    egyes = n % 10
    if egyes == 0:
        return TIZESEK[tizes]
    return ELOTAG[tizes] + EGYESEK[egyes]


def klipek(teljes):
    """(fajlnev, kimondando szoveg) parok."""
    ki = []
    hatar = 60 if teljes else 24
    for n in range(hatar):
        ki.append(("sz%02d" % n, szam_szoval(n)))
    if not teljes:
        for n in (25, 30, 35, 40, 45, 50, 55):
            ki.append(("sz%02d" % n, szam_szoval(n)))
    ki.append(("ora", SZO_ORA))
    ki.append(("perc", SZO_PERC))
    ki.append(("pont", SZO_PONT))
    return ki


# --- kulcsok ---------------------------------------------------------------

def kulcsok():
    with open(KULCS_FAJL, encoding="utf-8", errors="replace") as f:
        txt = f.read()
    latott, ki = set(), []
    for k in re.findall(r"AIza[A-Za-z0-9_\-]+", txt):
        if k not in latott:
            latott.add(k)
            ki.append(k)
    return ki


def gemini_pcm(text, hang, kulcs):
    url = ("https://generativelanguage.googleapis.com/v1beta/models/"
           "%s:generateContent" % MODELL)
    body = {"contents": [{"parts": [{"text": text}]}],
            "generationConfig": {
                "responseModalities": ["AUDIO"],
                "speechConfig": {"voiceConfig": {
                    "prebuiltVoiceConfig": {"voiceName": hang}}}}}
    req = urllib.request.Request(
        url, data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json",
                 "x-goog-api-key": kulcs, "User-Agent": "SuperDL-idohang"})
    with urllib.request.urlopen(req, timeout=180) as r:
        j = json.load(r)
    part = j["candidates"][0]["content"]["parts"][0]
    return base64.b64decode(part["inlineData"]["data"])


def pcm_rotalva(text, hang, kulcs_lista, allapot):
    n = len(kulcs_lista)
    start = allapot["idx"] % n
    utolso = None
    for off in range(n):
        i = (start + off) % n
        try:
            pcm = gemini_pcm(text, hang, kulcs_lista[i])
            allapot["idx"] = i
            return pcm
        except urllib.error.HTTPError as e:
            kod = e.code
            utolso = kod
            if kod in (401, 403, 429) and off < n - 1:
                continue
            raise RuntimeError("HTTP %s (a kulcs nincs kiirva)" % kod)
    raise RuntimeError("Minden kulcs elutasitott (HTTP %s)" % utolso)


def wav_ir(path, pcm, rate=24000):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm)


VAG = ("silenceremove=start_periods=1:start_duration=0:start_threshold=-45dB:"
       "detection=peak,areverse,"
       "silenceremove=start_periods=1:start_duration=0:start_threshold=-45dB:"
       "detection=peak,areverse")


def vag_es_alakit(nyers, cel_wav, cel_m4a):
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", nyers,
                    "-af", VAG, "-ar", "24000", "-ac", "1", cel_wav],
                   check=True)
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", cel_wav,
                    "-c:a", "aac", "-b:a", "64k", "-ar", "24000", "-ac", "1",
                    cel_m4a], check=True)


def elohallgatas(mappa, nevek, cel):
    """Osszefuzi a megadott klipeket 70 ms szunettel, egy mp3-ba."""
    lista = os.path.join(mappa, "_lista.txt")
    csend = os.path.join(mappa, "_csend.wav")
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-f", "lavfi",
                    "-i", "anullsrc=r=24000:cl=mono", "-t", "0.07", csend],
                   check=True)
    with open(lista, "w", encoding="utf-8") as f:
        for i, nev in enumerate(nevek):
            f.write("file '%s'\n" % os.path.join(mappa, nev + ".wav")
                    .replace("\\", "/"))
            if i < len(nevek) - 1:
                f.write("file '%s'\n" % csend.replace("\\", "/"))
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-f", "concat",
                    "-safe", "0", "-i", lista, "-c:a", "libmp3lame",
                    "-b:a", "96k", cel], check=True)
    os.remove(lista)
    os.remove(csend)


def main():
    mod = sys.argv[1] if len(sys.argv) > 1 else "proba"
    hang = sys.argv[2] if len(sys.argv) > 2 else ALAP_HANG
    mappa = os.path.join(KIMENET, hang.lower())
    nyers_mappa = os.path.join(mappa, "nyers")
    os.makedirs(nyers_mappa, exist_ok=True)

    ks = kulcsok()
    if not ks:
        print("Nincs kulcs a kulcsok.txt-ben.")
        return
    print("kulcsok: %d db" % len(ks))
    allapot = {"idx": 0}

    if mod == "proba":
        lista = [("sz14", szam_szoval(14)), ("ora", SZO_ORA),
                 ("sz35", szam_szoval(35)), ("perc", SZO_PERC)]
    else:
        lista = klipek(teljes=(mod == "mind"))

    kesz, kihagyva, hiba = 0, 0, 0
    for nev, szoveg in lista:
        cel_m4a = os.path.join(mappa, nev + ".m4a")
        if os.path.isfile(cel_m4a) and mod != "proba":
            kihagyva += 1
            continue
        nyers = os.path.join(nyers_mappa, nev + ".wav")
        try:
            pcm = pcm_rotalva(szoveg, hang, ks, allapot)
            wav_ir(nyers, pcm)
            vag_es_alakit(nyers, os.path.join(mappa, nev + ".wav"), cel_m4a)
            kesz += 1
            print("ok: %s" % nev)
        except Exception as ex:
            hiba += 1
            print("HIBA %s -> %s" % (nev, ex))
            if "Minden kulcs" in str(ex):
                print("Megallok. Kesobb ugyanezzel a paranccsal folytathato.")
                break
        time.sleep(0.4)

    print("kesz=%d kihagyva=%d hiba=%d" % (kesz, kihagyva, hiba))

    if mod == "proba" and kesz == 4:
        cel = os.path.join(KIMENET, "elohallgatas-%s.mp3" % hang.lower())
        elohallgatas(mappa, ["sz14", "ora", "sz35", "perc"], cel)
        print("elohallgatas: %s" % cel)


if __name__ == "__main__":
    main()
