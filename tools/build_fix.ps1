$ErrorActionPreference = "Continue"
Set-Location "C:\Users\msn\Documents\SuperDL-Android"
$log = "C:\Users\msn\Documents\SuperDL-Android\tools\build_fix.log"
cmd /c "gradlew.bat assembleDebug --console=plain 2>&1" | Out-File -FilePath $log -Encoding UTF8
"EXIT=$LASTEXITCODE" | Out-File -FilePath $log -Append -Encoding UTF8
