import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-1200:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "app/src/main", "tools/appfokusz_commit.py")
run("commit", "-m", """Alkalmazás szerinti fókusz: amíg egy app előtérben van, szűrt hívások

Alph kérése (2026-09-27): „pl. amikor a TikTokot használom, olyankor csak a
fehérlistás számok érhetnek el." Beállítások → Biztonság → Alkalmazás szerinti
fókusz: új fókusz (app + szigor: csak fehérlista / fehérlista és kedvencek /
fehérlista és névjegyek), Fókuszaim (ki-be), Állapot, Törlés, Súgó.
Az előtérben lévő appot a képernyőolvasó és a PIN-segéd akadálymentes
szolgáltatása figyeli (ForegroundAppTracker); a hívásszűrő a legszigorúbb
fókuszt alkalmazza, a fehérlista mindig átcsörög.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
""")
run("log", "--oneline", "-1")
run("checkout", "vasarlas")
run("merge", "master", "-m", "Merge master (Elena nema mod, app fokusz) into vasarlas")
print("AG:", run("rev-parse", "--abbrev-ref", "HEAD").stdout)
run("diff", "--name-only", "--diff-filter=U")
