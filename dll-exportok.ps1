$path = "C:\Users\msn\Downloads\Addonbrailab\addon\TTS.dll"
$bytes = [System.IO.File]::ReadAllBytes($path)
$text = [System.Text.Encoding]::ASCII.GetString($bytes)
# Az export nevek olvashato ASCII szavak a DLL-ben.
$matches = [regex]::Matches($text, "[A-Za-z_][A-Za-z0-9_@\.]{3,40}")
$names = $matches | ForEach-Object { $_.Value } | Sort-Object -Unique
"--- gyanus fuggvenynevek ---"
$names | Where-Object { $_ -match "(?i)tts|speak|synth|voice|wav|pcm|buffer|init|start|stop|text|audio|play" } | Select-Object -First 60
"--- hivatkozott DLL-ek ---"
$names | Where-Object { $_ -match "(?i)\.dll$" } | Select-Object -First 30
