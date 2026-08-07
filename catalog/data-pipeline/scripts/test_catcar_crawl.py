"""Bounded test crawl of catcar.info — feasibility probe, not production pipeline."""
from __future__ import annotations

import base64
import re
import sys
import time
from dataclasses import dataclass, field
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin, urlparse

import httpx

BASE = "https://www.catcar.info"
OUT_DIR = Path(__file__).resolve().parents[1] / "data" / "catcar_test_crawl"
HEADERS = {
    "User-Agent": "GTR-Catalog-Probe/1.0 (+https://github.com/local-dev)",
    "Accept": "text/html,application/xhtml+xml",
    "Accept-Language": "en-US,en;q=0.9",
}


def decode_l_param(url: str) -> str:
    parsed = urlparse(url)
    qs = dict(x.split("=", 1) for x in parsed.query.split("&") if "=" in x)
    token = qs.get("l", "")
    if not token:
        return ""
    pad = "=" * (-len(token) % 4)
    try:
        return base64.b64decode(token + pad).decode("utf-8", "replace")
    except Exception:
        return f"<decode-failed: {token[:40]}...>"


class TableLinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.links: list[tuple[str, str]] = []
        self._in_a = False
        self._href = ""
        self._text = ""

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "a":
            self._in_a = True
            self._href = ""
            self._text = ""
            for k, v in attrs:
                if k == "href" and v:
                    self._href = v

    def handle_data(self, data: str) -> None:
        if self._in_a:
            self._text += data

    def handle_endtag(self, tag: str) -> None:
        if tag == "a" and self._in_a:
            self._in_a = False
            if "?l=" in self._href:
                self.links.append((self._text.strip(), self._href))


@dataclass
class CrawlStep:
    url: str
    status: int
    bytes: int
    title: str = ""
    h1: str = ""
    link_count: int = 0
    has_map: bool = False
    has_img_catalog: bool = False
    has_coords_script: bool = False
    decoded_l: str = ""
    sample_links: list[str] = field(default_factory=list)
    error: str = ""


def parse_page(html: str) -> dict:
    title = re.search(r"<title>([^<]+)</title>", html, re.I)
    h1 = re.search(r"<h1>([^<]+)</h1>", html, re.I)
    parser = TableLinkParser()
    parser.feed(html)
    return {
        "title": title.group(1).strip() if title else "",
        "h1": h1.group(1).strip() if h1 else "",
        "links": parser.links,
        "has_map": "<map" in html.lower() or "usemap=" in html.lower(),
        "has_img_catalog": "big_catalog2" in html or "/_sysimg/" in html,
        "has_coords_script": "resizeImageCoords" in html,
    }


def fetch(client: httpx.Client, url: str) -> CrawlStep:
    step = CrawlStep(url=url, status=0, bytes=0)
    try:
        r = client.get(url, follow_redirects=True, timeout=30.0)
        step.status = r.status_code
        step.bytes = len(r.content)
        if r.status_code != 200:
            step.error = r.reason_phrase or "non-200"
            return step
        html = r.text
        meta = parse_page(html)
        step.title = meta["title"]
        step.h1 = meta["h1"]
        step.link_count = len(meta["links"])
        step.has_map = meta["has_map"]
        step.has_img_catalog = meta["has_img_catalog"]
        step.has_coords_script = meta["has_coords_script"]
        step.decoded_l = decode_l_param(url)
        step.sample_links = [label for label, _ in meta["links"][:8]]
        slug = re.sub(r"[^a-zA-Z0-9_-]+", "_", url.split("?")[0].rstrip("/").split("/")[-1] or "root")
        token = decode_l_param(url).replace("||", "_")[:40] or "index"
        safe = re.sub(r"[^a-zA-Z0-9_-]+", "_", token)
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        (OUT_DIR / f"{len(list(OUT_DIR.glob('*.html')))+1:02d}_{slug}_{safe[:30]}.html").write_text(
            html, encoding="utf-8"
        )
        return step
    except Exception as exc:
        step.error = str(exc)
        return step


def pick_link(links: list[tuple[str, str]], *needles: str) -> str | None:
    for label, href in links:
        upper = (label + href).upper()
        if any(n.upper() in upper for n in needles):
            return href
    return links[0][1] if links else None


def _safe_print(text: str) -> None:
    enc = getattr(sys.stdout, "encoding", None) or "utf-8"
    sys.stdout.buffer.write((text + "\n").encode(enc, errors="replace"))


def main() -> int:
    max_depth = int(sys.argv[1]) if len(sys.argv) > 1 else 6
    steps: list[CrawlStep] = []

    with httpx.Client(headers=HEADERS, http2=False) as client:
        # Root + Nissan entry
        for url in [
            f"{BASE}/",
            f"{BASE}/nissan/?lang=en",
        ]:
            steps.append(fetch(client, url))
            time.sleep(0.4)

        # Japan region (from prior probe)
        jp_region = (
            f"{BASE}/nissan/?l=cmVnaW9uPT1qcHx8c3Q9PTIwfHxzdHM9PXsiMTAiOiJcdTA0MjBcdTA0NGJcdTA0M2RcdTA0M2VcdTA0M2EiLCIyMCI6Ilx1MDQyZlx1MDQxZlx1MDQxZVx1MDQxZFx1MDQxOFx1MDQyZiJ9"
        )
        s = fetch(client, jp_region)
        steps.append(s)
        time.sleep(0.4)

        meta = parse_page((OUT_DIR / sorted(OUT_DIR.glob("*.html"))[-1].name).read_text(encoding="utf-8"))
        url = pick_link(meta["links"], "370Z", "Z34") or pick_link(meta["links"], "GT-R", "R35")
        depth = 0
        while url and depth < max_depth:
            if not url.startswith("http"):
                url = urljoin(BASE, url)
            s = fetch(client, url)
            steps.append(s)
            if s.error or s.status != 200:
                break
            html = (OUT_DIR / sorted(OUT_DIR.glob("*.html"))[-1].name).read_text(encoding="utf-8")
            meta = parse_page(html)
            if meta["has_map"]:
                _safe_print("DIAGRAM PAGE FOUND (image map present)")
                break
            # Prefer engine/section links, else first child
            next_url = pick_link(meta["links"], "ENGINE", "BODY", "ELECTRICAL", "SUSPENSION")
            if not next_url:
                next_url = meta["links"][0][1] if meta["links"] else None
            url = next_url
            depth += 1
            time.sleep(0.5)

    _safe_print("\n=== Catcar.info test crawl report ===\n")
    for i, st in enumerate(steps, 1):
        _safe_print(f"{i}. {st.status} {st.bytes:,}B  h1={st.h1!r}  links={st.link_count}")
        _safe_print(f"   {st.url[:120]}")
        if st.decoded_l:
            _safe_print(f"   l={st.decoded_l[:120]}")
        if st.has_map:
            _safe_print("   ** IMAGE MAP / HOTSPOTS **")
        if st.sample_links:
            _safe_print(f"   sample: {st.sample_links[:5]}")
        if st.error:
            _safe_print(f"   ERROR: {st.error}")
        _safe_print("")

    last = steps[-1] if steps else None
    cf = any(
        "cloudflare" in (OUT_DIR / f).read_text(encoding="utf-8", errors="ignore").lower()
        for f in sorted(OUT_DIR.glob("*.html"))[:1]
    )
    _safe_print("Summary:")
    _safe_print(f"  Pages fetched: {len(steps)}")
    _safe_print(f"  Cache dir: {OUT_DIR}")
    _safe_print("  Server-rendered HTML: yes (tables + ?l= tokens)")
    _safe_print(f"  Cloudflare detected: {cf}")
    _safe_print(f"  resizeImageCoords.js (hotspot scaling): {any(s.has_coords_script for s in steps)}")
    _safe_print(f"  Reached diagram (map): {bool(last and last.has_map)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
