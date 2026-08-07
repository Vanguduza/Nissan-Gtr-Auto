"""Bounded Megazip Nissan GT-R (R35) probe — mirrors catcar test crawl."""
from __future__ import annotations

import json
import re
import sys
import time
from dataclasses import dataclass, field
from html import unescape
from pathlib import Path
from urllib.parse import urljoin

import httpx

OUT_DIR = Path(__file__).resolve().parents[1] / "data" / "megazip_test_crawl"
BASE = "https://www.megazip.net"
RATE = 0.45
MAX_PAGES = int(sys.argv[1]) if len(sys.argv) > 1 else 12
HEADERS = {
    "User-Agent": "GTR-Catalog-Probe/1.0 (+local-dev; megazip-bounded-probe)",
    "Accept": "text/html,application/xhtml+xml",
    "Accept-Language": "en-US,en;q=0.9",
}

OEM_RE = re.compile(r"\b\d{5}-[A-Za-z0-9]{2,8}(?:-[A-Za-z0-9]+)?\b")
VARIANT_LINK = re.compile(
    r'href="(/zapchasti-dlya-avtomobilej/nissan/[^"]+)"[^>]*class="[^"]*s-catalog__body-variants-name',
    re.I,
)
SECTION_LINK = re.compile(
    r'href="(/zapchasti-dlya-avtomobilej/nissan/[^"]+)"[^>]*class="[^"]*part-group__(?:image-link|name)',
    re.I,
)
MODEL_LINK = re.compile(
    r'href="(/zapchasti-dlya-avtomobilej/nissan/[^"]+)"[^>]*class="[^"]*s-catalog__model-link',
    re.I,
)
DIAGRAM_IMG = re.compile(
    r'id="items_list_image"[^>]*src="(https://storage\.megazip\.net/catalog/[^"]+)"',
    re.I,
)
SECTION_THUMB = re.compile(r'src="(https://storage\.megazip\.net/catalog/[^"]+)"', re.I)
MAP_AREA = re.compile(
    r'<area[^>]*shape="[^"]*"[^>]*coords="([\d,\s]+)"[^>]*data-items-list-id="(\d+)"',
    re.I,
)
OEM_CELL = re.compile(r'class="[^"]*items-list__cell_type_number[^"]*"[^>]*>\s*([^<]+?)\s*</td>', re.I)


@dataclass
class Step:
    url: str
    status: int = 0
    bytes: int = 0
    title: str = ""
    level: str = ""
    oem_count: int = 0
    masked_yyyyy: int = 0
    sample_oem: list[str] = field(default_factory=list)
    diagram_img: str = ""
    area_hotspots: int = 0
    child_links: list[str] = field(default_factory=list)
    error: str = ""


def page_title(html: str) -> str:
    m = re.search(r"<title>([^<]+)</title>", html, re.I)
    return unescape(m.group(1).strip()) if m else ""


def classify_level(url: str, html: str) -> str:
    if DIAGRAM_IMG.search(html):
        return "diagram"
    if SECTION_LINK.search(html):
        return "section_list"
    if VARIANT_LINK.search(html):
        return "variant_list"
    if MODEL_LINK.search(html):
        return "model"
    if url.rstrip("/").endswith("/parts/nissan"):
        return "maker_hub"
    if url.rstrip("/").endswith("megazip.net") or url.rstrip("/").endswith("megazip.net/"):
        return "home"
    return "unknown"


def pick_exploded_diagram_url(html: str) -> str | None:
    """Prefer ASSEMBLY exploded diagrams; skip kit/list thumbnails."""
    skip = ("gasket kit", "standard tool", "owner", "manual")
    prefer = ("assembly", "cylinder block", "cylinder head", "body", "suspension")
    candidates: list[tuple[int, str]] = []
    for block in re.findall(
        r'<li[^>]*class="[^"]*part-group__item[^"]*"[^>]*id="part-group-\d+"[^>]*>.*?</li>',
        html,
        re.I | re.S,
    ):
        link_m = re.search(
            r'href="(/zapchasti-dlya-avtomobilej/nissan/[^"]+)"[^>]*class="[^"]*part-group__(?:image-link|name)',
            block,
            re.I,
        )
        name_m = re.search(r'class="[^"]*part-group__name[^"]*"[^>]*>([^<]+)<', block, re.I)
        desc_m = re.search(r'class="[^"]*part-group__description[^"]*"[^>]*>([^<]+)<', block, re.I)
        if not link_m:
            continue
        label = f"{name_m.group(1) if name_m else ''} {desc_m.group(1) if desc_m else ''}".lower()
        if any(s in label for s in skip):
            continue
        score = sum(3 for p in prefer if p in label)
        if "component parts" in label:
            score -= 1
        candidates.append((score, urljoin(BASE, link_m.group(1))))
    if not candidates:
        return None
    candidates.sort(key=lambda x: (-x[0], x[1]))
    return candidates[0][1]
def extract_child_urls(html: str, level: str) -> list[str]:
    if level == "section_list":
        picked = pick_exploded_diagram_url(html)
        if picked:
            return [picked]
    pat = {
        "maker_hub": MODEL_LINK,
        "model": MODEL_LINK,
        "variant_list": VARIANT_LINK,
        "section_list": SECTION_LINK,
    }.get(level, VARIANT_LINK)
    urls: list[str] = []
    for m in pat.finditer(html):
        urls.append(urljoin(BASE, m.group(1)))
    seen: set[str] = set()
    out: list[str] = []
    for u in urls:
        if u not in seen:
            seen.add(u)
            out.append(u)
    return out


def analyze_page(html: str) -> dict:
    oems = [x.strip() for x in OEM_RE.findall(html) if x.strip() and "YYYYY" not in x]
    cells = [c.strip() for c in OEM_CELL.findall(html) if re.search(r"\d{5}-", c)]
    for c in cells:
        if c not in oems:
            oems.append(c)
    areas = MAP_AREA.findall(html)
    img = DIAGRAM_IMG.search(html)
    return {
        "oem_count": len(oems),
        "sample_oem": oems[:15],
        "masked_yyyyy": html.count("YYYYY-YYYYY"),
        "area_hotspots": len(areas),
        "area_sample": areas[:3],
        "diagram_img": img.group(1) if img else "",
    }


def save_html(seq: int, url: str, html: str) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    tail = url.rstrip("/").split("/")[-1][:55]
    (OUT_DIR / f"{seq:02d}_{tail}.html").write_text(html, encoding="utf-8")


def fetch(client: httpx.Client, url: str, seq: int) -> Step:
    st = Step(url=url)
    try:
        r = client.get(url, follow_redirects=True, timeout=35.0)
        st.status = r.status_code
        st.bytes = len(r.content)
        if r.status_code != 200:
            st.error = r.reason_phrase or "non-200"
            return st
        html = r.text
        save_html(seq, url, html)
        st.title = page_title(html)
        st.level = classify_level(url, html)
        meta = analyze_page(html)
        st.oem_count = meta["oem_count"]
        st.masked_yyyyy = meta["masked_yyyyy"]
        st.sample_oem = meta["sample_oem"]
        st.diagram_img = meta["diagram_img"]
        st.area_hotspots = meta["area_hotspots"]
        st.child_links = extract_child_urls(html, st.level)
        if not st.diagram_img and st.level == "section_list":
            m = SECTION_THUMB.search(html)
            if m:
                st.diagram_img = m.group(1)
    except Exception as exc:
        st.error = str(exc)
    return st


def main() -> int:
    report: dict = {"fetched_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "base_url": BASE}
    steps: list[Step] = []
    seq = 0

    with httpx.Client(headers=HEADERS, http2=False) as client:
        try:
            rb = client.get(f"{BASE}/robots.txt", timeout=20.0)
            report["robots_txt"] = {"status": rb.status_code, "text": rb.text[:1500] if rb.status_code == 200 else None}
        except Exception as exc:
            report["robots_txt"] = {"error": str(exc)}
        time.sleep(RATE)

        ho = client.get(f"{BASE}/", timeout=20.0)
        report["cloudflare"] = {
            "server": ho.headers.get("server"),
            "cf_ray": ho.headers.get("cf-ray"),
            "cf_in_html": "cloudflare" in ho.text.lower()[:8000],
        }

        queue = [
            f"{BASE}/",
            f"{BASE}/parts/nissan",
            f"{BASE}/zapchasti-dlya-avtomobilej/nissan/nissan-gt-r-2063",
            f"{BASE}/zapchasti-dlya-avtomobilej/nissan/nissan-gt-r-2063/r35-6214",
        ]

        for url in queue:
            if len(steps) >= MAX_PAGES:
                break
            time.sleep(RATE)
            seq += 1
            steps.append(fetch(client, url, seq))

        tail = [
            f"{BASE}/zapchasti-dlya-avtomobilej/nissan/nissan-gt-r-2063/r35-6214/r35-181277",
        ]
        last = steps[-1] if steps else None
        if last and last.child_links:
            tail.insert(0, last.child_links[0])
        for url in tail:
            if len(steps) >= MAX_PAGES:
                break
            time.sleep(RATE)
            seq += 1
            last = fetch(client, url, seq)
            steps.append(last)
            if last.level == "section_list" and last.child_links and len(steps) < MAX_PAGES:
                time.sleep(RATE)
                seq += 1
                last = fetch(client, last.child_links[0], seq)
                steps.append(last)
                break

        diagram = next((s for s in reversed(steps) if s.area_hotspots > 0 or s.level == "diagram"), None)
        if diagram and diagram.diagram_img:
            try:
                ih = client.head(diagram.diagram_img, timeout=20.0)
                report["diagram_image_head"] = {
                    "url": diagram.diagram_img,
                    "status": ih.status_code,
                    "content_type": ih.headers.get("content-type"),
                    "content_length": ih.headers.get("content-length"),
                }
            except Exception as exc:
                report["diagram_image_head"] = {"error": str(exc)}

    report["steps"] = [
        {
            "url": s.url,
            "status": s.status,
            "bytes": s.bytes,
            "title": s.title,
            "level": s.level,
            "oem_count": s.oem_count,
            "masked_yyyyy": s.masked_yyyyy,
            "sample_oem": s.sample_oem[:10],
            "diagram_img": s.diagram_img,
            "area_hotspots": s.area_hotspots,
            "child_count": len(s.child_links),
            "error": s.error,
        }
        for s in steps
    ]

    report["hierarchy_observed"] = (
        "maker (/parts/nissan) -> model (nissan-gt-r-2063) -> chassis group (r35-6214) "
        "-> variant/build (r35-181277) -> section list (part-group-* slugs) -> diagram page"
    )
    report["url_structure"] = {
        "megazip": "REST slugs /zapchasti-dlya-avtomobilej/nissan/.../{name}-{numericId}",
        "catcar": "Single /nissan/ endpoint + ?lang=en&l=<base64 state blob>",
    }
    report["diagram_probe"] = {
        "url": diagram.url if diagram else None,
        "hotspot_count": diagram.area_hotspots if diagram else 0,
        "diagram_img_sample": diagram.diagram_img if diagram else None,
        "sample_oem": diagram.sample_oem[:12] if diagram else [],
        "hotspot_format": "<map><area shape=rect coords=... data-items-list-id=...>" if diagram and diagram.area_hotspots else None,
    }

    report["catcar_comparison"] = {
        "catcar_cloudflare": False,
        "megazip_cloudflare": bool(report.get("cloudflare", {}).get("cf_ray")),
        "catcar_oem_on_diagram": "masked YYYYY-YYYYY (0 real #####-XXXX in catcar probe)",
        "megazip_oem_on_diagram": (
            f"{diagram.oem_count if diagram else 0} OEM tokens; unmasked in HTML"
            if diagram
            else "not reached"
        ),
        "catcar_hotspots": "CSS .coord divs + resizeImageCoords.js (1 hotspot on 101A-001 sample)",
        "megazip_hotspots": f"{diagram.area_hotspots if diagram else 0} HTML <area> rects on sample diagram",
        "catcar_robots": "Crawl-delay: 10",
        "megazip_robots": "No global crawl-delay; blocks /cart, /search?q=, bots Ahrefs/Semrush",
    }

    report["orchestrator_note"] = {
        "module": "python -m data_pipeline.megazip_catalog_orchestrator",
        "smoke_flags": "--makers Nissan --max-pages 15 --phase crawl",
        "config_path": "data-pipeline/config/megazip_makers.json (monorepo root)",
        "not_run_reason": "Probe used scripts/test_megazip_crawl.py for GT-R-targeted chain; orchestrator hub crawl may not reach R35 within 15 pages",
    }

    report["verdict"] = {
        "scrape_feasible_html": bool(diagram and diagram.status == 200),
        "cloudflare_waf": bool(report.get("cloudflare", {}).get("cf_ray")),
        "all_responses_200": all(s.status == 200 for s in steps),
        "production_pipeline_fit": "Aligns with data_pipeline/megazip/parse_html.py (part-group + map areas + items-list table)",
        "pages_fetched": len(steps),
        "cache_dir": str(OUT_DIR),
    }

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "probe_report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    sys.stdout.write(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

