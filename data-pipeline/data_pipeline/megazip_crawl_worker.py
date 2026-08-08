"""Model-scoped Megazip crawl worker — safe to run beside a live orchestrator.

Claims only PENDING URLs for the assigned ``--models`` set. Does **not**
re-enqueue the hub, run two-phase remaining prep, self-heal, parse, transform,
or import. Shares ``megazip_state.db`` with the main crawler via atomic claims.

Usage (from ``data-pipeline/``)::

  python -m data_pipeline.megazip_crawl_worker --models pathfinder-2142 \\
    --out-root out/megazip --rate-limit 0.55

  python -m data_pipeline.megazip_crawl_worker --models tiida-tiida-latio-2092,murano-2120 \\
    --out-root out/megazip --rate-limit 0.55
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from pathlib import Path

from data_pipeline.megazip.config import DEFAULT_OUT_ROOT, MegazipConfig, build_maker_paths
from data_pipeline.megazip.crawl import crawl_maker

logger = logging.getLogger("data_pipeline.megazip_crawl_worker")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Megazip model-scoped crawl worker (parallel-safe; no orchestrator phases)."
    )
    parser.add_argument("--maker", default="Nissan")
    parser.add_argument(
        "--models",
        required=True,
        help="Comma-separated model_slug values to drain (e.g. pathfinder-2142)",
    )
    parser.add_argument("--out-root", type=Path, default=DEFAULT_OUT_ROOT)
    parser.add_argument("--makers-file", type=Path, default=None)
    parser.add_argument(
        "--rate-limit",
        type=float,
        default=0.55,
        help="Seconds between page fetches (default 0.55 — gentler when multiple workers run)",
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
        logger.error("State DB missing: %s — start the main orchestrator first", paths.state_db)
        return 1

    logger.info(
        "=== Megazip worker: %s models=%s rate=%.2fs ===",
        args.maker,
        ",".join(sorted(models)),
        args.rate_limit,
    )
    stats = asyncio.run(
        crawl_maker(
            paths,
            config,
            max_pages=args.max_pages,
            worker_mode=True,
            model_slugs=models,
            rate_limit_seconds=args.rate_limit,
        )
    )
    logger.info("Worker done: %s", stats)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
