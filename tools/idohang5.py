# -*- coding: utf-8 -*-
"""Ejfel es del percekkel: "Most nulla ora van." / "Most tizenket ora van." -
ezekbol vagjuk le a "van"-t, hogy "Most nulla ora harminc perc" legyen."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import idohang3 as G
import idohang as I

MONDATOK = {
    "ora00n": "Most nulla óra van.",
    "ora12n": "Most tizenkét óra van.",
}
os.makedirs(G.NYERS, exist_ok=True)
kulcsok = I.kulcsok()
allapot = {"idx": 0}
for nev, szoveg in MONDATOK.items():
    for proba in range(3):
        try:
            print(nev, G.keszit(nev, szoveg, kulcsok, allapot)); break
        except Exception as ex:
            print(nev, "nem ment:", ex)
