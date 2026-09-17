$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$file = 'C:\Users\msn\Documents\SuperDL-Android\tools\verzio1690.json'
$bytes = [System.IO.File]::ReadAllBytes($file)
if ($bytes.Length -lt 100) { throw "A fajl gyanusan rovid." }
$b64 = [System.Convert]::ToBase64String($bytes)
$tmp = 'C:\Users\msn\Documents\SuperDL-Android\tools\_verzio1690_api.json'
$sha = (gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.sha')
$payload = @{
    message = "1.69.0 - video, hangcimkek, emlekeztetok, zene-hangoskonyv, varazslo"
    content = $b64
    sha     = $sha
    branch  = "mobil"
} | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText($tmp, $payload, (New-Object System.Text.UTF8Encoding($false)))
gh api -X PUT repos/korosmezeydavid/SuperDL/contents/verzio.json --input $tmp --jq '.commit.sha'
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
Write-Output '--- ellenorzes ---'
$b = gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.content'
$txt = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String(($b -replace '\s','')))
($txt | ConvertFrom-Json).verzio
