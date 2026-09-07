chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\MainActivity.kt'
Write-Output "=== hol epul fel a hibajelentes ==="
Select-String -Path $m -Pattern 'SetupDiagnostics.build|HIBAJELENTES|HIBAJELENTÉS|bugReport|BugReport|buildBugReport' |
  Select-Object -First 30 |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
