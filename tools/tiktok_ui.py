# -*- coding: utf-8 -*-
"""A TikTok felulet-kepebol a lapfulek es a gombok, a szuleikkel."""
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")
tree = ET.parse(r"C:\Users\msn\Documents\SuperDL-Android\tools\tiktok_ui.xml")
parent = {c: p for p in tree.iter() for c in p}
KULCS = ("live", "alkot", "k\u00f6zl", "kozl", "\u00e9l\u0151", "fot\u00f3", "15 mp", "60 mp", "sz\u00f6veg")


def leir(n):
    a = n.attrib
    return "text=%r desc=%r class=%s click=%s long=%s scroll=%s bounds=%s id=%s" % (
        a.get("text"), a.get("content-desc"), a.get("class", "").split(".")[-1],
        a.get("clickable"), a.get("long-clickable"), a.get("scrollable"),
        a.get("bounds"), a.get("resource-id", "").split("/")[-1])


for n in tree.iter("node"):
    t = (n.attrib.get("text", "") + " " + n.attrib.get("content-desc", "")).lower()
    if any(k in t for k in KULCS):
        print("ELEM:", leir(n))
        p, d = parent.get(n), 1
        while p is not None and p.tag == "node" and d <= 6:
            print("   ^%d %s" % (d, leir(p)))
            p, d = parent.get(p), d + 1
        print()
