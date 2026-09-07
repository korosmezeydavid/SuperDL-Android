chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($apk in @(
  'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk',
  'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.63.4-debug.apk'
)) {
    Write-Output ("=== " + (Split-Path $apk -Leaf) + " ===")
    if (-not (Test-Path $apk)) { Write-Output "  (nincs meg)"; continue }
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
    $m = $zip.Entries | Where-Object { $_.FullName -match 'mobilenet' }
    if ($m) { $m | ForEach-Object { Write-Output ("  MEGVAN: " + $_.FullName + "  " + [math]::Round($_.Length/1MB,2) + " MB") } }
    else { Write-Output "  >>> NINCS BENNE mobilenet modell <<<" }
    $gyoker = $zip.Entries | Where-Object { $_.FullName -match '^assets/[^/]+$' }
    Write-Output ("  assets gyoker-fajlok: " + ($gyoker | Measure-Object).Count)
    $gyoker | Select-Object -First 12 | ForEach-Object { Write-Output ("     " + $_.FullName) }
    $zip.Dispose()
    Write-Output ""
}
