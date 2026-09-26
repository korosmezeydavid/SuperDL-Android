"""A jobb uj felvetelek a regiek helyere; a regi 'regi' mappaba kerul."""
import os, shutil, sys
D = r"C:\Users\msn\Documents\SuperDL-Android\idohang\leda"
REGI = os.path.join(D, "regi"); os.makedirs(REGI, exist_ok=True)
for par in sys.argv[1:]:
    uj, cel = par.split("=")
    for ext in (".wav", ".m4a"):
        if os.path.isfile(os.path.join(D, cel + ext)) and not os.path.isfile(os.path.join(REGI, cel + ext)):
            shutil.move(os.path.join(D, cel + ext), os.path.join(REGI, cel + ext))
        shutil.copy2(os.path.join(D, uj + ext), os.path.join(D, cel + ext))
    print(uj, "->", cel)
