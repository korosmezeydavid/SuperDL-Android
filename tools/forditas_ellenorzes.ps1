$ErrorActionPreference = "Continue"
$gyoker = "C:\Users\msn\Documents\SuperDL-Android"
$naplo = Join-Path $gyoker "tools\forditas_ellenorzes.log"
Remove-Item $naplo -ErrorAction SilentlyContinue
Set-Location $gyoker
& cmd /c "gradlew.bat :app:compileDebugKotlin --console=plain 2>&1" | Out-File -FilePath $naplo -Encoding utf8
"KESZ exit=$LASTEXITCODE" | Out-File -FilePath $naplo -Append -Encoding utf8
