import time
from faster_whisper import WhisperModel

src = r"C:\Users\msn\Documents\SuperDL-Android\tools\richard2.mp3"
out = r"C:\Users\msn\Documents\SuperDL-Android\tools\atirat2.txt"

model = WhisperModel("small", device="cpu", compute_type="int8", cpu_threads=4)
segments, info = model.transcribe(
    src, language="hu", vad_filter=True,
    vad_parameters=dict(min_silence_duration_ms=700),
    beam_size=1,
)
t0 = time.time()
with open(out, "w", encoding="utf-8") as f:
    f.write(f"# nyelv={info.language} hossz={info.duration:.0f}s\n")
    f.flush()
    for s in segments:
        line = f"[{int(s.start)//60:02d}:{int(s.start)%60:02d}] {s.text.strip()}\n"
        f.write(line)
        f.flush()
print("KESZ", time.time() - t0)
