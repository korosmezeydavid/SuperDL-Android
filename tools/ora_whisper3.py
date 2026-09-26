import os, sys
from faster_whisper import WhisperModel
D = r"C:\Users\msn\Documents\SuperDL-Android\idohang\leda"
m = WhisperModel(sys.argv[1], device="cpu", compute_type="int8")
out = open(os.path.join(D, "_whisper3.txt"), "w", encoding="utf-8")
for p in sys.argv[2:]:
    segs, _ = m.transcribe(os.path.join(D, p), language="hu", beam_size=5, word_timestamps=True)
    ws = [w for s in segs for w in s.words]
    out.write("%s | %s | min.biztos=%.2f\n" % (p, " ".join(w.word.strip() + "(%.2f)" % w.probability for w in ws),
                                                min(w.probability for w in ws)))
    out.flush()
out.write("KESZ\n"); out.close()
