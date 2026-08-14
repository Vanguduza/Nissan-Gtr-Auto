"""Watch Megazip workers and keep a fixed pool running.

Maintains ``--target-workers`` concurrent model workers. When a worker exits
before finishing (PENDING > 0), the same model is restarted immediately. When
a worker finishes cleanly (PENDING = 0), the next model from the replacement
queue (nearest to finish first) is started. Free slots are filled on every poll.
"""

from __future__ import annotations

import argparse
import logging
import sqlite3
import subprocess
import sys
import time
from pathlib import Path

from data_pipeline.megazip.config import DEFAULT_OUT_ROOT, MegazipConfig, build_maker_paths
from data_pipeline.megazip.replacement_queue import (
    load_queue,
    pending_by_model,
    pop_next,
    reorder_queue_nearest_first,
    seed_from_dead_models,
)
from data_pipeline.megazip.state import expire_stale_leases, init_db, list_active_leases

logger = logging.getLogger("data_pipeline.megazip_worker_supervisor")


def _live_worker_map() -> dict[int, str]:
    """Return {pid: model_slug} for running megazip_crawl_worker processes (Windows)."""
    out: dict[int, str] = {}
    try:
        import psutil  # type: ignore
    except ImportError:
        psutil = None  # type: ignore

    if psutil is not None:
        for proc in psutil.process_iter(["pid", "name", "cmdline"]):
            try:
                cmd = proc.info.get("cmdline") or []
                line = " ".join(cmd)
                if "megazip_crawl_worker" not in line:
                    continue
                model = ""
                for i, tok in enumerate(cmd):
                    if tok == "--models" and i + 1 < len(cmd):
                        model = cmd[i + 1].split(",")[0].strip()
                        break
                if model:
                    out[int(proc.info["pid"])] = model
            except (psutil.Error, TypeError, ValueError):
                continue
        return out

    if sys.platform.startswith("win"):
        ps = (
            "Get-CimInstance Win32_Process -Filter \"name='python.exe'\" | "
            "Where-Object { $_.CommandLine -match 'megazip_crawl_worker' } | "
            "ForEach-Object { "
            "if ($_.CommandLine -match '--models\\s+(\\S+)') { "
            "\"$($_.ProcessId)|$($Matches[1].Split(',')[0])\" } }"
        )
        try:
            raw = subprocess.check_output(
                ["powershell", "-NoProfile", "-Command", ps],
                text=True,
                stderr=subprocess.DEVNULL,
                timeout=30,
            )
        except (subprocess.SubprocessError, OSError):
            return out
        for line in raw.splitlines():
            line = line.strip()
            if "|" not in line:
                continue
            pid_s, model = line.split("|", 1)
            try:
                out[int(pid_s)] = model.strip()
            except ValueError:
                continue
    return out


def _pending_for(state_db: Path, model: str) -> int:
    conn = sqlite3.connect(f"file:{state_db.as_posix()}?mode=ro", uri=True, timeout=120.0)
    try:
        row = conn.execute(
            "SELECT COUNT(1) FROM queue WHERE status='PENDING' AND model_slug=?",
            (model,),
        ).fetchone()
        return int(row[0]) if row else 0
    finally:
        conn.close()


def _worker_id_for(model: str) -> str:
    base = model.split("-")[0] if model else "worker"
    return base[:24]


def _clear_lease(state_db: Path, model: str) -> None:
    try:
        from data_pipeline.megazip.state import connect

        conn = connect(state_db)
        try:
            conn.execute("DELETE FROM worker_leases WHERE model_slug = ?", (model,))
            conn.commit()
        finally:
            conn.close()
    except Exception as exc:  # noqa: BLE001
        logger.warning("Could not clear lease for %s: %s", model, exc)


def _spawn_worker(
    *,
    maker: str,
    model: str,
    out_root: Path,
    rate_limit: float,
    cwd: Path,
) -> subprocess.Popen:
    wid = _worker_id_for(model)
    log_dir = out_root
    log_dir.mkdir(parents=True, exist_ok=True)
    out_log = open(log_dir / f"worker_{wid}.out.log", "a", encoding="utf-8")  # noqa: SIM115
    err_log = open(log_dir / f"worker_{wid}.err.log", "a", encoding="utf-8")  # noqa: SIM115
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
    ]
    logger.info("Starting worker: %s", " ".join(cmd))
    return subprocess.Popen(
        cmd,
        cwd=str(cwd),
        stdout=out_log,
        stderr=err_log,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
    )


def _fill_worker_pool(
    *,
    maker: str,
    out_root: Path,
    state_db: Path,
    rate_limit: float,
    cwd: Path,
    target_workers: int,
    tracked: dict[int, str],
) -> None:
    """Spawn workers from the queue until ``target_workers`` are live."""
    while True:
        current = _live_worker_map()
        for pid, model in current.items():
            if pid not in tracked:
                tracked[pid] = model
        if len(current) >= target_workers:
            return
        skip = set(current.values())
        nxt = pop_next(out_root, skip=skip)
        if not nxt:
            logger.info(
                "Worker pool at %s/%s; replacement queue empty",
                len(current),
                target_workers,
            )
            return
        _clear_lease(state_db, nxt)
        proc = _spawn_worker(
            maker=maker,
            model=nxt,
            out_root=out_root,
            rate_limit=rate_limit,
            cwd=cwd,
        )
        tracked[proc.pid] = nxt
        logger.info("Pool fill started pid=%s model=%s (%s/%s)", proc.pid, nxt, len(current) + 1, target_workers)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Megazip worker pool supervisor (fixed concurrency + auto-restart)"
    )
    parser.add_argument("--maker", default="Nissan")
    parser.add_argument("--out-root", type=Path, default=DEFAULT_OUT_ROOT)
    parser.add_argument("--rate-limit", type=float, default=0.55)
    parser.add_argument("--poll-seconds", type=float, default=45.0)
    parser.add_argument(
        "--target-workers",
        type=int,
        default=6,
        help="Keep this many model workers running (default 6)",
    )
    parser.add_argument(
        "--prefer",
        default="",
        help="Comma-separated model_slugs to prioritize at front of queue (still nearest-first)",
    )
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    if args.target_workers < 1:
        logger.error("--target-workers must be >= 1")
        return 1

    config = MegazipConfig.load()
    paths = build_maker_paths(args.maker, args.out_root, config)
    cwd = Path(__file__).resolve().parent.parent
    init_db(paths.state_db)

    live = _live_worker_map()
    prefer = [m.strip() for m in args.prefer.split(",") if m.strip()]
    existing_queue = load_queue(args.out_root)
    if existing_queue:
        if prefer:
            from data_pipeline.megazip.replacement_queue import enqueue_models

            enqueue_models(args.out_root, prefer, front=True)
        logger.info(
            "Using existing replacement queue (%s entries): %s",
            len(existing_queue),
            load_queue(args.out_root)[:12],
        )
    else:
        pending = pending_by_model(paths.state_db)
        prefer = [m for m in prefer if m in pending]
        seed = seed_from_dead_models(
            args.out_root,
            paths.state_db,
            live_models=set(live.values()),
            prefer=prefer,
        )
        reorder_queue_nearest_first(
            args.out_root,
            paths.state_db,
            live_models=set(live.values()),
        )
        logger.info(
            "Replacement queue seeded (%s models): %s",
            len(seed["queued"]),
            load_queue(args.out_root)[:12],
        )
    logger.info("Watching %s live workers (target=%s): %s", len(live), args.target_workers, live)

    tracked = dict(live)
    _fill_worker_pool(
        maker=args.maker,
        out_root=args.out_root,
        state_db=paths.state_db,
        rate_limit=args.rate_limit,
        cwd=cwd,
        target_workers=args.target_workers,
        tracked=tracked,
    )

    while True:
        time.sleep(args.poll_seconds)
        expire_stale_leases(paths.state_db)
        current = _live_worker_map()

        for pid, model in list(tracked.items()):
            if pid in current:
                continue
            pending_n = _pending_for(paths.state_db, model)
            del tracked[pid]
            if pending_n == 0:
                logger.info(
                    "Worker pid=%s model=%s completed successfully (PENDING=0)",
                    pid,
                    model,
                )
                continue
            logger.warning(
                "Worker pid=%s model=%s exited with PENDING=%s — restarting same model",
                pid,
                model,
                pending_n,
            )
            _clear_lease(paths.state_db, model)
            proc = _spawn_worker(
                maker=args.maker,
                model=model,
                out_root=args.out_root,
                rate_limit=args.rate_limit,
                cwd=cwd,
            )
            tracked[proc.pid] = model

        for pid, model in current.items():
            if pid not in tracked:
                tracked[pid] = model

        _fill_worker_pool(
            maker=args.maker,
            out_root=args.out_root,
            state_db=paths.state_db,
            rate_limit=args.rate_limit,
            cwd=cwd,
            target_workers=args.target_workers,
            tracked=tracked,
        )

        logger.info(
            "alive=%s target=%s queue=%s leases=%s",
            len(_live_worker_map()),
            args.target_workers,
            load_queue(args.out_root)[:8],
            list_active_leases(paths.state_db),
        )


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(0) from None
