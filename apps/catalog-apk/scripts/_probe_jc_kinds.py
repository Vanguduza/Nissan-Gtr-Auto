#!/usr/bin/env python3
import re, urllib.request
from collections import Counter
UA = "Mozilla/5.0"
for brand, region in [("Mitsubishi","JP"),("Lexus","J"),("Honda","US")]:
    url = f"https://www.japancats.ru/{brand}/?Region={region}"
    html = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA})).read().decode("utf-8","replace")
    kinds = Counter(re.findall(r"javascript:submit\(\s*'([^']+)'", html))
    print(brand, len(html), kinds)
    if "lF" in kinds:
        rows = re.findall(
            r"submit\('lF','([^']+)','([^']+)','true'\)\">([^<]*)</a>\s*<td>\s*<a[^>]*>\s*([^<]+)\s*</a>\s*<td>\s*<a[^>]*>\s*([^<]+)\s*</a>",
            html, re.I,
        )
        print("  lF rows", len(rows), rows[:2])
