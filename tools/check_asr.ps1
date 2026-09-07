$ErrorActionPreference = "SilentlyContinue"
Write-Output "--- PYTHON ---"
python --version
where.exe python
Write-Output "--- PIP CSOMAGOK ---"
pip list | Select-String -Pattern "whisper|vosk|torch|ctranslate"
Write-Output "--- HF CACHE ---"
Get-ChildItem "$env:USERPROFILE\.cache\huggingface\hub" -Directory | Select-Object -ExpandProperty Name
Write-Output "--- WHISPER EXE ---"
where.exe whisper
where.exe whisper-cli
where.exe main.exe
Write-Output "--- HALO TESZT ---"
try {
  $r = Invoke-WebRequest -Uri "https://huggingface.co" -Method Head -TimeoutSec 20 -UseBasicParsing
  Write-Output ("huggingface.co = " + $r.StatusCode)
} catch { Write-Output ("huggingface.co HIBA: " + $_.Exception.Message) }
