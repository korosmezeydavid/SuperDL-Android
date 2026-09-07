$b64 = (gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.content') -replace "`n",''
$txt = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($b64))
Write-Output "--- ami TENYLEG kint van a mobil agon ---"
Write-Output $txt
