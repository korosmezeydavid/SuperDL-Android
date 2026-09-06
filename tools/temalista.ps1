# MI VAN A KOZOS KATALOGUSBAN
#   .\tools\temalista.ps1
#
# Felsorolja a katalogusban levo beszedtemakat, es a vegen azokat is,
# amiket mar megneztel, de meg nem tettel kozze.
$ErrorActionPreference = 'Continue'
# A MAGYAR EKEZETEK MIATT: a Windows konzol alapbol nem UTF-8, a Python
# stdoutja sem. Mindketto kell, kulonben a kepernyoolvaso krix-kraxot olvas.
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = 'utf-8'
& python -X utf8 "C:\Users\msn\Documents\SuperDL-Android\tools\kozos_tema.py" lista
