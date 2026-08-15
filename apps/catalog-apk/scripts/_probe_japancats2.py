#!/usr/bin/env python3
"""Probe japancats with utf-8 stdout."""
from __future__ import annotations

import re
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


def get(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html"})
    with urllib.request.urlopen(req, timeout=40) as resp:
        return resp.read().decode("utf-8", "replace")


def main() -> None:
    url = "https://www.japancats.ru/Nissan/?Region=US"
    html = get(url)
    Path = __import__("pathlib").Path
    Path(r"C:\Nissan GTR auto\apps\catalog-apk\scripts\_japancats_sample.html").write_text(html, encoding="utf-8")
    print("bytes", len(html))
    opts = re.findall(r'<option[^>]+value="([^"]*)"[^>]*>([^<]*)</option>', html, re.I)
    print("options", len(opts))
    for o in opts[:30]:
        print(" OPT", o[0][:60], "|", o[1][:60])
    hrefs = [h for h in re.findall(r'href="([^"]+)"', html) if any(x in h for x in ("Model", "Parts", "Region", "aspx"))]
    print("aspx hrefs", len(hrefs))
    for h in hrefs[:40]:
        print(" ", h)
    # table rows
    for m in re.finditer(r'<tr[^>]*>(.*?)</tr>', html, re.I | re.S):
        row = re.sub(r"<[^>]+>", " ", m.group(1))
        row = re.sub(r"\s+", " ", row).strip()
        if len(row) > 8 and re.search(r"[A-Z]{1,3}\d{2}", row):
            print("TR", row[:120])


if __name__ == "__main__":
    main()
