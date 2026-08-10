"""CLI: ACES/PIES XML → GTR catalog enrichment (thin adapter).

Examples::

  python -m data_pipeline.aces_pies_import \\
    --pies fixtures/aces_pies/sample_pies.xml \\
    --bundle fixtures/navara_d40_yd25 \\
    --out out/aces_pies_enrichment

  python -m data_pipeline.aces_pies_import \\
    --pies path/to/supplier_pies.xml \\
    --aces path/to/supplier_aces.xml \\
    --out out/aces_pies_enrichment \\
    --write-mapping config/epc_to_pcdb.json

  # Live: upsert stock_items.description + pnc_categories.pcdb_part_type_id
  python -m data_pipeline.aces_pies_import --pies ... --bundle ... --live

  # PCdb-only whole-catalog enrich (curated epc_to_pcdb.json → live upsert)
  python -m data_pipeline.aces_pies_import --pcdb-only --bundle out/megazip/nissan/bundle --live

Auto Care VCdb/PCdb/PAdb reference data is a paid subscription — fixtures use
synthetic IDs. Do not fork SandPIM into this monorepo; export XML from SandPIM
(or a supplier) and run this importer.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from data_pipeline.aces_pies.aces import (
    ACES_APPLY_STUB_REASON,
    aces_app_to_dict,
    parse_aces_xml,
    stub_apply_aces_apps,
)
from data_pipeline.aces_pies.enrich import (
    apply_pies_to_bundle,
    enrich_bundle_pcdb_then_pies,
    merge_pies_into_epc_mapping,
    stock_rows_from_pies,
)
from data_pipeline.aces_pies.pies import parse_pies_xml, pies_item_to_dict
from data_pipeline.import_catalog import (
    load_bundle,
    load_env_files,
    resolve_supabase_credentials,
    _batch_upsert_pnc,
    _batch_upsert_stock_items,
    _project,
)
from data_pipeline.megazip.config import DEFAULT_PCDB_FILE
from data_pipeline.megazip.enrich_pcdb import enrich_pcdb

_STOCK_LIVE_COLS = ("oem_part_number", "description")
_PNC_LIVE_COLS = (
    "pnc_code",
    "category_name",
    "subcategory_name",
    "assembly_group_id",
    "catalog_section_path",
    "pcdb_part_type_id",
)


def _write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def _apply_live(
    *,
    stock_rows: list[dict[str, Any]],
    pnc_rows: list[dict[str, Any]],
) -> dict[str, int]:
    load_env_files(
        Path.cwd() / ".env",
        Path.cwd().parent / ".env",
        override=True,
    )
    url, key = resolve_supabase_credentials()
    if not url or not key:
        raise SystemExit(
            "Live apply requires SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY (or SERVICE_KEY)."
        )
    from supabase import create_client  # optional extra

    client = create_client(url, key)
    stock_stats = _batch_upsert_stock_items(
        client,
        [_project(r, _STOCK_LIVE_COLS) for r in stock_rows if r.get("description")],
    )
    # Only PNCs that gained a pcdb id (and have category_name for upsert)
    pnc_payload = [
        _project(r, _PNC_LIVE_COLS)
        for r in pnc_rows
        if r.get("pnc_code") and r.get("category_name") and r.get("pcdb_part_type_id")
    ]
    pnc_stats = _batch_upsert_pnc(client, pnc_payload) if pnc_payload else None
    return {
        "stock_upserted": stock_stats.updated + stock_stats.inserted,
        "pnc_upserted": (pnc_stats.updated + pnc_stats.inserted) if pnc_stats else 0,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Import Auto Care PIES (and parse ACES) as catalog enrichment only."
    )
    parser.add_argument("--pies", type=Path, help="Path to PIES XML file")
    parser.add_argument("--aces", type=Path, help="Path to ACES XML file (parse + stub apply)")
    parser.add_argument(
        "--pcdb-only",
        action="store_true",
        help="Apply curated epc_to_pcdb.json to --bundle (no PIES/ACES XML required)",
    )
    parser.add_argument(
        "--bundle",
        type=Path,
        help="Catalog bundle directory (vehicle_master.json, pnc_categories.json, …)",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("out/aces_pies_enrichment"),
        help="Directory for enrichment JSON sidecars (default: out/aces_pies_enrichment)",
    )
    parser.add_argument(
        "--mapping",
        type=Path,
        default=None,
        help="Existing epc_to_pcdb.json to read (default: config/epc_to_pcdb.json)",
    )
    parser.add_argument(
        "--write-mapping",
        type=Path,
        default=None,
        help="Write merged PCdb mapping JSON (optional; does not overwrite unless set)",
    )
    parser.add_argument(
        "--skip-curated-pcdb",
        action="store_true",
        help="Do not run enrich_pcdb from epc_to_pcdb.json before PIES overlay",
    )
    parser.add_argument(
        "--live",
        action="store_true",
        help="Upsert stock_items.description + pnc_categories.pcdb_part_type_id to Supabase",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse and write sidecars only (default behavior unless --live)",
    )
    args = parser.parse_args(argv)

    if not args.pies and not args.aces:
        parser.error("Provide at least --pies and/or --aces")

    out_dir: Path = args.out
    out_dir.mkdir(parents=True, exist_ok=True)
    mapping_path = args.mapping or DEFAULT_PCDB_FILE

    report: dict[str, Any] = {
        "pies_file": str(args.pies) if args.pies else None,
        "aces_file": str(args.aces) if args.aces else None,
        "bundle": str(args.bundle) if args.bundle else None,
        "notes": [
            "Enrichment only — Supabase/EPC remains system of record.",
            "Auto Care VCdb/PCdb subscription required for production terminology IDs.",
            "SandPIM is an optional XML export satellite; not forked into this repo.",
        ],
    }

    pies_items = []
    if args.pies:
        if not args.pies.is_file():
            raise SystemExit(f"PIES file not found: {args.pies}")
        pies_items = parse_pies_xml(args.pies)
        _write_json(out_dir / "pies_items.json", [pies_item_to_dict(i) for i in pies_items])
        report["pies_items"] = len(pies_items)

    aces_apps = []
    if args.aces:
        if not args.aces.is_file():
            raise SystemExit(f"ACES file not found: {args.aces}")
        aces_apps = parse_aces_xml(args.aces)
        _write_json(out_dir / "aces_apps.json", [aces_app_to_dict(a) for a in aces_apps])
        stub = stub_apply_aces_apps(aces_apps)
        report["aces"] = stub
        report["notes"].append(ACES_APPLY_STUB_REASON)

    bundle: dict[str, Any] | None = None
    enrich_stats: dict[str, Any] = {}
    if args.bundle and pies_items:
        bundle = load_bundle(args.bundle)
        if args.skip_curated_pcdb:
            result = apply_pies_to_bundle(bundle, pies_items)
        else:
            result = enrich_bundle_pcdb_then_pies(bundle, pies_items, mapping_path=mapping_path)
        enrich_stats = {
            "pcdb_mapped": result.pcdb_mapped,
            "stock_descriptions": result.stock_descriptions,
            "attrs_retained": result.attrs_retained,
            "notes": result.notes,
        }
        _write_json(out_dir / "pies_attrs_by_oem.json", result.attrs_by_oem)
        _write_json(out_dir / "stock_items_from_pies.json", result.stock_rows)
        # Persist enriched PNC + display names next to out for dry-run review
        _write_json(out_dir / "pnc_categories_enriched.json", bundle.get("pnc_categories") or [])
        _write_json(out_dir / "oem_display_names.json", bundle.get("_oem_display_names") or {})
        report["enrichment"] = enrich_stats
    elif pies_items:
        rows = stock_rows_from_pies(pies_items)
        _write_json(out_dir / "stock_items_from_pies.json", rows)
        attrs = {i.part_number: dict(i.attributes) for i in pies_items if i.attributes}
        _write_json(out_dir / "pies_attrs_by_oem.json", attrs)
        report["enrichment"] = {
            "stock_descriptions": len(rows),
            "attrs_retained": len(attrs),
            "notes": ["No --bundle: PCdb PNC overlay skipped; stock rows from PIES only."],
        }

    if pies_items and args.write_mapping:
        doc, added = merge_pies_into_epc_mapping(pies_items, mapping_path=mapping_path)
        _write_json(args.write_mapping, doc)
        report["mapping_rows_added"] = added
        report["mapping_written"] = str(args.write_mapping)

    if args.live:
        if not pies_items:
            raise SystemExit("--live requires --pies")
        stock_rows = stock_rows_from_pies(pies_items)
        pnc_rows = (bundle or {}).get("pnc_categories") or []
        if not bundle:
            report["notes"].append(
                "Live without --bundle upserts stock descriptions only (no pcdb_part_type_id)."
            )
        live_stats = _apply_live(stock_rows=stock_rows, pnc_rows=pnc_rows)
        report["live"] = live_stats
    else:
        report["mode"] = "dry-run"

    _write_json(out_dir / "enrichment_report.json", report)
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
