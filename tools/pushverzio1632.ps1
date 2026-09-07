$ErrorActionPreference = 'Stop'
$file = 'C:\Users\msn\Documents\SuperDL-Android\tools\verzio1632.json'
$bytes = [System.IO.File]::ReadAllBytes($file)
Write-Output ("Feltoltendo bajtok: " + $bytes.Length)
if ($bytes.Length -lt 100) { throw "A fajl gyanusan rovid, nem toltjuk fel." }
$b64 = [System.Convert]::ToBase64String($bytes)

$sha = (gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.sha')
Write-Output ("Regi sha: " + $sha)

gh api -X PUT repos/korosmezeydavid/SuperDL/contents/verzio.json `
  -f message="1.63.2 - Direct Boot hang a zarkepernyon, SMS-szerepkor felismerese" `
  -f content=$b64 `
  -f sha=$sha `
  -f branch=mobil --jq '.commit.sha'
