"""Additive PCdb PartTerminologyID enrichment — re-runnable without re-crawl."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from data_pipeline.megazip.config import DEFAULT_PCDB_FILE


def _normalize_category(name: str) -> str:
    return re.sub(r"\s+", " ", (name or "").strip().upper())


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


def enrich_pcdb(bundle: dict[str, Any], mapping_path: Path | None = None) -> dict[str, int]:
    mapping = load_pcdb_mapping(mapping_path)
    applied = 0
    for pnc in bundle.get("pnc_categories") or []:
        if pnc.get("pcdb_part_type_id"):
            continue
        cat = _normalize_category(str(pnc.get("category_name") or ""))
        hit = mapping.get(cat)
        if not hit:
            # partial match on first token group
            for key, row in mapping.items():
                if key in cat or cat in key:
                    hit = row
                    break
        if hit:
            pnc["pcdb_part_type_id"] = hit.get("pcdb_part_type_id")
            # Keep label out of bundle rows — not in pnc_categories.schema.json /
            # upsert columns; use mapping file if UI needs the name.
            applied += 1
    return {"pcdb_mapped": applied, "pnc_total": len(bundle.get("pnc_categories") or [])}
