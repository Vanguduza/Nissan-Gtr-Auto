#!/usr/bin/env python3
"""Probe megazip maker page link patterns."""
from __future__ import annotations

import re
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


def get(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html"})
    with urllib.request.urlopen(req, timeout=40) as resp:
        return resp.read().decode("utf-8", "replace")


def main() -> None:
    for url in (
        "https://www.megazip.net/parts/nissan",
        "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan",
    ):
        try:
            html = get(url)
        except Exception as exc:  # noqa: BLE001
            print(url, "FAIL", exc)
            continue
        print("===", url, "bytes", len(html))
        hrefs = re.findall(r'href="([^"]+)"', html)
        nissan = [h for h in hrefs if "nissan" in h.lower()]
        print("nissan hrefs", len(nissan))
        for h in nissan[:50]:
            print(" ", h)
        classes = sorted(set(re.findall(r'class="([^"]*model[^"]*)"', html, re.I)))
        print("model classes", classes[:30])
        # look for zapchasti paths
        zap = [h for h in hrefs if "zapchasti" in h.lower() or "/parts/nissan/" in h.lower()]
        print("zap/parts-nissan", len(zap))
        for h in zap[:40]:
            print(" ", h)


if __name__ == "__main__":
    main()
