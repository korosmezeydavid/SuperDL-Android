Set-Location C:\Users\msn\Documents\SuperDL-Android
"AG: " + (git rev-parse --abbrev-ref HEAD)
Remove-Item test-log.txt -ErrorAction SilentlyContinue
Start-Process cmd -ArgumentList '/c','gradlew.bat testDebugUnitTest > test-log.txt 2>&1' -WindowStyle Hidden
'teszt elinditva'
