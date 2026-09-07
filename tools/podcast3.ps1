chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\MainActivity.kt'
Write-Output "=== voiceInput orok / guardok ==="
Select-String -Path $m -Pattern 'voiceInput\.isListening|voiceInput\.isActive|isListening|Varj|Várd|Várj|folyamatban' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== PodcastListBrowse aktivalas ==="
Select-String -Path $m -Pattern 'is AppFlow.PodcastListBrowse' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== voiceInput.cancel hivasok ==="
Select-String -Path $m -Pattern 'voiceInput\.cancel' |
  Select-Object -First 20 |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
