chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Write-Output "=== hol vannak a huf_banknote fajlok a forrasban ==="
Get-ChildItem -Path 'C:\Users\msn\Documents\SuperDL-Android\app\src' -Recurse -Filter 'huf_banknote*' -ErrorAction SilentlyContinue |
  ForEach-Object { Write-Output ("  " + $_.FullName + "   " + [math]::Round($_.Length/1MB,2) + " MB") }
Write-Output ""
Write-Output "=== forrasmappak (src) ==="
Get-ChildItem -Path 'C:\Users\msn\Documents\SuperDL-Android\app\src' -Directory |
  ForEach-Object { Write-Output ("  " + $_.Name) }
Write-Output ""
Write-Output "=== build.gradle: sourceSets / packaging / aapt ==="
Get-Content 'C:\Users\msn\Documents\SuperDL-Android\app\build.gradle' -Encoding UTF8 |
  Select-String -Pattern 'sourceSets|assets.srcDirs|packagingOptions|aaptOptions|noCompress|exclude|debug \{|release \{' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== .gitignore tartalmaz-e ilyet ==="
Select-String -Path 'C:\Users\msn\Documents\SuperDL-Android\.gitignore' -Pattern 'tflite|banknote|assets' -ErrorAction SilentlyContinue |
  ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
