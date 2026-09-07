chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "--- getSharedPreferences elofordulasok fajlonkent ---"
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  ForEach-Object {
    $c = (Select-String -Path $_.FullName -Pattern 'getSharedPreferences' | Measure-Object).Count
    if ($c -gt 0) {
      $de = (Select-String -Path $_.FullName -Pattern 'createDeviceProtectedStorageContext' | Measure-Object).Count
      $jel = if ($de -gt 0) { "VEDETT" } else { "nyers " }
      Write-Output ("  " + $jel + "  " + $c + "  " + $_.FullName.Substring($root.Length + 1))
    }
  }
