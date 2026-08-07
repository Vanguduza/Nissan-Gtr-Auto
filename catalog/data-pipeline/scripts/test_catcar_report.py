"""Summarize catcar.info probe results."""
from __future__ import annotations

import json
import re
import time
from pathlib import Path

import httpx

OUT = Path(__file__).resolve().parents[1] / "data" / "catcar_test_crawl"
HEADERS = {"User-Agent": "GTR-Catalog-Probe/1.0", "Accept-Language": "en-US,en;q=0.9"}

# GTR R35 engine diagram 101A-001 (Japan, 2007-2010 build group)
DIAGRAM = (
    "https://www.catcar.info/nissan/?lang=en&l="
    "cmVnaW9uPT1qcHx8c3Q9PTcwfHxzdHM9PXsiMTAiOiJcdTA0MjBcdTA0NGJcdTA0M2RcdTA0M2VcdTA0M2EiLCIyMCI6Ilx1MDQyZlx1MDQxZlx1MDQxZVx1MDQxZFx1MDQxOFx1MDQyZiIsIjUwIjoiTklTU0FOIEdULVIgKFIzNSkiLCI2MCI6Ilx1MDQxNFx1MDQxMlx1MDQxOFx1MDQxM1x1MDQxMFx1MDQyMlx1MDQxNVx1MDQxYlx1MDQyYywgXHUwNDIyXHUwNDFlXHUwNDFmXHUwNDFiXHUwNDE4XHUwNDEyXHUwNDFkXHUwNDEwXHUwNDJmIFx1MDQyMVx1MDQxOFx1MDQyMVx1MDQyMlx1MDQxNVx1MDQxY1x1MDQxMCIsIjcwIjoiMTAxQSAwMDEifXx8bW9kX2lkPT0yMTV8fHN0YXJ0PT0yMDA3LTExLTAxfHxlbmQ9PXx8c2E9PVp8fHNlY19pZD09MTAxfHxzdWZmPT1BfHxwYWdlPT0wMDF8fHN0YXJ0Z3JwPT0yMDA3LTExLTAxfHxlbmRncnA9PTIwMTAtMTEtMDE%3D"
)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    report: dict = {"fetched_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}

    with httpx.Client(headers=HEADERS, timeout=30) as client:
        home = client.get("https://www.catcar.info/")
        report["home"] = {
            "status": home.status_code,
            "server": home.headers.get("server"),
            "cf": "cloudflare" in home.text.lower()[:5000],
            "bytes": len(home.content),
        }

        robots = client.get("https://www.catcar.info/robots.txt")
        report["robots_txt"] = robots.text[:800] if robots.status_code == 200 else None

        diag = client.get(DIAGRAM)
        html = diag.text
        (OUT / "diagram_en_101A_001.html").write_text(html, encoding="utf-8")
        coords = re.findall(
            r'class="coord"[^>]*srcTop="(\d+)"[^>]*srcLeft="(\d+)"[^>]*srcWidth="(\d+)"[^>]*srcHeight="(\d+)"[^>]*srcNum="([^"]+)"',
            html,
        )
        imgs = re.findall(r"https?://ci\.catcar\.info/[^\"'\s>]+", html)
        masked = html.count("YYYYY-YYYYY")
        real_parts = re.findall(r"\b\d{5}-[A-Z0-9]{2,6}[A-Z0-9-]*\b", html)
        report["diagram_gtr_r35_101A_001"] = {
            "status": diag.status_code,
            "bytes": len(diag.content),
            "title": (re.search(r"<title>([^<]+)</title>", html) or [None, ""])[1],
            "coord_hotspots": len(coords),
            "coord_sample": coords[:3],
            "diagram_images": imgs,
            "masked_part_numbers": masked,
            "real_oem_in_html": len(real_parts),
            "has_html_image_map": "<area" in html.lower(),
            "hotspot_model": "css_div_coord" if coords else "unknown",
        }

        img_url = imgs[0] if imgs else None
        if img_url:
            img = client.head(img_url)
            report["cdn_image"] = {
                "url": img_url,
                "status": img.status_code,
                "content_type": img.headers.get("content-type"),
                "content_length": img.headers.get("content-length"),
            }

        total = client.get("https://www.catcar.info/totalcatalog/?lang=en")
        report["totalcatalog"] = {
            "status": total.status_code,
            "bytes": len(total.content),
            "h1": (re.search(r"<h1>([^<]+)</h1>", total.text) or [None, ""])[1],
            "brand_links": len(re.findall(r'href="/totalcatalog/[^"]+"', total.text)),
        }

    report["verdict"] = {
        "scrape_feasible_html": True,
        "cloudflare": False,
        "navigation": "base64 ?l= state tokens (st=20/50/60/70)",
        "diagram_cdn": "ci.catcar.info",
        "hotspots_parseable": report["diagram_gtr_r35_101A_001"]["coord_hotspots"] > 0,
        "oem_numbers_on_public_site": report["diagram_gtr_r35_101A_001"]["real_oem_in_html"] > 0,
        "production_pipeline_fit": "demo_only — part numbers redacted; prefer Megazip/FAST or licensed Tradesoft API",
    }

    path = OUT / "probe_report.json"
    path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
