$f = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\menu\MenuTree.kt'
Get-Content $f | Select-String -Pattern 'MenuItem\(' |
    Where-Object { $_.Line -match '_TOGGLE|_CYCLE|_SWITCH' } |
    ForEach-Object { $_.Line.Trim() }
