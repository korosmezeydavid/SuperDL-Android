$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
git add AI_START_HERE.md app/build.gradle
git add app/src/main/kotlin
git add tools/relnotes158.md tools/reltitle158.txt
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- titok-szken (uresnek kell lennie) ---'
git diff --cached --name-only | Select-String -Pattern 'keystore|ably_key|local.properties'
Write-Output '--- commit ---'
git commit -F tools/commitmsg158.txt
git push origin master
