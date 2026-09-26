import json, urllib.request
B = "https://raw.githubusercontent.com/korosmezeydavid/SuperDL/mobil/"
cat = json.loads(urllib.request.urlopen(B + "mobil-katalogus.json", timeout=30).read().decode("utf-8"))
m = [x for x in cat["modules"] if x["id"] == "beszelo-ora-leda"]
print("katalogus sor:", json.dumps(m, ensure_ascii=True)[:400])
raw = urllib.request.urlopen(B + m[0]["fajl"], timeout=60).read()
o = json.loads(raw.decode("utf-8"))
print("modul:", len(raw), "byte,", len(o["hangok"]), "hang, meret egyezik:", len(raw) == m[0]["meret"])
