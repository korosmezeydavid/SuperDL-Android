$t = Get-Content -Raw C:\Users\msn\Documents\SuperDL-Android\build_rel160.txt
$t = $t -replace [char]0, ''
$t = $t -replace "`r", ''
$lines = $t -split "`n"
foreach ($l in $lines) { if ($l -like '*BUILD*' -or $l -like '*EXIT=*' -or $l -like '*FAIL*') { $l.Trim() } }
