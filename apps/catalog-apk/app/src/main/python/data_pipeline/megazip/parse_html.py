"""Megazip HTML parser — EPC hierarchy, image-map hotspots, parts tables."""

from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass, field
from html import unescape
from typing import Any
from urllib.parse import urljoin, urlparse

from data_pipeline.parse_partsouq_html import normalize_chassis_code

_MAKER_HUB_MODEL = re.compile(
    r'href="(/parts/[^"]+|/zapchasti-dlya-avtomobilej/[^"]+)"[^>]*>([^<]{2,120})',
    re.I,
)
_VARIANT_ITEM = re.compile(
    r'<li[^>]*class="[^"]*s-catalog__body-variants-item[^"]*"[^>]*data-id="(\d+)"[^>]*>(.*?)</li>',
    re.I | re.S,
)
_VARIANT_LINK = re.compile(
    r'<a[^>]*class="[^"]*s-catalog__body-variants-name[^"]*"[^>]*href="([^"]+)"',
    re.I | re.S,
)
_VARIANT_LINK_ALT = re.compile(
    r'href="([^"]+)"[^>]*class="[^"]*s-catalog__body-variants-name',
    re.I,
)
_VARIANT_FRAME = re.compile(
    r'class="[^"]*search_value[^"]*"[^>]*>\s*([^<]+?)\s*<',
    re.I,
)
_MODEL_CATALOG_VARIANT = re.compile(
    r'<li[^>]*class="[^"]*filtred_item[^"]*"[^>]*data-id="(\d+)"[^>]*>\s*'
    r'<a[^>]*class="[^"]*s-catalog__model-link[^"]*"[^>]*href="([^"]+)"[^>]*>([^<]+)</a>',
    re.I | re.S,
)
_ATTR_TERM = re.compile(
    r'<dt[^>]*class="[^"]*s-catalog__attrs-term[^"]*"[^>]*>([^<]+)</dt>\s*'
    r'<dd[^>]*class="[^"]*s-catalog__attrs-data[^"]*"[^>]*>(.*?)</dd>',
    re.I | re.S,
)
_SECTION_ITEM = re.compile(
    r'<li[^>]*class="[^"]*part-group__item[^"]*"[^>]*id="part-group-(\d+)"[^>]*>(.*?)</li>',
    re.I | re.S,
)
_SECTION_LINK = re.compile(r'href="([^"]+)"[^>]*class="[^"]*part-group__(?:image-link|name)', re.I)
_SECTION_NAME = re.compile(r'class="[^"]*part-group__name[^"]*"[^>]*>([^<]+)<', re.I)
_SECTION_IMG = re.compile(r'src="(https://storage\.megazip\.net/catalog/[^"]+)"', re.I)
_DIAGRAM_IMG = re.compile(
    r'id="items_list_image"[^>]*src="(https://storage\.megazip\.net/catalog/[^"]+)"',
    re.I,
)
_MAP_AREA = re.compile(
    r'<area[^>]*shape="rect"[^>]*coords="([\d,\s]+)"[^>]*data-items-list-id="(\d+)"',
    re.I,
)
_PART_ROW = re.compile(
    r'data-items-list-id="(\d+)"[^>]*>.*?'
    r'(?:<td[^>]*>.*?</td>\s*){0,6}'
    r'.*?items-list__cell_type_number[^"]*"[^>]*>([^<]*)</td>',
    re.I | re.S,
)
_OEM_IN_ROW = re.compile(
    r'data-items-list-id="(\d+)"[\s\S]{0,2500}?'
    r'items-list__cell_type_number[^"]*"[^>]*>\s*(?:<p[^>]*>\s*)?([^<]+?)\s*(?:</p>)?\s*</td>',
    re.I,
)
_DATA_ITEM = re.compile(
    r'data-item="(\{&quot;[^"]+&quot;[^"]*\})"',
    re.I,
)
_ITEMS_LIST_ROW = re.compile(
    r'<tr[^>]*data-items-list-id="(\d+)"[^>]*>(.*?)</tr>',
    re.I | re.S,
)
_ITEMS_LIST_NUMBER = re.compile(
    r'class="[^"]*items-list__cell_type_number[^"]*"[^>]*>\s*(?:<p[^>]*class="[^"]*items-list__number[^"]*"[^>]*>\s*)?([^<]+?)\s*(?:</p>)?\s*</td>',
    re.I,
)
_ITEMS_LIST_REF = re.compile(
    r'class="[^"]*items-list__cell_type_ref[^"]*"[^>]*>\s*([^<]+?)\s*</td>',
    re.I,
)
_ITEMS_LIST_QTY = re.compile(
    r'class="[^"]*items-list__cell_type_quantity[^"]*"[^>]*>\s*([^<]+?)\s*</td>',
    re.I,
)
_DIAGRAM_IMG_DIMS = re.compile(
    r'id="items_list_image"[^>]*?(?:width="(\d+)"[^>]*height="(\d+)"|height="(\d+)"[^>]*width="(\d+)")',
    re.I,
)
_SLUG_TAIL = re.compile(r"-(\d+)$")

_SECTION_DEPRIORITIZE = (
    "gasket kit",
    "standard tool",
    "owner",
    "manual",
    "component parts",
)


def _clean(text: str) -> str:
    t = unescape(re.sub(r"<[^>]+>", " ", text))
    return re.sub(r"\s+", " ", t).strip()


def _slug_from_path(path: str) -> str:
    path = path.rstrip("/")
    return path.rsplit("/", 1)[-1] if path else ""


def _sort_key(name: str) -> str:
    return re.sub(r"^\W+", "", name.upper())


def classify_megazip_url(url: str) -> str:
    path = urlparse(url).path.lower()
    if "/parts/" in path and path.count("/") <= 3:
        return "maker_hub" if path.rstrip("/").count("/") == 2 else "model_hub"
    if "items_list_image" in path or re.search(r"/[^/]+-\d+$", path):
        if "part-group" in path or re.search(r"/[a-z0-9-]+-\d+$", path):
            parts = path.rstrip("/").split("/")
            if len(parts) >= 7:
                return "diagram"
    if "part-group" in path:
        return "section_list"
    if "s-catalog__body-variants" in path:
        return "variant_list"
    if re.search(r"/zapchasti-dlya-avtomobilej/", path):
        depth = path.rstrip("/").count("/")
        if depth <= 3:
            return "model_catalog"
        if depth == 4:
            return "variant_list"
        if depth == 5:
            return "section_list"
        return "diagram"
    return "unknown"


@dataclass
class ParsedPage:
    url: str
    page_type: str
    maker_slug: str = ""
    payload: dict[str, Any] = field(default_factory=dict)


def _is_maker_model_catalog_href(href: str, maker_slug: str) -> bool:
    if maker_slug not in href.lower():
        return False
    path = href.split("?")[0].rstrip("/")
    slug = _slug_from_path(path)
    if not slug or slug.lower() == maker_slug.lower():
        return False
    parts = [p for p in path.split("/") if p]
    if parts and parts[0] == "zapchasti-dlya-avtomobilej":
        if len(parts) < 3 or parts[1].lower() != maker_slug.lower():
            return False
        return bool(re.search(r"-\d+$", slug))
    if parts and parts[0] == "parts":
        if len(parts) < 3 or parts[1].lower() != maker_slug.lower():
            return False
        return True
    return False


def parse_maker_hub(html: str, url: str, maker_slug: str) -> ParsedPage:
    by_slug: dict[str, dict[str, Any]] = {}

    def add(href: str, label: str) -> None:
        if not _is_maker_model_catalog_href(href, maker_slug):
            return
        slug = _slug_from_path(href.split("?")[0])
        name = _clean(label) or slug.replace("-", " ").upper()
        if len(name) < 2:
            return
        entry = {
            "slug": slug,
            "display_name": name,
            "sort_key": _sort_key(name),
            "source_url": urljoin(url, href.split("?")[0]),
        }
        existing = by_slug.get(slug)
        if existing is None or len(name) > len(existing["display_name"]):
            by_slug[slug] = entry

    for href, label in _MAKER_HUB_MODEL.findall(html):
        add(href, label)

    for m in re.finditer(
        r'<a\s+([^>]*?\shref="(/(?:parts|zapchasti-dlya-avtomobilej)/[^"]+)"[^>]*)>',
        html,
        re.I | re.S,
    ):
        attrs = m.group(1)
        href = m.group(2)
        if maker_slug not in href.lower():
            continue
        if "sil-card" not in attrs and "s-catalog__model-link" not in attrs:
            continue
        name_m = re.search(r'data-name="([^"]+)"', attrs, re.I)
        name = name_m.group(1) if name_m else ""
        if not name:
            chunk = html[m.end() : m.end() + 800]
            h3 = re.search(r"<h3[^>]*>([^<]+)</h3>", chunk, re.I)
            name = h3.group(1) if h3 else ""
        add(href, name)

    for block in re.finditer(
        r'<script type="application/ld\+json">(.*?)</script>',
        html,
        re.I | re.S,
    ):
        try:
            data = json.loads(block.group(1))
        except json.JSONDecodeError:
            continue
        if data.get("@type") != "ItemList":
            continue
        for item in data.get("itemListElement") or []:
            if not isinstance(item, dict):
                continue
            item_url = str(item.get("url") or "")
            name = str(item.get("name") or "")
            if item_url:
                add(urlparse(item_url).path, name)

    models = sorted(by_slug.values(), key=lambda m: m["sort_key"])
    return ParsedPage(url, "maker_hub", maker_slug, {"models": models})


def _engine_from_attrs(attrs: dict[str, str]) -> str:
    """Pull engine code from Megazip attr dl terms (EN/RU common labels)."""
    for key in (
        "engine",
        "engine_code",
        "engine_model",
        "двигатель",
        "engine_",
    ):
        val = (attrs.get(key) or "").strip()
        if val:
            return val
    for key, val in attrs.items():
        if "engine" in key or key.startswith("двиг"):
            cleaned = (val or "").strip()
            if cleaned:
                return cleaned
    return ""


def _parse_attrs(block: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for term, val in _ATTR_TERM.findall(block):
        key = _clean(term).lower().replace(" ", "_")
        out[key] = _clean(val)
    return out


def _variant_href(block: str) -> str | None:
    m = _VARIANT_LINK.search(block) or _VARIANT_LINK_ALT.search(block)
    return m.group(1) if m else None


def _variant_frame_label(block: str, attrs: dict[str, str]) -> str:
    frame = attrs.get("frame") or attrs.get("frame_") or ""
    if frame:
        return frame
    frames = [_clean(x) for x in _VARIANT_FRAME.findall(block) if _clean(x)]
    return frames[0] if frames else ""


def parse_variant_list(html: str, url: str, maker_slug: str, model_slug: str) -> ParsedPage:
    variants: list[dict[str, Any]] = []
    for data_id, block in _VARIANT_ITEM.findall(html):
        href_raw = _variant_href(block)
        href = urljoin(url, href_raw) if href_raw else url
        attrs = _parse_attrs(block)
        frame = _variant_frame_label(block, attrs)
        chassis = normalize_chassis_code(frame) or frame.upper()
        slug = _slug_from_path(href)
        variants.append(
            {
                "slug": slug,
                "megazip_data_id": data_id,
                "chassis_code": chassis,
                "frame": frame,
                "grade": attrs.get("grade", ""),
                "sales_region": attrs.get("sales_region", ""),
                "year_label": attrs.get("year", ""),
                "engine_code": _engine_from_attrs(attrs),
                "source_url": href,
            }
        )
    if not variants:
        for data_id, href, label in _MODEL_CATALOG_VARIANT.findall(html):
            slug = _slug_from_path(href)
            frame = _clean(label)
            chassis = normalize_chassis_code(frame) or frame.upper()
            variants.append(
                {
                    "slug": slug,
                    "megazip_data_id": data_id,
                    "chassis_code": chassis,
                    "frame": frame,
                    "grade": "",
                    "sales_region": "",
                    "year_label": "",
                    "engine_code": "",
                    "source_url": urljoin(url, href),
                }
            )
    return ParsedPage(
        url,
        "variant_list",
        maker_slug,
        {"model_slug": model_slug, "variants": variants},
    )


def parse_section_list(html: str, url: str, maker_slug: str, model_slug: str, variant_slug: str) -> ParsedPage:
    sections: list[dict[str, Any]] = []
    for group_id, block in _SECTION_ITEM.findall(html):
        link_m = _SECTION_LINK.search(block)
        href = urljoin(url, link_m.group(1)) if link_m else url
        name_m = _SECTION_NAME.search(block)
        img_m = _SECTION_IMG.search(block)
        name = _clean(name_m.group(1)) if name_m else _slug_from_path(href).replace("-", " ").title()
        sections.append(
            {
                "slug": _slug_from_path(href),
                "assembly_group_id": group_id,
                "name": name,
                "thumbnail_url": img_m.group(1) if img_m else None,
                "source_url": href,
            }
        )
    sections.sort(key=lambda s: _section_rank(s.get("name") or ""))
    return ParsedPage(
        url,
        "section_list",
        maker_slug,
        {"model_slug": model_slug, "variant_slug": variant_slug, "sections": sections},
    )


def _png_dimensions(data: bytes) -> tuple[int, int] | None:
    if len(data) >= 24 and data[:8] == b"\x89PNG\r\n\x1a\n":
        return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")
    return None


def _diagram_image_dimensions(
    html: str,
    *,
    image_bytes: bytes | None = None,
    stored_width: int | None = None,
    stored_height: int | None = None,
) -> tuple[int, int]:
    if stored_width and stored_height and stored_width > 0 and stored_height > 0:
        return int(stored_width), int(stored_height)
    tag_m = _DIAGRAM_IMG_DIMS.search(html)
    if tag_m:
        w = tag_m.group(1) or tag_m.group(4)
        h = tag_m.group(2) or tag_m.group(3)
        if w and h:
            return int(w), int(h)
    if image_bytes:
        dims = _png_dimensions(image_bytes)
        if dims:
            return dims
    return 560, 819


def _parse_data_item_json(html: str) -> dict[str, dict[str, str]]:
    import json

    out: dict[str, dict[str, str]] = {}
    for raw in _DATA_ITEM.findall(html):
        try:
            blob = json.loads(unescape(raw))
        except json.JSONDecodeError:
            continue
        item_id = str(blob.get("itemslist_id") or blob.get("id") or "")
        if not item_id:
            continue
        out[item_id] = {
            "itemslist_id": item_id,
            "oem_part_number": str(blob.get("number") or "").strip().upper(),
            "callout_ref": str(blob.get("ref") or "").strip(),
            "description": str(blob.get("name") or "").strip(),
            "quantity": str(blob.get("quantity") or blob.get("qty") or "").strip(),
            "megazip_item_id": str(blob.get("id") or item_id).strip(),
        }
    return out


def _dedupe_map_areas(html: str) -> list[tuple[str, str]]:
    seen: set[tuple[str, str]] = set()
    areas: list[tuple[str, str]] = []
    for coords, item_id in _MAP_AREA.findall(html):
        key = (re.sub(r"\s+", "", coords.strip()), item_id)
        if key in seen:
            continue
        seen.add(key)
        areas.append((coords, item_id))
    return areas


def _area_coord_spans(areas: list[tuple[str, str]]) -> tuple[int, int]:
    xs: list[int] = []
    ys: list[int] = []
    for coords, _ in areas:
        parts = [int(x.strip()) for x in coords.split(",") if x.strip().isdigit()]
        if len(parts) != 4:
            continue
        x1, y1, x2, y2 = parts
        xs.extend((x1, x2))
        ys.extend((y1, y2))
    if not xs or not ys:
        return 0, 0
    return max(xs) - min(xs), max(ys) - min(ys)


def classify_diagram_kind(html: str) -> str:
    """Classify diagram page from hotspot geometry (Megazip image-map areas)."""
    areas = _dedupe_map_areas(html)
    if not areas:
        return "ambiguous"
    x_span, y_span = _area_coord_spans(areas)
    if y_span < 80 and x_span > 200:
        return "parts_list_raster"
    if y_span > 150:
        return "exploded_diagram"
    return "ambiguous"


def _section_rank(name: str) -> tuple[int, str]:
    lower = name.lower()
    score = 0
    if "assembly" in lower:
        score += 100
    for term in _SECTION_DEPRIORITIZE:
        if term in lower:
            score -= 50
    return (-score, _sort_key(name))


def _parse_parts_table(html: str) -> list[dict[str, Any]]:
    data_items = _parse_data_item_json(html)
    rows: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item_id, block in _ITEMS_LIST_ROW.findall(html):
        if item_id in seen:
            continue
        seen.add(item_id)
        meta = data_items.get(item_id, {})
        num_m = _ITEMS_LIST_NUMBER.search(block)
        ref_m = _ITEMS_LIST_REF.search(block)
        qty_m = _ITEMS_LIST_QTY.search(block)
        oem_raw = (meta.get("oem_part_number") or (num_m.group(1) if num_m else "")).strip()
        oem = re.sub(r"\s+", "", oem_raw.upper())
        if not oem or oem in ("—", "-", "SHOW"):
            continue
        rows.append(
            {
                "itemslist_id": item_id,
                "callout_ref": meta.get("callout_ref") or (ref_m.group(1).strip() if ref_m else None),
                "oem_part_number": oem,
                "description": meta.get("description") or None,
                "quantity": meta.get("quantity") or (qty_m.group(1).strip() if qty_m else None),
                "megazip_item_id": meta.get("megazip_item_id") or item_id,
            }
        )
    if rows:
        return rows
    for item_id, meta in data_items.items():
        oem = re.sub(r"\s+", "", meta.get("oem_part_number", "").upper())
        if not oem or oem in ("—", "-", "SHOW"):
            continue
        rows.append(
            {
                "itemslist_id": item_id,
                "callout_ref": meta.get("callout_ref") or None,
                "oem_part_number": oem,
                "description": meta.get("description") or None,
                "quantity": meta.get("quantity") or None,
                "megazip_item_id": meta.get("megazip_item_id") or item_id,
            }
        )
    return rows


def normalize_bbox(
    coords: str, img_width: int, img_height: int
) -> tuple[float, float, float, float] | None:
    parts = [int(x.strip()) for x in coords.split(",") if x.strip().isdigit()]
    if len(parts) != 4 or img_width <= 0 or img_height <= 0:
        return None
    x1, y1, x2, y2 = parts
    left, top = min(x1, x2), min(y1, y2)
    width, height = abs(x2 - x1), abs(y2 - y1)
    if width <= 0 or height <= 0:
        return None
    return (
        round(left / img_width, 6),
        round(top / img_height, 6),
        round(width / img_width, 6),
        round(height / img_height, 6),
    )


def parse_diagram_page(
    html: str,
    url: str,
    maker_slug: str,
    model_slug: str,
    variant_slug: str,
    section_slug: str,
    *,
    default_chassis: str = "",
    default_engine: str = "",
    image_bytes: bytes | None = None,
    stored_width: int | None = None,
    stored_height: int | None = None,
) -> ParsedPage:
    img_m = _DIAGRAM_IMG.search(html)
    image_url = img_m.group(1) if img_m else ""
    title_m = re.search(r"<title>([^<]+)</title>", html, re.I)
    title = _clean(title_m.group(1)) if title_m else section_slug.replace("-", " ").title()
    img_width, img_height = _diagram_image_dimensions(
        html,
        image_bytes=image_bytes,
        stored_width=stored_width,
        stored_height=stored_height,
    )
    diagram_kind = classify_diagram_kind(html)

    hotspots: dict[str, tuple[float, float, float, float]] = {}
    for coords, item_id in _dedupe_map_areas(html):
        bb = normalize_bbox(coords, img_width, img_height)
        if bb:
            hotspots[item_id] = bb

    parts_table = _parse_parts_table(html)
    for row in parts_table:
        bb = hotspots.get(row["itemslist_id"])
        if bb:
            row["bbox_x"], row["bbox_y"], row["bbox_width"], row["bbox_height"] = bb

    data_items = _parse_data_item_json(html)
    parts: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for item_id, oem_raw in _OEM_IN_ROW.findall(html):
        if item_id in seen_ids:
            continue
        seen_ids.add(item_id)
        meta = data_items.get(item_id, {})
        oem = re.sub(r"\s+", "", (meta.get("oem_part_number") or oem_raw).upper())
        if not oem or oem in ("—", "-", "SHOW"):
            continue
        pnc = f"MZ{item_id}"[:8]
        bb = hotspots.get(item_id)
        row: dict[str, Any] = {
            "itemslist_id": item_id,
            "megazip_item_id": meta.get("megazip_item_id") or item_id,
            "oem_part_number": oem,
            "pnc_code": pnc,
            "chassis_code": default_chassis,
            "engine_code": default_engine or None,
            "category_name": title,
            "callout_ref": meta.get("callout_ref") or None,
            "description": meta.get("description") or None,
            "quantity": meta.get("quantity") or None,
        }
        if bb:
            row["bbox_x"], row["bbox_y"], row["bbox_width"], row["bbox_height"] = bb
        parts.append(row)
    for item_id, meta in data_items.items():
        if item_id in seen_ids:
            continue
        oem = re.sub(r"\s+", "", meta.get("oem_part_number", "").upper())
        if not oem:
            continue
        bb = hotspots.get(item_id)
        row = {
            "itemslist_id": item_id,
            "megazip_item_id": meta.get("megazip_item_id") or item_id,
            "oem_part_number": oem,
            "pnc_code": f"MZ{item_id}"[:8],
            "chassis_code": default_chassis,
            "engine_code": default_engine or None,
            "category_name": title,
            "callout_ref": meta.get("callout_ref") or None,
            "description": meta.get("description") or None,
            "quantity": meta.get("quantity") or None,
        }
        if bb:
            row["bbox_x"], row["bbox_y"], row["bbox_width"], row["bbox_height"] = bb
        parts.append(row)

    return ParsedPage(
        url,
        "diagram",
        maker_slug,
        {
            "model_slug": model_slug,
            "variant_slug": variant_slug,
            "section_slug": section_slug,
            "title": title,
            "image_url": image_url,
            "image_width": img_width,
            "image_height": img_height,
            "diagram_kind": diagram_kind,
            "hotspot_count": len(hotspots),
            "parts_table": parts_table,
            "parts": parts,
        },
    )


def parse_html_page(
    html: str,
    url: str,
    *,
    maker_slug: str,
    model_slug: str = "",
    variant_slug: str = "",
    section_slug: str = "",
    default_chassis: str = "",
    default_engine: str = "",
    image_bytes: bytes | None = None,
    stored_width: int | None = None,
    stored_height: int | None = None,
) -> ParsedPage:
    page_type = classify_megazip_url(url)
    if page_type == "maker_hub" or (page_type == "model_hub" and not model_slug):
        return parse_maker_hub(html, url, maker_slug)
    if page_type in ("variant_list", "model_catalog"):
        ms = model_slug or _infer_model_slug(url)
        return parse_variant_list(html, url, maker_slug, ms)
    if page_type == "section_list":
        ms = model_slug or _infer_model_slug(url)
        vs = variant_slug or _infer_variant_slug(url)
        return parse_section_list(html, url, maker_slug, ms, vs)
    if page_type == "diagram":
        ms = model_slug or _infer_model_slug(url)
        vs = variant_slug or _infer_variant_slug(url)
        ss = section_slug or _infer_section_slug(url)
        return parse_diagram_page(
            html,
            url,
            maker_slug,
            ms,
            vs,
            ss,
            default_chassis=default_chassis,
            default_engine=default_engine,
            image_bytes=image_bytes,
            stored_width=stored_width,
            stored_height=stored_height,
        )
    return ParsedPage(url, page_type, maker_slug, {})


def _infer_model_slug(url: str) -> str:
    parts = urlparse(url).path.rstrip("/").split("/")
    if len(parts) >= 4:
        return parts[3]
    return ""


def _infer_variant_slug(url: str) -> str:
    parts = urlparse(url).path.rstrip("/").split("/")
    if len(parts) >= 5:
        return parts[4]
    return ""


def _infer_section_slug(url: str) -> str:
    return _slug_from_path(urlparse(url).path)


def cache_key(url: str) -> str:
    return hashlib.sha256(url.encode("utf-8")).hexdigest()[:32]
