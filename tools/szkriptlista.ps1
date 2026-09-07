chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Get-ChildItem 'C:\Users\msn\Documents\SuperDL-Android\tools' -Filter '*1632*' |
  ForEach-Object { Write-Output ("  " + $_.Name) }
Write-Output ""
Write-Output "--- verzio a build.gradle-ben ---"
Get-Content 'C:\Users\msn\Documents\SuperDL-Android\app\build.gradle' -Encoding UTF8 |
  Select-String -Pattern 'versionCode|versionName' |
  ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
