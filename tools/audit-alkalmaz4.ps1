Set-Location C:\Users\msn\Documents\SuperDL-Android
"AG: " + (git rev-parse --abbrev-ref HEAD)
git apply --3way proba-apk\audit-javitasok-4.patch 2>&1 | Select-String -NotMatch 'cleanly|LF will' | Select -Last 15
git diff --name-only --diff-filter=U
git diff --stat HEAD | Select -Last 1
Remove-Item build-log.txt -ErrorAction SilentlyContinue
Start-Process cmd -ArgumentList '/c','gradlew.bat assembleDebug > build-log.txt 2>&1' -WindowStyle Hidden
'build elinditva'
