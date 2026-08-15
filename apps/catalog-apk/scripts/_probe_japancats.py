#!/usr/bin/env python3
"""Probe japancats Nissan region page for real chassis."""
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
        "https://www.japancats.ru/Nissan/",
        "https://www.japancats.ru/Nissan/?Region=US",
        "https://japancats.ru/Nissan/",
    ):
        try:
            html = get(url)
        except Exception as exc:  # noqa: BLE001
            print(url, "FAIL", exc)
            continue
        print("===", url, "bytes", len(html))
        # forms / selects
        for m in re.finditer(r'<select[^>]*>(.{0,500})</select>', html, re.I | re.S):
            print("SELECT", m.group(0)[:300].replace("\n", " "))
        opts = re.findall(r'<option[^>]+value="([^"]*)"[^>]*>([^<]*)</option>', html, re.I)
        print("options", len(opts), opts[:20])
        hrefs = [h for h in re.findall(r'href="([^"]+)"', html) if "Model" in h or "Parts" in h or "Region" in h]
        print("model/parts hrefs", len(hrefs))
        for h in hrefs[:30]:
            print(" ", h)
        # radio regions
        radios = re.findall(r'name=["\']r["\'][^>]*value=["\']([^"\']+)["\']', html, re.I)
        print("radios", radios)
        labels = re.findall(r'<label[^>]*>([^<]+)</label>', html, re.I)
        print("labels", labels[:20])
        # look for chassis-like tokens
        for m in re.finditer(r'\b([A-Z]{1,3}\d{2,3}[A-Z]?)\b', html):
            pass
        codes = sorted(set(re.findall(r'\b((?:R|V|Z|Y|S|B|C|E|D|N|P|T|W)\d{2}[A-Z]?)\b', html)))
        print("chassis-ish", codes[:40])


if __name__ == "__main__":
    main()
