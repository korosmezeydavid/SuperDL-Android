chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "=== Hangiranytu forrasa ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'Hangiranytu|Hangiranytu|HANG_IRANYTU|AudioCompass|hangiranytu' |
  Select-Object -First 15 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== 'lekerdezes sikertelen' uzenetek ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'lekérdezés sikertelen|lekerdezes sikertelen' |
  Select-Object -First 15 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== Overpass / POI API hivasok ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'overpass|nominatim|photon|api\.openstreetmap' |
  Select-Object -First 15 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
