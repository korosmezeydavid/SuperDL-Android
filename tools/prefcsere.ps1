chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
# Csak azok a tarolok, amelyek az elso feloldas ELOTT is szoba johetnek:
# a beszedmotor, a hangvisszajelzes, a gesztusok es a zarolas kornyeke.
$fajlok = @(
  'tts\TtsSettingsStore.kt',
  'tts\TtsEngineStore.kt',
  'tts\PronunciationDictionary.kt',
  'tts\VerbosityPrefs.kt',
  'feedback\AlertSoundSettingsStore.kt',
  'feedback\AlertSoundStore.kt',
  'feedback\DeviceStateStore.kt',
  'feedback\GestureSoundHelper.kt',
  'feedback\SoundThemeStore.kt',
  'gestures\GestureOrientation.kt',
  'security\LockPinStore.kt',
  'voicetheme\VoiceThemeStore.kt'
)
foreach ($f in $fajlok) {
    $p = Join-Path $root $f
    if (-not (Test-Path $p)) { Write-Output ("HIANYZIK: " + $f); continue }
    $sz = Get-Content -Path $p -Raw -Encoding UTF8
    $db = ([regex]::Matches($sz, 'context\.getSharedPreferences\(')).Count
    $db2 = ([regex]::Matches($sz, '\.getSharedPreferences\(')).Count
    Write-Output ($f + "  osszes=" + $db2 + "  context.-es=" + $db)
}
