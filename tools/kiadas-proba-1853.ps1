$ErrorActionPreference = 'Continue'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
if ((git branch --show-current) -ne 'master') { throw 'Nem a masteren vagyunk.' }
git add app/build.gradle app/src/main/kotlin/com/superdl/launcher/MainActivity.kt app/src/main/kotlin/com/superdl/launcher/screenreader/ScreenReaderPrefs.kt tools/commit-uzenet1853.txt tools/kiadas-proba-1853.ps1 2>&1 | Out-Null
git commit -q -F tools/commit-uzenet1853.txt
Write-Output ('master: ' + (git log --oneline -1))
# A proba-ag megkapja a javitast, hogy a telefonra minden egyben menjen.
git checkout vasarlas 2>&1 | Out-Null
git merge --no-edit master 2>&1 | Select-Object -Last 2
Write-Output ('vasarlas: ' + (git log --oneline -1))
