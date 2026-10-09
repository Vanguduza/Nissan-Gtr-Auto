"""Model-scoped Megazip crawl worker — lease-aware, parallel-safe.

Acquires exclusive SQLite leases on ``--models`` so the main orchestrator and
other workers never claim the same model. Heartbeats every ~30s; releases on exit.

On successful drain (exit 0), optionally starts the next model from
``worker_replacement_queue.json`` (see ``megazip_worker_supervisor``).

Usage (from ``data-pipeline/``)::

  python -m data_pipeline.megazip_crawl_worker --models pathfinder-2142 \\
    --worker-id pathfinder --out-root out/megazip --rate-limit 0.55
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import subprocess
import sys
from pathlib import Path

from data_pipeline.megazip.config import DEFAULT_OUT_ROOT, MegazipConfig, build_maker_paths
from data_pipeline.megazip.crawl import crawl_maker
from data_pipeline.megazip.replacement_queue import pop_next
from data_pipeline.megazip.state import init_db, list_active_leases

logger = logging.getLogger("data_pipeline.megazip_crawl_worker")


def _spawn_replacement(*, maker: str, model: str, out_root: Path, rate_limit: float) -> None:
    wid = model.split("-")[0][:24]
    cwd = Path(__file__).resolve().parent.parent
    out_log = open(out_root / f"worker_{wid}.out.log", "a", encoding="utf-8")  # noqa: SIM115
    err_log = open(out_root / f"worker_{wid}.err.log", "a", encoding="utf-8")  # noqa: SIM115
    cmd = [
        sys.executable,
        "-u",
        "-m",
        "data_pipeline.megazip_crawl_worker",
        "--maker",
        maker,
        "--models",
        model,
        "--worker-id",
        wid,
        "--out-root",
        str(out_root),
        "--rate-limit",
        str(rate_limit),
        "--spawn-next-on-success",
    ]
    logger.info("Spawning replacement worker for %s", model)
    subprocess.Popen(
        cmd,
        cwd=str(cwd),
        stdout=out_log,
        stderr=err_log,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
    )


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
    parser.add_argument(
        "--spawn-next-on-success",
        action="store_true",
        help="After a successful drain, start the next model from the replacement queue",
    )
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

    init_db(paths.state_db)
    existing = list_active_leases(paths.state_db)
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
    except Exception:  # noqa: BLE001
        logger.exception("Worker failed")
        return 3

    logger.info("Worker done: %s", stats)
    logger.info("Active leases after exit: %s", list_active_leases(paths.state_db) or "{}")

    if args.spawn_next_on_success:
        nxt = pop_next(args.out_root, skip=set())
        if nxt:
            _spawn_replacement(
                maker=args.maker,
                model=nxt,
                out_root=args.out_root,
                rate_limit=args.rate_limit,
            )
        else:
            logger.info("No replacement model queued")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
