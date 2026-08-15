#!/usr/bin/env python3
"""List 7zap brands on hub vs harvested."""
from __future__ import annotations

import json
import re
import urllib.request
from pathlib import Path

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
req = urllib.request.Request(
    "https://7zap.com/en/catalog/cars/",
    headers={"User-Agent": UA, "Accept": "text/html"},
)
html = urllib.request.urlopen(req, timeout=40).read().decode("utf-8", "replace")
live = sorted({m.group(1).lower() for m in re.finditer(r"/en/catalog/cars/([a-z0-9-]+)/", html, re.I)})
have = {
    m["slug"].lower()
    for m in json.loads(
        Path(r"C:\Nissan GTR auto\apps\catalog-apk\app\src\main\assets\site_catalogs\7zap.json").read_text(
            encoding="utf-8"
        )
    )["makers"]
}
print("live", len(live))
print("have", len(have))
print("missing", [x for x in live if x not in have])
