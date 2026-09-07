Remove-Item -Recurse -Force "C:\Users\msn\Documents\SuperDL-Android\tools\aar_peek" -ErrorAction SilentlyContinue
Remove-Item -Force "C:\Users\msn\Documents\SuperDL-Android\app\libs\whmobile-sources.jar" -ErrorAction SilentlyContinue
Remove-Item -Force "C:\Users\msn\Documents\SuperDL-Android\tools\aarapi.ps1" -ErrorAction SilentlyContinue
Remove-Item -Force "C:\Users\msn\Documents\SuperDL-Android\tools\gostat.ps1" -ErrorAction SilentlyContinue
Remove-Item -Force "C:\Users\msn\Documents\SuperDL-Android\tools\gowait.ps1" -ErrorAction SilentlyContinue
Remove-Item -Force "C:\Users\msn\Documents\SuperDL-Android\tools\getgo.ps1" -ErrorAction SilentlyContinue
Remove-Item -Force "C:\Users\msn\Documents\SuperDL-Android\tools\getgo.txt" -ErrorAction SilentlyContinue
Remove-Item -Force "C:\Users\msn\Documents\SuperDL-Android\tools\getgo2.txt" -ErrorAction SilentlyContinue
Remove-Item -Force "C:\Users\msn\Documents\SuperDL-Android\tools\aarwait.ps1" -ErrorAction SilentlyContinue
Write-Output "kesz"
Get-ChildItem "C:\Users\msn\Documents\SuperDL-Android\app\libs" | Select-Object Name,Length
