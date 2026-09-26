import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1200:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "app/src/main", "tools/nema_commit.py")
run("commit", "-m", """Néma mód: az emlékeztetők mindig rendesen szólnak

Alph döntése: „nagyon fontos a gyógyszer-emlékeztető és minden emlékeztető
normálisan szóljon néma módban is". A gyógyszer-, program- és ébresztő-
emlékeztető ismétlődő hangja és az időzítő bemondásai néma módban is szólnak;
a kapcsoló szövege és a súgó ezt mondja.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-1")
run("checkout", "vasarlas")
run("merge", "master", "-m", "Merge master (nema mod emlekeztetok) into vasarlas")
print("AG:", run("rev-parse", "--abbrev-ref", "HEAD").stdout)
run("diff", "--name-only", "--diff-filter=U")
