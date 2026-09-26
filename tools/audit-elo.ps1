Set-Location C:\Users\msn\Documents\SuperDL-Android
"AG: " + (git rev-parse --abbrev-ref HEAD)
git archive --format=zip -o C:\Users\msn\Documents\audit-forras.zip vasarlas app/src/main app/src/debug app/src/test/kotlin app/build.gradle build.gradle settings.gradle
(Get-Item C:\Users\msn\Documents\audit-forras.zip).Length
Remove-Item lint-log.txt -ErrorAction SilentlyContinue
Start-Process cmd -ArgumentList '/c','gradlew.bat lintDebug > lint-log.txt 2>&1' -WindowStyle Hidden
'lint elinditva'
