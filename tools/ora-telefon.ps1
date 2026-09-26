$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$rcv = 'com.superdl.launcher.debug/com.superdl.launcher.patrol.TalkingClockDebugReceiver'
& $adb devices
& $adb install -r 'C:\Users\msn\Documents\SuperDL-Android\proba-apk\SuperDL-vasarlas-proba-debug.apk'
& $adb shell dumpsys package com.superdl.launcher.debug | Select-String 'versionName'
& $adb logcat -c
& $adb shell am broadcast -n $rcv --es mod letolt
Start-Sleep 25
& $adb logcat -d -s SuperDL.OraProba SuperDL.TalkingClock SuperDL.Catalog AndroidRuntime
