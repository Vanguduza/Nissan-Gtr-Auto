#!/usr/bin/env python3
import re
import urllib.request
import sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
url = "https://www.japancats.ru/Toyota/?Region=J"
html = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA})).read().decode("utf-8","replace")
# anchors with lF
for m in re.finditer(r'<a[^>]+href="javascript:submit\(\'lF\',\'([^\']+)\',\'([^\']+)\'[^"]*"[^>]*>([^<]{0,120})</a>', html):
    print(m.group(1), m.group(2), "|", m.group(3)[:80])
    break
# print nearby structure
idx = html.find("javascript:submit('lF'")
print(html[idx-200:idx+300])
print("---")
# look for model names near n0
for m in re.finditer(r'(?:class="[^"]*"|<li[^>]*>).{0,80}javascript:submit\(\'lF\'', html):
    pass
# extract list headers
headers = re.findall(r'<li class="[^"]*"[^>]*>\s*<[^>]+>([^<]{2,60})</', html)
print("headers", headers[:20])
# any text with chassis-like near submit
chunks = re.findall(r'>([^<]{2,80})</(?:a|span|b|strong|div)>', html)
interesting = [c for c in chunks if re.search(r'[A-Z]{1,3}\d{2}|ALPHARD|PRIUS|LAND', c, re.I)]
print("interesting", len(interesting))
for c in interesting[:30]:
    print(" ", c)
