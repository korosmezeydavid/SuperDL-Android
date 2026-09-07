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

$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk'
Write-Output '--- 3. verzio es a JAVITOTT action a manifestben ---'
$aapt = Get-ChildItem 'C:\Users\msn\AppData\Local\Android\Sdk\build-tools' -Filter aapt2.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if ($aapt) {
    & $aapt.FullName dump badging $apk 2>$null | Select-String -Pattern "versionName" | Select-Object -First 1
    Write-Output '  queries action a lefordult manifestben:'
    & $aapt.FullName dump xmltree --file AndroidManifest.xml $apk 2>$null | Select-String -Pattern 'TTS_SERVICE' | ForEach-Object { Write-Output ("    " + $_.Line.Trim()) }
}

Write-Output '--- 4. hangok a KIADASI APK-ban ---'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$k = $zip.Entries | Where-Object { $_.FullName -like "assets/zarhang/*" }
Write-Output ("zarhang: $($k.Count) klip")
$e = $zip.Entries | Where-Object { $_.FullName -like "assets/hangtemak/elena/*" }
Write-Output ("Elena: $($e.Count) klip")
$zip.Dispose()
