$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add app/build.gradle
git add app/src/main/kotlin
git add tools/relnotes1633.md tools/reltitle1633.txt tools/verzio1633.json tools/commitmsg1633.txt
git add tools/publish1633.ps1 tools/build_release1633.ps1
git add tools/relnotes1632.md
git add dokumentumok
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- titok-szken (URESNEK kell lennie) ---'
git diff --cached --name-only | Select-String -Pattern 'keystore|ably_key|local.properties'
Write-Output '--- commit ---'
git commit -F tools/commitmsg1633.txt
git push origin master
