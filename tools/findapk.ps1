Get-ChildItem 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk' -Recurse -Filter *.apk |
    Sort-Object LastWriteTime -Descending |
    Select-Object FullName, LastWriteTime, Length | Format-List
