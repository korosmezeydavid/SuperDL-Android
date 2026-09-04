$log = "C:\Users\msn\Documents\SuperDL-Android\tools\aar_build.txt"
"START $(Get-Date -Format s)" | Out-File $log -Encoding utf8

$goRoot = "C:\Users\msn\gotool\go"
$goPath = "C:\Users\msn\gopath"
$sdk    = "C:\Users\msn\AppData\Local\Android\Sdk"
$ndk    = "$sdk\ndk\26.1.10909125"

if (-not (Test-Path "$goRoot\bin\go.exe")) { "NINCS GO" | Out-File $log -Encoding utf8 -Append; exit 1 }
if (-not (Test-Path $ndk)) { "NINCS NDK: $ndk" | Out-File $log -Encoding utf8 -Append; exit 1 }

$env:GOROOT = $goRoot
$env:GOPATH = $goPath
$env:ANDROID_HOME = $sdk
$env:ANDROID_NDK_HOME = $ndk
$env:PATH = "$goRoot\bin;$goPath\bin;" + $env:PATH

Set-Location "C:\Users\msn\Documents\SuperDL-Android\wormhole-go"

"--- go get fuggosegek ---" | Out-File $log -Encoding utf8 -Append
& go get github.com/psanford/wormhole-william@v1.0.8 2>&1 | Out-File $log -Encoding utf8 -Append
# A gomobile bind megkoveteli, hogy a golang.org/x/mobile BENNE legyen a modul
# fuggosegi grafjaban. Mivel a forrasunk nem importalja, eszkoz-fuggosegkent
# vesszuk fel (`-tool`) — igy a `go mod tidy` sem dobja ki.
& go get -tool golang.org/x/mobile/cmd/gobind 2>&1 | Out-File $log -Encoding utf8 -Append
& go get golang.org/x/mobile/bind 2>&1 | Out-File $log -Encoding utf8 -Append

"--- gomobile telepites ---" | Out-File $log -Encoding utf8 -Append
& go install golang.org/x/mobile/cmd/gomobile@latest 2>&1 | Out-File $log -Encoding utf8 -Append

"--- gomobile init ---" | Out-File $log -Encoding utf8 -Append
& "$goPath\bin\gomobile.exe" init 2>&1 | Out-File $log -Encoding utf8 -Append

"--- gomobile bind ---" | Out-File $log -Encoding utf8 -Append
New-Item -ItemType Directory -Force -Path "C:\Users\msn\Documents\SuperDL-Android\app\libs" | Out-Null
$bindArgs = @(
    "bind",
    "-target=android/arm64,android/arm",
    "-androidapi", "26",
    "-o", "C:\Users\msn\Documents\SuperDL-Android\app\libs\whmobile.aar",
    "./whmobile"
)
& "$goPath\bin\gomobile.exe" @bindArgs 2>&1 | Out-File $log -Encoding utf8 -Append
"BIND EXIT=$LASTEXITCODE" | Out-File $log -Encoding utf8 -Append

if (Test-Path "C:\Users\msn\Documents\SuperDL-Android\app\libs\whmobile.aar") {
    $a = Get-Item "C:\Users\msn\Documents\SuperDL-Android\app\libs\whmobile.aar"
    ("AAR: {0:N1} MB" -f ($a.Length/1MB)) | Out-File $log -Encoding utf8 -Append
} else {
    "AAR: NINCS" | Out-File $log -Encoding utf8 -Append
}
"VEGE $(Get-Date -Format s)" | Out-File $log -Encoding utf8 -Append
