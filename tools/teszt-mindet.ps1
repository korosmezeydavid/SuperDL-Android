Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$m = 'app\src\test\resources\akcio_minta'
New-Item -ItemType Directory -Force "$m\dm", "$m\rossmann" | Out-Null
Copy-Item 'C:\Users\msn\Documents\akcio_kutatas\dm\dm_kiarusitas_raw.json', 'C:\Users\msn\Documents\akcio_kutatas\dm\dm_kiarusitas_parsed.json' "$m\dm\" -Force
Copy-Item 'C:\Users\msn\Documents\akcio_kutatas\rossmann\sample_gql_first100_page2.json', 'C:\Users\msn\Documents\akcio_kutatas\rossmann\sample_rossmann_offers.json' "$m\rossmann\" -Force
Start-Process cmd -ArgumentList '/c','gradlew.bat testDebugUnitTest --tests com.superdl.launcher.offers.* > test-log.txt 2>&1' -WindowStyle Hidden
Write-Output 'teszt elindult'
