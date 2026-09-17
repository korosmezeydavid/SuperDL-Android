$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\patrol\PatrolStore.kt'
'--- PatrolStore: intervallum es elso riasztas ---'
Get-Content $p | Select-String -Pattern 'INTERVAL|FIRST_ALERT|cycleTimeInterval|cycleFirstAlert|timeInterval|firstAlert' -Context 0,3 |
    ForEach-Object { $_.Line.Trim(); $_.Context.PostContext | ForEach-Object { '    ' + $_.Trim() } }
