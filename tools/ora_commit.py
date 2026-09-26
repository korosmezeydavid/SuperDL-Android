import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print(r.stdout[-3000:], r.stderr[-2000:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
k = "app/src/main/kotlin/com/superdl/launcher/"
files = [k + "patrol/TalkingClock.kt", k + "patrol/PatrolAnnouncer.kt", k + "battery/BatteryPatrolService.kt",
         k + "catalog/CatalogModule.kt", k + "catalog/CatalogClient.kt", k + "catalog/CatalogStore.kt",
         "app/build.gradle", "app/src/debug/AndroidManifest.xml",
         "app/src/debug/kotlin/com/superdl/launcher/patrol/TalkingClockDebugReceiver.kt",
         "tools/beszelo_ora_modul.py", "tools/katalogus_ora.py", "tools/ora_vag.py", "tools/ora_whisper.py",
         "tools/ora_whisper2.py", "tools/ora_whisper3.py", "tools/ora_proba2.py", "tools/ora_minta.py",
         "tools/ora_csere.py", "tools/idohang5.py", "tools/idohang7.py", "tools/idohang8.py",
         "tools/idohang.py", "tools/idohang3.py", "tools/idohang4.py", "tools/build-bg.ps1"]
files = [f for f in files if os.path.exists(f)]
run("add", *files)
msg = """1.86.0 - Beszélő óra: élő hangon, a katalógusból letölthető modulból

- Új modul-típus: talkingclock (beszélő óra), a Hangzás és megjelenés csoportban
- A Teljes őrség időbemondása egész órakor és ötpercenként a modul klipjeiből
  szól: „Most tizennégy óra harminc perc van." — pontos szünetekkel, egy darabban
- Ha a modul nincs meg, vagy a klip nem szólal meg: marad a felolvasó
- Letöltéskor a klipek azonnal kibontódnak; törléskor a felolvasó veszi át
- Fejlesztői próba-vevő (csak debug)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
"""
run("commit", "-m", msg)
run("log", "--oneline", "-2")
