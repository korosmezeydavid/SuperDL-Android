chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'

Write-Output "=== 1. GYALOGOS IDOBECSLES (sebesseg) ==="
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern 'gyalog|walkSpeed|WALK_SPEED|perc.*km|km.*perc|5\.0f|1\.4|4\.5' |
  Select-String -Pattern 'sebess|speed|perc|MPS|kmh' |
  Select-Object -First 20 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }

Write-Output ""
Write-Output "=== 2. METRO / M2 megnevezes ==="
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern '"M1"|"M2"|"M3"|"M4"|metro|metró' |
  Select-Object -First 20 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }

Write-Output ""
Write-Output "=== 3. FAJLKEZELO: visszalepes / pozicio ==="
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern 'FileBrowse|fileBrowse|fajlkezelo' |
  Select-Object -First 15 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
