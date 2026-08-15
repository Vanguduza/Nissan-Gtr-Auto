#!/usr/bin/env python3
"""Probe megazip model page for chassis links."""
from __future__ import annotations

import re
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


def get(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html"})
    with urllib.request.urlopen(req, timeout=40) as resp:
        return resp.read().decode("utf-8", "replace")


def main() -> None:
    url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/skyline-2086"
    html = get(url)
    print("bytes", len(html))
    hrefs = re.findall(r'href="([^"]+)"', html)
    sky = [h for h in hrefs if "skyline" in h.lower() or "r32" in h.lower() or "r33" in h.lower() or "r34" in h.lower()]
    print("sky/rxx hrefs", len(sky))
    for h in sky[:60]:
        print(" ", h)
    # numbered variant paths
    deep = [h for h in hrefs if re.search(r"/zapchasti-dlya-avtomobilej/nissan/skyline", h, re.I)]
    print("skyline paths", len(deep))
    for h in deep[:40]:
        print(" ", h)
    classes = sorted(set(re.findall(r'class="([^"]*(?:variant|model|chassis|body)[^"]*)"', html, re.I)))
    print("classes", classes[:40])
    # sample anchors with text
    for m in re.finditer(r'<a[^>]+href="([^"]+)"[^>]*>([^<]{2,80})</a>', html, re.I):
        href, label = m.group(1), m.group(2).strip()
        if "skyline" in href.lower() and href.count("/") >= 4:
            print("A", label[:50], "->", href)


if __name__ == "__main__":
    main()
