$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'

Write-Output '=== 1. COMMIT ==='
git add app/build.gradle app/src/main/AndroidManifest.xml
git add app/src/main/kotlin/com/superdl/launcher/setup/SetupDiagnostics.kt
git add tools/relnotes1634.md tools/commitmsg1634.txt tools/verzio1634.json
git add tools/build_release1634.ps1 tools/publish1634.ps1 tools/kiadas1634.ps1
Write-Output '--- staged ---'
git diff --cached --name-only
Write-Output '--- titok-szken (URESNEK kell lennie) ---'
git diff --cached --name-only | Select-String -Pattern 'keystore|ably_key|local.properties'
git commit -F tools/commitmsg1634.txt
git push origin master

Write-Output ''
Write-Output '=== 2. KIADAS ==='
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
gh release create v1.63.4 (Join-Path $dir 'SuperDL.apk') (Join-Path $dir 'SuperDL-1.63.4.apk') --repo korosmezeydavid/SuperDL-Android --title "1.63.4 - A beszedmotor, ami vegig ott volt" --notes-file 'C:\Users\msn\Documents\SuperDL-Android\tools\relnotes1634.md'

Write-Output ''
Write-Output '=== 3. VERZIOFAJL ==='
$file = 'C:\Users\msn\Documents\SuperDL-Android\tools\verzio1634.json'
$bytes = [System.IO.File]::ReadAllBytes($file)
if ($bytes.Length -lt 100) { throw "A fajl gyanusan rovid." }
$b64 = [System.Convert]::ToBase64String($bytes)
$tmp = 'C:\Users\msn\Documents\SuperDL-Android\tools\_verzio1634_api.json'
$sha = (gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.sha')
$payload = @{
    message = "1.63.4 - beszedmotor-lekerdezes action-szovege javitva"
    content = $b64
    sha     = $sha
    branch  = "mobil"
} | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText($tmp, $payload, (New-Object System.Text.UTF8Encoding($false)))
gh api -X PUT repos/korosmezeydavid/SuperDL/contents/verzio.json --input $tmp --jq '.commit.sha'
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Output 'KESZ.'
