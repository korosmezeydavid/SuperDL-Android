$log = "C:\Users\msn\Documents\SuperDL-Android\tools\getgo2.txt"
"START $(Get-Date -Format s)" | Out-File $log -Encoding utf8
Stop-Process -Id 18284 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
Remove-Item -Recurse -Force "C:\Users\msn\gotool" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "C:\Users\msn\gotool" | Out-Null
Set-Location "C:\Users\msn\gotool"
# A beepitett tar (bsdtar) nagysagrendekkel gyorsabb az Expand-Archive-nal:
# a Go forrasfaja tizenharomezer apro fajl, amit az Expand-Archive egyesevel
# nyit meg es zar be.
& tar.exe -xf "$env:TEMP\go_portable.zip" 2>&1 | Out-File $log -Encoding utf8 -Append
"TAR EXIT=$LASTEXITCODE" | Out-File $log -Encoding utf8 -Append
if (Test-Path "C:\Users\msn\gotool\go\src\flag\flag.go") {
    "OK: a forrasfa teljes" | Out-File $log -Encoding utf8 -Append
    & "C:\Users\msn\gotool\go\bin\go.exe" version 2>&1 | Out-File $log -Encoding utf8 -Append
} else {
    "HIANYOS a forrasfa" | Out-File $log -Encoding utf8 -Append
}
"VEGE $(Get-Date -Format s)" | Out-File $log -Encoding utf8 -Append
