"""Re-parse Engine attrs from Megazip cache into parsed_pages, then refresh vehicle_master.

Does not re-crawl. Reads diagram/variant HTML from page_cache, patches engine_code onto
parsed payloads, rebuilds the hierarchy bundle, and optionally upserts vehicle_master
to hosted Supabase.

Usage (from data-pipeline/)::

  python scripts/backfill_megazip_engine_codes.py
  python scripts/backfill_megazip_engine_codes.py --live-import
"""

from __future__ import annotations

import argparse
import json
import logging
import sqlite3
import time
from pathlib import Path
from urllib.parse import urlparse

from data_pipeline.import_catalog import (
    import_supabase,
    load_env_files,
    resolve_supabase_credentials,
)
from data_pipeline.import_hierarchy_catalog import (
    import_hierarchy_supabase,
    load_hierarchy_bundle,
    sanitize_legacy_bundle_for_import,
)
from data_pipeline.megazip.config import DEFAULT_OUT_ROOT, MegazipConfig, build_maker_paths
from data_pipeline.megazip.parse_html import (
    _engine_from_attrs,
    _normalize_engine_code,
    _parse_attrs,
    parse_variant_list,
)
from data_pipeline.megazip.state import upsert_parsed
from data_pipeline.megazip.transform import transform_maker
from data_pipeline.validate import validate_bundle

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("backfill_engine_codes")


def _patch_diagram_payload(payload: dict, engine: str) -> bool:
    if not engine:
        return False
    changed = False
    if (payload.get("engine_code") or "") != engine:
        payload["engine_code"] = engine
        changed = True
    for part in payload.get("parts") or []:
        if (part.get("engine_code") or "") != engine:
            part["engine_code"] = engine
            changed = True
    return changed


def reparse_engines_from_cache(
    state_db: Path,
    *,
    maker_slug: str = "nissan",
    limit: int | None = None,
) -> dict[str, int]:
    conn = sqlite3.connect(state_db, timeout=120.0)
    conn.row_factory = sqlite3.Row
    try:
        rows = conn.execute(
            """
            SELECT p.url, p.page_type, p.payload_json, c.cache_path
            FROM parsed_pages p
            JOIN page_cache c ON c.url = p.url
            WHERE p.maker_slug = ?
              AND p.page_type IN ('diagram', 'variant_list')
            ORDER BY p.url
            """,
            (maker_slug,),
        ).fetchall()
    finally:
        conn.close()

    stats = {
        "scanned": 0,
        "diagram_patched": 0,
        "variant_patched": 0,
        "missing_cache": 0,
        "no_engine": 0,
        "unchanged": 0,
    }
    for row in rows:
        if limit is not None and stats["scanned"] >= limit:
            break
        stats["scanned"] += 1
        cache_path = Path(row["cache_path"])
        if not cache_path.is_file():
            stats["missing_cache"] += 1
            continue
        try:
            html = cache_path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            stats["missing_cache"] += 1
            continue

        payload = json.loads(row["payload_json"])
        page_type = row["page_type"]
        url = row["url"]

        if page_type == "diagram":
            engine = _engine_from_attrs(_parse_attrs(html))
            if not engine:
                stats["no_engine"] += 1
                continue
            if _patch_diagram_payload(payload, engine):
                upsert_parsed(state_db, url, "diagram", maker_slug, payload)
                stats["diagram_patched"] += 1
            else:
                stats["unchanged"] += 1
            continue

        model_slug = payload.get("model_slug") or ""
        parsed = parse_variant_list(html, url, maker_slug, model_slug)
        new_variants = parsed.payload.get("variants") or []
        if not any(v.get("engine_code") for v in new_variants):
            stats["no_engine"] += 1
            continue
        old_by_slug = {v.get("slug"): v for v in (payload.get("variants") or [])}
        merged = []
        changed = False
        for nv in new_variants:
            ov = dict(old_by_slug.get(nv.get("slug")) or {})
            eng = _normalize_engine_code(nv.get("engine_code") or "")
            if eng and (ov.get("engine_code") or "") != eng:
                changed = True
            ov.update(nv)
            ov["engine_code"] = eng
            merged.append(ov)
        if changed:
            payload["variants"] = merged
            upsert_parsed(state_db, url, "variant_list", maker_slug, payload)
            stats["variant_patched"] += 1
        else:
            stats["unchanged"] += 1

    return stats


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maker", default="Nissan")
    parser.add_argument("--out-root", type=Path, default=DEFAULT_OUT_ROOT)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument(
        "--skip-reparse",
        action="store_true",
        help="Only transform (+ optional import) using current parsed_pages",
    )
    parser.add_argument(
        "--live-import",
        action="store_true",
        help="Upsert vehicle_master to hosted Supabase",
    )
    parser.add_argument(
        "--full-hierarchy-import",
        action="store_true",
        help="With --live-import, run full hierarchy import instead of vehicle_master only",
    )
    args = parser.parse_args(argv)

    config = MegazipConfig.load(None)
    paths = build_maker_paths(args.maker, args.out_root, config)
    if not paths.state_db.is_file():
        logger.error("state db missing: %s", paths.state_db)
        return 2

    if not args.skip_reparse:
        t0 = time.perf_counter()
        stats = reparse_engines_from_cache(
            paths.state_db, maker_slug=paths.slug, limit=args.limit
        )
        logger.info("reparse done in %.1fs: %s", time.perf_counter() - t0, stats)

    storage_prefix = f"epc/{paths.slug}"
    t1 = time.perf_counter()
    bundle = transform_maker(paths, storage_prefix=storage_prefix)
    vm = bundle.get("vehicle_master") or []
    with_engine = sum(1 for r in vm if r.get("engine_code"))
    logger.info(
        "transform done in %.1fs: vehicles=%s with_engine=%s variants=%s",
        time.perf_counter() - t1,
        len(vm),
        with_engine,
        len(bundle.get("catalog_variants") or []),
    )

    if not args.live_import:
        logger.info("dry run complete — pass --live-import to push vehicle_master")
        return 0

    repo_root = Path(__file__).resolve().parents[2]
    pipeline_root = Path(__file__).resolve().parents[1]
    load_env_files(pipeline_root / ".env", repo_root / ".env", override=True)
    url, key = resolve_supabase_credentials()
    host = urlparse(url or "").hostname or ""
    if not url or not key:
        logger.error("missing Supabase credentials")
        return 2
    if host in {"127.0.0.1", "localhost"}:
        logger.error("refusing import to local %s", host)
        return 3

    if args.full_hierarchy_import:
        disk = load_hierarchy_bundle(paths.bundle_dir)
        disk = sanitize_legacy_bundle_for_import(disk)
        validate_bundle(
            {
                k: disk[k]
                for k in ("vehicle_master", "pnc_categories", "part_fitment", "diagram_assets")
            }
        )
        result = import_hierarchy_supabase(
            disk, url=url, key=key, ensure_stock_items=False, prune_stale=False
        )
        logger.info("full hierarchy import: %s", result)
        return 0

    sanitized = sanitize_legacy_bundle_for_import(
        {
            "vehicle_master": bundle.get("vehicle_master") or [],
            "pnc_categories": [],
            "part_fitment": [],
            "diagram_assets": [],
        }
    )
    result = import_supabase(
        {
            "vehicle_master": sanitized["vehicle_master"],
            "pnc_categories": [],
            "part_fitment": [],
            "diagram_assets": [],
        },
        url=url,
        key=key,
        ensure_stock_items=False,
        prune_stale=False,
    )
    logger.info("vehicle_master import: %s", result)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
