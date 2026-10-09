"""Parse PartSouq parts-page HTML into catalog payloads (parts + hotspot boxes).

PartSouq does not embed JSON catalogs. Parts and clickable diagram labels live in
HTML (``part-search-tr`` rows and ``.item.lable`` hotspots with ``data-position``).
"""

from __future__ import annotations

import re
from typing import Any
from urllib.parse import parse_qs, urljoin, urlparse

_ATTR_RE = re.compile(
    r"""([^\s=]+)\s*=\s*(?:\"([^\"]*)\"|'([^']*)')""",
    re.IGNORECASE,
)
_ZOOM_SPLIT_RE = re.compile(
    r'(?=<div[^>]+id="zoom_container_\d+")',
    re.IGNORECASE,
)
_DRAG_IMG_RE = re.compile(
    r"<img\b([^>]*\bclass=\"[^\"]*\bdrag\b[^\"]*\"[^>]*)>",
    re.IGNORECASE,
)
_LABEL_RE = re.compile(
    r"<div\b([^>]*\blable-single\b[^>]*)>",
    re.IGNORECASE,
)
_PART_ROW_RE = re.compile(
    r'<tr\b[^>]*class="[^"]*\bpart-search-tr\b[^"]*"[^>]*>(.*?)</tr>',
    re.IGNORECASE | re.DOTALL,
)
_OEM_HREF_RE = re.compile(
    r'class="oem"[^>]*>.*?<a[^>]+href="[^"]*[?&]q=([^\"&]+)(?:&[^"]*)?"[^>]*>([^<]*)</a>',
    re.IGNORECASE | re.DOTALL,
)
_CODE_TD_RE = re.compile(
    r'class="codeonimage"[^>]*>([^<]*)</td>',
    re.IGNORECASE,
)
_ENGINE_TD_RE = re.compile(
    r'<td\b[^>]*class="[^"]*\bhidden-xs\b[^"]*"[^>]*>([^<]+)</td>',
    re.IGNORECASE,
)
_WH_RE = re.compile(r"width:\s*(\d+)px;\s*height:\s*(\d+)px", re.IGNORECASE)
_ENGINE_TOKEN_RE = re.compile(r"\b([A-Z]{2,3}\d{2}[A-Z]{0,3})\b")


def _attrs(tag_attrs: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for match in _ATTR_RE.finditer(tag_attrs):
        key = match.group(1).lower()
        out[key] = match.group(2) if match.group(2) is not None else (match.group(3) or "")
    return out


def _parse_pair(raw: str | None) -> tuple[float, float] | None:
    if not raw:
        return None
    parts = [p.strip() for p in raw.split(",")]
    if len(parts) != 2:
        return None
    try:
        return float(parts[0]), float(parts[1])
    except ValueError:
        return None


def _absolute_url(src: str, *, base_url: str) -> str:
    if src.startswith(("http://", "https://")):
        return src
    return urljoin(base_url.rstrip("/") + "/", src.lstrip("/"))


def _table_part_index(html: str) -> dict[str, dict[str, str]]:
    """Map OEM / code-on-image → row fields from the parts table."""
    index: dict[str, dict[str, str]] = {}
    for row_html in _PART_ROW_RE.findall(html):
        oem_match = _OEM_HREF_RE.search(row_html)
        if not oem_match:
            continue
        oem = (oem_match.group(1) or oem_match.group(2) or "").strip()
        if not oem:
            continue
        code_match = _CODE_TD_RE.search(row_html)
        code = (code_match.group(1).strip() if code_match else "") or ""
        engines = [t.strip() for t in _ENGINE_TD_RE.findall(row_html) if t.strip()]
        engine = ""
        for token in engines:
            if _ENGINE_TOKEN_RE.fullmatch(token.upper()):
                engine = token.upper()
                break
        name = ""
        tds = re.findall(r"<td\b[^>]*>(.*?)</td>", row_html, flags=re.IGNORECASE | re.DOTALL)
        if len(tds) >= 2:
            name = re.sub(r"<[^>]+>", "", tds[1]).strip()
        row = {"oem": oem, "codeonimage": code, "engine_code": engine, "name": name}
        index[oem.upper()] = row
        if code:
            index[code.upper()] = row
    return index


def _hints_from_alt(alt: str) -> dict[str, Any]:
    """Parse alts like ``NISSAN 200SX 07.1994 THROTTLE CHAMBER``."""
    hints: dict[str, Any] = {}
    cleaned = re.sub(r"\s+", " ", alt or "").strip()
    if not cleaned:
        return hints
    tokens = cleaned.split(" ")
    if tokens and tokens[0].upper() == "NISSAN" and len(tokens) >= 2:
        hints["model_variant"] = tokens[1]
    year_matches = re.findall(r"\b((?:19|20)\d{2})\b", cleaned)
    if year_matches:
        year = int(year_matches[0])
        hints["production_year"] = year
        hints["year_start"] = year
        hints["year_end"] = year
    return hints


def _hints_from_url(url: str) -> dict[str, Any]:
    hints: dict[str, Any] = {}
    if not url:
        return hints
    qs = parse_qs(urlparse(url).query)
    for key in ("vid", "gid"):
        values = qs.get(key) or qs.get(key.upper())
        if values and values[0]:
            hints[key] = values[0]
    return hints


_GENERIC_PART_NAMES = frozenset(
    {
        "BOLT",
        "NUT",
        "SCREW",
        "WASHER",
        "PIN",
        "CLIP",
        "CLAMP",
        "RIVET",
        "GROMMET",
        "O-RING",
        "ORING",
        "SEAL",
        "PLUG",
        "CAP",
    }
)

# Leading tokens that are assembly names, not vehicle models.
_ASSEMBLY_START_WORDS = frozenset(
    {
        "BRAKE",
        "ENGINE",
        "POWER",
        "TRAIN",
        "AIR",
        "FUEL",
        "STEERING",
        "SUSPENSION",
        "TRANSMISSION",
        "CLUTCH",
        "EXHAUST",
        "COOLING",
        "HEATER",
        "WIPER",
        "DOOR",
        "HOOD",
        "ROOF",
        "FLOOR",
        "SEAT",
        "INSTRUMENT",
        "LIGHTING",
        "WIRING",
        "PISTON",
        "ELECTRICAL",
        "BODY",
        "CHASSIS",
        "INTERIOR",
        "EXTERIOR",
    }
)

_KNOWN_NISSAN_MODEL_HEADS = frozenset(
    {
        "ALMERA", "ALTIMA", "ARMADA", "BLUEBIRD", "CEFIRO", "CUBE",
        "FRONTIER", "JUKE", "LAUREL", "LEAF", "MAXIMA", "MICRA",
        "MURANO", "NAVARA", "NOTE", "PATHFINDER", "PATROL", "PRIMERA",
        "QASHQAI", "ROGUE", "SENTRA", "SILVIA", "SKYLINE", "SUNNY",
        "TERRANO", "TITAN", "VERSA", "X-TRAIL",
    }
)

_MODEL_HEAD_RE = re.compile(
    r"^("
    r"[A-Z0-9][A-Z0-9+]{1,20}"
    r"(?:\s*/\s*[A-Z0-9+][A-Z0-9+]{0,20})?"
    r"(?:\s+(?:COUPE|SEDAN|WAGON|HATCH|CAB|TRUCK|VAN))?"
    r")\s+(.+)$",
    re.IGNORECASE,
)


def is_generic_part_name(name: str | None) -> bool:
    if not name:
        return False
    token = re.sub(r"\s+", " ", str(name)).strip().upper()
    return token in _GENERIC_PART_NAMES


def strip_vehicle_model_prefix(title: str | None) -> str:
    """Strip leading maker/model tokens from diagram titles."""
    text = re.sub(r"\s+", " ", (title or "")).strip()
    if not text:
        return ""
    text = re.sub(r"^NISSAN\s+", "", text, flags=re.IGNORECASE)
    text = re.sub(r"\b0?[1-9]\.(?:19|20)\d{2}\b", " ", text)
    text = re.sub(r"\b(?:19|20)\d{2}\b", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    match = _MODEL_HEAD_RE.match(text)
    if not match:
        return text
    head, rest = match.group(1), match.group(2)
    head_u = head.upper()
    first = head_u.split()[0]
    first_base = first.split("/", 1)[0]
    rest_first = rest.upper().split()[0] if rest.strip() else ""
    looks_like_model = (
        bool(re.search(r"[\d+/]", head_u))
        or first_base in _KNOWN_NISSAN_MODEL_HEADS
        or (first not in _ASSEMBLY_START_WORDS and rest_first in _ASSEMBLY_START_WORDS)
    )
    if looks_like_model and rest.strip():
        return rest.strip(" -;,:")
    return text


def normalize_epc_category_name(name: str | None) -> str | None:
    if name is None:
        return None
    cleaned = strip_vehicle_model_prefix(str(name))
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" -;,:")
    return cleaned.upper() if cleaned else None


def category_hints_from_url(url: str) -> dict[str, str]:
    """Extract EPC category hints from PartSouq query params (``cname`` / ``cid``)."""
    hints: dict[str, str] = {}
    if not url:
        return hints
    qs = parse_qs(urlparse(url).query)
    for key in ("cname", "CNAME", "category", "cat"):
        values = qs.get(key)
        if values and values[0]:
            name = re.sub(r"\+", " ", values[0]).strip()
            name = re.sub(r"\s+", " ", name)
            if name:
                hints["category_name"] = name.upper()
                break
    return hints


def subcategory_from_diagram_title(title: str | None) -> str | None:
    """Legacy helper: only return an explicit ``; SUB`` tail when present."""
    hints = assembly_hints_from_diagram_title(title or "")
    return hints.get("subcategory_name")


def assembly_hints_from_diagram_title(title: str | None) -> dict[str, str]:
    """Parse diagram titles into assembly category / unit subcategory.

    Examples:
      ``QASHQAI+2 PISTON,CRANKSHAFT & FLYWHEEL; ILLUSTRATION``
      → category=PISTON,CRANKSHAFT & FLYWHEEL, subcategory=ILLUSTRATION
    """
    hints: dict[str, str] = {}
    raw = re.sub(r"\s+", " ", (title or "")).strip()
    if not raw:
        return hints
    body = strip_vehicle_model_prefix(raw)
    if ";" in body:
        left, right = body.split(";", 1)
        cat = normalize_epc_category_name(left)
        sub = normalize_epc_category_name(right)
        if cat:
            hints["category_name"] = cat
        if sub:
            hints["subcategory_name"] = sub
        return hints
    cat = normalize_epc_category_name(body)
    if cat and not is_generic_part_name(cat):
        hints["category_name"] = cat
    return hints


def parse_diagram_section(
    section_html: str,
    *,
    page_html: str,
    source_url: str = "",
    base_url: str = "https://partsouq.com",
    table_index: dict[str, dict[str, str]] | None = None,
) -> dict[str, Any] | None:
    """Parse one zoom-container block into a catalog payload, or None if empty."""
    img_match = _DRAG_IMG_RE.search(section_html)
    if not img_match:
        return None
    img_attrs = _attrs(img_match.group(1))
    src = img_attrs.get("src")
    if not src:
        return None

    container_wh = _WH_RE.search(section_html)
    container_w = float(container_wh.group(1)) if container_wh else 0.0
    container_h = float(container_wh.group(2)) if container_wh else 0.0

    index = table_index if table_index is not None else _table_part_index(page_html)
    parts: list[dict[str, Any]] = []
    max_right = 0.0
    max_bottom = 0.0

    for label_match in _LABEL_RE.finditer(section_html):
        attrs = _attrs(label_match.group(1))
        title = (attrs.get("data-title") or "").strip()
        code = (attrs.get("data-codeonimage") or "").strip()
        position = _parse_pair(attrs.get("data-position"))
        size = _parse_pair(attrs.get("data-size"))
        if not title or position is None or size is None:
            continue

        title_bits = title.split(None, 1)
        oem = title_bits[0]
        name = title_bits[1] if len(title_bits) > 1 else ""
        left, top = position
        width, height = size
        max_right = max(max_right, left + width)
        max_bottom = max(max_bottom, top + height)

        lookup = index.get(oem.upper()) or index.get(code.upper()) or {}
        part: dict[str, Any] = {
            "oem_part_number": oem,
            "part_number": oem,
            "pnc": code or lookup.get("codeonimage") or "",
            "codeonimage": code,
            "description": name or lookup.get("name") or "",
            "left": left,
            "top": top,
            "width": width,
            "height": height,
        }
        engine = lookup.get("engine_code")
        if engine:
            part["engine_code"] = engine
        parts.append(part)

    if not parts:
        return None

    image_width = max(container_w, max_right, 1.0)
    image_height = max(container_h, max_bottom, 1.0)
    for part in parts:
        part["image_width"] = image_width
        part["image_height"] = image_height

    alt = img_attrs.get("alt") or ""
    vehicle = {
        **_hints_from_url(source_url),
        **_hints_from_alt(alt),
    }
    engines = {p["engine_code"] for p in parts if p.get("engine_code")}
    if len(engines) == 1:
        vehicle["engine_code"] = next(iter(engines))

    url_category = category_hints_from_url(source_url)
    diagram_category = assembly_hints_from_diagram_title(alt)
    category_name = url_category.get("category_name") or diagram_category.get("category_name")
    subcategory_name = diagram_category.get("subcategory_name")
    if not subcategory_name and url_category.get("category_name"):
        diagram_name = diagram_category.get("category_name")
        if diagram_name and diagram_name != category_name:
            subcategory_name = diagram_name

    payload = {
        "image_url": _absolute_url(src, base_url=base_url),
        "image_width": image_width,
        "image_height": image_height,
        "vehicle": vehicle,
        "parts": parts,
        "source_url": source_url,
        "diagram_title": alt,
    }
    if category_name:
        payload["category_name"] = category_name
    if subcategory_name:
        payload["subcategory_name"] = subcategory_name
    return payload


def parse_partsouq_parts_html(
    html: str,
    *,
    source_url: str = "",
    base_url: str = "https://partsouq.com",
) -> list[dict[str, Any]]:
    """Return one catalog payload per diagram found on a PartSouq parts page."""
    if "lable-single" not in html and "part-search-tr" not in html:
        return []

    table_index = _table_part_index(html)
    payloads: list[dict[str, Any]] = []

    sections = _ZOOM_SPLIT_RE.split(html)
    for section in sections:
        if "zoom_container_" not in section.lower() and "lable-single" not in section.lower():
            continue
        payload = parse_diagram_section(
            section,
            page_html=html,
            source_url=source_url,
            base_url=base_url,
            table_index=table_index,
        )
        if payload:
            payloads.append(payload)

    # Fallback: whole page as one section (single-diagram pages / odd markup)
    if not payloads:
        payload = parse_diagram_section(
            html,
            page_html=html,
            source_url=source_url,
            base_url=base_url,
            table_index=table_index,
        )
        if payload:
            payloads.append(payload)

    return payloads


def is_partsouq_parts_html(html: str) -> bool:
    return "lable-single" in html or "part-search-tr" in html


_VEHICLE_CELL_RE = re.compile(
    r'<td[^>]*data-title="([^"]+)"[^>]*>(.*?)</td>',
    re.IGNORECASE | re.DOTALL,
)
_YEAR_FROM_RE = re.compile(r"\b((?:19|20)\d{2})\b")
_CSS_NOISE_RE = re.compile(r"(?:PX|PX\d)$", re.IGNORECASE)


def is_partsouq_vehicle_url(url: str) -> bool:
    path = urlparse(url).path.lower()
    return "/catalog/genuine/vehicle" in path or path.rstrip("/").endswith("/vehicle")


def is_partsouq_vehicle_html(html: str) -> bool:
    lower = html.lower()
    return 'data-title="model"' in lower and 'data-title="name"' in lower


def vid_from_url(url: str) -> str | None:
    if not url:
        return None
    qs = parse_qs(urlparse(url).query)
    values = qs.get("vid") or qs.get("VID")
    if values and str(values[0]).strip():
        return str(values[0]).strip()
    return None


def normalize_chassis_code(raw: str) -> str:
    """Normalize PartSouq Model codes like ``JJ10E`` → ``JJ10``."""
    code = re.sub(r"[^A-Z0-9]", "", (raw or "").upper())
    if not code:
        return ""
    while len(code) >= 3 and code[-1].isalpha() and any(c.isdigit() for c in code[:-1]):
        peeled = code[:-1]
        if re.fullmatch(r"[A-Z]{1,3}\d{2,3}", peeled) or re.fullmatch(
            r"[A-Z]\d{2}[A-Z]?", peeled
        ):
            return peeled
        code = peeled
    return code


def _cell_text(raw: str) -> str:
    text = re.sub(r"<[^>]+>", " ", raw or "")
    return re.sub(r"\s+", " ", text).strip()


def _vehicle_table_fields(html: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    for title, raw in _VEHICLE_CELL_RE.findall(html):
        key = title.strip().lower()
        if key in fields:
            continue
        value = _cell_text(raw)
        if value:
            fields[key] = value
    return fields


def _engine_from_vehicle_html(html: str, *, chassis_code: str = "") -> str | None:
    """Best-effort engine token from vehicle HTML, skipping chassis / CSS noise."""
    chassis = chassis_code.upper()
    fields = _vehicle_table_fields(html)
    raw_chassis = re.sub(r"[^A-Z0-9]", "", (fields.get("model") or "").upper())
    for match in _ENGINE_TOKEN_RE.finditer(html.upper()):
        token = match.group(1).upper()
        if token in {chassis, raw_chassis}:
            continue
        if _CSS_NOISE_RE.search(token):
            continue
        # Classic Nissan engines: MR20DE, YD25DDTi, HR16DE (not JJ10 / D40)
        if re.fullmatch(r"[A-Z]{2,3}\d{2}[A-Z]{1,4}", token):
            return token
    return None

def parse_partsouq_vehicle_html(
    html: str,
    *,
    source_url: str = "",
) -> dict[str, Any] | None:
    """Parse a PartSouq ``/vehicle`` page into a vid-keyed identity dict."""
    if not is_partsouq_vehicle_html(html):
        return None

    fields = _vehicle_table_fields(html)
    model_raw = fields.get("model") or ""
    chassis = normalize_chassis_code(model_raw)
    if not chassis and not fields.get("name"):
        return None

    vid = vid_from_url(source_url)
    if not vid:
        # Fall back to vid embedded in page links
        m = re.search(r"[?&]vid=(\d+)", html, flags=re.IGNORECASE)
        if m:
            vid = m.group(1)

    year: int | None = None
    year_raw = fields.get("modelyearfrom") or fields.get("model year from") or ""
    year_match = _YEAR_FROM_RE.search(year_raw) or _YEAR_FROM_RE.search(html[:8000])
    if year_match:
        year = int(year_match.group(1))

    engine = _engine_from_vehicle_html(html, chassis_code=chassis)

    identity: dict[str, Any] = {
        "vid": vid,
        "chassis_code": chassis or None,
        "chassis_raw": model_raw.upper().strip() or None,
        "model_variant": fields.get("name") or None,
        "grade": fields.get("grade") or fields.get("vehicle grade") or None,
        "market": fields.get("market") or None,
        "production_year": year,
        "year_start": year,
        "year_end": year,
        "engine_code": engine,
        "source_url": source_url or None,
    }
    return {k: v for k, v in identity.items() if v is not None}
