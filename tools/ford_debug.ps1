chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = 'utf-8'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
Start-Transcript -Path 'C:\Users\msn\Documents\SuperDL-Android\tools\ford_debug.log' -Force | Out-Null
& .\gradlew.bat assembleDebug --console=plain 2>&1 | Select-Object -Last 40
Write-Output ("EXIT=" + $LASTEXITCODE)
Stop-Transcript | Out-Null
