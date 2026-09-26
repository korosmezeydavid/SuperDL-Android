import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1500:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "vasarlas":
    raise SystemExit("NEM VASARLAS")
k = "app/src/main/kotlin/com/superdl/launcher/"
run("add", k + "MainActivity.kt", k + "offers/OfferText.kt", "tools/vas_commit3.py")
run("commit", "-m", """Akciós újság: közös withArticle a masterről; „szeptember 24. napjától"

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-3")
