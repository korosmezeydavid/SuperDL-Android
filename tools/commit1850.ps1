$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add app/build.gradle
git add app/src/main/kotlin/com/superdl/launcher/MainActivity.kt
git add app/src/main/kotlin/com/superdl/launcher/screenreader/ScreenReaderPrefs.kt
git add app/src/main/kotlin/com/superdl/launcher/screenreader/ScreenReaderService.kt
git add dokumentumok/spdlosszegzo.txt
git add tools/relnotes1850.md
git add tools/verzio1850.json
git add tools/build_release1850.ps1
git add tools/commit1850.ps1
git commit -F 'C:\Users\msn\Documents\SuperDL-Android\tools\commit-uzenet1850.txt'
Write-Output '--- AG ---'
git log --oneline -3
