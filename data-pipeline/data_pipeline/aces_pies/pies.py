"""PIES XML parser — product descriptions, brands, PartTerminologyID, attributes.

Accepts common PIES 6.x–8.x Item shapes (namespaced or not). Does not validate
against Auto Care XSDs (subscription/reference DBs required for full validation).
"""

from __future__ import annotations

import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class PiesItem:
    """Normalized PIES Item row keyed by part number."""

    part_number: str
    brand_aaia_id: str | None = None
    part_terminology_id: int | None = None
    descriptions: dict[str, str] = field(default_factory=dict)
    attributes: dict[str, str] = field(default_factory=dict)
    maintenance_type: str | None = None

    @property
    def primary_description(self) -> str:
        """Prefer DES / EXT / INV / ABR English-ish marketing copy."""
        preferred = ("DES", "EXT", "INV", "ABR", "ASC", "SHO")
        for code in preferred:
            hit = self.descriptions.get(code)
            if hit:
                return hit
        for value in self.descriptions.values():
            if value:
                return value
        return ""


def _local(tag: str) -> str:
    if "}" in tag:
        return tag.rsplit("}", 1)[-1]
    return tag


def _text(el: ET.Element | None) -> str:
    if el is None or el.text is None:
        return ""
    return el.text.strip()


def _find_child(parent: ET.Element, *names: str) -> ET.Element | None:
    wanted = {n.lower() for n in names}
    for child in parent:
        if _local(child.tag).lower() in wanted:
            return child
    return None


def _find_children(parent: ET.Element, *names: str) -> list[ET.Element]:
    wanted = {n.lower() for n in names}
    return [c for c in parent if _local(c.tag).lower() in wanted]


def _attr(el: ET.Element, *keys: str) -> str:
    lower = {k.lower(): v for k, v in el.attrib.items()}
    for key in keys:
        if key.lower() in lower:
            return (lower[key.lower()] or "").strip()
    # namespaced attribute keys
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


def _parse_item(el: ET.Element) -> PiesItem | None:
    part_el = _find_child(el, "PartNumber", "Part")
    part_number = _text(part_el) or _attr(el, "PartNumber")
    if not part_number:
        return None

    brand = _text(_find_child(el, "BrandAAIAID", "BrandID", "Brand")) or _attr(
        el, "BrandAAIAID", "BrandID"
    )
    term_raw = _text(_find_child(el, "PartTerminologyID", "PartTypeID")) or _attr(
        el, "PartTerminologyID", "PartTypeID", "id"
    )
    # Nested PartType/@id (ACES-shaped inside some PIES hybrids)
    if not term_raw:
        part_type = _find_child(el, "PartType", "PartTerminology")
        if part_type is not None:
            term_raw = _attr(part_type, "id", "PartTerminologyID") or _text(part_type)

    descriptions: dict[str, str] = {}
    for desc_wrap in _find_children(el, "Descriptions", "ItemDescription"):
        nodes = (
            _find_children(desc_wrap, "Description")
            if _local(desc_wrap.tag).lower() == "descriptions"
            else [desc_wrap]
        )
        for desc in nodes:
            code = (_attr(desc, "DescriptionCode", "code") or "DES").upper()
            text = _text(desc)
            if text:
                descriptions[code] = text

    attributes: dict[str, str] = {}
    for attr_wrap in _find_children(el, "ProductAttributes", "Attributes", "ItemAttributes"):
        for attr in list(attr_wrap) + (
            [attr_wrap] if _local(attr_wrap.tag).lower().startswith("productattribute") else []
        ):
            if _local(attr.tag).lower() not in {
                "productattribute",
                "attribute",
                "padbattribute",
            }:
                continue
            key = (
                _attr(attr, "AttributeID", "PadbAttributeID", "id", "name")
                or _attr(attr, "AttributeCode")
                or "attr"
            )
            value = _text(attr) or _attr(attr, "value")
            if value:
                attributes[key] = value

    # Flat ProductAttribute siblings under Item
    for attr in _find_children(el, "ProductAttribute", "Attribute"):
        key = _attr(attr, "AttributeID", "PadbAttributeID", "id", "name") or "attr"
        value = _text(attr) or _attr(attr, "value")
        if value:
            attributes[key] = value

    return PiesItem(
        part_number=part_number.upper().strip(),
        brand_aaia_id=brand.upper() or None,
        part_terminology_id=_parse_int(term_raw),
        descriptions=descriptions,
        attributes=attributes,
        maintenance_type=_attr(el, "MaintenanceType") or None,
    )


def parse_pies_xml(source: str | Path | bytes) -> list[PiesItem]:
    """Parse a PIES XML document into normalized items (dedupe by part number, last wins)."""
    if isinstance(source, Path):
        raw = source.read_bytes()
    elif isinstance(source, str) and not source.lstrip().startswith("<"):
        raw = Path(source).read_bytes()
    elif isinstance(source, str):
        raw = source.encode("utf-8")
    else:
        raw = source

    root = ET.fromstring(raw)
    items: dict[str, PiesItem] = {}

    # Walk any depth for Item nodes (Header/Items/Item or flat Item)
    stack = [root]
    while stack:
        node = stack.pop()
        if _local(node.tag).lower() == "item":
            parsed = _parse_item(node)
            if parsed:
                items[parsed.part_number] = parsed
            continue
        stack.extend(list(node))

    return list(items.values())


def pies_item_to_dict(item: PiesItem) -> dict[str, Any]:
    return {
        "part_number": item.part_number,
        "brand_aaia_id": item.brand_aaia_id,
        "part_terminology_id": item.part_terminology_id,
        "primary_description": item.primary_description,
        "descriptions": dict(item.descriptions),
        "attributes": dict(item.attributes),
        "maintenance_type": item.maintenance_type,
    }
