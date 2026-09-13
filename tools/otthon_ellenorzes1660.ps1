$ErrorActionPreference = 'Stop'
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
$apk = Join-Path $dir 'SuperDL.apk'
if (-not (Test-Path $apk)) { $apk = Join-Path $dir 'SuperDL-1.66.0-release.apk' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$enc = [System.Text.Encoding]::GetEncoding(28591)
$sb = New-Object System.Text.StringBuilder
foreach ($e in $zip.Entries) {
    if ($e.FullName -like '*.dex') {
        $s = $e.Open()
        $ms = New-Object System.IO.MemoryStream
        $s.CopyTo($ms)
        $s.Close()
        [void]$sb.Append($enc.GetString($ms.ToArray()))
        $ms.Close()
    }
}
$zip.Dispose()
$blob = $sb.ToString()
Write-Output ("DEX meret: " + $blob.Length)

# FIGYELEM: csak EKEZET NELKULI reszletek lehetnek a tuben!
$needles = @(
    'otthon_ujjlenyomat',
    'otthon_figyeles_be',
    'otthon_figyeles_proba',
    'otthon_figyeles_cimzettek',
    'Otthon betan',
    'Otthon-figyel',
    'llj meg otthon',
    'Visszasz',
    'LES m',
    'otthoni wifi h',
    'otthoni mobilcell',
    'automatikus',
    'HomeWatchReceiver',
    'HomeWatchService',
    'HomeWatchAlertActivity',
    'home_train',
    'home_forget'
)
$hiba = 0
foreach ($n in $needles) {
    if ($blob.Contains($n)) {
        Write-Output ("OK      : " + $n)
    } else {
        Write-Output ("HIANYZIK: " + $n)
        $hiba++
    }
}
Write-Output ''
if ($hiba -eq 0) { Write-Output 'EREDMENY: MINDEN RENDBEN' } else { Write-Output ("EREDMENY: " + $hiba + " HIANY") }
