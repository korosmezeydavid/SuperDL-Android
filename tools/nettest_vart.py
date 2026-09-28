# -*- coding: utf-8 -*-
"""A telefonos Internet-teszt VÁRT KIMENETEINEK előállítása a windowsos eredetiből.

Miért így: a telefonos szövegeknek (ítélet, részletek, jelfokozatok, a Wi-Fi
bejárás összefoglalója) BETŰRE egyezniük kell a Windows-változattal — a
felhasználó a két gépen ugyanazt a mondatot tanulja meg. Kézzel másolt
„várt" szöveg előbb-utóbb elcsúszna; ez a szkript MAGÁT a Python-eredetit
futtatja, és Kotlin-fájlt ír belőle.

Használat:
    python3 tools/nettest_vart.py <a windowsos superdl mappa, benne nettest.py> \
        > app/src/test/kotlin/com/superdl/launcher/nettest/PyExpected.kt
"""

import json
import sys

sys.path.insert(0, sys.argv[1] if len(sys.argv) > 1 else "/home/claude/win-net")
import nettest as nt  # noqa: E402

MB = 1024 * 1024


def kt(s):
    """Kotlin-szövegliterál."""
    return '"' + (s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
                  .replace("\n", "\\n")) + '"'


def ktlist(xs):
    return "listOf(" + ", ".join(kt(x) for x in xs) + ")"


def ktd(x):
    r = repr(float(x))
    return r if ("." in r or "e" in r or "E" in r) else r + ".0"


def ktdlist(xs):
    return "listOf<Double>(" + ", ".join(ktd(x) for x in xs) + ")"


# ------------------------------------------------------------- profilok
# (a Kotlin-oldal PONTOSAN ezekből épít NetTestResult-ot)
PROFILES = [
    dict(mod="teljes", le=45.3, fel=9.87, lecs=52.1, felcs=11.2,
         lem=[40.1, 45.3, 47.0, 44.2, 52.1], felm=[9.8, 10.1],
         kmin=22.1, katl=25.67, jit=1.53, siker=100.0, dns=12.34,
         lb=45 * MB + 123456, fb=9 * MB,
         pub=dict(ip="84.2.33.144", host="catv-84-2-33-144.catv.fixed.telekom.hu",
                  szolgaltato="Magyar Telekom plc.", asn="5483", varos="Budapest",
                  orszag="HU", kiszolgalo="BUD"),
         szolg=[("Modulok és frissítés (GitHub)", True, 34.5),
                ("YouTube (letöltés, keresés)", True, 21.0)],
         hibak=[]),
    dict(mod="teljes", le=3.2, fel=0.4, lecs=3.9, felcs=0.5,
         lem=[3.1, 3.2, 3.3], felm=[0.4],
         kmin=180.0, katl=250.5, jit=35.25, siker=91.7, dns=80.0,
         lb=2 * MB, fb=300000,
         pub=dict(ip="2001:4c4c:1234:5600::1", host="", szolgaltato="Vodafone",
                  asn="", varos="", orszag="HU", kiszolgalo="VIE"),
         szolg=[], hibak=[]),
    dict(mod="teljes", le=10.0, fel=5.0, lecs=45.0, felcs=6.0,
         lem=[2.1, 10.0, 45.0, 9.5, 11.0], felm=[5.0, 4.9, 6.0],
         kmin=15.0, katl=18.25, jit=2.0, siker=100.0, dns=5.0,
         lb=10 * MB, fb=4 * MB,
         pub=dict(ip="", host="", szolgaltato="", asn="", varos="", orszag="",
                  kiszolgalo=""),
         szolg=[], hibak=[]),
    dict(mod="takarekos", le=120.55, fel=30.25, lecs=130.0, felcs=31.0,
         lem=[118.0, 120.55, 125.0], felm=[30.25],
         kmin=8.0, katl=8.5, jit=0.25, siker=100.0, dns=0.35,
         lb=6 * MB, fb=3 * MB,
         pub=dict(ip="10.20.30.40", host="", szolgaltato="", asn="12345",
                  varos="Győr", orszag="", kiszolgalo=""),
         szolg=[], hibak=[]),
    dict(mod="gyors", le=0.0, fel=0.0, lecs=0.0, felcs=0.0, lem=[], felm=[],
         kmin=20.0, katl=22.0, jit=1.0, siker=100.0, dns=3.0, lb=0, fb=0,
         pub=dict(ip="84.2.1.1", host="", szolgaltato="Digi", asn="", varos="",
                  orszag="", kiszolgalo=""),
         szolg=[("Modulok és frissítés (GitHub)", True, 12.25),
                ("Névfeloldás (DNS-kiszolgáló)", False, 4000.0)],
         hibak=[]),
    dict(mod="gyors", le=0.0, fel=0.0, lecs=0.0, felcs=0.0, lem=[], felm=[],
         kmin=0.0, katl=0.0, jit=0.0, siker=33.3, dns=0.0, lb=0, fb=0,
         pub=dict(ip="", host="", szolgaltato="", asn="", varos="", orszag="",
                  kiszolgalo=""),
         szolg=[], hibak=[]),
    dict(mod="teljes", le=0.0, fel=0.0, lecs=0.0, felcs=0.0, lem=[], felm=[],
         kmin=0.0, katl=0.0, jit=0.0, siker=0.0, dns=0.0, lb=0, fb=0,
         pub=dict(ip="", host="", szolgaltato="", asn="", varos="", orszag="",
                  kiszolgalo=""),
         szolg=[("Modulok és frissítés (GitHub)", False, 4001.2),
                ("YouTube (letöltés, keresés)", False, 4000.9)],
         hibak=["letöltés: timed out", "feltöltés: timed out",
                "egyik mérő-forrás sem adott adatot", "a", "b", "c", "d"]),
    dict(mod="teljes", le=600.0, fel=250.0, lecs=640.0, felcs=260.0,
         lem=[590.0, 600.0, 640.0], felm=[250.0],
         kmin=3.0, katl=4.0, jit=0.5, siker=100.0, dns=1.0, lb=300 * MB, fb=120 * MB,
         pub=dict(ip="1.2.3.4", host="", szolgaltato="", asn="", varos="",
                  orszag="", kiszolgalo=""),
         szolg=[], hibak=[], megszakitva=True),
    dict(mod="teljes", le=0.25, fel=0.05, lecs=0.35, felcs=0.15,
         lem=[0.25, 0.2], felm=[0.05],
         kmin=0.0, katl=0.0, jit=0.0, siker=100.0, dns=0.05, lb=100000, fb=20000,
         pub=dict(ip="1.2.3.4", host="", szolgaltato="", asn="", varos="",
                  orszag="", kiszolgalo=""),
         szolg=[], hibak=[]),
    dict(mod="teljes", le=25.0, fel=2.0, lecs=27.0, felcs=9.0,
         lem=[24.0, 25.0, 27.0, 25.5], felm=[0.5, 2.0, 9.0, 1.9],
         kmin=40.0, katl=210.0, jit=12.0, siker=100.0, dns=25.0,
         lb=25 * MB, fb=2 * MB,
         pub=dict(ip="5.6.7.8", host="", szolgaltato="", asn="", varos="",
                  orszag="", kiszolgalo=""),
         szolg=[], hibak=[]),
]


def eredmeny(p):
    e = nt.Eredmeny(ido="2026-09-28 20:15:00", mod=p["mod"])
    s = e.sebesseg
    s.le_mbps, s.fel_mbps = p["le"], p["fel"]
    s.le_csucs_mbps, s.fel_csucs_mbps = p["lecs"], p["felcs"]
    s.le_mintak, s.fel_mintak = list(p["lem"]), list(p["felm"])
    s.keses_ms, s.keses_atlag_ms = p["kmin"], p["katl"]
    s.ingadozas_ms, s.sikeres_probak, s.dns_ms = p["jit"], p["siker"], p["dns"]
    s.le_bajt, s.fel_bajt = p["lb"], p["fb"]
    pb = p["pub"]
    e.publikus = nt.Publikus(ip=pb["ip"], host=pb["host"], szolgaltato=pb["szolgaltato"],
                             asn=pb["asn"], varos=pb["varos"], orszag=pb["orszag"],
                             kiszolgalo=pb["kiszolgalo"])
    e.szolgaltatasok = list(p["szolg"])
    e.hibak = list(p["hibak"])
    e.megszakitva = bool(p.get("megszakitva", False))
    return e


def szakasz(sorok, fej):
    """A `fej` fejlécű szakasz (a következő „— … —" fejlécig)."""
    if fej not in sorok:
        return []
    i = sorok.index(fej)
    j = i + 1
    while j < len(sorok) and not (sorok[j].startswith("— ") and sorok[j].endswith(" —")):
        j += 1
    return sorok[i:j]


def kt_profile(p):
    pb = p["pub"]
    sz = ", ".join("ServiceProbe(%s, %s, %s)" % (kt(n), "true" if ok else "false", ktd(ms))
                   for n, ok, ms in p["szolg"])
    return (
        "NetTestResult(time = \"2026-09-28 20:15:00\", mode = %s).apply {\n"
        "            speed.downMbps = %s; speed.upMbps = %s\n"
        "            speed.downPeakMbps = %s; speed.upPeakMbps = %s\n"
        "            speed.downSamples = %s; speed.upSamples = %s\n"
        "            speed.latencyMinMs = %s; speed.latencyAvgMs = %s\n"
        "            speed.jitterMs = %s; speed.successPct = %s; speed.dnsMs = %s\n"
        "            speed.downBytes = %dL; speed.upBytes = %dL\n"
        "            pub = NetPublic(ip = %s, host = %s, provider = %s, asn = %s, city = %s, country = %s, colo = %s)\n"
        "            services = listOf(%s)\n"
        "            errors.addAll(%s)\n"
        "            cancelled = %s\n"
        "        }"
        % (kt(p["mod"]), ktd(p["le"]), ktd(p["fel"]), ktd(p["lecs"]), ktd(p["felcs"]),
           ktdlist(p["lem"]), ktdlist(p["felm"]), ktd(p["kmin"]), ktd(p["katl"]),
           ktd(p["jit"]), ktd(p["siker"]), ktd(p["dns"]), p["lb"], p["fb"],
           kt(pb["ip"]), kt(pb["host"]), kt(pb["szolgaltato"]), kt(pb["asn"]),
           kt(pb["varos"]), kt(pb["orszag"]), kt(pb["kiszolgalo"]), sz,
           ktlist(p["hibak"]), "true" if p.get("megszakitva") else "false"))


out = []
w = out.append
w("package com.superdl.launcher.nettest")
w("")
w("// GENERÁLT FÁJL — ne szerkeszd kézzel!")
w("// Előállítja: tools/nettest_vart.py, a windowsos nettest.py FUTTATÁSÁVAL.")
w("// Minden érték a Python-eredeti valódi kimenete; a NetTestTextTest ezzel veti össze a Kotlin-átiratot.")
w("")
w("object PyExpected {")

# jelfokozatok
dbms = [-30, -55, -56, -65, -66, -72, -73, -80, -81, -100, -200, -201]
w("    val GRADE_INPUT = listOf(%s)" % ", ".join(str(d) for d in dbms))
w("    val GRADE = listOf(%s)" % ", ".join(
    "%s to %s" % (kt(a), kt(b)) for a, b in (nt.jel_minosites(d) for d in dbms)))

jel = [(0, 0, True), (-62, 0, True), (-49, 88, False), (-81, 40, True), (-72, 0, False),
       (-55, 100, True), (-99, 1, False)]
w("    val SIGNAL_TEXT = listOf(%s)" % ", ".join(
    "Pair(Triple(%d, %d, %s), %s)" % (d, p, "true" if m else "false", kt(nt.jel_szoveg(d, p, m)))
    for d, p, m in jel))

pcts = [0, 1, 3, 50, 88, 99, 100, 150, -5]
w("    val DBM_ESTIMATE = listOf(%s)" % ", ".join("%d to %d" % (p, nt.dbm_becsles(p)) for p in pcts))

fr = [-100, -85, -70, -62, -60, -50, -35, -20]
w("    val FREQUENCY = listOf(%s)" % ", ".join("%d to %s" % (d, ktd(nt.jel_frekvencia(d))) for d in fr))

ido = [0, 0.4, 0.5, 1.5, 2.5, 59.4, 59.5, 59.6, 60, 61, 119.5, 200, 3599, 3600, 3661, 7322.5, -3]
w("    val TIME_TEXT = listOf(%s)" % ", ".join("%s to %s" % (ktd(x), kt(nt.ido_szoveg(x))) for x in ido))

gig = [0, -1, 0.5, 1, 8.19, 50, 100, 941.3, 1000, 13.653]
w("    val ONE_GIG = listOf(%s)" % ", ".join("%s to %s" % (ktd(x), kt(nt.egy_giga_ideje(x))) for x in gig))

ips = ["", "  ", "84.2.33.144", "2001:db8:85a3::8a2e:370:7334", "fe80::1", "1.2.3", "::1",
       " 10.0.0.1 ", "a:b"]
w("    val MASK = listOf(%s)" % ", ".join("%s to %s" % (kt(x), kt(nt.maszkol_ip(x))) for x in ips))

nums = [0, 0.04, 0.05, 0.15, 0.25, 0.35, 1.0, 12.345, 99.95, 100, 2.675, 1e6, 45.3, 9.87, 0.45]
w("    val NUM = listOf(%s)" % ", ".join("%s to %s" % (ktd(x), kt(nt._szam(x))) for x in nums))

mb = [(0, 1.0), (1000000, 0), (1000000, 1.0), (123456789, 7.3), (65536, 0.5), (5 * MB, 2.49)]
w("    val MBPS = listOf(%s)" % ", ".join("Triple(%dL, %s, %s)" % (b, ktd(t), ktd(nt._mbps(b, t))) for b, t in mb))

fok = [0, -1, 4.99, 5, 29.9, 30, 99.9, 100, 499, 500, 1200]
w("    val GRADE_SPEED = listOf(%s)" % ", ".join("%s to %s" % (ktd(x), kt(nt._fokozat(x))) for x in fok))

# profilok
w("    fun profiles(): List<NetTestResult> = listOf(")
w(",\n".join("        " + kt_profile(p) for p in PROFILES))
w("    )")

verd, speed, pub, pubfull, need, warn = [], [], [], [], [], []
for p in PROFILES:
    e = eredmeny(p)
    verd.append(nt.osszefoglalo(e))
    s = nt.sorok(e)
    speed.append(s[1:s.index("— Kapcsolat —")])
    pub.append(szakasz(s, "— Publikus adatok —"))
    pubfull.append(szakasz(nt.sorok(e, teljes_ip=True), "— Publikus adatok —"))
    need.append(szakasz(s, "— Mire elég ez a net? —"))
    warn.append(szakasz(s, "— Mérési figyelmeztetések —"))

w("    val VERDICT = %s" % ktlist(verd))
for nev, lst in (("SPEED_LINES", speed), ("PUBLIC_LINES", pub), ("PUBLIC_LINES_FULL", pubfull),
                 ("NEED_LINES", need), ("WARNING_LINES", warn)):
    w("    val %s = listOf(\n%s\n    )" % (nev, ",\n".join("        " + ktlist(x) for x in lst)))
w("    val WINDOWS_FULL_IP_HINT = %s" % kt(
    "(A teljes IP-cím elrejtve – a „Teljes IP megjelenítése” gombbal kérhető)"))

# napló
naplo = [
    {"ido": "2026-09-01 20:00:00", "sebesseg": {"le_mbps": 45.3, "fel_mbps": 9.87, "keses_atlag_ms": 25.67}},
    {"ido": "2026-09-02 20:00:00", "sebesseg": {"le_mbps": 0, "fel_mbps": 0, "keses_atlag_ms": 30}},
    {"ido": "2026-09-03 20:00:00", "sebesseg": {"le_mbps": 12.25, "fel_mbps": 1.05, "keses_atlag_ms": 41.25}},
    {"ido": "2026-09-04 20:00:00", "sebesseg": {"le_mbps": 100, "fel_mbps": 20, "keses_atlag_ms": 9}},
]
nt.naplo_betolt = lambda: naplo
w("    val HISTORY_INPUT = listOf(%s)" % ", ".join(
    "Triple(%s, %s, Pair(%s, %s))" % (kt(d["ido"]), ktd(d["sebesseg"]["le_mbps"]),
                                      ktd(d["sebesseg"]["fel_mbps"]), ktd(d["sebesseg"]["keses_atlag_ms"]))
    for d in naplo))
w("    val HISTORY_LINES = %s" % ktlist(nt.naplo_sorok()))
w("    val HISTORY_AVG_10 = %s" % kt(nt.naplo_atlag()))
w("    val HISTORY_AVG_2 = %s" % kt(nt.naplo_atlag(2)))
nt.naplo_betolt = lambda: [naplo[1]]
w("    val HISTORY_AVG_NONE = %s" % kt(nt.naplo_atlag()))

# Wi-Fi bejárás
seqs = [
    dict(k=3, m=[-60, -61, -62, -63, -64, -58, -58, -70, -71, -80], pontok={3: "konyha", 7: "hálószoba", 9: "  padlás  "}),
    dict(k=3, m=[-50, -52, -55], pontok={2: "nappali"}),
    dict(k=1, m=[-60, -60, -61, -59], pontok={}),
    dict(k=0, m=[-60, -60, -61], pontok={0: ""}),
    dict(k=5, m=[], pontok={}),
    dict(k=3, m=[-61, -62, -64, -63], pontok={}),
]
w("    data class Walk(val threshold: Int, val readings: List<Int>, val marks: Map<Int, String>,")
w("                    val spoken: List<Boolean>, val placeTexts: List<String>, val emptyMark: Boolean,")
w("                    val best: Int, val weakest: Int, val average: Int, val summary: String,")
w("                    val saveText: String, val saveTextNet: String)")
w("    val WALKS = listOf(")
walks = []
for sq in seqs:
    n = nt.JelNaplo(sq["k"])
    ures = n.pont("x") == ("", 0, 0)
    spoken, ptexts = [], []
    for i, d in enumerate(sq["m"]):
        spoken.append(n.hozzaad(d, 0))
        if i in sq["pontok"]:
            ptexts.append(n.pont_szoveg(n.pont(sq["pontok"][i])))
    walks.append("        Walk(%d, listOf<Int>(%s), mapOf<Int, String>(%s), listOf<Boolean>(%s), %s, %s, %d, %d, %d, %s, %s, %s)" % (
        sq["k"], ", ".join(str(x) for x in sq["m"]),
        ", ".join("%d to %s" % (k, kt(v)) for k, v in sq["pontok"].items()),
        ", ".join("true" if x else "false" for x in spoken), ktlist(ptexts),
        "true" if ures else "false", n.legjobb(), n.leggyengebb(), n.atlag(),
        kt(n.osszefoglalo()), kt(n.mentheto_szoveg()), kt(n.mentheto_szoveg("Otthoni wifi"))))
w(",\n".join(walks))
w("    )")
w("}")
sys.stdout.write("\n".join(out) + "\n")
