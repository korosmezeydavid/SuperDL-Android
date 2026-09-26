import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1500:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "app/src/main", "tools/audit-alkalmaz5.ps1", "tools/audit_commit5.py")
run("commit", "-m", """Audit 5.: a 4. kör javításainak finomítása (ellenőrzés után)

- Várakozó hívás: a csengés átvétele csak a hívás tényleges vége után, késleltetve és
  újraellenőrizve — letevéskor nincs fölösleges csengés és szellem-képernyő
- Lépésszámláló: csak a tegnapi utolsó állásból indul az új nap
- „Mi a vészhívó száma?" nem indít S.O.S.-t (csak a „vészhívás")
- Átnevezés: kis/nagybetű-érzékeny tárolón sem ír felül másik fájlt

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-1")
run("checkout", "vasarlas")
r = run("merge", "master", "-m", "Merge master (audit 4-5.) into vasarlas")
print("AG:", run("rev-parse", "--abbrev-ref", "HEAD").stdout)
run("diff", "--name-only", "--diff-filter=U")
