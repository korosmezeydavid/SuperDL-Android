Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
& .\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --console=plain *> 'C:\Users\msn\Documents\SuperDL-Android\tools\_fuggosegek.txt'
"EXIT=$LASTEXITCODE" | Out-File -Append 'C:\Users\msn\Documents\SuperDL-Android\tools\_fuggosegek.txt'
