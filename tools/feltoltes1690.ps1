$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\feltoltes1690.log'
"START $(Get-Date -Format o)" | Out-File $log -Encoding utf8

gh release upload v1.69.0 (Join-Path $dir 'SuperDL.apk') --repo korosmezeydavid/SuperDL-Android --clobber 2>&1 |
    Out-File $log -Append -Encoding utf8
"APK1 kesz $(Get-Date -Format o)" | Out-File $log -Append -Encoding utf8

gh release upload v1.69.0 (Join-Path $dir 'SuperDL-1.69.0.apk') --repo korosmezeydavid/SuperDL-Android --clobber 2>&1 |
    Out-File $log -Append -Encoding utf8
"APK2 kesz $(Get-Date -Format o)" | Out-File $log -Append -Encoding utf8

gh release edit v1.69.0 --repo korosmezeydavid/SuperDL-Android --draft=false 2>&1 |
    Out-File $log -Append -Encoding utf8
"KOZZETEVE $(Get-Date -Format o)" | Out-File $log -Append -Encoding utf8
