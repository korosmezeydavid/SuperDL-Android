# -*- coding: utf-8 -*-
"""Pillanatfelvetel a boltok oldalairól + a Windows-modul eredménye rajtuk.

A Kotlin-átirat tesztje UGYANEZEKET a fájlokat olvassa, és ugyanannyi
terméket kell találnia. Így a két program eredménye összevethető, net
nélkül, ugyanazon a bemeneten."""
import hashlib
import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.expanduser(r"~\.superdl\modules\akciok"))
from akciok_mod import penny, aldi  # noqa: E402

MAPPA = r"C:\Users\msn\Documents\SuperDL-Android\app\src\test\resources\akcio_minta"
os.makedirs(MAPPA, exist_ok=True)
UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
index = {}


def get_bytes(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "hu-HU,hu;q=0.9"})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    nev = hashlib.sha1(url.encode()).hexdigest()[:16] + ".txt"
    with open(os.path.join(MAPPA, nev), "wb") as f:
        f.write(data)
    index[url] = nev
    return data


def get(url):
    return get_bytes(url).decode("utf-8", "replace")


p = penny.letolt(get)
a = aldi.publitas_letolt(get_bytes)
with open(os.path.join(MAPPA, "index.json"), "w", encoding="utf-8") as f:
    json.dump(index, f, ensure_ascii=False, indent=0)
with open(os.path.join(MAPPA, "elvart.json"), "w", encoding="utf-8") as f:
    json.dump({
        "penny": len(p), "aldi": len(a),
        "penny_elso": [[t.nev, t.ar, t.kartyas_ar, t.kategoria] for t in p[:5]],
        "aldi_elso": [[t.nev, t.ar, t.kiszereles, t.kategoria] for t in a[:5]],
        "penny_arral": sum(1 for t in p if t.ar is not None),
        "aldi_arral": sum(1 for t in a if t.ar is not None),
    }, f, ensure_ascii=False, indent=1)
print("penny", len(p), "aldi", len(a), "fajlok", len(index))
