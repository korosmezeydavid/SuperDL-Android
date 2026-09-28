$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$out = 'C:\Users\msn\Documents\SuperDL-Android\kiadas1860-log.txt'
function L($t) { $t | Out-File -Append $out }
L '=== 1. TITOK-SZKEN (URESNEK kell lennie) ==='
$bad = git log --name-only --pretty=format: origin/master..master | Select-String -Pattern 'keystore|ably_key|local.properties|akcio_minta/'
if ($bad) { L "TILTOTT FAJL: $bad"; throw 'TITOK-SZKEN BUKOTT' }
L 'ures - rendben'
git log --oneline origin/master..master | Out-File -Append $out
git push origin master *>> $out
L '=== 2. KIADAS ==='
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
gh release create v1.86.0 (Join-Path $dir 'SuperDL.apk') (Join-Path $dir 'SuperDL-1.86.0.apk') --repo korosmezeydavid/SuperDL-Android --title "1.86.0 - Akcios ujsag 12 bolttal, beszelo ora, nema mod, alkalmazas szerinti fokusz, atfogo hibajavitas" --notes-file 'C:\Users\msn\Documents\SuperDL-Android\tools\relnotes1860.md' *>> $out
L '=== 3. VERZIOFAJL ==='
$file = 'C:\Users\msn\Documents\SuperDL-Android\tools\verzio1860.json'
$bytes = [System.IO.File]::ReadAllBytes($file)
if ($bytes.Length -lt 100) { throw "A fajl gyanusan rovid." }
$b64 = [System.Convert]::ToBase64String($bytes)
$tmp = 'C:\Users\msn\Documents\SuperDL-Android\tools\_verzio1860_api.json'
$sha = (gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.sha')
$payload = @{
    message = "1.86.0 - akcios ujsag 12 bolttal, beszelo ora, nema mod, alkalmazas szerinti fokusz, atfogo hibajavitas"
    content = $b64
    sha     = $sha
    branch  = "mobil"
} | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText($tmp, $payload, (New-Object System.Text.UTF8Encoding($false)))
gh api -X PUT repos/korosmezeydavid/SuperDL/contents/verzio.json --input $tmp --jq '.commit.sha' *>> $out
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
L 'KESZ.'
