"""Parse FAST-like JSON exports into schema-valid catalog records."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from data_pipeline.validate import SCHEMA_NAMES, validate_bundle

# Hierarchy: model_variant → chassis/engine → PNC → diagram → OEM part
FASTRecord = dict[str, Any]


def _omit_none(row: dict[str, Any]) -> dict[str, Any]:
    return {k: v for k, v in row.items() if v is not None}


def _pnc_from_oem(oem: str) -> str:
    """First five digits of OEM prefix map to PNC (Nissan convention)."""
    return oem.split("-", 1)[0]


def parse_fast_document(doc: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    """Convert a FAST-like hierarchy document into import-ready tables."""
    vehicles: list[dict[str, Any]] = []
    pnc_map: dict[str, dict[str, Any]] = {}
    fitments: list[dict[str, Any]] = []
    diagrams: list[dict[str, Any]] = []

    model_variant = doc["model_variant"]
    chassis_code = doc["chassis_code"]
    engine_code = doc.get("engine_code")
    production_year = doc.get("production_year")
    vin_prefix = doc.get("vin_prefix")

    vehicles.append(
        _omit_none(
            {
                "vin_prefix": vin_prefix,
                "chassis_code": chassis_code,
                "engine_code": engine_code,
                "production_year": production_year,
                "model_variant": model_variant,
            }
        )
    )

    for assembly in doc.get("assemblies", []):
        pnc_code = assembly["pnc_code"]
        pnc_map[pnc_code] = _omit_none(
            {
                "pnc_code": pnc_code,
                "category_name": assembly["category_name"],
                "subcategory_name": assembly.get("subcategory_name"),
            }
        )

        diagram_path = assembly.get("diagram_path")
        if diagram_path and not any(d["storage_path"] == diagram_path for d in diagrams):
            # Section-level diagrams are shared by several assemblies — dedupe
            # by storage_path; the first assembly's PNC is the representative.
            diagrams.append(
                _omit_none(
                    {
                        "storage_path": diagram_path,
                        "pnc_code": pnc_code,
                        "chassis_code": chassis_code,
                        "engine_code": engine_code,
                        "content_type": assembly.get("diagram_content_type", "image/png"),
                        "source_url": assembly.get("diagram_source_url"),
                        "provenance": assembly.get("diagram_provenance"),
                    }
                )
            )

        for part in assembly.get("parts", []):
            oem = part["oem_part_number"]
            fitments.append(
                _omit_none(
                    {
                        "oem_part_number": oem,
                        "pnc_code": pnc_code,
                        "chassis_code": chassis_code,
                        "engine_code": engine_code,
                        "superseded_by": part.get("superseded_by"),
                        "bbox_x": part.get("bbox_x"),
                        "bbox_y": part.get("bbox_y"),
                        "bbox_width": part.get("bbox_width"),
                        "bbox_height": part.get("bbox_height"),
                        "diagram_path": diagram_path,
                    }
                )
            )

    # Infer PNC rows for parts missing explicit assembly metadata
    for fitment in fitments:
        pnc_code = fitment.get("pnc_code") or _pnc_from_oem(fitment["oem_part_number"])
        fitment["pnc_code"] = pnc_code
        if pnc_code not in pnc_map:
            pnc_map[pnc_code] = {
                "pnc_code": pnc_code,
                "category_name": "Uncategorized",
                "subcategory_name": None,
            }

    bundle = {
        "vehicle_master": vehicles,
        "pnc_categories": list(pnc_map.values()),
        "part_fitment": fitments,
        "diagram_assets": diagrams,
    }
    validate_bundle(bundle)
    return bundle


def parse_fast_file(path: Path) -> dict[str, list[dict[str, Any]]]:
    with path.open(encoding="utf-8") as fh:
        doc = json.load(fh)
    return parse_fast_document(doc)


def write_bundle(bundle: dict[str, list[dict[str, Any]]], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    oem_names = bundle.get("_oem_display_names")
    if isinstance(oem_names, dict) and oem_names:
        target = out_dir / "oem_display_names.json"
        with target.open("w", encoding="utf-8") as fh:
            json.dump(oem_names, fh, indent=2, sort_keys=True)
            fh.write("\n")
    for name in SCHEMA_NAMES:
        records = bundle.get(name)
        if records is None:
            continue
        target = out_dir / f"{name}.json"
        with target.open("w", encoding="utf-8") as fh:
            json.dump(records, fh, indent=2)
            fh.write("\n")
