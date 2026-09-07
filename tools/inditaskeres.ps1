chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\MainActivity.kt'
Write-Output "--- startFirstRunSetupIfNeeded hivasa es kornyeke ---"
Select-String -Path $m -Pattern 'startFirstRunSetupIfNeeded\(\)|override fun onCreate|override fun onResume' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
