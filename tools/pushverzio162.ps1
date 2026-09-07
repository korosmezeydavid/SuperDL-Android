$ErrorActionPreference = 'Stop'
# ABSZOLUT UT. Egy korabbi kiadasnal relativ ut miatt URES verzio.json kerult ki
# az eles frissites-ellenorzo fajlba. Ezt ne rontsuk el megegyszer.
$file = 'C:\Users\msn\Documents\SuperDL-Android\tools\verzio162.json'
$bytes = [System.IO.File]::ReadAllBytes($file)
Write-Output ("Feltoltendo bajtok: " + $bytes.Length)
if ($bytes.Length -lt 100) { throw "A fajl gyanusan rovid, nem toltjuk fel." }
$b64 = [System.Convert]::ToBase64String($bytes)

$sha = (gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.sha')
Write-Output ("Regi sha: " + $sha)

gh api -X PUT repos/korosmezeydavid/SuperDL/contents/verzio.json `
  -f message="1.62.0 - Hibajelentes a varazslobol, halkithato beszed" `
  -f content=$b64 `
  -f sha=$sha `
  -f branch=mobil --jq '.commit.sha'
