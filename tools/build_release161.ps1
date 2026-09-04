Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
& .\gradlew.bat assembleRelease --console=plain *> 'C:\Users\msn\Documents\SuperDL-Android\build_rel161.txt'
"EXIT=$LASTEXITCODE" | Out-File -Append 'C:\Users\msn\Documents\SuperDL-Android\build_rel161.txt'
