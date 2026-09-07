$p = 'C:\Users\msn\Documents\SuperDL-Android\build_gest.txt'
$lines = Get-Content $p
for ($i = 0; $i -lt $lines.Count; $i++) {
  if ($lines[$i] -match '^e: ') {
    Write-Output ("--- " + $i + " ---")
    Write-Output $lines[$i]
    if ($i + 1 -lt $lines.Count) { Write-Output $lines[$i + 1] }
    if ($i + 2 -lt $lines.Count) { Write-Output $lines[$i + 2] }
  }
}
