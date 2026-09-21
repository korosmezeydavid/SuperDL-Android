# -*- coding: utf-8 -*-
"""Elromlott ekezetek helyreallitasa a forrasban.

MI TORTENT: valamikor egy UTF-8 fajlt valaki CP1250-kent olvasott be, es
ugy mentette vissza UTF-8-kent. Ilyenkor az "u" ket karakterre esik szet
(U+0102 U+013D), a "•" haromra, es igy tovabb.

MIERT SZAMIT: nem csak megjegyzesekben van - a zenelejatszo, a
hangoskonyv-lejatszo es a podcast-lejatszo VEZERLO-FELIRATAIBAN is.
Azokat a program KIMONDJA. Vakon egy elromlott felirat nem szepseghiba,
hanem ertelmezhetetlen szo.

HOGYAN JAVIT: csak azt irja at, ami visszafejtve ERVENYES magyar betut
vagy ismert irasjelet ad. Amihez nem biztos, ahhoz nem nyul.

    python ekezetjavito.py           - csak jelentes
    python ekezetjavito.py javit     - a javitas elvegzese
"""
import os
import sys

ROOT = r"C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin"

# A romlas kezdo-karakterei (a UTF-8 vezetobajtok CP1250-beli kepe).
LEAD = "\u0102\u0139\u00c2\u00e2"

ELFOGADHATO = set(
    "\u00e1\u00e9\u00ed\u00f3\u00f6\u0151\u00fa\u00fc\u0171"
    "\u00c1\u00c9\u00cd\u00d3\u00d6\u0150\u00da\u00dc\u0170"
    "\u2022\u2013\u2014\u2026\u201e\u201d\u201c\u2019\u2018"
)


def bajtok(darab):
    """A romlott karakterek visszafejtese bajtokka.

    A cp1250 tablaban nehany bajt (0x81, 0x90...) DEFINIALATLAN, ezert a
    sima .encode('cp1250') ott kivetelt dob - pedig pont azok a bajtok
    kellenek. A C1-tartomanyt ezert kozvetlenul kepezzuk le.
    """
    ki = bytearray()
    for ch in darab:
        k = ord(ch)
        if 0x80 <= k <= 0x9F:
            ki.append(k)
        else:
            ki.extend(ch.encode("cp1250"))
    return bytes(ki)


def javit(txt):
    ki = []
    i = 0
    n = len(txt)
    csere = 0
    while i < n:
        ch = txt[i]
        if ch in LEAD:
            talalt = False
            for hossz in (2, 3, 4):
                darab = txt[i:i + hossz]
                if len(darab) < hossz:
                    break
                try:
                    vissza = bajtok(darab).decode("utf-8")
                except Exception:
                    continue
                if len(vissza) == 1 and vissza in ELFOGADHATO:
                    ki.append(vissza)
                    i += hossz
                    csere += 1
                    talalt = True
                    break
            if talalt:
                continue
        ki.append(ch)
        i += 1
    return "".join(ki), csere


def main():
    ir = len(sys.argv) > 1 and sys.argv[1] == "javit"
    osszes = 0
    fajlok = 0
    for gyoker, _, nevek in os.walk(ROOT):
        for nev in nevek:
            if not nev.endswith(".kt"):
                continue
            p = os.path.join(gyoker, nev)
            try:
                txt = open(p, "rb").read().decode("utf-8")
            except Exception:
                print("NEM UTF-8, kihagyva:", p)
                continue
            uj, csere = javit(txt)
            if csere == 0:
                continue
            fajlok += 1
            osszes += csere
            print("%-34s %3d csere" % (nev, csere))
            if ir:
                open(p, "wb").write(uj.encode("utf-8"))
    print("---")
    print("%d fajl, %d csere. %s" % (fajlok, osszes,
                                     "JAVITVA." if ir else "Csak jelentes."))


if __name__ == "__main__":
    main()
