chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
foreach ($d in @('calendar','macro','timer')) {
    $p = Join-Path $root $d
    Write-Output ("=== " + $d + " mappa ===")
    if (Test-Path $p) {
        Get-ChildItem -Path $p -Filter *.kt | ForEach-Object {
            Write-Output ("  " + $_.Name + "   " + [math]::Round($_.Length/1024) + " kB")
        }
    } else { Write-Output "  (nincs)" }
    Write-Output ""
}
Write-Output "=== makro vegrehajtas: mit tud most ==="
Get-ChildItem -Path (Join-Path $root 'macro') -Filter *.kt -ErrorAction SilentlyContinue |
  Select-String -Pattern 'fun run|fun execute|fun perform|MenuAction|enum class' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== naptar emlekezteto ==="
Get-ChildItem -Path (Join-Path $root 'calendar') -Filter *.kt -ErrorAction SilentlyContinue |
  Select-String -Pattern 'class |fun |AlarmManager|Reminder' |
  Select-Object -First 30 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
