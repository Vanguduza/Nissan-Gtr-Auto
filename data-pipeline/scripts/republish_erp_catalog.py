"""Re-enrich catalog bundle from scraped_data + upload diagrams + optional live import.

Fixes EPC category labels (diagram titles / URL ``cname``), part display names,
and Storage assets for existing crawls without re-parsing HTML.

Usage (from data-pipeline/)::

  python scripts/republish_erp_catalog.py --skip-upload --skip-import
  python scripts/republish_erp_catalog.py --upload-diagrams
  python scripts/republish_erp_catalog.py --upload-diagrams --live-import
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from data_pipeline.amayama_catalog_auto import ScrapeConfig, download_bundle_diagrams  # noqa: E402
from data_pipeline.cache_parse_worker import refresh_bundle  # noqa: E402
from data_pipeline.import_catalog import (  # noqa: E402
    import_supabase,
    load_bundle,
    load_env_files,
    resolve_supabase_credentials,
)
from data_pipeline.bundle_filter import filter_complete_bundle  # noqa: E402

DEFAULT_STATE_DB = ROOT / "crawler_state.db"
DEFAULT_PARSE_DB = ROOT / "out" / "cache_parse_state.db"
DEFAULT_OUT = ROOT / "out" / "erp_catalog_v1"
DEFAULT_DIAGRAMS = ROOT / "out" / "diagram_downloads"


def refresh_bundle_from_db(
    *,
    crawl_db: Path,
    parse_db: Path,
    out_dir: Path,
) -> dict:
    counts = refresh_bundle(crawl_db=crawl_db, parse_db=parse_db, out_dir=out_dir)
    (out_dir / "catalog_meta.json").write_text(
        json.dumps(counts, indent=2) + "\n", encoding="utf-8"
    )
    print("Refreshed bundle:", counts)
    return load_bundle(out_dir)


async def upload_diagram_assets(
    bundle: dict,
    *,
    config: ScrapeConfig,
    diagrams_dir: Path,
) -> None:
    await download_bundle_diagrams(
        bundle,
        diagrams_dir=diagrams_dir,
        config=config,
        upload=True,
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Re-enrich and republish ERP catalog bundle.")
    parser.add_argument("--state-db", type=Path, default=DEFAULT_STATE_DB)
    parser.add_argument("--parse-db", type=Path, default=DEFAULT_PARSE_DB)
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--config", type=Path, default=ROOT / "config" / "scrape.json")
    parser.add_argument("--diagrams-dir", type=Path, default=DEFAULT_DIAGRAMS)
    parser.add_argument("--upload-diagrams", action="store_true")
    parser.add_argument("--skip-upload", action="store_true")
    parser.add_argument("--live-import", action="store_true")
    parser.add_argument("--skip-import", action="store_true")
    parser.add_argument(
        "--skip-refresh",
        action="store_true",
        help="Use existing JSON on disk (skip SQLite refresh; safe while crawl is active)",
    )
    parser.add_argument(
        "--complete-only",
        action="store_true",
        help="Import only vehicles with complete fitments; exclude identity-only rows",
    )
    parser.add_argument(
        "--prune-stale",
        action=argparse.BooleanOptionalAction,
        default=None,
        help="Delete live rows not in filtered bundle (default: on with --complete-only --live-import)",
    )
    args = parser.parse_args(argv)

    needs_env = (
        (args.upload_diagrams and not args.skip_upload)
        or (args.live_import and not args.skip_import)
    )
    if needs_env:
        load_env_files(ROOT.parent / ".env", ROOT / ".env", override=True)

    config = ScrapeConfig.load(args.config)
    if args.skip_refresh:
        print(f"Using frozen bundle at {args.out_dir} (--skip-refresh)")
        bundle = load_bundle(args.out_dir)
    else:
        bundle = refresh_bundle_from_db(
            crawl_db=args.state_db,
            parse_db=args.parse_db,
            out_dir=args.out_dir,
        )

    if args.upload_diagrams and not args.skip_upload:
        print("Uploading diagram assets to Storage…")
        asyncio.run(
            upload_diagram_assets(bundle, config=config, diagrams_dir=args.diagrams_dir)
        )

    if args.live_import and not args.skip_import:
        url, key = resolve_supabase_credentials()
        if not url or not key:
            print(
                "ERROR: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY "
                "(or SUPABASE_SERVICE_KEY) required for --live-import"
            )
            return 1
        bundle = load_bundle(args.out_dir)
        if args.complete_only:
            bundle, meta = filter_complete_bundle(bundle, completed_only=True)
            print(
                f"Complete-only: {meta['vehicles_out']} vehicles, "
                f"chassis {meta['parts_complete_chassis']}"
            )
        prune_stale = args.prune_stale
        if prune_stale is None:
            prune_stale = bool(args.complete_only)
        result = import_supabase(
            bundle,
            url=url,
            key=key,
            ensure_stock_items=True,
            prune_stale=prune_stale,
        )
        for table, stat in result.stats.items():
            print(f"  {table}: +{stat.inserted} ~{stat.updated} ={stat.unchanged}")
        for note in result.notes:
            print(f"  # {note}")

    print(f"Done — bundle at {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
