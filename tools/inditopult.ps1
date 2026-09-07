chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$apk = (Get-ChildItem -Path 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug' -Filter *.apk |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1)

Write-Output "--- TELEPITES ---"
& $adb install -r $apk.FullName 2>&1 | Select-Object -Last 2

Write-Output ""
Write-Output "--- MEGJELENIK-E AZ ALKALMAZASLISTABAN (LAUNCHER) ---"
& $adb shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER" 2>&1 |
    Select-String -Pattern 'superdl'

Write-Output ""
Write-Output "--- KEZDOKEPERNYOKENT (HOME) ---"
& $adb shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME" 2>&1 |
    Select-String -Pattern 'superdl'
Write-Output "KESZ."
