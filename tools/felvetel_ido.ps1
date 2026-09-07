chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\radio'
Write-Output "=== idozitett felvetel hossza ==="
Get-ChildItem -Path $root -Filter *.kt |
  Select-String -Pattern 'DURATION|duration|perc|MAX_|hossz|180|3 ' |
  Select-Object -First 30 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
