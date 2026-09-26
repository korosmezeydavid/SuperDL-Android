Set-Location C:\Users\msn\Documents\SuperDL-Android
"AG: " + (git rev-parse --abbrev-ref HEAD)
Remove-Item lint-log.txt -ErrorAction SilentlyContinue
Start-Process cmd -ArgumentList '/c','gradlew.bat lintDebug > lint-log.txt 2>&1' -WindowStyle Hidden
'lint elinditva'
