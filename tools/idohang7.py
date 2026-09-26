# -*- coding: utf-8 -*-
"""Gyenge ora-mondatok ujra: tobb felvetel, beszedfelismerovel valasztunk."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import idohang3 as G
import idohang as I

E = "é"; O = "ó"
FELADAT = [
    ("ora19", "Most tizenkilenc " + O + "ra van."),
    ("ora00", "Most " + E + "jf" + E + "l van."),
    ("ora12", "Most d" + E + "l van."),
    ("ora00n", "Most nulla " + O + "ra van."),
]
kulcsok = I.kulcsok()
allapot = {"idx": 0}
for alap, szoveg in FELADAT:
    for betu in ("b", "c"):
        nev = alap + betu
        try:
            print(nev, G.keszit(nev, szoveg, kulcsok, allapot), flush=True)
        except Exception as ex:
            print(nev, "nem ment:", ex, flush=True)
