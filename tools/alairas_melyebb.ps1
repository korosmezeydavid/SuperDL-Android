$ErrorActionPreference = 'Continue'
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Start-Transcript -Path 'C:\Users\msn\Documents\SuperDL-Android\tools\_alairas.log' -Force | Out-Null
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk"
$signer = "C:\Users\msn\AppData\Local\Android\Sdk\build-tools\37.0.0\apksigner.bat"

Write-Output "--- A: ellenorzes REGI Androidra is (min-sdk 21) ---"
Write-Output "Ha itt a v1 IGAZ, akkor a korabbi 'v1 hamis' csak annyit jelentett,"
Write-Output "hogy 26-os minSdk-nal az apksigner MEG SEM NEZI a v1-et."
& $signer verify --min-sdk-version 21 --verbose $apk 2>&1 | Select-String -Pattern "scheme|Verifies|WARNING|ERROR"

Write-Output ""
Write-Output "--- B: ellenorzes a VALODI celra (Android 14, api 34) ---"
& $signer verify --min-sdk-version 34 --max-sdk-version 34 --verbose $apk 2>&1 | Select-String -Pattern "scheme|Verifies|WARNING|ERROR"

Write-Output ""
Write-Output "--- C: van-e v4 kiseroallomany (.idsig) ---"
Get-ChildItem "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release" -Filter *.idsig -ErrorAction SilentlyContinue |
    Select-Object Name, Length

Write-Output ""
Write-Output "--- D: A KIADOTT FAJL UJJLENYOMATA (ezt kell a tesztelonek ellenoriznie) ---"
$h = Get-FileHash $apk -Algorithm SHA256
Write-Output ("SHA-256: " + $h.Hash)
Write-Output ("meret:   " + (Get-Item $apk).Length + " bajt")

Write-Output ""
Write-Output "--- E: UGYANEZ A GITHUBROL LETOLTOTT FAJLRA ---"
$tmp = "C:\Users\msn\Documents\SuperDL-Android\tools\_letoltott.apk"
try {
    Invoke-WebRequest -Uri 'https://github.com/korosmezeydavid/SuperDL-Android/releases/latest/download/SuperDL.apk' -OutFile $tmp -UseBasicParsing
    $h2 = Get-FileHash $tmp -Algorithm SHA256
    Write-Output ("SHA-256: " + $h2.Hash)
    Write-Output ("meret:   " + (Get-Item $tmp).Length + " bajt")
    if ($h.Hash -eq $h2.Hash) { Write-Output "EGYEZIK - a kiadott fajl ep." }
    else { Write-Output "NEM EGYEZIK - a kiadasban MAS fajl van, mint amit epitettunk!" }
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
} catch { Write-Output ("letoltes hiba: " + $_.Exception.Message) }
Stop-Transcript | Out-Null
