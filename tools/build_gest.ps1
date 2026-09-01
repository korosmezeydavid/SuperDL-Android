Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
& .\gradlew.bat assembleDebug --console=plain *> 'C:\Users\msn\Documents\SuperDL-Android\build_gest.txt'
"EXIT=$LASTEXITCODE" | Out-File -Append 'C:\Users\msn\Documents\SuperDL-Android\build_gest.txt'
