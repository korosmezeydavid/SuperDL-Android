$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell "run-as com.superdl.launcher.debug ls -R files"
"---- EXTERNAL ----"
& $adb shell "ls -R /sdcard/Android/data/com.superdl.launcher.debug/files"
