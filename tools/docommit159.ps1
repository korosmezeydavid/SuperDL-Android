$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add app/build.gradle app/src/main/AndroidManifest.xml
git add app/src/main/kotlin app/src/main/res/xml app/src/main/res/values/strings.xml
git add tools/relnotes159.md tools/reltitle159.txt tools/verzio159.json tools/commitmsg159.txt
git add tools/publish159.ps1 tools/dorelease159.ps1 tools/pushverzio159.ps1 tools/build_release159.ps1
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- titok-szken (uresnek kell lennie) ---'
git diff --cached --name-only | Select-String -Pattern 'keystore|ably_key|local.properties'
Write-Output '--- commit ---'
git commit -F tools/commitmsg159.txt
git push origin master
