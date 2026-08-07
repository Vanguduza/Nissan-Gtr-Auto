"""Deep drill-down on catcar.info to reach a diagram page."""
from __future__ import annotations

import base64
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urljoin

import httpx

OUT = Path(__file__).resolve().parents[1] / "data" / "catcar_test_crawl"
HEADERS = {"User-Agent": "GTR-Catalog-Probe/1.0", "Accept-Language": "en-US,en;q=0.9"}


class AllLinks(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.links: list[tuple[str, str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag != "a":
            return
        href = next((v for k, v in attrs if k == "href" and v), "")
        if href:
            self.links.append(("", href))


def decode_l(url: str) -> str:
    m = re.search(r"[?&]l=([^&]+)", url)
    if not m:
        return ""
    token = unquote(m.group(1))
    pad = "=" * (-len(token) % 4)
    try:
        return base64.b64decode(token + pad).decode("utf-8", "replace")
    except Exception:
        return token[:120]


def analyze(html: str) -> dict:
    links = AllLinks()
    links.feed(html)
    maps = re.findall(r"<area[^>]+>", html, re.I)
    imgs = re.findall(r'<img[^>]+src="([^"]+)"', html, re.I)
    parts = re.findall(r"\b\d{5}-[A-Z0-9]{2,6}[A-Z0-9-]*\b", html)
    return {
        "maps": len(maps),
        "map_samples": maps[:3],
        "imgs": [i for i in imgs if "catalog" in i.lower() or "_sysimg" in i][:5],
        "parts": parts[:10],
        "h1": (re.search(r"<h1>([^<]+)</h1>", html) or [None, ""])[1],
        "links": links.links,
    }


def log(msg: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT / "deep_report.txt").open("a", encoding="utf-8") as f:
        f.write(msg + "\n")
    sys.stdout.buffer.write((msg + "\n").encode("utf-8", errors="replace"))


def fetch(client: httpx.Client, url: str, label: str) -> str:
    r = client.get(url, follow_redirects=True, timeout=30)
    html = r.text
    safe = re.sub(r"[^a-zA-Z0-9_-]+", "_", label)[:60]
    (OUT / f"deep_{safe}.html").write_text(html, encoding="utf-8")
    meta = analyze(html)
    log(f"\n--- {label} ---")
    log(f"status={r.status_code} bytes={len(r.content)} h1={meta['h1']!r}")
    log(f"l={decode_l(str(r.url))[:160]}")
    log(f"maps={meta['maps']} catalog_imgs={meta['imgs']}")
    if meta["map_samples"]:
        log("area sample: " + meta["map_samples"][0][:200])
    if meta["parts"]:
        log("part nums: " + str(meta["parts"][:5]))
    log(f"child ?l= links: {sum(1 for _, h in meta['links'] if '?l=' in h and h.split('l=', 1)[1])}")
    return html


def first_l_link(html: str) -> str | None:
    p = AllLinks()
    p.feed(html)
    for _, href in p.links:
        if "?l=" in href and href.split("l=", 1)[1]:
            return href
    return None


def main() -> None:
    (OUT / "deep_report.txt").write_text("", encoding="utf-8")
    gtr = (
        "https://www.catcar.info/nissan/?l="
        "cmVnaW9uPT1qcHx8c3Q9PTUwfHxzdHM9PXsiMTAiOiJcdTA0MjBcdTA0NGJcdTA0M2RcdTA0M2VcdTA0M2EiLCIyMCI6Ilx1MDQyZlx1MDQxZlx1MDQxZVx1MDQxZFx1MDQxOFx1MDQyZiIsIjUwIjoiTklTU0FOIEdULVIgKFIzNSkifXx8bW9kX2lkPT0yMTV8fHN0YXJ0PT0yMDA3LTExLTAxfHxlbmQ9PQ%3D%3D"
    )
    with httpx.Client(headers=HEADERS) as client:
        html = fetch(client, gtr, "gtr_r35_sections")
        for step in range(6):
            nxt = first_l_link(html)
            if not nxt:
                log("no deeper link")
                break
            nxt = urljoin("https://www.catcar.info", nxt)
            html = fetch(client, nxt, f"depth_{step + 1}")
            if analyze(html)["maps"] > 0:
                log("\nSUCCESS: diagram page with image map hotspots")
                break

        fetch(client, "https://www.catcar.info/totalcatalog/?lang=en", "totalcatalog_en")


if __name__ == "__main__":
    main()
