chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Get-ChildItem -Path 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs' -Filter *.apk -Recurse |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 6 |
  ForEach-Object { Write-Output ($_.LastWriteTime.ToString('HH:mm:ss') + "  " + [math]::Round($_.Length/1MB,1) + " MB  " + $_.FullName) }
