import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1500:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "app/src/main/kotlin/com/superdl/launcher/patrol/TalkingClock.kt", "tools/ora-telefon.ps1",
    "tools/ora-telefon2.ps1", "tools/ora_telefon_ell.py", "tools/ora_ellenor.py", "tools/ora_commit.py", "tools/ora_commit2.py")
run("commit", "-m", """Beszélő óra: a visszafejtett klipek végéről a töltelék-csend levágva

Telefonon mérve a három klipes mondat ~80 ms-mal hosszabb volt a gépen
gyártottnál (AAC töltelék a klipek végén). Most egyforma.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("checkout", "vasarlas")
run("merge", "master", "-m", "Merge master into vasarlas")
print("AG:", run("rev-parse", "--abbrev-ref", "HEAD").stdout)
