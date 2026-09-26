"""Elohallgatas a vegleges alakkal: "Most tizennegy ora harminc perc van."
Egesz orakor az eredeti mondat. A "van" mindig ugyanaz (a 16 orasbol)."""
import wave, os, subprocess, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import idohang as I
D = r"C:\Users\msn\Documents\SuperDL-Android\idohang\leda"
R = 24000
VAN = "veg/ora16.wav"
def olv(p):
    w = wave.open(os.path.join(D, p)); s = w.readframes(w.getnframes()); w.close(); return s
def csend(ms): return b"\x00\x00" * (R * ms // 1000)
def mondat(ora, perc):
    if perc == 0:
        return olv("ora%02d.wav" % ora)
    o = "ora%02dn.wav" % ora if ora in (0, 12) else "ora%02d.wav" % ora
    return olv("eleje/" + o) + csend(50) + olv("perc%02d.wav" % perc) + csend(15) + olv(VAN)
PELDAK = [(14, 0), (14, 30), (14, 10), (8, 15), (0, 45), (0, 0), (12, 5), (12, 0), (19, 55), (11, 20)]
pcm = b"".join(mondat(o, p) + csend(900) for o, p in PELDAK)
tmp = os.path.join(D, "_minta.wav")
w = wave.open(tmp, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(R); w.writeframes(pcm); w.close()
cel = os.path.join(os.path.dirname(D), "ora-perc-minta-uj.mp3")
subprocess.run([I.FFMPEG, "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libmp3lame", "-b:a", "96k", cel], check=True)
os.remove(tmp); print(cel)
