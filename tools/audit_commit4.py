import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-2000:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "app/src/main", "tools/audit-alkalmaz4.ps1", "tools/audit_commit4.py")
msg = """Audit 4. kör: PIN-segéd, sötét képernyő, mentés, vészhívás-szavak

- PIN-segéd: nem takarja el a vészhívót, a bejövő hívást és az ébresztőt a zárolt
  képernyőn; képernyő kikapcsolásakor leáll a figyelése
- Sötét képernyő (függöny): Android 12+ alatt nem blokkolja a többi alkalmazás érintését
- Gesztus-irány: újraindítás után, feloldás előtt is megmarad
- Biztonsági mentés: bevásárlólisták, kiejtési szótár, S.O.S.-mondat, naptár, Braille,
  képernyőolvasó és más beállítások is bekerülnek (jelszó és PIN nem)
- Összeomlás-kezelő: súlyos háttérhibát nem nyel el csendben
- Elena: „vészhívás", „vész", „vészjelző" újra S.O.S.; „sos hívás" nem névjegyhívás
- Várakozó hívás: ha az aktív hívás véget ér, a csengő hívás hallható és látható marad
- Rádió: sima .m3u8 lejátszási lista újra megy; fájl átnevezése csak kis/nagybetűvel
- Alkalmazásindítás hanggal: rövid nevű app nem indul el tévedésből
- Fényérzékelő: háttérben nem sípol tovább; százalék helyesen
- Lépésszámláló: a nap korábbi lépései nem vesznek el
- Műveletsor-felvétel: újraindítás után nem marad „felvétel" állapotban

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
"""
run("commit", "-m", msg)
run("log", "--oneline", "-2")
