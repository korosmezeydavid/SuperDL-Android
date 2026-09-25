# -*- coding: utf-8 -*-
"""A beszelo ora PERC-klipjei, Leda hangjan: 5, 10, 15 ... 55.

Csak az ot perces ertekek kellenek - a koztes ertekek (11, 34) nem.

FOLYTATHATO: ami mar megvan, azt kihagyja. A Gemini ingyenes kerete
kulcsonkent napi 10 keres, es a sikertelen keres is fogyaszt, ezert a
tizenegy mondat tobb naposra is nyulhat - ugyanezzel a paranccsal
barmikor folytathato.

    python idohang4.py         - a hianyzo perc-mondatok
    python idohang4.py minta   - elohallgatas a keszekbol
"""
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import idohang as I
import idohang3 as G

PERCEK = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55]


def perc_mondat(n):
    """Az ora-mondat utan hangzo toredek: "Harminc perc." """
    return I.szam_szoval(n).capitalize() + " " + I.SZO_PERC + "."


def main():
    mappa = G.MAPPA
    os.makedirs(G.NYERS, exist_ok=True)

    if len(sys.argv) > 1 and sys.argv[1] == "minta":
        megvan = [n for n in PERCEK
                  if os.path.isfile(os.path.join(mappa, "perc%02d.wav" % n))]
        if not megvan:
            print("Meg egy perc-klip sincs kesz.")
            return
        nevek = ["ora14"] + ["perc%02d" % n for n in megvan]
        cel = os.path.join(I.KIMENET, "leda-perc-mintak.mp3")
        I.elohallgatas(mappa, nevek, cel)
        print("elohallgatas: %s (%d perc-klip)" % (cel, len(megvan)))
        return

    kulcsok = I.kulcsok()
    allapot = {"idx": 0}
    print("kulcsok: %d db" % len(kulcsok))

    kesz = kihagyva = 0
    for n in PERCEK:
        nev = "perc%02d" % n
        szoveg = perc_mondat(n)
        try:
            allapot_szo = G.keszit(nev, szoveg, kulcsok, allapot)
        except Exception as ex:
            print("MEGALLTAM a(z) %s-nal: %s" % (nev, ex))
            print("Ugyanezzel a paranccsal barmikor folytathato.")
            break
        if allapot_szo == "kesz":
            kesz += 1
            print("ok: %s  (%s)" % (nev, szoveg))
            time.sleep(1.5)
        else:
            kihagyva += 1

    megvan = len([f for f in os.listdir(mappa)
                  if f.startswith("perc") and len(f) == 10
                  and f.endswith(".m4a")])
    print("most kesz=%d kihagyva=%d | osszesen megvan: %d / %d"
          % (kesz, kihagyva, megvan, len(PERCEK)))


if __name__ == "__main__":
    main()
