import json, urllib.request, urllib.parse

base = "https://futar.bkk.hu/api/query/v1/ws/otp/api/where/plan-trip"
params = {
    "key": "bkk-web",
    "version": "2",
    "appVersion": "SuperDL/1.6",
    "fromPlace": "47.5006,19.0844",
    "toPlace":   "47.5178,19.1330",
    "mode": "TRANSIT,WALK",
    "time": "now",
}
url = base + "?" + urllib.parse.urlencode(params)
req = urllib.request.Request(url, headers={"User-Agent": "SuperDL/1.6"})
try:
    raw = urllib.request.urlopen(req, timeout=25).read().decode("utf-8")
    j = json.loads(raw)
    it = j["data"]["entry"]["plan"]["itineraries"][0]
    st, en = it.get("startTime"), it.get("endTime")
    print("itinerary 'duration' mezo =", it.get("duration"))
    if st and en:
        d = en - st
        print("endTime - startTime =", d, "ezredmasodperc =", round(d/60000, 1), "perc")
    print()
    for i, leg in enumerate(it["legs"]):
        dur = leg.get("duration")
        lst, len_ = leg.get("startTime"), leg.get("endTime")
        valos = (len_ - lst) / 60000.0 if (lst and len_) else None
        print("leg%d mode=%-10s duration=%-9s distance=%s" % (i, leg.get("mode"), dur, leg.get("distance")))
        print("     to.name = %r" % ((leg.get("to") or {}).get("name")))
        if valos is not None:
            print("     valodi hossz = %.1f perc" % valos)
            if dur:
                print("     duration/60000 = %.2f    duration/60 = %.2f" % (dur/60000.0, dur/60.0))
except Exception as e:
    print("HIBA:", type(e).__name__, e)
