# A YOUTUBE TAKAREKOS MOD OSSZEOMLASANAK ELLENORZESE.
#
# Geza jelentesebol (1.63.6 kiadasi):
#   NoClassDefFoundError: android/support/v4/util/ArrayMap
#   at android.support.v4.media.MediaMetadataCompat.<clinit>
#
# Ez a szkript a KIADASI APK dex-allomanyaiban keresi meg, hogy a ket
# osztaly tenylegesen BENNE VAN-E. Forditasi hiba nem volt akkor sem, amikor
# hianyoztak - csak futasidoben derult ki. Ezert nem eleg a fordulas: latni
# kell a lenyomatot a kesz csomagban.
$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk'
if (-not (Test-Path $apk)) { throw "Nincs meg a kiadasi APK: $apk" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)

# MIT KELL KERESNI — ES MIERT VALTOZOTT.
#
# Az elso valtozat az `android/support/v4/util/ArrayMap`-et kereste, mert a
# 1.63.6-os osszeomlas EZT hianyolta. Csakhogy az a REGI tamogato konyvtar
# osztalya volt. Az androidx.media ugyanezt a munkat az
# `androidx/collection/ArrayMap`-pel vegzi — a regi nevre tehat mar nincs is
# szukseg, es a hianya NEM hiba.
#
# Ezert nem a regi nevet keressuk, hanem azt, ami az UJ uton kell:
# a media-osztalyokat es az AndroidX gyujtemenyt.
$keresett = @(
    'androidx/collection/ArrayMap',
    'android/support/v4/media/MediaMetadataCompat',
    'android/support/v4/media/session/MediaSessionCompat'
)
$talalt = @{}
foreach ($k in $keresett) { $talalt[$k] = $false }

foreach ($e in $zip.Entries) {
    if ($e.FullName -notlike '*.dex') { continue }
    $ms = New-Object System.IO.MemoryStream
    $s = $e.Open()
    $s.CopyTo($ms)
    $s.Close()
    $bytes = $ms.ToArray()
    $ms.Close()
    # A dex a tipusneveket nyers ASCII-kent tarolja, ezert eleg szovegkent nezni.
    $szoveg = [System.Text.Encoding]::ASCII.GetString($bytes)
    foreach ($k in $keresett) {
        if (-not $talalt[$k] -and $szoveg.Contains($k)) { $talalt[$k] = $true }
    }
}
$zip.Dispose()

Write-Output '--- MEDIA OSZTALYOK A KIADASI APK-BAN ---'
$hiba = 0
foreach ($k in $keresett) {
    if ($talalt[$k]) {
        Write-Output ("    MEGVAN     " + $k)
    } else {
        Write-Output ("    HIANYZIK   " + $k)
        $hiba++
    }
}
if ($hiba -gt 0) {
    Write-Output ''
    Write-Output ">>> FIGYELEM: $hiba osztaly hianyzik. A takarekos mod ossze fog omlani."
} else {
    Write-Output ''
    Write-Output '>>> Rendben: mind a harom osztaly benne van a csomagban.'
}
