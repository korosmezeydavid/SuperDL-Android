import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1200:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "app/src/main", "tools/elena_nema_commit.py")
run("commit", "-m", """Elena: néma mód be- és kikapcsolása hangból

Eddig „Ez a parancs még nem elérhető hangból" volt a válasz. Most:
„kapcsold be/ki a néma módot", „néma mód be/ki", „legyen néma" — a be/ki
szándékot tiszteletben tartja (ha már olyan, megmondja); a puszta „néma mód" vált.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-1")
r = subprocess.run(["git", "archive", "--format=zip", "-o", r"proba-apk\master-forras.zip", "master",
                    "app/src/main", "app/build.gradle"], capture_output=True)
print("archive", r.returncode, os.path.getsize(r"proba-apk\master-forras.zip"))
