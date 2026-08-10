"""ACES XML parser — application / fitment rows (parse + stub apply).

GTR ``part_fitment`` remains EPC-authoritative for bbox + ``diagram_path``.
ACES apps are parsed into normalized records for future additive import once a
provenance/source column (or side table) exists. Do not merge ACES into EPC
hotspot rows.
"""

from __future__ import annotations

import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any

# Documented stub reason for CLI / enrichment reports.
ACES_APPLY_STUB_REASON = (
    "ACES application upsert is stubbed until (1) an additive fitment_source "
    "(aces|epc) column or aces_applications side table exists, (2) a VCdb "
    "BaseVehicle↔vehicle_master bridge is curated, and (3) licensed/supplier "
    "ACES XML is available. Parsed apps write to enrichment sidecars only — "
    "never replace EPC bbox/diagram_path authority on part_fitment."
)


@dataclass(frozen=True)
class AcesApp:
    """Normalized ACES App row (aftermarket application)."""

    part_number: str
    base_vehicle_id: int | None = None
    part_terminology_id: int | None = None
    position_id: int | None = None
    qty: int | None = None
    year: int | None = None
    make_id: int | None = None
    model_id: int | None = None
    qualifiers: tuple[str, ...] = ()


def _local(tag: str) -> str:
    if "}" in tag:
        return tag.rsplit("}", 1)[-1]
    return tag


def _text(el: ET.Element | None) -> str:
    if el is None or el.text is None:
        return ""
    return el.text.strip()


def _attr(el: ET.Element, *keys: str) -> str:
    lower = {k.lower(): v for k, v in el.attrib.items()}
    for key in keys:
        if key.lower() in lower:
            return (lower[key.lower()] or "").strip()
    for raw, value in el.attrib.items():
        if _local(raw).lower() in {k.lower() for k in keys}:
            return (value or "").strip()
    return ""


def _parse_int(raw: str) -> int | None:
    text = (raw or "").strip()
    if not text:
        return None
    try:
        return int(text)
    except ValueError:
        return None


def _child(parent: ET.Element, *names: str) -> ET.Element | None:
    wanted = {n.lower() for n in names}
    for child in parent:
        if _local(child.tag).lower() in wanted:
            return child
    return None


def _parse_app(el: ET.Element) -> AcesApp | None:
    part_el = _child(el, "Part", "PartNumber")
    part_number = _text(part_el) or _attr(el, "Part", "PartNumber")
    if not part_number:
        return None

    base = _child(el, "BaseVehicle")
    part_type = _child(el, "PartType", "PartTerminology")
    position = _child(el, "Position")
    qty_el = _child(el, "Qty", "Quantity")
    year_el = _child(el, "Years", "Year")

    qualifiers: list[str] = []
    for q in el:
        name = _local(q.tag)
        if name.lower() in {"mfrlabel", "qual", "qdbqualifier", "note"}:
            label = _text(q) or _attr(q, "id", "value")
            if label:
                qualifiers.append(f"{name}:{label}")

    make = _child(el, "Make")
    model = _child(el, "Model")

    return AcesApp(
        part_number=part_number.upper().strip(),
        base_vehicle_id=_parse_int(_attr(base, "id") if base is not None else "")
        or _parse_int(_text(base)),
        part_terminology_id=_parse_int(_attr(part_type, "id") if part_type is not None else "")
        or _parse_int(_text(part_type)),
        position_id=_parse_int(_attr(position, "id") if position is not None else "")
        or _parse_int(_text(position)),
        qty=_parse_int(_text(qty_el) or _attr(el, "Qty")),
        year=_parse_int(_attr(year_el, "from", "id") if year_el is not None else "")
        or _parse_int(_text(year_el)),
        make_id=_parse_int(_attr(make, "id") if make is not None else ""),
        model_id=_parse_int(_attr(model, "id") if model is not None else ""),
        qualifiers=tuple(qualifiers),
    )


def parse_aces_xml(source: str | Path | bytes) -> list[AcesApp]:
    """Parse ACES XML into application rows (order preserved; duplicates kept)."""
    if isinstance(source, Path):
        raw = source.read_bytes()
    elif isinstance(source, str) and not source.lstrip().startswith("<"):
        raw = Path(source).read_bytes()
    elif isinstance(source, str):
        raw = source.encode("utf-8")
    else:
        raw = source

    root = ET.fromstring(raw)
    apps: list[AcesApp] = []
    stack = [root]
    while stack:
        node = stack.pop()
        if _local(node.tag).lower() == "app":
            parsed = _parse_app(node)
            if parsed:
                apps.append(parsed)
            continue
        # depth-first reverse so document order is roughly preserved
        stack.extend(reversed(list(node)))
    return apps


def aces_app_to_dict(app: AcesApp) -> dict[str, Any]:
    return {
        "part_number": app.part_number,
        "base_vehicle_id": app.base_vehicle_id,
        "part_terminology_id": app.part_terminology_id,
        "position_id": app.position_id,
        "qty": app.qty,
        "year": app.year,
        "make_id": app.make_id,
        "model_id": app.model_id,
        "qualifiers": list(app.qualifiers),
        "source": "aces",
    }


def stub_apply_aces_apps(apps: list[AcesApp]) -> dict[str, Any]:
    """Return a clear stub report — does not mutate catalog tables."""
    return {
        "status": "stubbed",
        "reason": ACES_APPLY_STUB_REASON,
        "apps_parsed": len(apps),
        "apps_applied": 0,
    }
