"""Apply PIES enrichment onto GTR catalog bundle / epc_to_pcdb mapping / stock rows.

Maps into existing columns only:
  - ``pnc_categories.pcdb_part_type_id`` (via OEM→PNC fitment join)
  - ``stock_items.description`` (+ brand hint prefix when helpful)
  - attrs kept in enrichment sidecar (no ``attrs`` jsonb on stock_items yet)
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from data_pipeline.aces_pies.pies import PiesItem, pies_item_to_dict
from data_pipeline.megazip.enrich_pcdb import enrich_pcdb, load_pcdb_mapping


def _normalize_category(name: str) -> str:
    return re.sub(r"\s+", " ", (name or "").strip().upper())


@dataclass
class EnrichmentResult:
    """Stats + optional sidecar payloads from a PIES apply pass."""

    pcdb_mapped: int = 0
    stock_descriptions: int = 0
    mapping_rows_added: int = 0
    attrs_retained: int = 0
    notes: list[str] = field(default_factory=list)
    stock_rows: list[dict[str, Any]] = field(default_factory=list)
    attrs_by_oem: dict[str, dict[str, str]] = field(default_factory=dict)
    pies_items: list[dict[str, Any]] = field(default_factory=list)


def stock_rows_from_pies(
    items: list[PiesItem],
    *,
    include_brand_prefix: bool = True,
    max_description_len: int = 200,
) -> list[dict[str, Any]]:
    """Build ``stock_items``-shaped rows from PIES (description only — existing cols)."""
    rows: list[dict[str, Any]] = []
    for item in items:
        desc = item.primary_description
        if not desc:
            continue
        if include_brand_prefix and item.brand_aaia_id and not desc.upper().startswith(
            item.brand_aaia_id
        ):
            desc = f"{item.brand_aaia_id} {desc}"
        rows.append(
            {
                "oem_part_number": item.part_number,
                "description": desc[:max_description_len],
                "brand_aaia_id": item.brand_aaia_id,
                "part_terminology_id": item.part_terminology_id,
            }
        )
    return rows


def apply_pies_to_bundle(
    bundle: dict[str, Any],
    items: list[PiesItem],
    *,
    overwrite_description: bool = True,
) -> EnrichmentResult:
    """Enrich in-memory catalog bundle from PIES items.

    - Sets ``pcdb_part_type_id`` on PNC rows when an OEM in ``part_fitment`` matches
      a PIES item that carries ``PartTerminologyID`` (does not clear existing IDs).
    - Writes OEM → description into ``bundle['_oem_display_names']`` and optional
      ``oem_display_names`` for stock upsert paths.
    - Retains PAdb-like attributes in ``result.attrs_by_oem`` (sidecar only).
    """
    result = EnrichmentResult(pies_items=[pies_item_to_dict(i) for i in items])
    by_oem = {i.part_number: i for i in items}

    oem_to_pncs: dict[str, set[str]] = {}
    for fit in bundle.get("part_fitment") or []:
        oem = str(fit.get("oem_part_number") or "").upper().strip()
        pnc = fit.get("pnc_code")
        if not oem or not pnc:
            continue
        oem_to_pncs.setdefault(oem, set()).add(str(pnc))

    pnc_rows = {str(r.get("pnc_code")): r for r in (bundle.get("pnc_categories") or []) if r.get("pnc_code")}
    for oem, item in by_oem.items():
        if not item.part_terminology_id:
            continue
        for pnc in oem_to_pncs.get(oem, ()):
            row = pnc_rows.get(pnc)
            if not row:
                continue
            if row.get("pcdb_part_type_id"):
                continue
            row["pcdb_part_type_id"] = item.part_terminology_id
            result.pcdb_mapped += 1

    display: dict[str, str] = {}
    existing = bundle.get("_oem_display_names") or bundle.get("oem_display_names") or {}
    if isinstance(existing, dict):
        display.update({str(k).upper(): str(v) for k, v in existing.items()})

    for item in items:
        if item.attributes:
            result.attrs_by_oem[item.part_number] = dict(item.attributes)
            result.attrs_retained += 1
        desc = item.primary_description
        if not desc:
            continue
        if item.brand_aaia_id and not desc.upper().startswith(item.brand_aaia_id):
            desc = f"{item.brand_aaia_id} {desc}"
        if not overwrite_description and display.get(item.part_number):
            continue
        display[item.part_number] = desc[:200]
        result.stock_descriptions += 1

    bundle["_oem_display_names"] = display
    bundle["oem_display_names"] = display
    result.stock_rows = stock_rows_from_pies(items)
    result.notes.append(
        "PIES attributes retained in sidecar only — stock_items has no attrs jsonb column yet."
    )
    return result


def merge_pies_into_epc_mapping(
    items: list[PiesItem],
    *,
    mapping_path: Path | None = None,
    category_by_terminology: dict[int, str] | None = None,
) -> tuple[dict[str, Any], int]:
    """Merge PIES PartTerminologyID into ``epc_to_pcdb.json``-shaped mapping.

    Without Auto Care PCdb names, rows are keyed by optional
    ``category_by_terminology`` labels or ``PIES PTID {id}`` placeholders so
    operators can rename after licensing real PCdb.
    """
    existing = load_pcdb_mapping(mapping_path)
    # Rebuild full document
    path = mapping_path
    if path and path.is_file():
        doc: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    else:
        doc = {
            "version": 1,
            "notes": (
                "Optional EPC assembly group name → Auto Care PCdb PartTerminologyID. "
                "Additive only; EPC category_name remains authoritative for shop-by-diagram UX."
            ),
            "mappings": [],
        }

    by_key = {
        _normalize_category(str(r.get("epc_category_normalized") or "")): r
        for r in (doc.get("mappings") or [])
        if r.get("epc_category_normalized")
    }
    # Keep prior load_pcdb_mapping hits
    for key, row in existing.items():
        by_key.setdefault(key, row)

    added = 0
    labels = category_by_terminology or {}
    seen_ids = {
        int(r["pcdb_part_type_id"])
        for r in by_key.values()
        if r.get("pcdb_part_type_id") is not None
    }

    for item in items:
        ptid = item.part_terminology_id
        if not ptid or ptid in seen_ids:
            continue
        label = labels.get(ptid) or item.primary_description or f"PIES PTID {ptid}"
        key = _normalize_category(label)
        if key in by_key:
            continue
        by_key[key] = {
            "epc_category_normalized": key,
            "pcdb_part_type_id": ptid,
            "pcdb_part_type_name": label[:120],
            "source": "pies",
        }
        seen_ids.add(ptid)
        added += 1

    doc["mappings"] = list(by_key.values())
    return doc, added


def enrich_bundle_pcdb_then_pies(
    bundle: dict[str, Any],
    items: list[PiesItem],
    *,
    mapping_path: Path | None = None,
) -> EnrichmentResult:
    """Run curated ``epc_to_pcdb`` enrich first, then PIES OEM→PNC overlay."""
    enrich_pcdb(bundle, mapping_path)
    return apply_pies_to_bundle(bundle, items)
