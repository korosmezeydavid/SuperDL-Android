import re, sys
L = open(r"C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\menu\MenuTree.kt", encoding="utf-8").read().split("\n")
cel = sys.argv[1]
idx = next(i for i, l in enumerate(L) if '"%s"' % cel in l and "MenuItem(" in l)
ut = [re.search(r'MenuItem\("[^"]+", "([^"]+)"', L[idx]).group(1)]
ind = len(L[idx]) - len(L[idx].lstrip())
for i in range(idx - 1, -1, -1):
    l = L[i]; d = len(l) - len(l.lstrip())
    if d < ind and "MenuItem(" in l and "SUBMENU" in l:
        ut.append(re.search(r'MenuItem\("[^"]+", "([^"]+)"', l).group(1)); ind = d
open(r"C:\Users\msn\Documents\SuperDL-Android\tools\menu_ut.txt", "w", encoding="utf-8").write(" -> ".join(reversed(ut)))
