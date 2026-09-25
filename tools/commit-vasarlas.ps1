Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$ag = git branch --show-current
if ($ag -ne 'vasarlas') { throw "Nem a vasarlas agon vagyunk: $ag" }
git add .gitignore app/build.gradle
git add app/src/main/kotlin/com/superdl/launcher/MainActivity.kt
git add app/src/main/kotlin/com/superdl/launcher/flow/AppFlow.kt
git add app/src/main/kotlin/com/superdl/launcher/help/HelpTexts.kt
git add app/src/main/kotlin/com/superdl/launcher/menu/MenuTree.kt
git add app/src/main/kotlin/com/superdl/launcher/offers
git add app/src/test/kotlin
git add tools/akcio_minta.py tools/commit-uzenet-vasarlas.txt tools/commit-vasarlas.ps1
Write-Output '--- ami bekerul ---'
git diff --cached --stat
git commit -q -F tools/commit-uzenet-vasarlas.txt
git log --oneline -4
Write-Output '--- tavoli agak (a vasarlas NEM lehet kozte) ---'
git branch -r
