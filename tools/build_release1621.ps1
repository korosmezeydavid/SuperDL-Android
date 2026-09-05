Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
& .\gradlew.bat assembleRelease --console=plain *> 'C:\Users\msn\Documents\SuperDL-Android\build_rel1621.txt'
"EXIT=$LASTEXITCODE" | Out-File -Append 'C:\Users\msn\Documents\SuperDL-Android\build_rel1621.txt'
