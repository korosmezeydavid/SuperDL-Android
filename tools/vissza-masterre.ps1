Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
# A Vasarlas-proba APK-ja kulon helyre, hogy ki lehessen probalni.
$proba = 'C:\Users\msn\Documents\SuperDL-Android\proba-apk'
New-Item -ItemType Directory -Force $proba | Out-Null
$apk = Get-ChildItem 'app\build\outputs\apk\debug\*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Copy-Item $apk.FullName (Join-Path $proba 'SuperDL-vasarlas-proba-debug.apk') -Force
Write-Output ("proba APK: " + (Get-Item (Join-Path $proba 'SuperDL-vasarlas-proba-debug.apk')).Length)
# A munkakonyvtar a masterre: igy egy kiadas veletlenul sem viheti ki a titkot.
git checkout master 2>&1 | Out-Null
Write-Output ("ag most: " + (git branch --show-current))
Test-Path 'app\src\main\kotlin\com\superdl\launcher\offers'
