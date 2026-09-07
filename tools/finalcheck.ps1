Write-Output '--- 1. kodolas-proba: a mobil agon levo verzio.json ---'
$b64 = (gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.content') -replace "`n",''
$bytes = [System.Convert]::FromBase64String($b64)
$utf8strict = New-Object System.Text.UTF8Encoding($false, $true)
try {
    $j = $utf8strict.GetString($bytes) | ConvertFrom-Json
    Write-Output ("Ervenyes UTF-8. verzio = " + $j.verzio + " / kiadva " + $j.kiadva)
} catch { Write-Output ("KODOLASI HIBA: " + $_.Exception.Message) }

Write-Output '--- 2. a tesztelo letolto-linkje ---'
try {
    $h = Invoke-WebRequest -Uri 'https://github.com/korosmezeydavid/SuperDL-Android/releases/latest/download/SuperDL.apk' -Method Head -UseBasicParsing
    Write-Output ("HTTP " + $h.StatusCode + "  meret: " + $h.Headers['Content-Length'])
} catch { Write-Output ("HIBA: " + $_.Exception.Message) }

Write-Output '--- 3. a kiadas fajljai ---'
gh release view v1.58.0 --repo korosmezeydavid/SuperDL-Android --json assets |
    ConvertFrom-Json | ForEach-Object { $_.assets } |
    ForEach-Object { Write-Output ($_.name + "  " + $_.size + " byte") }
