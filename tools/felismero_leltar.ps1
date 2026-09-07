chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "=== felismero / vision modulok ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Where-Object { $_.FullName -match 'vision|recogn|felismer|camera|image|ocr|currency|braille' } |
  ForEach-Object { Write-Output ("  " + $_.FullName.Substring($root.Length+1) + "   " + [math]::Round($_.Length/1024) + " kB") }
Write-Output ""
Write-Output "=== jelenetleiras / scene menupontok ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'SCENE|DESCRIBE|LEIRAS|WHAT_IS' |
  Select-Object -First 20 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== vaku / torch hasznalat ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'FLASH_MODE|setTorch|torchMode|TORCH|FLASHLIGHT' |
  Select-Object -First 20 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
