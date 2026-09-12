# YOUTUBE-DIAGNOSZTIKA ELLENORZESE A KESZ CSOMAGBAN
#
# MIERT KELL: az 1.63.6-os osszeomlas fordituskor nem latszott, csak a kesz
# kiadasi csomagban. Azota a szabaly: amit a tesztelonek szanunk, azt a DEX-ben
# kell viszontlatni, nem a forrasban.
#
# A sztringeket az R8 nem nevezi at, ezert a magyar mondatokra keresunk.

$ErrorActionPreference = 'Stop'
# A SuperDL.apk-t vizsgaljuk, mert PONTOSAN AZ megy fel a kiadasba. A
# verziozott peldany csak masolat; ha valaha eltavolodnanak egymastol, a
# kiadott fajlt kell ellenoriznunk, nem a masolatat.
$apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\SuperDL.apk'
if (-not (Test-Path $apk)) { throw "Nincs meg a kiadasi APK: $apk" }

$tmp = Join-Path $env:TEMP 'sdl_diag_1639'
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

# CSAK EKEZET NELKULI RESZLETEK. Ez a szkript maga is atmegy tobb
# kodolason (szerkeszto, PowerShell), es az ekezetek ott romlanak el,
# nem a DEX-ben. Elso korben pont ez adott hamis "HIANYZIK" valaszt.
$keresett = @(
    ' YOUTUBE-PR',
    'A YouTube most nem adott ki hangfolyamot',
    'Megvan a hang, de a YouTube elutas',
    'Megnyitom a rendes lej',
    'kimenetel: ',
    'elakadt ('
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
