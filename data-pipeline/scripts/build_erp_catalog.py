"""Build ERP-ready catalog bundle from completed vehicles (identity + fitments).

Completed = chassis codes present in part_fitment with bbox/diagram data.
Writes filtered bundle to out/erp_catalog_v1/ and validates + dry-run import.
"""
from __future__ import annotations

import json
import sqlite3
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from data_pipeline.cache_parse_worker import refresh_bundle
from data_pipeline.import_catalog import import_catalog, load_bundle
from data_pipeline.validate import validate_bundle

OUT = ROOT / "out" / "erp_catalog_v1"
BUNDLE = ROOT / "out" / "partsouq_bundle"
CRAWL_DB = ROOT / "crawler_state.db"
PARSE_DB = ROOT / "out" / "cache_parse_state.db"


def _vehicle_key(row: dict) -> tuple:
    return (
        row.get("vin_prefix"),
        row.get("chassis_code"),
        row.get("engine_code"),
        row.get("production_year"),
        row.get("model_variant"),
    )


def _fitment_key(row: dict) -> tuple:
    return (
        row.get("oem_part_number"),
        row.get("chassis_code"),
        row.get("engine_code"),
        row.get("pnc_code"),
    )


def _complete_fitments(full: dict) -> list[dict]:
    return [
        f
        for f in (full.get("part_fitment") or [])
        if f.get("chassis_code")
        and f.get("bbox_x") is not None
        and f.get("diagram_path")
    ]


def _sanitize_vehicle_rows(rows: list[dict]) -> list[dict]:
    """Drop invalid production_year so bundle passes JSON schema (min 1980)."""
    out: list[dict] = []
    for row in rows:
        cleaned = dict(row)
        year = cleaned.get("production_year")
        if isinstance(year, int) and year < 1980:
            cleaned.pop("production_year", None)
        out.append(cleaned)
    return out


def build_erp_bundle(full: dict, *, completed_only: bool) -> tuple[dict, dict]:
    """ERP bundle: all parsed vehicles for VIN/model search + fitments where crawled.

    ``completed_only=True`` restricts vehicle_master to chassis that already have parts.
    Default (False) keeps every identity/parts vehicle row so ``search_catalog`` vin|model
    works for the full parsed frontier while part|pnc modes cover fitment-backed chassis.
    """
    complete_fitments = _complete_fitments(full)
    chassis_with_parts = {f["chassis_code"] for f in complete_fitments}
    engines_by_chassis = Counter(
        (f["chassis_code"], f.get("engine_code")) for f in complete_fitments
    )

    vehicles = full.get("vehicle_master") or []
    if completed_only:
        completed_vehicles = [v for v in vehicles if v.get("chassis_code") in chassis_with_parts]
        vehicle_keys = {_vehicle_key(v) for v in completed_vehicles}
        for v in vehicles:
            if v.get("chassis_code") in chassis_with_parts and _vehicle_key(v) not in vehicle_keys:
                completed_vehicles.append(v)
                vehicle_keys.add(_vehicle_key(v))
        vehicles = completed_vehicles

    pnc_codes = {f.get("pnc_code") for f in complete_fitments if f.get("pnc_code")}
    pncs = [p for p in (full.get("pnc_categories") or []) if p.get("pnc_code") in pnc_codes]

    diagram_paths = {f.get("diagram_path") for f in complete_fitments if f.get("diagram_path")}
    diagrams = [
        d
        for d in (full.get("diagram_assets") or [])
        if d.get("storage_path") in diagram_paths
    ]

    bundle = {
        "vehicle_master": _sanitize_vehicle_rows(vehicles),
        "pnc_categories": pncs,
        "part_fitment": complete_fitments,
        "diagram_assets": diagrams,
    }

    identity_chassis = sorted({v.get("chassis_code") for v in vehicles if v.get("chassis_code")})
    meta = {
        "parts_complete_chassis": sorted(chassis_with_parts),
        "identity_chassis": identity_chassis,
        "identity_only_chassis": sorted(set(identity_chassis) - chassis_with_parts),
        "chassis_fitment_counts": dict(
            Counter(f["chassis_code"] for f in complete_fitments).most_common()
        ),
        "engines_by_chassis": {
            f"{c}|{e or '?'}": n for (c, e), n in engines_by_chassis.most_common()
        },
        "vehicles": len(vehicles),
        "fitments": len(complete_fitments),
        "pncs": len(pncs),
        "diagrams": len(diagrams),
        "completed_only": completed_only,
        "criteria": (
            "vehicle_master: chassis with fitment data only"
            if completed_only
            else "vehicle_master: all parsed identity+parts rows; fitments: bbox+diagram complete"
        ),
    }
    return bundle, meta


def write_bundle(bundle: dict, out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, rows in bundle.items():
        (out_dir / f"{name}.json").write_text(
            json.dumps(rows, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )


def main() -> int:
    import argparse

    parser = argparse.ArgumentParser(description="Build ERP-importable catalog snapshot.")
    parser.add_argument(
        "--skip-refresh",
        action="store_true",
        help="Use existing out/partsouq_bundle (faster if watcher already refreshed).",
    )
    parser.add_argument(
        "--completed-only",
        action="store_true",
        help="Restrict vehicle_master to chassis that already have fitment rows.",
    )
    args = parser.parse_args()

    if args.skip_refresh and BUNDLE.exists():
        print("Using existing bundle at", BUNDLE)
    else:
        print("Refreshing full bundle from crawl + parse DBs...")
        refresh_counts = refresh_bundle(crawl_db=CRAWL_DB, parse_db=PARSE_DB, out_dir=BUNDLE)
        print("Full bundle:", refresh_counts)

    full = load_bundle(BUNDLE)
    erp_bundle, meta = build_erp_bundle(full, completed_only=args.completed_only)

    conn = sqlite3.connect(PARSE_DB)
    try:
        meta["identities_parsed"] = conn.execute(
            "SELECT COUNT(*) FROM vehicle_identity"
        ).fetchone()[0]
    finally:
        conn.close()

    write_bundle(erp_bundle, OUT)
    (OUT / "catalog_meta.json").write_text(
        json.dumps(meta, indent=2) + "\n", encoding="utf-8"
    )

    validate_bundle(erp_bundle)
    result = import_catalog(erp_bundle)
    counts = result.store.row_counts() if result.store else {}

    print("\n=== ERP catalog v1 ===")
    print(f"Output: {OUT}")
    print(f"  vehicle_master: {counts.get('vehicle_master', 0)}")
    print(f"  part_fitment:   {counts.get('part_fitment', 0)}")
    print(f"  pnc_categories: {counts.get('pnc_categories', 0)}")
    print(f"  diagram_assets: {counts.get('diagram_assets', 0)}")
    print(
        f"Parts-complete chassis ({len(meta['parts_complete_chassis'])}): "
        f"{meta['parts_complete_chassis']}"
    )
    pending = meta["identity_only_chassis"]
    print(
        f"Identity-only (VIN/model search, no parts yet) ({len(pending)}): "
        f"{pending[:15]}{'...' if len(pending) > 15 else ''}"
    )
    print("\nImport to Supabase when ready:")
    print(f"  python -m data_pipeline.import_catalog {OUT} --live")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
