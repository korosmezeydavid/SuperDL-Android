chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main'
Write-Output "=== environment mappa ==="
Get-ChildItem -Path (Join-Path $root 'kotlin\com\superdl\launcher\environment') -Filter *.kt |
  ForEach-Object { Write-Output ("  " + $_.Name + "   " + [math]::Round($_.Length/1024) + " kB") }
Write-Output ""
Write-Output "=== milyen modellt tolt be ==="
Get-ChildItem -Path (Join-Path $root 'kotlin\com\superdl\launcher\environment') -Filter *.kt |
  Select-String -Pattern 'tflite|\.task|assets|loadModel|Interpreter|ObjectDetector|modelName|MODEL' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== milyen modell-fajlok vannak az assetsben ==="
Get-ChildItem -Path (Join-Path $root 'assets') -Recurse -Include *.tflite,*.task,*.pt,*.onnx -ErrorAction SilentlyContinue |
  ForEach-Object { Write-Output ("  " + $_.FullName.Substring((Join-Path $root 'assets').Length+1) + "   " + [math]::Round($_.Length/1MB,1) + " MB") }
Write-Output ""
Write-Output "=== hibauzenetek a szkennerben ==="
Select-String -Path (Join-Path $root 'kotlin\com\superdl\launcher\environment\EnvironmentScannerActivity.kt') -Pattern 'speak\(|Log\.|catch' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
