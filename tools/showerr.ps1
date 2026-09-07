$t = Get-Content -Raw C:\Users\msn\Documents\SuperDL-Android\build_gest.txt
$t = $t -replace [char]0, ''
$t = $t -replace "`r", ''
$t = $t -replace "`n", ' '
$t = $t -replace ' +', ' '
$i = 0
while ($true) {
  $p = $t.IndexOf('e: file', $i)
  if ($p -lt 0) { break }
  $seg = $t.Substring($p, [Math]::Min(400, $t.Length - $p))
  Write-Output ('---- ' + $seg)
  $i = $p + 7
}
