chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\camera\FaceCameraActivity.kt'
Write-Output "=== ImageCapture felepitese ==="
Select-String -Path $p -Pattern 'ImageCapture|flashMode|FLASH_MODE|imageCapture' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
