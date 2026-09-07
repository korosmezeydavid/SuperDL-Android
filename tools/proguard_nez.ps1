chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\proguard-rules.pro'
if (Test-Path $p) {
    Write-Output "=== proguard-rules.pro ==="
    Get-Content $p -Encoding UTF8 | ForEach-Object { Write-Output ("  " + $_) }
} else { Write-Output "NINCS proguard-rules.pro" }
Write-Output ""
Write-Output "=== a kiadasi APK-ban benne van-e a tflite es a modell ==="
$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$zip.Entries | Where-Object { $_.FullName -match 'tflite|tensorflow|libtensorflow' } |
  Select-Object -First 12 |
  ForEach-Object { Write-Output ("  " + $_.FullName + "   " + [math]::Round($_.Length/1MB,2) + " MB") }
$zip.Dispose()
