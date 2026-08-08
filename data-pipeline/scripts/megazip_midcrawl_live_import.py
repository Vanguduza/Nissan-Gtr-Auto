"""Mid-crawl live import of the already-filtered Nissan Megazip bundle."""

from __future__ import annotations

import logging
import time
from pathlib import Path

from data_pipeline.import_hierarchy_catalog import (
    import_hierarchy_bundle_dir,
    load_hierarchy_bundle,
    sanitize_legacy_bundle_for_import,
)
from data_pipeline.validate import validate_bundle

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
logger = logging.getLogger("midcrawl_import")


def main() -> int:
    bundle_dir = Path("out/megazip/nissan/bundle")
    repo_root = Path(__file__).resolve().parents[2]
    pipeline_root = Path(__file__).resolve().parents[1]

    # Prefer repo-root .env (hosted SoR) over data-pipeline/.env (often local Docker).
    from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials
    from urllib.parse import urlparse

    load_env_files(pipeline_root / ".env", repo_root / ".env", override=True)
    url, key = resolve_supabase_credentials()
    host = urlparse(url or "").hostname or ""
    logger.info("supabase host=%s key=%s", host, "yes" if key else "no")
    if not url or not key:
        logger.error("missing Supabase credentials")
        return 2
    if host in {"127.0.0.1", "localhost"}:
        logger.error(
            "refusing import to local %s — start Docker/`pnpm db:start`, or use hosted URL in repo-root .env",
            host,
        )
        return 3

    t0 = time.perf_counter()
    logger.info("loading bundle from %s", bundle_dir)
    bundle = load_hierarchy_bundle(bundle_dir)
    logger.info(
        "loaded models=%s variants=%s diagrams=%s fitments=%s (%.1fs)",
        len(bundle.get("catalog_models") or []),
        len(bundle.get("catalog_variants") or []),
        len(bundle.get("catalog_diagrams") or []),
        len(bundle.get("part_fitment") or []),
        time.perf_counter() - t0,
    )

    t1 = time.perf_counter()
    sanitized = sanitize_legacy_bundle_for_import(bundle)
    logger.info("sanitized legacy tables (%.1fs)", time.perf_counter() - t1)

    t2 = time.perf_counter()
    validate_bundle(
        {
            k: sanitized[k]
            for k in ("vehicle_master", "pnc_categories", "part_fitment", "diagram_assets")
        }
    )
    logger.info("validate OK (%.1fs)", time.perf_counter() - t2)

    # Write sanitized legacy tables back so import path need not re-sanitize from dirty shapes.
    for name in ("vehicle_master", "pnc_categories", "part_fitment", "diagram_assets"):
        path = bundle_dir / f"{name}.json"
        import json

        path.write_text(
            json.dumps(sanitized[name], indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        logger.info("wrote %s rows=%s", path.name, len(sanitized[name]))

    logger.info("starting live import (complete_only=False; disk already filtered)")
    result = import_hierarchy_bundle_dir(
        bundle_dir,
        live=True,
        complete_only=False,
        prune_stale=True,
        ensure_stock_items=True,
    )
    logger.info("notes=%s", result.notes)
    for key, stats in sorted(result.stats.items()):
        logger.info(
            "%s inserted=%s updated=%s",
            key,
            getattr(stats, "inserted", stats),
            getattr(stats, "updated", None),
        )
    logger.info("DONE in %.1fs", time.perf_counter() - t0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
