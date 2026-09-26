import os, sys
from faster_whisper import WhisperModel
D = r"C:\Users\msn\Documents\SuperDL-Android\idohang\leda"
m = WhisperModel(sys.argv[1] if len(sys.argv) > 1 else "small", device="cpu", compute_type="int8")
out = open(os.path.join(D, "_whisper.txt"), "w", encoding="utf-8")
def fut(p, cimke):
    segs, _ = m.transcribe(p, language="hu", word_timestamps=True, beam_size=5)
    szavak = [(w.word.strip(), int(w.start * 1000), int(w.end * 1000)) for s in segs for w in s.words]
    out.write("%s | %s | %s\n" % (cimke, " ".join(x[0] for x in szavak), szavak)); out.flush()
for h in range(24):
    if h in (0, 12): continue
    n = "ora%02d.wav" % h
    fut(os.path.join(D, n), "TELJES " + n)
    fut(os.path.join(D, "eleje", n), "ELEJE  " + n)
out.write("KESZ\n"); out.close()
