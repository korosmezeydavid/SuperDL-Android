chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\screenreader\ScreenReaderService.kt'
Select-String -Path $p -Pattern 'override fun |private fun startProximityWatch|unregisterReceiver|registerReceiver' | ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
