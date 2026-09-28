import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1500:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "tools/commit_nettest.py")
staged = run("diff", "--cached", "--name-only").stdout.split()
bad = [f for f in staged if any(x in f for x in ("keystore", "ably_key", "local.properties", "akcio_minta"))]
if bad:
    raise SystemExit("TILTOTT FAJL: %s" % bad)
run("commit", "-m", """Internet-teszt a telefonon (a Windowsos Internet-teszt átirata)

Eszközök > Internet-teszt: Teljes teszt, Takarékos teszt, Gyors
ellenőrzés, Wi-Fi jelerősség figyelése, Korábbi mérések.
- Sebesség (Cloudflare, bemelegítés levonásával, csúcs, ingadozás),
  késleltetés (TCP-kapcsolatnyitás), ingadozás, DNS-idő, publikus adatok
  (az IP alapból rejtve), a SuperDL szolgáltatásainak elérhetősége
- Telefonos többlet: Wi-Fi dBm, sáv, csatorna, szabvány, rx/tx;
  mobilhálózat generációja, szolgáltató, jelszint és dBm; VPN,
  mért kapcsolat, captive portal, privát DNS
- Ítélet elöl, utána önmagában is érthető sorok; mobilneten a teljes
  teszt előtt rákérdez; balra söpréssel bármikor leállítható
- Wi-Fi bejárás mesh-építéshez: 1,5 mp-enként mér, a hang magassága
  követi a jelet, csak az érdemi változást mondja, helyek megjelölése
  diktálva, összefoglaló (hova kell még egység), megosztható jegyzőkönyv
- Előzmények (200), mentésbe felvéve; egyezés-próba a Python-eredetivel

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-2")
