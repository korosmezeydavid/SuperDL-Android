$ErrorActionPreference = 'Stop'
$b64 = (gh api repos/korosmezeydavid/SuperDL/contents/mobil-katalogus.json?ref=mobil --jq '.content') -replace "`n",''
$bytes = [System.Convert]::FromBase64String($b64)
[System.Text.Encoding]::UTF8.GetString($bytes) | Out-File -FilePath 'C:\Users\msn\Documents\SuperDL-Android\tools\mobil-katalogus.json' -Encoding UTF8
Get-Content 'C:\Users\msn\Documents\SuperDL-Android\tools\mobil-katalogus.json'
