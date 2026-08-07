"""Build ERP-ready catalog bundle from completed vehicles (identity + fitments).

Completed = chassis codes present in part_fitment with bbox/diagram data.
Writes filtered bundle to out/erp_catalog_v1/ and validates + dry-run import.
"""
from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from data_pipeline.bundle_filter import filter_complete_bundle
from data_pipeline.cache_parse_worker import refresh_bundle
from data_pipeline.import_catalog import import_catalog, load_bundle
from data_pipeline.validate import validate_bundle

OUT = ROOT / "out" / "erp_catalog_v1"
BUNDLE = ROOT / "out" / "partsouq_bundle"
CRAWL_DB = ROOT / "crawler_state.db"
PARSE_DB = ROOT / "out" / "cache_parse_state.db"


def build_erp_bundle(full: dict, *, completed_only: bool) -> tuple[dict, dict]:
    """Thin wrapper — see ``filter_complete_bundle`` for criteria."""
    bundle, meta = filter_complete_bundle(full, completed_only=completed_only)
    meta["vehicles"] = meta["vehicles_out"]
    meta["fitments"] = meta["fitments_out"]
    meta["pncs"] = meta["pncs_out"]
    meta["diagrams"] = meta["diagrams_out"]
    meta["identity_only_chassis"] = meta["excluded_identity_only_chassis"]
    return bundle, meta


def write_bundle(bundle: dict, out_dir: Path) -> None:
    from data_pipeline.parse_fast import write_bundle as _write_tables

    _write_tables(bundle, out_dir)


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
