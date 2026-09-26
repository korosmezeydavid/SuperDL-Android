Set-Location C:\Users\msn\Documents\SuperDL-Android
"AG: " + (git rev-parse --abbrev-ref HEAD)
Remove-Item build-log.txt -ErrorAction SilentlyContinue
Start-Process cmd -ArgumentList '/c','gradlew.bat assembleDebug > build-log.txt 2>&1' -WindowStyle Hidden
'elinditva'
