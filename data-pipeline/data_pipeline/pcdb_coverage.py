"""Hosted catalog PCdb coverage snapshot + optional live PCdb enrich.

Uses import_catalog credential helpers (repo-root .env wins). Never prints secrets.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from data_pipeline.aces_pies_import import _apply_live, main as aces_pies_main
from data_pipeline.import_catalog import (
    UPSERT_BATCH_SIZE,
    load_env_files,
    resolve_supabase_credentials,
)
from data_pipeline.megazip.config import DEFAULT_PCDB_FILE
from data_pipeline.megazip.enrich_pcdb import enrich_pcdb


def _client():
    load_env_files(
        Path.cwd() / ".env",
        Path.cwd().parent / ".env",
        override=True,
    )
    url, key = resolve_supabase_credentials()
    if not url or not key:
        raise SystemExit("Missing SUPABASE_URL or privileged server key in .env")
    from supabase import create_client

    return create_client(url, key), url


def _fetch_pnc_categories(client: Any) -> list[dict[str, Any]]:
    """Page all pnc_categories (PK is pnc_code — not id)."""
    rows: list[dict[str, Any]] = []
    offset = 0
    page = UPSERT_BATCH_SIZE
    select = (
        "pnc_code,category_name,subcategory_name,assembly_group_id,"
        "catalog_section_path,pcdb_part_type_id"
    )
    while True:
        resp = (
            client.table("pnc_categories")
            .select(select)
            .order("pnc_code")
            .range(offset, offset + page - 1)
            .execute()
        )
        batch = resp.data or []
        rows.extend(batch)
        if len(batch) < page:
            break
        offset += page
    return rows


def coverage_snapshot() -> dict[str, Any]:
    client, url = _client()
    host = url.split("//")[-1].split(".")[0] if url else ""
    out: dict[str, Any] = {"project_ref": host}

    for table in ("pnc_categories", "stock_items", "part_fitment", "vehicle_master"):
        resp = client.table(table).select("*", count="exact").limit(0).execute()
        out[f"{table}_count"] = resp.count

    with_pcdb = (
        client.table("pnc_categories")
        .select("pnc_code", count="exact")
        .not_.is_("pcdb_part_type_id", "null")
        .limit(0)
        .execute()
    )
    without = (
        client.table("pnc_categories")
        .select("pnc_code", count="exact")
        .is_("pcdb_part_type_id", "null")
        .limit(0)
        .execute()
    )
    total = out["pnc_categories_count"] or 0
    mapped = with_pcdb.count or 0
    out["pnc_with_pcdb"] = mapped
    out["pnc_without_pcdb"] = without.count or 0
    out["pcdb_pct"] = round(100.0 * mapped / total, 2) if total else 0.0
    return out


def enrich_hosted_pnc(*, mapping_path: Path | None = None) -> dict[str, Any]:
    """Pull hosted pnc_categories, apply curated mapping, upsert pcdb ids (additive)."""
    client, _ = _client()
    rows = _fetch_pnc_categories(client)
    bundle = {"pnc_categories": rows}
    stats = enrich_pcdb(bundle, mapping_path)
    live = _apply_live(stock_rows=[], pnc_rows=bundle["pnc_categories"])
    return {"enrichment": stats, "live": live, "hosted_pnc_pulled": len(rows)}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="PCdb coverage / live enrich against hosted SoR")
    parser.add_argument("--snapshot", action="store_true", help="Print hosted coverage JSON")
    parser.add_argument(
        "--live-hosted",
        action="store_true",
        help="Enrich all hosted pnc_categories via curated mapping and upsert",
    )
    parser.add_argument(
        "--live-from-bundle",
        type=Path,
        help="Enrich local bundle and upsert (may miss hosted-only PNCs)",
    )
    parser.add_argument(
        "--mapping",
        type=Path,
        default=None,
        help="epc_to_pcdb.json path (default: config/epc_to_pcdb.json)",
    )
    parser.add_argument(
        "--write-bundle",
        action="store_true",
        help="With --live-from-bundle, write enriched pnc_categories.json into bundle",
    )
    args = parser.parse_args(argv)

    if not args.snapshot and not args.live_hosted and not args.live_from_bundle:
        parser.error("Provide --snapshot, --live-hosted, and/or --live-from-bundle")

    report: dict[str, Any] = {}
    if args.snapshot or args.live_hosted or args.live_from_bundle:
        report["before"] = coverage_snapshot()

    mapping = args.mapping or DEFAULT_PCDB_FILE

    if args.live_hosted:
        report["hosted_apply"] = enrich_hosted_pnc(mapping_path=mapping)

    if args.live_from_bundle:
        bundle_dir = args.live_from_bundle
        out = Path("out/aces_pies_enrichment/pcdb_live")
        rc = aces_pies_main(
            [
                "--pcdb-only",
                "--bundle",
                str(bundle_dir),
                "--out",
                str(out),
                "--mapping",
                str(mapping),
                "--live",
            ]
        )
        if rc != 0:
            return rc
        enrich_report = json.loads((out / "enrichment_report.json").read_text(encoding="utf-8"))
        report["bundle_enrichment"] = enrich_report.get("enrichment")
        report["bundle_live"] = enrich_report.get("live")
        if args.write_bundle:
            enriched = json.loads(
                (out / "pnc_categories_enriched.json").read_text(encoding="utf-8")
            )
            target = bundle_dir / "pnc_categories.json"
            target.write_text(
                json.dumps(enriched, indent=2, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            report["bundle_written"] = str(target)

    if args.live_hosted or args.live_from_bundle:
        report["after"] = coverage_snapshot()
        print(json.dumps(report, indent=2))
    else:
        print(json.dumps(report["before"], indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
