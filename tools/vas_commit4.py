import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1500:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "vasarlas":
    raise SystemExit("NEM VASARLAS")
# a mintak (akcio_minta) SOHA nem kerulnek be; csak a mar hozzaadott forrasok + ez a fajl
run("add", "tools/vas_commit4.py")
staged = run("diff", "--cached", "--name-only").stdout.split()
bad = [f for f in staged if any(x in f for x in ("keystore", "ably_key", "local.properties", "akcio_minta"))]
if bad:
    raise SystemExit("TILTOTT FAJL: %s" % bad)
for f in staged:
    if f != "tools/vas_commit4.py" and os.path.exists(f) and "AIza" in open(f, encoding="utf-8", errors="ignore").read():
        raise SystemExit("KULCS GYANU: %s" % f)
run("commit", "-m", """AkciĂłs ĂşjsĂˇg: a Windows 0.6-0.7 boltjai Ă©s funkciĂłi a telefonon

- Ăšj boltok: MĂĽller (drogĂ©ria- Ă©s parfĂĽmĂ©ria-prospektus PDF), Pepco (heti
  ĂşjsĂˇg), Libri (KĂ¶nyvutca akciĂłs kĂ¶nyvei), Illatorium (a sajĂˇt parfĂĽmbolt
  teljes kĂ­nĂˇlata, nem akciĂł â€“ ki is mondja)
- KĂ¶zĂ¶s termĂ©kcsoportok minden boltban (OfferGroups, a csoport.py Ăˇtirata,
  9800/9800 egyezĂ©s); a bolt megnyitĂˇsakor a csoportok jĂ¶nnek, a bolt sajĂˇt
  kategĂłriĂˇi az utolsĂł sorban
- BoltfajtĂˇk a menĂĽben (Ă‰lelmiszerlĂˇncok, DrogĂ©ria Ă©s kozmetika), Ă©s
  â€žMinden Ă©lelmiszerlĂˇnc / drogĂ©ria egyszerre" nĂ©zet termĂ©kcsoportonkĂ©nt
- TermĂ©k-mĹ±veletek: MegnyitĂˇs a bolt oldalĂˇn, CĂ­m mĂˇsolĂˇsa; a webshopos
  boltoknĂˇl kimondja, hogy online is rendelhetĹ‘
- A keresĂ©s a megjegyzĂ©sben is keres (Illatorium: â€žversace")
- A vĂˇrakozĂˇs kĂ¶zben a letĂ¶ltĂ©s lĂ©pĂ©sĂ©t mondja (Libri: 20. oldalâ€¦)
- MentĂ©s boltonkĂ©nti zĂˇrral; a rĂ©gi mentĂ©s termĂ©kcsoportja pĂłtlĂłdik
- EgyezĂ©s-prĂłbĂˇk a Windows-modul kimenetĂ©vel (Pepco 41, Libri 1015,
  Illatorium 2389, MĂĽller 87/87)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-3")
run("status", "--short")
