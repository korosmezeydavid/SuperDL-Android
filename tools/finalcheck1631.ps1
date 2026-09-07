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
gh release view v1.63.1 --repo korosmezeydavid/SuperDL-Android --json assets |
    ConvertFrom-Json | ForEach-Object { $_.assets } |
    ForEach-Object { Write-Output ($_.name + "  " + $_.size + " byte") }

Write-Output '--- 4. a katalogus es az elso beszedtema ---'
try {
    $k = Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/korosmezeydavid/SuperDL/mobil/mobil-katalogus.json' -UseBasicParsing
    Write-Output ("katalogus HTTP " + $k.StatusCode + "  " + $k.RawContentLength + " byte")
    $t = Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/korosmezeydavid/SuperDL/mobil/temak/tema-elena.json' -Method Head -UseBasicParsing
    Write-Output ("tema-elena HTTP " + $t.StatusCode + "  meret: " + $t.Headers['Content-Length'])
} catch { Write-Output ("HIBA: " + $_.Exception.Message) }
