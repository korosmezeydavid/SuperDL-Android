import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1500:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "dokumentumok/spdlosszegzo.txt", "tools/kiadas1860b.ps1", "tools/commit_osszegzo1860.py")
run("commit", "-m", """Összegző AI-nak: frissítve 1.85.0 -> 1.86.0

Vásárlás/akciós újság (4.9), alkalmazás szerinti fókusz (4.10), átfogó
hibakeresés (4.11), beszélő óra, néma mód, Elena, képernyőolvasó
1.85.1-1.85.4, WiFi-portál biztonsága, mentés, tervezett és ismert hibák.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-2")
