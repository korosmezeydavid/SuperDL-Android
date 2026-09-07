chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\currency'
Write-Output "=== hol toltjuk be a modelleket, es mi tortenik ha nincs ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'huf_banknote|loadMappedFile|assets\.open|catch|available|isAvailable|fallback' |
  Select-Object -First 40 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
