#!/usr/bin/env python3
import re
import urllib.request
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
html = urllib.request.urlopen(
    urllib.request.Request("https://japan-parts.eu/", headers={"User-Agent": UA})
).read().decode("utf-8", "replace")
print("lexus hrefs:")
for h in re.findall(r'href="([^"]*lexus[^"]*)"', html, re.I):
    print(" ", h)
print("toyota sample:")
for h in re.findall(r'href="(/toyota/[^"]+)"', html, re.I)[:10]:
    print(" ", h)
# try /lexus page
try:
    lhtml = urllib.request.urlopen(
        urllib.request.Request("https://japan-parts.eu/lexus", headers={"User-Agent": UA})
    ).read().decode("utf-8", "replace")
    print("lexus page", len(lhtml))
    for h in re.findall(r'href="(/lexus/[^"]+)"', lhtml, re.I)[:20]:
        print(" ", h)
except Exception as e:
    print("lexus fail", e)
