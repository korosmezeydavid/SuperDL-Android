chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
$fajlok = @(
  'tts\TtsSettingsStore.kt','tts\TtsEngineStore.kt','tts\PronunciationDictionary.kt',
  'tts\VerbosityPrefs.kt','feedback\AlertSoundSettingsStore.kt','feedback\AlertSoundStore.kt',
  'feedback\DeviceStateStore.kt','feedback\GestureSoundHelper.kt','feedback\SoundThemeStore.kt',
  'gestures\GestureOrientation.kt','security\LockPinStore.kt','voicetheme\VoiceThemeStore.kt'
)
foreach ($f in $fajlok) {
    $p = Join-Path $root $f
    $sz = Get-Content -Path $p -Raw -Encoding UTF8
    $ered = $sz
    # ctx.getSharedPreferences(X, Context.MODE_PRIVATE)  ->  SafePrefs.get(ctx, X)
    $sz = [regex]::Replace($sz,
        '([A-Za-z_][A-Za-z0-9_]*)\.getSharedPreferences\(\s*([^,()]+?)\s*,\s*Context\.MODE_PRIVATE\s*\)',
        'com.superdl.launcher.storage.SafePrefs.get($1, $2)')
    if ($sz -ne $ered) {
        [System.IO.File]::WriteAllText($p, $sz, (New-Object System.Text.UTF8Encoding($false)))
        $maradt = ([regex]::Matches($sz, '\.getSharedPreferences\(')).Count
        Write-Output ("ATIRVA: " + $f + "   maradt=" + $maradt)
    } else {
        Write-Output ("valtozatlan: " + $f)
    }
}
