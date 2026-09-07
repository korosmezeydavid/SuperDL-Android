$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add app/build.gradle
git add app/src/main/kotlin
git add app/src/main/assets
git add tools/relnotes1631.md tools/reltitle1631.txt tools/verzio1631.json tools/commitmsg1631.txt
git add tools/publish1631.ps1 tools/build_release1631.ps1
git add tools/elena_modul.py tools/katalogus_elena.py tools/katalogus_ellenor.py
git add dokumentumok
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- titok-szken (URESNEK kell lennie) ---'
git diff --cached --name-only | Select-String -Pattern 'keystore|ably_key|local.properties'
Write-Output '--- commit ---'
git commit -F tools/commitmsg1631.txt
git push origin master
