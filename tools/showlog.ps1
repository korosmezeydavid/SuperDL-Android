$t = Get-Content -Raw C:\Users\msn\Documents\SuperDL-Android\build_gest.txt
$t = $t -replace [char]0, ''
$lines = $t -split "`n"
$lines | Select-String -Pattern 'BUILD|error:|^e: |FAILED' | Select-Object -First 30
