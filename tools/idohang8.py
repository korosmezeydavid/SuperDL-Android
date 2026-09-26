# -*- coding: utf-8 -*-
"""Gyenge perc-klipek ujra (5 es 10), ket felvetel mindegyikbol."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import idohang3 as G
import idohang as I

FELADAT = [("perc05", "Öt perc."), ("perc10", "Tíz perc.")]
kulcsok = I.kulcsok()
allapot = {"idx": 0}
for alap, szoveg in FELADAT:
    for betu in ("b", "c"):
        nev = alap + betu
        for proba in range(2):
            try:
                print(nev, G.keszit(nev, szoveg, kulcsok, allapot), flush=True); break
            except Exception as ex:
                print(nev, "nem ment:", ex, flush=True)
