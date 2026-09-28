Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
& .\gradlew.bat assembleRelease --console=plain *> 'C:\Users\msn\Documents\SuperDL-Android\build_rel1860.txt'
"EXIT=$LASTEXITCODE" | Out-File -Append 'C:\Users\msn\Documents\SuperDL-Android\build_rel1860.txt'
$ErrorActionPreference = 'Stop'
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
$src = Join-Path $dir 'SuperDL-1.86.0-release.apk'
if (-not (Test-Path $src)) { throw "Nincs meg a kiadasi APK: $src" }
Get-ChildItem $dir -Filter 'SuperDL-1.85.0*.apk' | Remove-Item -Force -ErrorAction SilentlyContinue
Copy-Item $src (Join-Path $dir 'SuperDL.apk') -Force
Copy-Item $src (Join-Path $dir 'SuperDL-1.86.0.apk') -Force
Get-ChildItem $dir -Filter *.apk | Select-Object Name, Length | Format-Table -AutoSize | Out-File -Append 'C:\Users\msn\Documents\SuperDL-Android\build_rel1860.txt'
$as = Get-ChildItem 'C:\Users\msn\AppData\Local\Android\Sdk\build-tools' -Directory | Sort-Object Name -Descending | Select-Object -First 1
$apksigner = Join-Path $as.FullName 'apksigner.bat'
& $apksigner verify --print-certs (Join-Path $dir 'SuperDL.apk') *>> 'C:\Users\msn\Documents\SuperDL-Android\build_rel1860.txt'
('SHA256=' + (Get-FileHash (Join-Path $dir 'SuperDL.apk') -Algorithm SHA256).Hash) | Out-File -Append 'C:\Users\msn\Documents\SuperDL-Android\build_rel1860.txt'
'KESZ-BUILD' | Out-File -Append 'C:\Users\msn\Documents\SuperDL-Android\build_rel1860.txt'
