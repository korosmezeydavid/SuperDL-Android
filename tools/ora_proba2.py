"""ora:perc parokbol mondat (eleje + perc + egyseges 'van'), majd felismeres."""
import wave, os, sys
from faster_whisper import WhisperModel
D = r"C:\Users\msn\Documents\SuperDL-Android\idohang\leda"
P = os.path.join(D, "_proba"); os.makedirs(P, exist_ok=True)
R = 24000
VAN = "veg/ora16.wav"
def olv(p):
    w = wave.open(os.path.join(D, p)); s = w.readframes(w.getnframes()); w.close(); return s
def cs(ms): return b"\x00\x00" * (R * ms // 1000)
m = WhisperModel("small", device="cpu", compute_type="int8")
out = open(os.path.join(D, "_proba2.txt"), "w", encoding="utf-8")
for par in sys.argv[1:]:
    o, p = par.split(":")
    x = olv("eleje/%s.wav" % o) + cs(50) + olv("%s.wav" % p) + cs(15) + olv(VAN)
    f = os.path.join(P, "%s_%s.wav" % (o, p))
    w = wave.open(f, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(R); w.writeframes(x); w.close()
    segs, _ = m.transcribe(f, language="hu", beam_size=5, word_timestamps=True)
    ws = [w for s in segs for w in s.words]
    out.write("%s | %s\n" % (par, " ".join(w.word.strip() + "(%.2f)" % w.probability for w in ws))); out.flush()
out.write("KESZ\n"); out.close()
