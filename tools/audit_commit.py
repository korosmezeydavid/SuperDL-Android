import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-2500:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "app/src/main", "tools/audit-elo.ps1", "tools/audit-alkalmaz.ps1", "tools/teszt-bg.ps1", "tools/audit_commit.py")
msg = """Átfogó hibakeresés (audit): összeomlások, beragadások, elveszett riasztások

Összeomlás / Android 14:
- Képernyőolvasó: tooltipText csak Android 9-től (8.x-en minden söprés összeomlott)
- S.O.S.: előtér-szolgáltatás típusa engedély szerint (helyengedély nélkül nem indult)
- Képernyőfelvétel: mikrofon-típus csak RECORD_AUDIO mellett
- WiFi-portál, féreglyuk, feltöltés: startForeground a stopSelf előtt
- GPS saját hely mentése: rossz típuskényszerítés helyett biztonságos ellenőrzés
- Telefonkereső csengés: setLooping csak Android 9-től

Biztonság, életmentő funkciók:
- S.O.S.: alapértelmezett telefonapp nélkül soha nem bont fel egy felvett hívást
- Gyógyszer-emlékeztető: a halasztás nem törli a másnapi riasztást
- Ébresztő: a második indítás nem hagy maga után leállíthatatlan csengést;
  törölt ébresztő szundija nem szól
- Bediktált telefonszám: minden számcsoport megmarad (eddig csak az első)
- Időzítő: ébren tartás, hogy kikapcsolt képernyővel is időben szóljon
- WiFi-portál: útvonal-kijátszás ellen kanonikus útvonal; a favicon nem zár ki

Beszéd, felület:
- speakThen: egy félbevágott korábbi mondat jelzése nem indítja el az új műveletet
- Elena ébresztőszó: minden parancs után újra figyel
- Megerősítések, amiket a következő mondat elvágott (SMS elküldve, PIN, stb.)
- Háttéreredmények nem rántják vissza a felhasználót (GPS, YouTube, keresés, frissítés)
- Katalógus: balra söprés nem ragad be; üres kijelölésnél van kiút
- Jelszómező: diktálás, beillesztés, Braille-kijelző nem árulja el a jelszót
- Magyar nyelv: névelők, órában/percben, óránként, „összesen"
- Egyebek: IMAP végtelen ciklus, diktafon mentés/memória, podcast .part,
  számológép -szer/-ször, útvonal-kanyarok (OSRM), nyári időszámítás napja,
  „háromnegyed óra", hírek egy hibás forrással, lista végén görgetés

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
"""
run("commit", "-m", msg)
run("log", "--oneline", "-2")
run("status", "--short", "--untracked-files=no")
