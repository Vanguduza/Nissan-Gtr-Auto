#!/usr/bin/env python3
import re
import urllib.request
import sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
url = "https://www.japancats.ru/Toyota/?Region=J"
html = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA})).read().decode("utf-8","replace")
print("bytes", len(html))
kinds = re.findall(r"javascript:submit\(\s*'([^']+)'", html)
from collections import Counter
print(Counter(kinds))
subs = re.findall(r"javascript:submit\(([^)]{0,250})\)", html)
print("count", len(subs))
for s in subs[:12]:
    print(" ", s[:220])
# full third-arg capture with quotes
full = re.findall(r"javascript:submit\(\s*'([^']+)'\s*,\s*'([^']+)'\s*,\s*'([^']+)'", html)
print("full", len(full))
print(full[:3])
