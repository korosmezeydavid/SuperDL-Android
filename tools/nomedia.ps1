$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'

Write-Output "=== find mukodik-e egyaltalan a /sdcard-on ==="
& $adb shell "find /sdcard -type f 2>/dev/null | wc -l"

Write-Output ""
Write-Output "=== a .nomedia fajl adatai ==="
& $adb shell "ls -la /sdcard/Music/.nomedia"

Write-Output ""
Write-Output "=== van-e mashol is .nomedia ==="
& $adb shell "find /sdcard -name '.nomedia' 2>/dev/null"

Write-Output ""
Write-Output "=== MediaStore: hany hangfajl van a Music mappabol ==="
& $adb shell "content query --uri content://media/external/audio/media --projection _data 2>/dev/null | grep -ic '/Music/'"

Write-Output ""
Write-Output "=== MediaStore: elso 5 bejegyzes ==="
& $adb shell "content query --uri content://media/external/audio/media --projection _data 2>/dev/null | head -5"
