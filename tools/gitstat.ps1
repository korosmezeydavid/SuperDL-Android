Set-Location "C:\Users\msn\Documents\SuperDL-Android"
& git status --short
Write-Output "--- kulcs-szures ---"
& git status --short | Select-String -Pattern 'keystore|ably_key|local.properties'
