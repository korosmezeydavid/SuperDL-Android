chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "=== minden hely, ahol bindToLifecycle van ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'bindToLifecycle' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== amelyik mar hasznal torchot ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'TorchController|enableTorch' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
