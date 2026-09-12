# A KATALOGUS-JAVITAS ELLENORZESE A KESZ CSOMAGBAN
#
# MIERT KELL: az 1.63.6-os osszeomlas fordituskor nem latszott, csak a kesz
# kiadasi csomagban. Azota a szabaly: amit a tesztelonek szanunk, azt a DEX-ben
# kell viszontlatni.
#
# CSAK EKEZET NELKULI RESZLETEK: a szkript maga is atmegy tobb kodolason, es
# az ekezetek OTT romlanak el, nem a DEX-ben. Ez mar adott egyszer hamis
# "HIANYZIK" valaszt.

$ErrorActionPreference = 'Stop'
$apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\SuperDL.apk'
if (-not (Test-Path $apk)) { throw "Nincs meg a kiadasi APK: $apk" }

$tmp = Join-Path $env:TEMP 'sdl_kat_16310'
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
    'nincs a kiszolg',                       # 404 - a mi hibank
    'nem enged t',                           # 403 - varj par percet
    'nem v',                                 # idotullepes / valasz
    'Nincs internetkapcsolat. A m',          # halozat-ellenorzes elore
    'jabb Super DL kell',                    # minAppVersion
    'z, ez',                                 # "hibas, ezert nem tudom megnyitni"
    'dd le',                                 # "Toltsd le ujra"
    'daviszlek',                             # az atjaro ajanlata ("odaviszlek")
    'lok, ha k',                             # "Szolok, ha kesz"
    'g t',                                   # "Meg toltom"
    'modul let'                              # az ertesites szovege
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

Write-Host '--- a letolto szolgaltatas osztalya ---'
$aapt = Get-ChildItem 'C:\Users\msn\AppData\Local\Android\Sdk\build-tools' -Filter aapt2.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if ($aapt) {
    $tree = & $aapt.FullName dump xmltree --file AndroidManifest.xml $apk 2>$null
    $van = $tree | Select-String -Pattern 'CatalogDownloadService'
    if ($van) { Write-Host 'MEGVAN   CatalogDownloadService a manifestben' }
    else { Write-Host 'HIANYZIK CatalogDownloadService a manifestben'; $hiba = 1 }
}

Remove-Item $tmp -Recurse -Force
if ($hiba -ne 0) { Write-Host 'BUKOTT'; exit 1 }
Write-Host 'RENDBEN'
exit 0
