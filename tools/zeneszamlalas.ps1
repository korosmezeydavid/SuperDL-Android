$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'

Write-Output "=== Osszes hangfajl a fo tarhelyen ==="
& $adb shell "find /sdcard -type f 2>/dev/null | grep -icE '[.](mp3|m4a|aac|wav|ogg|opus|flac|wma)$'"

Write-Output ""
Write-Output "=== Mappankent, darabszam szerint (elso 25) ==="
& $adb shell "find /sdcard -type f 2>/dev/null | grep -iE '[.](mp3|m4a|aac|wav|ogg|opus|flac|wma)$' | sed 's|/[^/]*$||' | sort | uniq -c | sort -rn | head -25"

Write-Output ""
Write-Output "=== A Music mappan belul, kiterjesztes szerint ==="
& $adb shell "find /sdcard/Music -type f 2>/dev/null | sed 's|.*[.]||' | tr 'A-Z' 'a-z' | sort | uniq -c | sort -rn | head -15"

Write-Output ""
Write-Output "=== MediaStore szerint (amit a rendszer zenenek lat) ==="
& $adb shell "content query --uri content://media/external/audio/media --projection _id 2>/dev/null | wc -l"
