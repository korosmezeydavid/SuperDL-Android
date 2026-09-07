# MI TORTENT A TELEPITESNEL — a rendszer naplojabol
$ErrorActionPreference = 'Continue'
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Start-Transcript -Path 'C:\Users\msn\Documents\SuperDL-Android\tools\_telepnaplo.log' -Force | Out-Null

$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$id = "S62Pro2208000115"

Write-Output "--- A TELEPITES NAPLOJA ---"
& $adb -s $id logcat -d 2>&1 |
    Select-String -Pattern "PackageInstaller|PackageManager|InstallStaging|INSTALL_FAILED|installPackage|verification|PackageParser|ApkSignature|IntentResolver|Verifier" |
    Select-Object -Last 60

Write-Output ""
Write-Output "--- OSSZEOMLASOK ---"
& $adb -s $id logcat -d -s AndroidRuntime:E 2>&1 | Select-Object -Last 20

Write-Output ""
Write-Output "--- JELENLEGI ALLAPOT ---"
& $adb -s $id shell dumpsys package com.superdl.launcher 2>&1 |
    Select-String -Pattern "versionName|versionCode|signatures|firstInstallTime|lastUpdateTime|installerPackageName" |
    Select-Object -First 10
Stop-Transcript | Out-Null
