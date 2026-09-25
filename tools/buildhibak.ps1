$log = 'C:\Users\msn\Documents\SuperDL-Android\build-log.txt'
Select-String -Path $log -Pattern '^e: |BUILD |FAILED' | Select-Object -First 30 | ForEach-Object { Write-Output $_.Line }
