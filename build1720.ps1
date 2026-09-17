Set-Location "C:\Users\msn\Documents\SuperDL-Android"
$log = "C:\Users\msn\Documents\SuperDL-Android\build1720.log"
if (Test-Path $log) { Remove-Item $log -Force }
& ".\gradlew.bat" assembleDebug --console=plain *> $log
"EXITCODE=$LASTEXITCODE" | Out-File -Append -Encoding utf8 $log
