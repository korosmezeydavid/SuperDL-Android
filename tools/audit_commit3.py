import subprocess, os
os.chdir(r"C:\Users\msn\Documents\SuperDL-Android")
def run(*a):
    r = subprocess.run(["git", *a], capture_output=True, text=True, encoding="utf-8")
    print((r.stdout + r.stderr)[-2000:])
    return r
if run("rev-parse", "--abbrev-ref", "HEAD").stdout.strip() != "master":
    raise SystemExit("NEM MASTER")
run("add", "app/src/main", "tools/audit-alkalmaz3.ps1", "tools/audit_commit3.py", "tools/lint-bg.ps1")
msg = """Audit 3. kör: hamis S.O.S., képernyőolvasó, navigáció, lejátszók

Életmentő / veszélyes:
- Elena: az „elvesztettem", „veszek", „sose" többé nem indít (és nem állít le) S.O.S.-t
  — csak a „sos", „s o s", „vészhelyzet", „vészjelzés" szó
- Elena: „utolsó hívások" nem hív fel egy „ok"-ra illeszkedő névjegyet
- Műveletsor: más alkalmazásban nem nyom meg hasonló gombot; hívás érkezésekor megáll

Képernyőolvasó:
- Folyamatos felolvasás minden mozdulatra és alkalmazásváltásra leáll
  (pl. hívásfogadáskor nem olvas tovább)
- Zároláskor a csúszka/sebesség-állítás és a kijelölés-mód kilép
- Érintéses felfedezés ugyanazon az elemen újraindítható
- „Valószínűleg" címke csak szöveg nélküli elemre kerül
- Elnevezés hanggal: az elem nem vész el diktálás közben
- Módváltás, műveletsor-indítás időzítése, JSON null címke

Navigáció, lejátszók:
- Visszafelé bejárt útvonalon minden kanyar elhangzik; közeledés-jelzés eseményhez kötve
- Irány-címkék hézagai (pl. 70,5° jobbra, nem balra előtted)
- Hangos iránytű: friss helyzet, hibaüzenet csak egyszer
- Késő vonat nem tűnik el a listából
- YouTube, rádió: leállítás után nem szól tovább árva lejátszó; nincs dupla állomás
- Hangfókusz: a kézzel megállított lejátszás nem indul el magától
- Podcast/hangoskönyv vége után újra elejéről; hangoskönyv helye rendszeresen mentve
- Zene: lejátszhatatlan lista nem pörög végtelenül
- HLS rádióadók; értesítés-azonosító ütközések (hallókészülék, környezet)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu
"""
run("commit", "-m", msg)
run("log", "--oneline", "-2")
