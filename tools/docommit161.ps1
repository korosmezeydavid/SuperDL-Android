$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add app/build.gradle
git add app/src/main/AndroidManifest.xml
git add app/src/main/kotlin
git add app/libs/whmobile.aar
git add wormhole-go
git add tools/relnotes161.md tools/reltitle161.txt tools/verzio161.json tools/commitmsg161.txt
git add tools/publish161.ps1 tools/dorelease161.ps1 tools/pushverzio161.ps1 tools/build_release161.ps1 tools/finalcheck161.ps1
git add tools/build_wormhole_aar.ps1 tools/install_dbg.ps1 tools/reinstall_dbg.ps1
git add dokumentumok
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- titok-szken (URESNEK kell lennie) ---'
git diff --cached --name-only | Select-String -Pattern 'keystore|ably_key|local.properties'
Write-Output '--- commit ---'
git commit -F tools/commitmsg161.txt
git push origin master
