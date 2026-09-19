$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.superdl.launcher.debug"
$f = "shared_prefs/superdl_setup.xml"

"--- jelenlegi allapot ---"
& $adb shell "run-as $pkg cat $f"

"--- wizard_done torlese (csak ez az egy sor) ---"
& $adb shell "run-as $pkg sed -i '/wizard_done/d' $f"
& $adb shell "am force-stop $pkg"

"--- utana ---"
& $adb shell "run-as $pkg cat $f"
