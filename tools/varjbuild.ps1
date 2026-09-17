$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\rel1690.log'
$end = (Get-Date).AddMinutes(15)
while ((Get-Date) -lt $end) {
    $t = Get-Content $log -Tail 4 -ErrorAction SilentlyContinue
    if ($t -match 'BUILD SUCCESSFUL' -or $t -match 'BUILD FAILED') { break }
    Start-Sleep -Seconds 15
}
Get-Content $log -Tail 6
