# AZ MK-V M0 ES M1 ELLENORZESE A KESZ CSOMAGBAN
#
# CSAK EKEZET NELKULI RESZLETEK: a szkript maga is atmegy tobb kodolason, es
# az ekezetek OTT romlanak el, nem a DEX-ben. Ez mar ketszer adott hamis
# "HIANYZIK" valaszt.

$ErrorActionPreference = 'Stop'
$apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\SuperDL.apk'
if (-not (Test-Path $apk)) { throw "Nincs meg a kiadasi APK: $apk" }

$tmp = Join-Path $env:TEMP 'sdl_mkv_1640'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Path $tmp | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
foreach ($e in $zip.Entries) {
    if ($e.FullName -match '^classes\d*\.dex$') {
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $tmp $e.FullName), $true)
    }
}
$zip.Dispose()

$keresett = @(
    # --- M1: adott napokon ismetlodo ebreszto ---
    'Mely napokon sz',              # a nap-kijelolo belepoje
    'Legal',                        # "Legalabb egy napot valassz ki"
    # FIGYELEM: "Osszesen" NEM jo minta - a szovegben "Osszesen" O-UMLAUTTAL
    # van. Ugyanez a csapda mar harmadszor: a mintabol is ki kell hagyni
    # MINDEN ekezetes betut, nem csak a szoveg kozepen levoket.
    'sszesen ',                     # a futo osszeg ("Osszesen 3 nap")
    'kijel',                        # "kijelolve" / "kijeloles torolve"
    # --- M0: igazmondo kuldes ---
    'sem ment el',                  # "megsem ment el"
    'Utols',                        # "Utolso uzenet sorsa" menupont
    'rkezett a c',                  # "megerkezett a cimzetthez"
    'g nem jelezte vissza',         # kezbesites meg nincs
    'l sem ind',                    # "el sem inditotta a kuldest"
    'g nem k'                       # "Meg nem kuldtel uzenetet"
)

$talalt = @{}
foreach ($k in $keresett) { $talalt[$k] = $false }

Get-ChildItem $tmp -Filter '*.dex' | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    foreach ($k in $keresett) {
        if ($text.Contains($k)) { $talalt[$k] = $true }
    }
}

$hiba = 0
foreach ($k in $keresett) {
    if ($talalt[$k]) { Write-Host ("MEGVAN   " + $k) }
    else { Write-Host ("HIANYZIK " + $k); $hiba = 1 }
}

Remove-Item $tmp -Recurse -Force
if ($hiba -ne 0) { Write-Host 'BUKOTT'; exit 1 }
Write-Host 'RENDBEN'
exit 0
