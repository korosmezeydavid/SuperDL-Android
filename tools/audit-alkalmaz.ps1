Set-Location C:\Users\msn\Documents\SuperDL-Android
"AG: " + (git rev-parse --abbrev-ref HEAD)
git status --short --untracked-files=no
git apply --3way --check proba-apk\audit-javitasok.patch 2>&1 | Select -Last 15
"--- alkalmazas"
git apply --3way proba-apk\audit-javitasok.patch 2>&1 | Select -Last 25
git status --short --untracked-files=no | Measure-Object -Line
