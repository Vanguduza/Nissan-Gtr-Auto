#!/usr/bin/env python3
"""Probe homepage maker list vs car makers."""
from __future__ import annotations

import re
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


def get(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html"})
    with urllib.request.urlopen(req, timeout=40) as resp:
        return resp.read().decode("utf-8", "replace")


def main() -> None:
    html = get("https://www.megazip.net/")
    parts = re.findall(r'href="(/parts/([a-z0-9-]+))/?\"', html, re.I)
    zap = re.findall(r'href="(/zapchasti-dlya-avtomobilej/([a-z0-9-]+))/?\"', html, re.I)
    print("parts makers", len({p[1] for p in parts}), sorted({p[1] for p in parts})[:40])
    print("zap makers", len({p[1] for p in zap}), sorted({p[1] for p in zap})[:40])
    # arctic-cat models
    ahtml = get("https://www.megazip.net/parts/arctic-cat")
    a_models = re.findall(r'href="(/zapchasti-dlya-avtomobilej/arctic-cat/([a-z0-9-]+))"', ahtml, re.I)
    a_parts = re.findall(r'href="(/parts/arctic-cat/([a-z0-9-]+))"', ahtml, re.I)
    print("arctic zap models", len(a_models), a_models[:10])
    print("arctic parts models", len(a_parts), a_parts[:10])
    other = [h for h in re.findall(r'href="([^"]+)"', ahtml) if "arctic" in h.lower()][:30]
    print("arctic sample", other)


if __name__ == "__main__":
    main()
