#!/usr/bin/env python3
import re, urllib.request, sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
UA = "Mozilla/5.0"
html = urllib.request.urlopen(urllib.request.Request(
    "https://www.japancats.ru/Mitsubishi/?Region=JP", headers={"User-Agent": UA}
)).read().decode("utf-8","replace")
idx = html.find("javascript:submit('lF'")
print(repr(html[idx-80:idx+350]))
# looser row parse
rows = re.findall(
    r"submit\('lF','([^']+)','([^']+)'[^)]*\)\">([^<]*)</a>\s*<td[^>]*>\s*<a[^>]*>\s*([^<]+)\s*</a>\s*<td[^>]*>\s*<a[^>]*>\s*([^<]+)\s*</a>",
    html, re.I,
)
print("loose", len(rows), rows[:3])
# maybe missing td tags - try split by tr
trs = re.findall(r"<tr>(.*?)</tr>", html, re.I|re.S)
print("trs", len(trs))
for tr in trs[:5]:
    cells = re.findall(r"<td[^>]*>(.*?)</td>", tr, re.I|re.S)
    if not cells:
        cells = re.findall(r"<td[^>]*>(.*?)(?=<td|</tr>)", tr, re.I|re.S)
    texts = [re.sub(r"<[^>]+>", "", c).strip() for c in cells]
    if any(texts):
        print(texts[:5])
