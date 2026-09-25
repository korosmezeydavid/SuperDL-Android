Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$mem = Get-CimInstance Win32_OperatingSystem
Write-Output ("RAM osszes GB: {0:N1}, szabad GB: {1:N1}" -f ($mem.TotalVisibleMemorySize/1MB), ($mem.FreePhysicalMemory/1MB))
Write-Output ("CPU magok: " + (Get-CimInstance Win32_Processor | Measure-Object -Property NumberOfLogicalProcessors -Sum).Sum)
foreach ($p in @(@('..\SuperDL-wt-ipaper','vasarlas-ipaper'), @('..\SuperDL-wt-pdf','vasarlas-pdf'))) {
  if (-not (Test-Path $p[0])) {
    git worktree add $p[0] -b $p[1] vasarlas 2>&1 | Select-Object -Last 1
  }
  Copy-Item local.properties (Join-Path $p[0] 'local.properties') -Force
  if (Test-Path 'app\src\main\assets\ably_key.txt') {
    New-Item -ItemType Directory -Force (Join-Path $p[0] 'app\src\main\assets') | Out-Null
    Copy-Item 'app\src\main\assets\ably_key.txt' (Join-Path $p[0] 'app\src\main\assets\ably_key.txt') -Force
  }
}
git worktree list
