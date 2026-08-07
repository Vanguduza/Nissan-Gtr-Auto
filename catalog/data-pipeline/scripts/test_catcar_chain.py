"""Manual chain: GTR R35 -> Engine -> subgroups -> diagram."""
from __future__ import annotations

import base64
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote

import httpx

OUT = Path(__file__).resolve().parents[1] / "data" / "catcar_test_crawl"
HEADERS = {"User-Agent": "GTR-Catalog-Probe/1.0", "Accept-Language": "en-US,en;q=0.9"}


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


class LinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.block_links: list[str] = []
        self.table_links: list[str] = []
        self._class = ""
        self._href = ""

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag != "a":
            return
        self._class = next((v for k, v in attrs if k == "class" and v), "")
        self._href = next((v for k, v in attrs if k == "href" and v), "")

    def handle_endtag(self, tag: str) -> None:
        if tag != "a" or not self._href or "?l=" not in self._href:
            return
        if not self._href.split("l=", 1)[1]:
            return
        if "blocks__item" in self._class:
            self.block_links.append(self._href)
        elif "table__td" in self._class or "table" in self._class:
            self.table_links.append(self._href)
        else:
            self.table_links.append(self._href)


def inspect(html: str) -> dict:
    p = LinkParser()
    p.feed(html)
    areas = re.findall(r"<area[^>]+>", html, re.I)
    img_maps = re.findall(r'usemap="#([^"]+)"', html, re.I)
    catalog_imgs = re.findall(r'/_sysimg/[^"\']+\.(?:png|gif|jpg)', html, re.I)
    parts = re.findall(r"\b\d{5}-[A-Z0-9]{2,6}[A-Z0-9-]*\b", html)
    ref_nums = re.findall(r'class="[^"]*ref[^"]*"[^>]*>([^<]+)<', html, re.I)
    return {
        "h1": (re.search(r"<h1>([^<]+)</h1>", html) or [None, ""])[1],
        "areas": len(areas),
        "area_sample": areas[0][:250] if areas else "",
        "usemaps": img_maps[:3],
        "catalog_imgs": catalog_imgs[:5],
        "parts": parts[:8],
        "block_links": p.block_links,
        "table_links": p.table_links,
    }


def log(msg: str) -> None:
    sys.stdout.buffer.write((msg + "\n").encode("utf-8", errors="replace"))


def step(client: httpx.Client, url: str, name: str) -> dict:
    r = client.get(url, timeout=30)
    html = r.text
    (OUT / f"chain_{name}.html").write_text(html, encoding="utf-8")
    meta = inspect(html)
    log(f"\n== {name} ==")
    log(f"{r.status_code} {len(r.content)}B h1={meta['h1']!r}")
    log(f"state: {decode_l(url)[:180]}")
    log(f"areas={meta['areas']} usemaps={meta['usemaps']} catalog_imgs={meta['catalog_imgs']}")
    if meta["area_sample"]:
        log(f"area: {meta['area_sample']}")
    if meta["parts"]:
        log(f"parts: {meta['parts']}")
    log(f"block_links={len(meta['block_links'])} table_links={len(meta['table_links'])}")
    return meta


def main() -> None:
    engine = (
        "https://www.catcar.info/nissan/?l="
        "cmVnaW9uPT1qcHx8c3Q9PTYwfHxzdHM9PXsiMTAiOiJcdTA0MjBcdTA0NGJcdTA0M2RcdTA0M2VcdTA0M2EiLCIyMCI6Ilx1MDQyZlx1MDQxZlx1MDQxZVx1MDQxZFx1MDQxOFx1MDQyZiIsIjUwIjoiTklTU0FOIEdULVIgKFIzNSkiLCI2MCI6Ilx1MDQxNFx1MDQxMlx1MDQxOFx1MDQxM1x1MDQxMFx1MDQyMlx1MDQxNVx1MDQxYlx1MDQyYywgXHUwNDIyXHUwNDFlXHUwNDFmXHUwNDFiXHUwNDE4XHUwNDEyXHUwNDFkXHUwNDEwXHUwNDJmIFx1MDQyMVx1MDQxOFx1MDQyMVx1MDQyMlx1MDQxNVx1MDQxY1x1MDQxMCJ9fHxtb2RfaWQ9PTIxNXx8c3RhcnQ9PTIwMDctMTEtMDF8fGVuZD09fHxzYT09Wg%3D%3D"
    )
    with httpx.Client(headers=HEADERS) as client:
        m = step(client, engine, "01_engine_section")
        url = m["block_links"][0] if m["block_links"] else (m["table_links"][0] if m["table_links"] else None)
        for i in range(2, 8):
            if not url:
                log("stopped: no child link")
                break
            m = step(client, url, f"{i:02d}_depth")
            if m["areas"] > 0:
                log("\n*** DIAGRAM WITH HOTSPOTS ***")
                break
            url = (
                m["table_links"][0]
                if m["table_links"]
                else (m["block_links"][0] if m["block_links"] else None)
            )


if __name__ == "__main__":
    main()
