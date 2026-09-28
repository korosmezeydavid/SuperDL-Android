$ErrorActionPreference = 'Continue'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$out = 'C:\Users\msn\Documents\SuperDL-Android\kiadas1860-log.txt'
function L($t) { $t | Out-File -Append $out }
L '=== 2. KIADAS (folytatas) ==='
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
cmd /c "gh release create v1.86.0 `"$dir\SuperDL.apk`" `"$dir\SuperDL-1.86.0.apk`" --repo korosmezeydavid/SuperDL-Android --title `"1.86.0 - Akcios ujsag 12 bolttal, beszelo ora, nema mod, alkalmazas szerinti fokusz, atfogo hibajavitas`" --notes-file tools\relnotes1860.md >> kiadas1860-log.txt 2>&1"
L "gh exit: $LASTEXITCODE"
if ($LASTEXITCODE -ne 0) { L 'KIADAS HIBA - verziofajlt NEM frissitem'; exit 1 }
L '=== 3. VERZIOFAJL ==='
$file = 'C:\Users\msn\Documents\SuperDL-Android\tools\verzio1860.json'
$bytes = [System.IO.File]::ReadAllBytes($file)
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
$c = gh api -X PUT repos/korosmezeydavid/SuperDL/contents/verzio.json --input $tmp --jq '.commit.sha'
L "verzio commit: $c"
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
L 'KESZ.'
