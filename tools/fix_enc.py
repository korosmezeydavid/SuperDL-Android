import sys, io

p = sys.argv[1]
txt = io.open(p, encoding='utf-8').read()
if txt.startswith('\ufeff'):
    txt = txt[1:]

m = {
    '\u0102\u00a9': '\u00e9',
    '\u0102\u02c7': '\u00e1',
    '\u0139\u2018': '\u0151',
    '\u0139\u00b1': '\u0171',
    '\u0102\u015f': '\u00fa',
    '\u0102\u0142': '\u00f3',
    '\u0102\u015b': '\u00fc',
    '\u0102\u00b6': '\u00f6',
    '\u0102\u00ad': '\u00ed',
    '\u00e2\u20ac\u017e': '\u201e',
    '\u00e2\u20ac\u0165': '\u201d',
    '\u00e2\u20ac\u201d': '\u2014',
    '\u00e2\u20ac\u201c': '\u2013',
    '\u00e2\u20ac\u00a6': '\u2026',
    '\u00c2': '',
}
for k, v in m.items():
    txt = txt.replace(k, v)

io.open(p, 'w', encoding='utf-8', newline='').write(txt)

bad = [repr(c) for c in ('\u0102', '\u0139') if c in txt]
print('maradt hibas:', bad if bad else 'NINCS')
