Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$log = Get-Content build-log.txt -Tail 3
$log
if (-not ($log -match 'BUILD SUCCESSFUL')) { Write-Output 'A forditas meg nem kesz vagy hibas.'; exit 1 }
if ((git branch --show-current) -ne 'vasarlas') { throw 'Nem a vasarlas agon vagyunk.' }
$apk = Get-ChildItem 'app\build\outputs\apk\debug\*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Copy-Item $apk.FullName 'proba-apk\SuperDL-vasarlas-proba-debug.apk' -Force
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb install -r 'proba-apk\SuperDL-vasarlas-proba-debug.apk'
# Vissza a masterre, es a master debug-APK ujraforditasa a hatterben.
git checkout master 2>&1 | Out-Null
Write-Output ('ag most: ' + (git branch --show-current))
Start-Process cmd -ArgumentList '/c','gradlew.bat assembleDebug > build-log.txt 2>&1' -WindowStyle Hidden
