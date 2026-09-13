$ErrorActionPreference = 'Stop'
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
$apk = Join-Path $dir 'SuperDL.apk'
if (-not (Test-Path $apk)) { $apk = Join-Path $dir 'SuperDL-1.67.0-release.apk' }
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

# FIGYELEM: csak EKEZET NELKULI reszletek, a KODBOL kimasolva!
$needles = @(
    'szovegtar_schema',
    'szovegtar_migralva',
    'superdl_text_bank',
    'Sablon k',
    'Gombokra k',
    'kinek k',
    'Bet',
    'kukac',
    'alulvon',
    '/textbank',
    '/textbank/save',
    '/textbank/delete',
    '/textbank/up',
    'nincs gombhoz k',
    'Sablonjaim (',
    'Betelt a sz',
    'a telefonon t'
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

# A NAVIGACIO KET HELYEN VAN. Ha csak az egyikbe kerul be a ful, a fooldalrol
# nem erheto el - ezt a projekt doksija kulon nevesiti mint csapdat.
$p1 = Get-Content 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\files\PortalControlPages.kt' -Raw
$p2 = Get-Content 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\files\WifiPortalServer.kt' -Raw
if ($p1 -match 'textbank') { Write-Output 'OK      : nav 1 (PortalControlPages)' } else { Write-Output 'HIANYZIK: nav 1'; $hiba++ }
if ($p2 -match 'href="/textbank"') { Write-Output 'OK      : nav 2 (buildIndexPage)' } else { Write-Output 'HIANYZIK: nav 2'; $hiba++ }

# A mentesbol eddig kimaradt a szovegtar.
$b = Get-Content 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\backup\BackupManager.kt' -Raw
if ($b -match 'superdl_text_bank') { Write-Output 'OK      : mentes tartalmazza a szovegtarat' } else { Write-Output 'HIANYZIK: mentes'; $hiba++ }

Write-Output ''
if ($hiba -eq 0) { Write-Output 'EREDMENY: MINDEN RENDBEN' } else { Write-Output ("EREDMENY: " + $hiba + " HIANY") }
