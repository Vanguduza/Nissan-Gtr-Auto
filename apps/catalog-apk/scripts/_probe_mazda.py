#!/usr/bin/env python3
import re
import urllib.request
import sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
url = "https://www.japancats.ru/Mazda/?Region=US"
req = urllib.request.Request(url, headers={"User-Agent": UA})
html = urllib.request.urlopen(req, timeout=40).read().decode("utf-8", "replace")
print("bytes", len(html))
subs = re.findall(r"javascript:submit\(([^)]{0,300})\)", html)
print("submit count", len(subs))
for s in subs[:15]:
    print(" ", s[:200])
brackets = re.findall(r"\[([A-Z0-9]{2,12})\]\s*([^<\n]{2,60})", html)
print("brackets", len(brackets), brackets[:10])
# look for Modification
print("mod", "Modification" in html, "hF" in html)
# sample around catalog
idx = html.find("CModels")
print("CModels", idx)
if idx < 0:
    idx = html.find("iner_block")
print(html[idx:idx+500] if idx >= 0 else html[5000:5500])
