Set-Location "C:\Users\msn\Documents\SuperDL-Android"
git add -A
git commit -m "1.74.0 - a felajanlas akkor is jojjon, ha nincs mit beallitani

Ket ut kimaradt: ha az elso inditaskor mar minden engedely megvan
(ujratelepites, vagy valaki elore megadta oket), a varazslonak nincs
dolga - de a tanulo mod felajanlasa ugyanugy jar. Eppen ez Alph sajat
telefonjanak helyzete is, tehat igy kiprobalhato.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WHMcycDiQUDuTQ7pSZgWxu" --quiet
git log --oneline -1
$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.74.0-debug.apk"
