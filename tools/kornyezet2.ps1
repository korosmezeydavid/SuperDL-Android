chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\environment\EnvironmentScannerActivity.kt'
Write-Output "=== kamera bekotes es analizis ==="
Select-String -Path $p -Pattern 'bindToLifecycle|ImageAnalysis|setAnalyzer|ImageProxy|imageProxy|close\(\)|cameraProvider' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
