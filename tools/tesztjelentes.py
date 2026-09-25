import glob, sys, xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding="utf-8")
for f in sorted(glob.glob(r"C:\Users\msn\Documents\SuperDL-Android\app\build\test-results\testDebugUnitTest\*.xml")):
    r = ET.parse(f).getroot()
    if "offers" not in r.get("name", ""):
        continue
    print("%s: tests=%s skipped=%s failures=%s errors=%s" % (r.get("name").split(".")[-1], r.get("tests"), r.get("skipped"), r.get("failures"), r.get("errors")))
    out = r.find("system-out")
    if out is not None and out.text:
        for line in out.text.splitlines():
            if any(k in line for k in ("DM:", "ROSSMANN:", "PENNY:", "ALDI:", "Spar", "Auchan", "Lidl", "Tesco", "SPAR", "AUCHAN", "LIDL", "TESCO")):
                print("   ", line[:160])
