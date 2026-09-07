$ErrorActionPreference = 'Stop'
$file = 'C:\Users\msn\Documents\SuperDL-Android\tools\verzio1633.json'
$bytes = [System.IO.File]::ReadAllBytes($file)
Write-Output ("Feltoltendo bajtok: " + $bytes.Length)
if ($bytes.Length -lt 100) { throw "A fajl gyanusan rovid, nem toltjuk fel." }
$b64 = [System.Convert]::ToBase64String($bytes)
$tmp = 'C:\Users\msn\Documents\SuperDL-Android\tools\_verzio1633_api.json'

$sha = (gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.sha')
Write-Output ("Regi sha: " + $sha)

$payload = @{
    message = "1.63.3 - Direct Boot osszeomlas javitasa, varazslo zsakutca, telefon-szerepkor"
    content = $b64
    sha     = $sha
    branch  = "mobil"
} | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText($tmp, $payload, (New-Object System.Text.UTF8Encoding($false)))

gh api -X PUT repos/korosmezeydavid/SuperDL/contents/verzio.json --input $tmp --jq '.commit.sha'
Remove-Item $tmp -Force -ErrorAction SilentlyContinue
