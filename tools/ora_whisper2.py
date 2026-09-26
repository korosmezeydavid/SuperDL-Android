import os, sys
from faster_whisper import WhisperModel
D = r"C:\Users\msn\Documents\SuperDL-Android\idohang\leda"
m = WhisperModel("small", device="cpu", compute_type="int8")
out = open(os.path.join(D, "_whisper2.txt"), "w", encoding="utf-8")
for p in sys.argv[1:]:
    segs, _ = m.transcribe(os.path.join(D, p), language="hu", beam_size=5)
    out.write("%s | %s\n" % (p, " ".join(s.text.strip() for s in segs))); out.flush()
out.write("KESZ\n"); out.close()
