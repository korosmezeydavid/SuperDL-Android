$ErrorActionPreference = 'Stop'
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
$src = Join-Path $dir 'SuperDL-1.63.10-release.apk'
if (-not (Test-Path $src)) { throw "Nincs meg a kiadasi APK: $src" }
Get-ChildItem $dir -Filter 'SuperDL-1.63.9*.apk' | Remove-Item -Force -ErrorAction SilentlyContinue
Copy-Item $src (Join-Path $dir 'SuperDL.apk') -Force
Copy-Item $src (Join-Path $dir 'SuperDL-1.63.10.apk') -Force
Get-ChildItem $dir -Filter *.apk | Select-Object Name, Length | Format-Table -AutoSize
Write-Output "--- SHA-256 ---"
(Get-FileHash (Join-Path $dir 'SuperDL.apk') -Algorithm SHA256).Hash
Write-Output "--- pontos meret bajtban ---"
(Get-Item (Join-Path $dir 'SuperDL.apk')).Length
