$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\MainActivity.kt'
'--- MainActivity: CYCLE kezelok ---'
Get-Content $m | Select-String -Pattern '_CYCLE ->' | ForEach-Object { $_.Line.Trim() }
'--- MenuTree: CYCLE enum ertekek ---'
$t = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\menu\MenuTree.kt'
Get-Content $t | Select-String -Pattern '_CYCLE' | ForEach-Object { $_.Line.Trim() }
