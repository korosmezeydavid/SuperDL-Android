chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\radio\RadioPlayerActivity.kt'
Write-Output "=== hibakezeles ==="
Select-String -Path $p -Pattern 'erheto|OnErrorListener|setOnError|onError|prepare|speak\(' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== RadioStation mezoi ==="
$s = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\radio\RadioStation.kt'
Get-Content -Path $s -Encoding UTF8 | ForEach-Object { Write-Output ("  " + $_) }
