"""Model-scoped Megazip crawl worker ΓÇö lease-aware, parallel-safe.

Acquires exclusive SQLite leases on ``--models`` so the main orchestrator and
other workers never claim the same model. Heartbeats every ~30s; releases on exit.

Usage (from ``data-pipeline/``)::

  python -m data_pipeline.megazip_crawl_worker --models pathfinder-2142 \\
    --worker-id pathfinder --out-root out/megazip --rate-limit 0.55
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from pathlib import Path

from data_pipeline.megazip.config import DEFAULT_OUT_ROOT, MegazipConfig, build_maker_paths
from data_pipeline.megazip.crawl import crawl_maker
from data_pipeline.megazip.state import init_db, list_active_leases

logger = logging.getLogger("data_pipeline.megazip_crawl_worker")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Megazip model-scoped crawl worker (lease-aware; no orchestrator phases)."
    )
    parser.add_argument("--maker", default="Nissan")
    parser.add_argument(
        "--models",
        required=True,
        help="Comma-separated model_slug values to drain (e.g. pathfinder-2142)",
    )
    parser.add_argument(
        "--worker-id",
        default=None,
        help="Stable worker id for leases (default: derived from models)",
    )
    parser.add_argument("--out-root", type=Path, default=DEFAULT_OUT_ROOT)
    parser.add_argument("--makers-file", type=Path, default=None)
    parser.add_argument(
        "--rate-limit",
        type=float,
        default=0.55,
        help="Seconds between page fetches (default 0.55)",
    )
    parser.add_argument("--max-pages", type=int, default=None)
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    models = frozenset(m.strip() for m in args.models.split(",") if m.strip())
    if not models:
        logger.error("--models is empty")
        return 1

    config = MegazipConfig.load(args.makers_file)
    paths = build_maker_paths(args.maker, args.out_root, config)
    if not paths.state_db.is_file():
        logger.error("State DB missing: %s ΓÇö start the main orchestrator first", paths.state_db)
        return 1

    init_db(paths.state_db)
    existing = list_active_leases(paths.state_db)
    conflicts = {m: existing[m] for m in models if m in existing and existing[m] != (args.worker_id or "")}
    # Allow same worker-id to re-acquire (restart). Block other owners.
    wid = args.worker_id or ("worker-" + "-".join(sorted(models))[:80])
    conflicts = {m: w for m, w in existing.items() if m in models and w != wid}
    if conflicts:
        logger.error("Models already leased: %s", conflicts)
        return 2

    logger.info(
        "=== Megazip worker %s: models=%s rate=%.2fs ===",
        wid,
        ",".join(sorted(models)),
        args.rate_limit,
    )
    logger.info("Active leases before start: %s", list_active_leases(paths.state_db) or "{}")

    try:
        stats = asyncio.run(
            crawl_maker(
                paths,
                config,
                max_pages=args.max_pages,
                worker_mode=True,
                worker_id=wid,
                model_slugs=models,
                rate_limit_seconds=args.rate_limit,
            )
        )
    except RuntimeError as exc:
        logger.error("%s", exc)
        return 2

    logger.info("Worker done: %s", stats)
    logger.info("Active leases after exit: %s", list_active_leases(paths.state_db) or "{}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
