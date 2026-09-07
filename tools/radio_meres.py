import json, urllib.request, urllib.error

UA = {"User-Agent": "SuperDL-Android/1.0"}
BASE = "https://de1.api.radio-browser.info"
PATH = "/json/stations/bycountrycodeexact/HU?order=clickcount&reverse=true&hidebroken=true&limit=30"

def get(url, timeout=15):
    req = urllib.request.Request(url, headers=UA)
    return urllib.request.urlopen(req, timeout=timeout).read().decode("utf-8", "replace")

try:
    data = json.loads(get(BASE + PATH))
except Exception as e:
    print("A lista lekerdezese sikertelen:", type(e).__name__, e)
    raise SystemExit

print("Visszakapott adok szama:", len(data))
print()
print("%-38s %-6s %-6s %s" % ("NEV", "OK", "KLIKK", "STREAM"))
print("-" * 100)
for s in data:
    nev = (s.get("name") or "").strip()[:38]
    ok = s.get("lastcheckok")
    klikk = s.get("clickcount")
    url = (s.get("url_resolved") or s.get("url") or "")[:44]
    print("%-38s %-6s %-6s %s" % (nev, ok, klikk, url))

print()
print("=== A KOZSZOLGALATI ADOK ALLAPOTA ===")
kulcs = ("petofi", "kossuth", "bartok", "dankó", "danko", "mr1", "mr2")
talalt = [s for s in data if any(k in (s.get("name") or "").lower() for k in kulcs)]
if not talalt:
    print("EGYETLEN kozszolgalati ado SINCS a listaban.")
else:
    for s in talalt:
        print(" -", s.get("name"), "->", s.get("url_resolved") or s.get("url"))

print()
print("=== NEV SZERINTI KERESES: petofi ===")
try:
    p = json.loads(get(BASE + "/json/stations/search?name=petofi&order=clickcount&reverse=true&hidebroken=true&limit=10"))
    if not p:
        print("A kereses is URES.")
    for s in p:
        print(" -", s.get("name"), "| ok:", s.get("lastcheckok"), "|", (s.get("url_resolved") or s.get("url")))
except Exception as e:
    print("kereses hiba:", e)
