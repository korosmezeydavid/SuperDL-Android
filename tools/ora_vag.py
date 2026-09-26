"""Az ora-mondatokbol ("Most tizennegy ora van.") levagja a "van"-t.
Kimenet: idohang/leda/eleje/oraNN.wav ("Most tizennegy ora") es veg/oraNN.wav ("van.")."""
import wave, struct, math, os, sys
D = r"C:\Users\msn\Documents\SuperDL-Android\idohang\leda"
ELEJE = os.path.join(D, "eleje"); VEG = os.path.join(D, "veg")
os.makedirs(ELEJE, exist_ok=True); os.makedirs(VEG, exist_ok=True)

def olvas(p):
    w = wave.open(p); r = w.getframerate(); n = w.getnframes()
    s = list(struct.unpack("<%dh" % n, w.readframes(n))); w.close()
    return r, s

def ir(p, r, s):
    w = wave.open(p, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(r)
    w.writeframes(struct.pack("<%dh" % len(s), *s)); w.close()

def burkolo(s, r, win_ms=25, hop_ms=5):
    win = r * win_ms // 1000; hop = r * hop_ms // 1000
    e = []
    for i in range(0, max(1, len(s) - win), hop):
        blk = s[i:i + win]
        e.append(math.sqrt(sum(x * x for x in blk) / len(blk)))
    return e, hop

def csucsok(e, hop_ms=5):
    mx = max(e); p = []
    for i in range(1, len(e) - 1):
        if e[i] >= e[i - 1] and e[i] >= e[i + 1] and e[i] > 0.2 * mx:
            if p and (i - p[-1]) * hop_ms < 60:
                if e[i] > e[p[-1]]: p[-1] = i
                continue
            if p:
                vol = min(e[p[-1]:i + 1])
                if vol > 0.75 * min(e[i], e[p[-1]]):
                    if e[i] > e[p[-1]]: p[-1] = i
                    continue
            p.append(i)
    return p

def vag(nev):
    r, s = olvas(os.path.join(D, nev))
    e, hop = burkolo(s, r)
    p = csucsok(e)
    a, b = p[-2], p[-1]
    m = min(range(a, b + 1), key=lambda i: e[i])
    cut = m * hop + (r * 25 // 1000) // 2
    if (len(s) - cut) * 1000 // r < 200 and len(p) >= 3:
        # a "van" n-je kulon csucsnak latszott: egy csuccsal korabban vagunk
        a, b = p[-3], p[-2]
        m = min(range(a, b + 1), key=lambda i: e[i])
        cut = m * hop + (r * 25 // 1000) // 2
    fade = r * 12 // 1000
    eleje = s[:cut]
    for k in range(fade):
        eleje[-fade + k] = int(eleje[-fade + k] * (1 - (k + 1) / fade))
    veg = s[cut:]
    for k in range(min(fade, len(veg))):
        veg[k] = int(veg[k] * (k + 1) / fade)
    ir(os.path.join(ELEJE, nev), r, eleje)
    ir(os.path.join(VEG, nev), r, veg)
    return len(s) * 1000 // r, cut * 1000 // r, len(p), int(100 * e[m] / max(e))

nevek = sys.argv[1:] or ["ora%02d.wav" % h for h in range(24) if h not in (0, 12)]
for nm in nevek:
    tot, cut, np_, dip = vag(nm)
    print(nm, "hossz", tot, "vagas", cut, "van-hossz", tot - cut, "csucs", np_, "volgy%", dip)
