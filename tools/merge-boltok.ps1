$ErrorActionPreference = 'Continue'
Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
if ((git branch --show-current) -ne 'vasarlas') { throw 'Nem a vasarlas agon vagyunk.' }
git merge --no-edit vasarlas-ipaper 2>&1 | Select-Object -Last 3
git merge --no-edit vasarlas-pdf 2>&1 | Select-Object -Last 3
git log --oneline -5
Write-Output '--- mintak atmasolasa a tesztekhez (git-ignored) ---'
foreach ($wt in @('..\SuperDL-wt-ipaper','..\SuperDL-wt-pdf')) {
  $src = Join-Path $wt 'app\src\test\resources\akcio_minta'
  Get-ChildItem $src -Directory | ForEach-Object {
    $dst = Join-Path 'app\src\test\resources\akcio_minta' $_.Name
    Copy-Item $_.FullName $dst -Recurse -Force
    Write-Output ("  " + $_.Name + ": " + (Get-ChildItem $dst -Recurse -File | Measure-Object).Count + " fajl")
  }
}
Write-Output '--- PDFBox a programban mar hasznalva? ---'
Get-ChildItem 'app\src\main\kotlin' -Recurse -Filter '*.kt' | Select-String -Pattern 'PDFBoxResourceLoader' -List | ForEach-Object { Write-Output ($_.Path + ':' + $_.LineNumber + ': ' + $_.Line.Trim()) }
Write-Output '--- download alairasok ---'
Get-ChildItem 'app\src\main\kotlin\com\superdl\launcher\offers' -Filter '*.kt' | Select-String -Pattern 'fun download\(' -Context 0,6 | ForEach-Object { Write-Output ($_.Filename + ': ' + $_.Line.Trim()); $_.Context.PostContext | ForEach-Object { Write-Output ('    ' + $_.Trim()) } }
