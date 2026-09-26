import os, wave, struct
from faster_whisper import WhisperModel
T = r"C:\Users\msn\Documents\SuperDL-Android\idohang\leda\_telefon"
P = r"C:\Users\msn\Documents\SuperDL-Android\idohang\beszelo-ora-leda\mondatok"
out = open(os.path.join(T, "_eredmeny.txt"), "w", encoding="utf-8")
def info(p):
    w = wave.open(p); n = w.getnframes(); r = w.getframerate(); s = struct.unpack("<%dh" % n, w.readframes(n)); w.close()
    lead = next((i for i, x in enumerate(s) if abs(x) > 300), n)
    tail = n - 1 - max((i for i, x in enumerate(s) if abs(x) > 300), default=0)
    return n * 1000 // r, lead * 1000 // r, tail * 1000 // r
m = WhisperModel("small", device="cpu", compute_type="int8")
for f in sorted(os.listdir(T)):
    if not f.endswith(".wav"): continue
    pc = os.path.join(P, f[4:])
    segs, _ = m.transcribe(os.path.join(T, f), language="hu", beam_size=5)
    out.write("%s telefon(ms,eleje-csend,vege-csend)=%s gep=%s | %s\n" % (f, info(os.path.join(T, f)), info(pc), " ".join(s.text.strip() for s in segs)))
    out.flush()
out.write("KESZ\n"); out.close()
