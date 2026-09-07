chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\textreader\TextReaderActivity.kt'
Write-Output "=== mezok es analizis ==="
Select-String -Path $p -Pattern 'private var imageAnalysis|private val cameraExecutor|inner class FrameAnalyzer|override fun analyze|override fun onDestroy|imageProxy' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
