$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add app/build.gradle
git add app/src/main/kotlin
git add tools/relnotes160.md tools/reltitle160.txt tools/verzio160.json tools/commitmsg160.txt
git add tools/publish160.ps1 tools/dorelease160.ps1 tools/pushverzio160.ps1 tools/build_release160.ps1 tools/finalcheck160.ps1
git add dokumentumok
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- titok-szken (uresnek kell lennie) ---'
git diff --cached --name-only | Select-String -Pattern 'keystore|ably_key|local.properties'
Write-Output '--- commit ---'
git commit -F tools/commitmsg160.txt
git push origin master
