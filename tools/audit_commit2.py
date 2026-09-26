import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-2000:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "app/src/main", "tools/audit-alkalmaz2.ps1", "tools/audit_commit2.py")
msg = """Audit 2. kör: hívásvárakoztatás, kettős SIM, WiFi-portál biztonsága

Telefon:
- Várakozó hívás elutasítása nem bontja a folyamatban lévő beszélgetést
- Kettős SIM „mindig kérdezzen" beállítással: a hívás nem ragad be (SIM-választás)
- Tartott hívás: a másik hívás vége után visszaveszi, nem tűnik el
- Beszélgetés közben érkező hívás: rövid várakoztató hang, nem teljes csengőhang
- Hívásszűrő fehér/feketelista: 06… és +36… ugyanaz a szám
- Hívó neve: telefonkönyv-keresés (PhoneLookup), nincs téves név rövid számokra
- Kihangosítás ki: fülhallgató/Bluetooth útvonal megmarad
- Kimenő hívást a szűrő nem naplóz „kiszűrt hívásnak"
- MMS érkezésekor legalább szól; gyorsválasz SMS (más appokból) megy
- SMS: egy sikertelen rész nem íródik felül „elküldve"-re
- Értesítések: saját, folyamatban lévő és csoport-összegző nem szól újra
- Naptár-riasztás: törölt/áthelyezett eseményre nem szól, az ablak előregördül

WiFi-portál és fájlok:
- CSRF elleni védelem (SameSite süti, Origin-ellenőrzés), XSS a törlés-kérdésben
- A tárolt e-mail jelszó nem mehet ki más szerverre
- Csak helyi hálózatról érhető el; ékezetes fájlnevek feltöltéskor helyesek
- Nagyobb mentés visszatöltése; média letöltése memória-kímélően
- Fájlkezelő: törlés, méret, ZIP-infó háttérben (nem fagy); átnevezés nem ír felül;
  áthelyezés ugyanabba a mappába nem duplikál; megosztási napló atomi írás
- Felhőmegosztás: HTML-válaszból nem lesz hamis link

Javítás az 1. kör után:
- Képernyőolvasó: lista végén nem vált véletlenül fület
- Időzített SMS: ismeretlen szó előtt „és fél óra" nem akasztja meg az értelmezést

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
"""
run("commit", "-m", msg)
run("log", "--oneline", "-2")
