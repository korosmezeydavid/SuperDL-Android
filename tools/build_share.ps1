$ErrorActionPreference = "Continue"
Set-Location "C:\Users\msn\Documents\SuperDL-Android"
$log = "C:\Users\msn\Documents\SuperDL-Android\tools\build_share.txt"
cmd /c ".\gradlew.bat assembleDebug --console=plain 2>&1" | Out-File -FilePath $log -Encoding utf8
"EXIT=$LASTEXITCODE" | Out-File -FilePath $log -Encoding utf8 -Append
