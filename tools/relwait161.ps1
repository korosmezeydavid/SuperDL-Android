$log = 'C:\Users\msn\Documents\SuperDL-Android\build_rel161.txt'
for ($i = 0; $i -lt 12; $i++) {
    $c = Get-Content $log -Tail 3 -ErrorAction SilentlyContinue
    if ($c -match 'EXIT=') { break }
    Start-Sleep -Seconds 20
}
Get-Content $log -Tail 10
