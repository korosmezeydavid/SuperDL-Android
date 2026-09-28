import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1500:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "AI_START_HERE.md", "tools/relnotes1860.md", "tools/verzio1860.json",
    "tools/build_release1860.ps1", "tools/kiadas1860.ps1", "tools/commit1860.py")
run("commit", "-m", """1.86.0 kiadás: összegző, verziófájl, kiadási szkriptek

Vásárlás (akciós újság 12 bolttal), beszélő óra, néma módban szóló
emlékeztetők, Elena néma mód, alkalmazás szerinti fókusz, képernyőolvasó
1.85.1-1.85.4, öt kör átfogó hibakeresés.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-2")
