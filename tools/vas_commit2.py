import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1500:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "vasarlas":
    raise SystemExit("NEM VASARLAS")
k = "app/src/main/kotlin/com/superdl/launcher/"
run("add", k + "offers/OfferStore.kt", k + "MainActivity.kt", k + "flow/AppFlow.kt", k + "help/HelpTexts.kt",
    "tools/vas_commit2.py", "tools/menu_ut.py")
run("commit", "-m", """Akciós újság: nagy újság mobilneten — rákérdez a méret megadásával

Eddig a Lidl és a Tesco újságját mobilneten egyáltalán nem töltötte le.
Alph kérése: legyen választható. Most kimondja a méretet (a legutóbb mért
vagy a becsült megabájtot), és megkérdezi: jobbra igen, balra nem (ha van
korábbi, azt mutatja).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-1")
