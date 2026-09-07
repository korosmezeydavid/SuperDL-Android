$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add app/build.gradle
git add app/src/main/kotlin
git add app/src/main/assets
git add app/src/main/AndroidManifest.xml
git add tools/relnotes1630.md tools/reltitle1630.txt tools/verzio1630.json tools/commitmsg1630.txt
git add tools/publish1630.ps1 tools/build_release1630.ps1
git add dokumentumok
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- titok-szken (URESNEK kell lennie) ---'
git diff --cached --name-only | Select-String -Pattern 'keystore|ably_key|local.properties'
Write-Output '--- commit ---'
git commit -F tools/commitmsg1630.txt
git push origin master
