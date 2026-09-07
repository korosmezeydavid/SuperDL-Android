chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\MainActivity.kt'
Write-Output "--- befejezes / kesz utak ---"
Select-String -Path $m -Pattern 'finishFirstRunSetup|fun startSetupWizard|SetupWizardBrowse\(' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- speakDetail / index1Of a varazslo tetelnel ---"
Select-String -Path 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\setup\SetupRequirements.kt' -Pattern 'fun speakDetail|fun index1Of|fun all\(' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
