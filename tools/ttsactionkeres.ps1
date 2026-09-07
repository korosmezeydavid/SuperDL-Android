chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main'
Write-Output "--- a HELYTELEN action-szoveg elofordulasai ---"
Get-ChildItem -Path $root -Include *.kt,*.xml -Recurse |
  Select-String -Pattern 'speech\.tts\.engine\.INTENT_ACTION_TTS_SERVICE' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- a HELYES action-szoveg elofordulasai ---"
Get-ChildItem -Path $root -Include *.kt,*.xml -Recurse |
  Select-String -Pattern 'intent\.action\.TTS_SERVICE' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- TtsEngineStore motor-lekerdezese ---"
Get-ChildItem -Path $root -Include *.kt -Recurse |
  Select-String -Pattern 'getEngines|queryIntentServices|TTS_SERVICE' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
