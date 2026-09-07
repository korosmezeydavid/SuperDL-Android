$ErrorActionPreference = 'Stop'
$src = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL-1.57.0-release.apk'
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
Copy-Item $src (Join-Path $dir 'SuperDL.apk') -Force
Copy-Item $src (Join-Path $dir 'SuperDL-1.57.0.apk') -Force
Get-ChildItem $dir -Filter *.apk | Select-Object Name, Length | Format-Table -AutoSize
