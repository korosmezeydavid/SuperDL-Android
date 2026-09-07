$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add app/build.gradle
git add app/src/main/kotlin
git add tools/relnotes1621.md tools/reltitle1621.txt tools/verzio1621.json tools/commitmsg1621.txt
git add tools/publish1621.ps1 tools/build_release1621.ps1
git add dokumentumok
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- titok-szken (URESNEK kell lennie) ---'
git diff --cached --name-only | Select-String -Pattern 'keystore|ably_key|local.properties'
Write-Output '--- commit ---'
git commit -F tools/commitmsg1621.txt
git push origin master
