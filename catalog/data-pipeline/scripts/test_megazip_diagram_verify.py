"""Fetch a real Megazip exploded diagram, download full PNG, verify hotspot mapping."""
from __future__ import annotations

import json
import re
import sys
from io import BytesIO
from pathlib import Path
from urllib.parse import urljoin

import httpx

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    Image = None  # type: ignore

OUT = Path(__file__).resolve().parents[1] / "data" / "megazip_test_crawl"
HEADERS = {"User-Agent": "GTR-Catalog-Probe/1.0", "Accept-Language": "en-US,en;q=0.9"}

# GTR R35 — Cylinder block & oil pan (ASSEMBLY) — exploded EPC diagram, not gasket kit list
DIAGRAM_URL = (
    "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/nissan-gt-r-2063/"
    "r35-6214/r35-181277/cylinder-block-oil-pan-2293503"
)

IMG_RE = re.compile(
    r'id="items_list_image"[^>]*?(?:width="(\d+)"[^>]*height="(\d+)"|height="(\d+)"[^>]*width="(\d+)")?[^>]*src="(https://storage\.megazip\.net/catalog/[^"]+)"',
    re.I | re.S,
)
# fallback if attrs order differs
IMG_RE2 = re.compile(
    r'id="items_list_image"[^>]*src="(https://storage\.megazip\.net/catalog/[^"]+)"',
    re.I,
)
AREA_RE = re.compile(
    r'<area[^>]*shape="rect"[^>]*coords="([\d,\s]+)"[^>]*data-items-list-id="(\d+)"',
    re.I,
)
PART_RE = re.compile(
    r'data-items-list-id="(\d+)"[\s\S]{0,3000}?items-list__cell_type_number[^"]*"[^>]*>\s*([^<]+?)\s*</td>',
    re.I,
)
REF_RE = re.compile(
    r'data-items-list-id="(\d+)"[\s\S]{0,2000}?items-list__cell_type_ref[^"]*"[^>]*>\s*([^<]+?)\s*</td>',
    re.I,
)


def log(msg: str) -> None:
    sys.stdout.buffer.write((msg + "\n").encode("utf-8", errors="replace"))


def upscale_url(url: str) -> list[str]:
    """Megazip CDN size prefixes observed: S, 2S, M, 2M, L (try variants)."""
    candidates = [url]
    for prefix in ("M", "2M", "L", "2L", "XL"):
        candidates.append(re.sub(r"/catalog/[A-Z0-9]+/", f"/catalog/{prefix}/", url, count=1))
    # dedupe preserve order
    seen: set[str] = set()
    out: list[str] = []
    for u in candidates:
        if u not in seen:
            seen.add(u)
            out.append(u)
    return out


def parse_coords(raw: str) -> tuple[int, int, int, int]:
    parts = [int(x.strip()) for x in raw.split(",")[:4]]
    return parts[0], parts[1], parts[2], parts[3]


def classify_diagram(areas: list[tuple[int, int, int, int, str]]) -> str:
    if not areas:
        return "no_hotspots"
    ys = [a[1] for a in areas] + [a[3] for a in areas]
    xs = [a[0] for a in areas] + [a[2] for a in areas]
    y_span = max(ys) - min(ys)
    x_span = max(xs) - min(xs)
    # Gasket-kit "list" images: hotspots cluster on one horizontal band (y span ~10-50)
    if y_span < 80 and x_span > 200:
        return "parts_list_table_image"
    if y_span > 150 and x_span > 150:
        return "exploded_diagram"
    return "ambiguous"


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    with httpx.Client(headers=HEADERS, timeout=45, follow_redirects=True) as client:
        r = client.get(DIAGRAM_URL)
        html = r.text
        (OUT / "diagram_cylinder_block_2293503.html").write_text(html, encoding="utf-8")

        m = IMG_RE.search(html) or IMG_RE2.search(html)
        if not m:
            log("ERROR: no items_list_image found")
            return 1

        if m.lastindex and m.lastindex >= 5:
            html_w, html_h = m.group(1), m.group(2)
            img_url = m.group(5)
        else:
            html_w, html_h = None, None
            img_url = m.group(1)

        areas_raw = AREA_RE.findall(html)
        # dedupe areas by (coords, id)
        seen_a: set[tuple[str, str]] = set()
        areas: list[tuple[int, int, int, int, str]] = []
        for coords, lid in areas_raw:
            key = (coords, lid)
            if key in seen_a:
                continue
            seen_a.add(key)
            x1, y1, x2, y2 = parse_coords(coords)
            areas.append((x1, y1, x2, y2, lid))

        parts = {pid: num.strip() for pid, num in PART_RE.findall(html)}
        refs = {pid: ref.strip() for pid, ref in REF_RE.findall(html)}

        best_url = img_url
        best_bytes = b""
        best_size = (0, 0)
        for candidate in upscale_url(img_url):
            try:
                ir = client.get(candidate)
                if ir.status_code != 200 or "image" not in ir.headers.get("content-type", ""):
                    continue
                if Image:
                    im = Image.open(BytesIO(ir.content))
                    w, h = im.size
                else:
                    w, h = 0, len(ir.content)
                if w * h >= best_size[0] * best_size[1]:
                    best_url = candidate
                    best_bytes = ir.content
                    best_size = (w, h)
            except Exception:
                continue

        png_path = OUT / "diagram_cylinder_block.png"
        png_path.write_bytes(best_bytes)

        img_w, img_h = best_size
        html_w_i = int(html_w) if html_w else img_w
        html_h_i = int(html_h) if html_h else img_h

        violations: list[str] = []
        mapped: list[dict] = []
        for x1, y1, x2, y2, lid in areas:
            entry = {
                "items_list_id": lid,
                "ref": refs.get(lid, ""),
                "part_number": parts.get(lid, ""),
                "coords": [x1, y1, x2, y2],
            }
            mapped.append(entry)
            if x2 > html_w_i or y2 > html_h_i:
                violations.append(f"id={lid} coords exceed HTML dims {html_w_i}x{html_h_i}: {x1,y1,x2,y2}")
            if img_w and (x2 > img_w or y2 > img_h):
                violations.append(f"id={lid} coords exceed PNG dims {img_w}x{img_h}")

        kind = classify_diagram(areas)

        overlay_path = OUT / "diagram_cylinder_block_hotspots.png"
        if Image and best_bytes and areas:
            im = Image.open(BytesIO(best_bytes)).convert("RGBA")
            draw = ImageDraw.Draw(im, "RGBA")
            scale_x = im.width / html_w_i if html_w_i else 1.0
            scale_y = im.height / html_h_i if html_h_i else 1.0
            for x1, y1, x2, y2, lid in areas:
                sx1 = int(x1 * scale_x)
                sy1 = int(y1 * scale_y)
                sx2 = int(x2 * scale_x)
                sy2 = int(y2 * scale_y)
                draw.rectangle([sx1, sy1, sx2, sy2], outline=(255, 0, 0, 220), width=2)
                label = refs.get(lid) or parts.get(lid, lid)[:8]
                draw.text((sx1 + 2, sy1 + 2), label[:12], fill=(255, 255, 0, 255))
            im.save(overlay_path)

        report = {
            "diagram_url": DIAGRAM_URL,
            "title": (re.search(r"<title>([^<]+)</title>", html) or [None, ""])[1],
            "diagram_kind": kind,
            "wrong_probe_example": {
                "name": "Engine gasket kit",
                "kind": "parts_list_table_image",
                "note": "Previous probe picked a kit/list thumbnail — not an exploded drawing",
            },
            "image": {
                "html_src": img_url,
                "best_download_url": best_url,
                "html_dimensions": {"width": html_w_i, "height": html_h_i},
                "png_dimensions": {"width": img_w, "height": img_h},
                "bytes": len(best_bytes),
                "local_png": str(png_path),
                "overlay_png": str(overlay_path) if overlay_path.exists() else None,
            },
            "hotspots": {
                "count_unique": len(areas),
                "count_raw_in_html": len(areas_raw),
                "coord_y_span": max(a[3] for a in areas) - min(a[1] for a in areas) if areas else 0,
                "coord_x_span": max(a[2] for a in areas) - min(a[0] for a in areas) if areas else 0,
                "mapping_violations": violations,
                "mapping_ok": len(violations) == 0,
                "samples": mapped[:8],
            },
        }

        report["parser_check"] = {}
        try:
            import sys

            repo = Path(__file__).resolve().parents[3] / "data-pipeline"
            if str(repo) not in sys.path:
                sys.path.insert(0, str(repo))
            from data_pipeline.megazip.parse_html import parse_diagram_page

            parsed = parse_diagram_page(
                html,
                DIAGRAM_URL,
                "nissan",
                "nissan-gt-r-2063",
                "r35-181277",
                "cylinder-block-oil-pan-2293503",
                default_chassis="R35",
                image_bytes=best_bytes,
            )
            with_hotspot = [p for p in parsed.payload["parts"] if p.get("bbox_x") is not None]
            report["parser_check"] = {
                "image_width": parsed.payload["image_width"],
                "image_height": parsed.payload["image_height"],
                "parts_with_bbox": len(with_hotspot),
                "sample_parts": with_hotspot[:5],
            }
        except Exception as exc:
            report["parser_check"] = {"error": str(exc)}

        report_path = OUT / "diagram_verification.json"
        report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        log(json.dumps(report, indent=2, ensure_ascii=False))
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
