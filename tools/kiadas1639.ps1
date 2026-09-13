$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'

Write-Output '=== 1. PUSH (a commit mar keszen all) ==='
Write-Output '--- titok-szken (URESNEK kell lennie) ---'
git log --name-only --pretty=format: origin/master..master | Select-String -Pattern 'keystore|ably_key|local.properties'
Write-Output '--- ami kimegy ---'
git log --oneline origin/master..master
git push origin master

Write-Output ''
Write-Output '=== 2. KIADAS ==='
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
gh release create v1.63.9 (Join-Path $dir 'SuperDL.apk') (Join-Path $dir 'SuperDL-1.63.9.apk') --repo korosmezeydavid/SuperDL-Android --title "1.63.9 - Egy elakadt kerdes, es az egesz elhallgatott" --notes-file 'C:\Users\msn\Documents\SuperDL-Android\tools\relnotes1639.md'

Write-Output ''
Write-Output '=== 3. VERZIOFAJL ==='
$file = 'C:\Users\msn\Documents\SuperDL-Android\tools\verzio1639.json'
$bytes = [System.IO.File]::ReadAllBytes($file)
if ($bytes.Length -lt 100) { throw "A fajl gyanusan rovid." }
$b64 = [System.Convert]::ToBase64String($bytes)
$tmp = 'C:\Users\msn\Documents\SuperDL-Android\tools\_verzio1639_api.json'
$sha = (gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.sha')
$payload = @{
    message = "1.63.9 - youtube feloldas vedohalo, ket zsakutca szetvalasztva, diagnosztika a jelentesben"
    content = $b64
    sha     = $sha
    branch  = "mobil"
} | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText($tmp, $payload, (New-Object System.Text.UTF8Encoding($false)))
gh api -X PUT repos/korosmezeydavid/SuperDL/contents/verzio.json --input $tmp --jq '.commit.sha'
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Output 'KESZ.'
