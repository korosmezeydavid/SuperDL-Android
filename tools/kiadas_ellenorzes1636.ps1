Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
Write-Output '--- push allapot (uresnek kell lennie) ---'
git log --oneline origin/master..master
Write-Output '--- kiadas ---'
gh release view v1.63.6 --repo korosmezeydavid/SuperDL-Android 2>&1 | Select-Object -First 12
Write-Output '--- verzio.json a mobil agon ---'
gh api repos/korosmezeydavid/SuperDL/contents/verzio.json?ref=mobil --jq '.content' 2>&1 | ForEach-Object {
    if ($_ -match '^[A-Za-z0-9+/=\s]+$') {
        $t = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String(($_ -replace '\s','')))
        if ($t -match '"verzio":\s*"([^"]+)"') { Write-Output ("verzio.json: " + $Matches[1]) }
    } else { Write-Output $_ }
}
Write-Output 'KESZ.'
