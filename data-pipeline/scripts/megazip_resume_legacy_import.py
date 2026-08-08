"""Resume legacy fitment/vehicle import after hierarchy tables already landed."""

from __future__ import annotations

import logging
import time
from pathlib import Path
from urllib.parse import urlparse

from data_pipeline.import_catalog import (
    import_supabase,
    load_env_files,
    resolve_supabase_credentials,
)
from data_pipeline.import_hierarchy_catalog import (
    load_hierarchy_bundle,
    sanitize_legacy_bundle_for_import,
)
from data_pipeline.validate import validate_bundle

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
logger = logging.getLogger("resume_legacy_import")


def main() -> int:
    bundle_dir = Path("out/megazip/nissan/bundle")
    repo_root = Path(__file__).resolve().parents[2]
    pipeline_root = Path(__file__).resolve().parents[1]
    load_env_files(pipeline_root / ".env", repo_root / ".env", override=True)
    url, key = resolve_supabase_credentials()
    host = urlparse(url or "").hostname or ""
    logger.info("supabase host=%s", host)
    if not url or not key or host in {"127.0.0.1", "localhost"}:
        logger.error("hosted Supabase credentials required")
        return 2

    t0 = time.perf_counter()
    bundle = sanitize_legacy_bundle_for_import(load_hierarchy_bundle(bundle_dir))
    validate_bundle(
        {
            k: bundle[k]
            for k in ("vehicle_master", "pnc_categories", "part_fitment", "diagram_assets")
        }
    )
    logger.info(
        "legacy rows vehicles=%s pncs=%s fitments=%s",
        len(bundle["vehicle_master"]),
        len(bundle["pnc_categories"]),
        len(bundle["part_fitment"]),
    )
    result = import_supabase(
        {
            "vehicle_master": bundle["vehicle_master"],
            "pnc_categories": bundle["pnc_categories"],
            "part_fitment": bundle["part_fitment"],
            "diagram_assets": bundle["diagram_assets"],
            "_oem_display_names": bundle.get("_oem_display_names") or {},
        },
        url=url,
        key=key,
        ensure_stock_items=True,
        prune_stale=True,
    )
    logger.info("notes=%s", result.notes)
    for name, stats in sorted(result.stats.items()):
        logger.info(
            "%s inserted=%s updated=%s",
            name,
            getattr(stats, "inserted", stats),
            getattr(stats, "updated", None),
        )
    logger.info("DONE in %.1fs", time.perf_counter() - t0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
