chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\transit\TransitHelper.kt'
Write-Output "=== jaratnev / akadalymentes / tipus ==="
Select-String -Path $p -Pattern 'akadálymentes|shortName|routeType|jarat|járat|type ==' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== gyalogos idobecsles kornyeke (400-440) ==="
$lines = Get-Content $p -Encoding UTF8
for ($j = 400; $j -lt 440; $j++) { if ($j -lt $lines.Count) { Write-Output ("  " + ($j+1) + ": " + $lines[$j]) } }
