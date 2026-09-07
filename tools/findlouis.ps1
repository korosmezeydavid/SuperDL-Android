$roots = @(
  'C:\Program Files (x86)\NVDA\louis\tables',
  'C:\Program Files\NVDA\louis\tables',
  "$env:APPDATA\nvda",
  "$env:LOCALAPPDATA\Programs"
)
foreach ($r in $roots) {
  if (Test-Path $r) {
    Write-Output "=== $r ==="
    Get-ChildItem -Path $r -Recurse -Filter 'hu*' -ErrorAction SilentlyContinue |
      Select-Object -First 30 | ForEach-Object { $_.FullName }
  }
}
Write-Output '=== teljes kereses a C: alatt (hu*.ctb / hu*.utb) ==='
Get-ChildItem -Path 'C:\' -Recurse -Include 'hu*.ctb','hu*.utb','hu*.cti' -ErrorAction SilentlyContinue -Force |
  Select-Object -First 40 | ForEach-Object { $_.FullName }
