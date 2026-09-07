chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
Write-Output "--- ADB DEVICES ---"
& $adb devices
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS csatlakoztatott keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()
Write-Output "Keszulek: $id"

Write-Output ""
Write-Output "--- ZAROLVA-E MEG ---"
& $adb -s $id shell "dumpsys trust | grep -i deviceLocked"

Write-Output ""
Write-Output "--- FUT-E A SUPERDL FOLYAMAT ---"
$p = & $adb -s $id shell "ps -A | grep superdl"
if ([string]::IsNullOrWhiteSpace($p)) { Write-Output "  NEM FUT" } else { Write-Output $p }

Write-Output ""
Write-Output "--- BEKOTOTT A11Y SZOLGALTATASOK ---"
& $adb -s $id shell "dumpsys accessibility | grep -i 'Bound services'" 2>&1 | Select-Object -First 6

Write-Output ""
Write-Output "--- CSOMAG DIRECT BOOT JELZOK ---"
& $adb -s $id shell "dumpsys package com.superdl.launcher.debug | grep -i 'directBoot'" 2>&1 | Select-Object -First 8

Write-Output ""
Write-Output "--- LOCKED_BOOT_COMPLETED PROBA ---"
& $adb -s $id shell "am broadcast -a android.intent.action.LOCKED_BOOT_COMPLETED -p com.superdl.launcher.debug" 2>&1 | Select-Object -First 4
Start-Sleep -Seconds 3
$p2 = & $adb -s $id shell "ps -A | grep superdl"
if ([string]::IsNullOrWhiteSpace($p2)) { Write-Output "  UTANA SEM FUT" } else { Write-Output $p2 }
Write-Output "KESZ."
