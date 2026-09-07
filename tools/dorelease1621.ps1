$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release'
$title = (Get-Content 'C:\Users\msn\Documents\SuperDL-Android\tools\reltitle1621.txt' -Encoding UTF8 -Raw).Trim()
gh release create v1.62.1 (Join-Path $dir 'SuperDL.apk') (Join-Path $dir 'SuperDL-1.62.1.apk') --repo korosmezeydavid/SuperDL-Android --title $title --notes-file 'C:\Users\msn\Documents\SuperDL-Android\tools\relnotes1621.md'
