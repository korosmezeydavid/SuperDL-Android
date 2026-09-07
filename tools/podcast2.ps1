chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\MainActivity.kt'
Write-Output "=== Podcast erintesek a MainActivity-ben ==="
Select-String -Path $m -Pattern 'Podcast' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
