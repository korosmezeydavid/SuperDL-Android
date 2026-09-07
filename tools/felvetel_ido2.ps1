chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\MainActivity.kt'
Write-Output "=== hol allitja a felhasznalo a felvetel hosszat ==="
Select-String -Path $m -Pattern 'durationMinutes|RadioScheduleDuration|felvetel hossz' |
  Select-Object -First 20 |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
