$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add tools/kozos_tema.py
git add tools/temanezd.ps1 tools/temakozze.ps1 tools/temalista.ps1 tools/temavissza.ps1
git add dokumentumok/kozos-beszedtemak-utmutato.md
git add dokumentumok/hirlevel-1.63.1.txt
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- commit ---'
git commit -m "Kozos beszedtema-katalogus kezeloeszkoz (nezd/kozzetesz/lista/visszavon)

A telefon oldala 1.63.1-ben kesz: a Bekuldom a kozosbe feltolti a temat es
kuld egy levelet a linkkel. Innentol ez a negy parancs veszi at:

  temanezd.ps1 <link>     letoltes, ELLENORZES, kicsomagolas meghallgatasra
  temakozze.ps1 <az>      feltoltes a mobil agra + katalogus-sor
  temalista.ps1           mi van fent, es mit neztel meg de nem tettel kozze
  temavissza.ps1 <az>     levetel a katalogusbol (-Fajlt: a fajl torlese is)

BIZTONSAG: a bekuldott JSON idegen adat, ezert NEM valtozatlanul kerul
kozze. A szkript szetszedi, darabonkent ellenorzi, es UJRAEPITI csak az
ismert mezokbol es csak a hat ismert esemenybol. Kiesik minden ismeretlen
mezo es esemeny, az 1 kB alatti (csonka) es 600 kB feletti klip, a nem
hangformatum, es a 4 MB feletti csomag. A szabad szoveg (nev, szerzo,
leiras) vezerlokarakter-mentesitve es hosszra vagva - ez a telefonon
ELHANGZIK, ott egy ezerkarakteres leiras nem hibauzenet, hanem vegtelen
mondat.

Utkozes-vedelem: azonos azonositoju meglevo temanal a kozzetetel MEGALL es
kiirja a jelenlegi szerzot; felulirni csak a -Felulir kapcsoloval lehet,
olyankor a verzioszam magatol no.

A jelentes klipenkent kiirja a hosszt (ffprobe), es hat masodperc felett
figyelmeztet: ezek a hangok naponta tobbszor szolalnak meg, es a klip UTAN
meg elhangzik a szazalek is.

Ekezetek: a wrapperek chcp 65001 + PYTHONIOENCODING + python -X utf8
hasznalnak, kulonben a kepernyoolvaso krix-kraxot olvasna a konzolrol.

Kiprobalva: a lanc vegigfutott egy eldobhato proba-temaval (kozzetetel,
majd visszavonas fajltorlessel), es az ujraepites tenyleg kiszurte a
kitalalt esemenyt es a titkos mezot.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu"
git push origin master
