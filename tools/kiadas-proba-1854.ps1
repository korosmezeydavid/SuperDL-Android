$ErrorActionPreference = 'Continue'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
if ((git branch --show-current) -ne 'master') { throw 'Nem a masteren vagyunk.' }
git add app/build.gradle app/src/main/kotlin/com/superdl/launcher/screenreader/ScreenReaderService.kt app/src/main/kotlin/com/superdl/launcher/screenreader/ScreenReaderNavigator.kt tools/commit-uzenet1854.txt tools/kiadas-proba-1854.ps1 tools/tiktok_ui.py 2>&1 | Out-Null
git commit -q -F tools/commit-uzenet1854.txt
Write-Output ('master: ' + (git log --oneline -1))
git checkout vasarlas 2>&1 | Out-Null
git merge --no-edit master 2>&1 | Select-Object -Last 1
Write-Output ('vasarlas: ' + (git log --oneline -1))
Start-Process cmd -ArgumentList '/c','gradlew.bat assembleDebug > build-log.txt 2>&1' -WindowStyle Hidden
