"""Additive PCdb PartTerminologyID enrichment — re-runnable without re-crawl."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from data_pipeline.megazip.config import DEFAULT_PCDB_FILE


def _normalize_category(name: str) -> str:
    return re.sub(r"\s+", " ", (name or "").strip().upper())


def epc_category_stem(name: str) -> str:
    """Strip Megazip ``FOR <vehicle…>`` suffixes so assembly group names match.

    Example: ``WIRING FOR 2004 - 2011 NISSAN ALTIMA …`` → ``WIRING``.
    """
    cat = _normalize_category(name)
    if " FOR " in cat:
        cat = cat.split(" FOR ", 1)[0].strip()
    return cat


def load_pcdb_mapping(path: Path | None = None) -> dict[str, dict[str, Any]]:
    p = path or DEFAULT_PCDB_FILE
    if not p.is_file():
        return {}
    data = json.loads(p.read_text(encoding="utf-8"))
    out: dict[str, dict[str, Any]] = {}
    for row in data.get("mappings") or []:
        key = _normalize_category(str(row.get("epc_category_normalized") or ""))
        if key:
            out[key] = row
    return out


def resolve_pcdb_row(
    category_name: str,
    mapping: dict[str, dict[str, Any]],
) -> dict[str, Any] | None:
    """Resolve mapping for an EPC category — exact, stem, then longest substring."""
    cat = _normalize_category(category_name)
    if not cat:
        return None
    stem = epc_category_stem(cat)
    hit = mapping.get(cat) or mapping.get(stem)
    if hit:
        return hit
    # Prefer longest key so ``BRAKE PIPING & CONTROL`` wins over ``BRAKE``.
    best: dict[str, Any] | None = None
    best_len = -1
    for key, row in mapping.items():
        if not key:
            continue
        if (key in stem or stem in key or key in cat or cat in key) and len(key) > best_len:
            best = row
            best_len = len(key)
    return best


def enrich_pcdb(bundle: dict[str, Any], mapping_path: Path | None = None) -> dict[str, int]:
    mapping = load_pcdb_mapping(mapping_path)
    applied = 0
    already = 0
    for pnc in bundle.get("pnc_categories") or []:
        if pnc.get("pcdb_part_type_id"):
            already += 1
            continue
        hit = resolve_pcdb_row(str(pnc.get("category_name") or ""), mapping)
        if hit and hit.get("pcdb_part_type_id") is not None:
            pnc["pcdb_part_type_id"] = hit.get("pcdb_part_type_id")
            # Keep label out of bundle rows — not in pnc_categories.schema.json /
            # upsert columns; use mapping file if UI needs the name.
            applied += 1
    total = len(bundle.get("pnc_categories") or [])
    return {
        "pcdb_mapped": applied,
        "pcdb_already": already,
        "pnc_total": total,
        "pcdb_coverage": already + applied,
    }
