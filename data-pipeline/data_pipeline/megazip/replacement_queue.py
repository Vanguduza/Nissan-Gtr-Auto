"""Replacement queue for Megazip model workers.

Unfinished models wait here ordered by ascending PENDING (nearest finish first).
The supervisor keeps a fixed worker pool; on crash it restarts the same model,
on success it pops the next queued model.
"""

from __future__ import annotations

import json
import logging
import sqlite3
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

QUEUE_NAME = "worker_replacement_queue.json"


def queue_path(out_root: Path) -> Path:
    return Path(out_root) / QUEUE_NAME


def load_queue(out_root: Path) -> list[str]:
    path = queue_path(out_root)
    if not path.is_file():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict):
        models = data.get("models") or []
    else:
        models = data
    return [str(m).strip() for m in models if str(m).strip()]


def save_queue(out_root: Path, models: list[str]) -> None:
    path = queue_path(out_root)
    path.parent.mkdir(parents=True, exist_ok=True)
    # de-dupe preserve order
    seen: set[str] = set()
    ordered: list[str] = []
    for m in models:
        if m not in seen:
            seen.add(m)
            ordered.append(m)
    path.write_text(
        json.dumps({"models": ordered}, indent=2) + "\n",
        encoding="utf-8",
    )


def enqueue_models(out_root: Path, models: list[str], *, front: bool = False) -> list[str]:
    q = load_queue(out_root)
    incoming = [m for m in models if m]
    if front:
        q = incoming + [m for m in q if m not in incoming]
    else:
        for m in incoming:
            if m not in q:
                q.append(m)
    save_queue(out_root, q)
    return q


def pop_next(out_root: Path, *, skip: set[str] | None = None) -> str | None:
    skip = skip or set()
    q = load_queue(out_root)
    for i, model in enumerate(q):
        if model in skip:
            continue
        rest = q[:i] + q[i + 1 :]
        save_queue(out_root, rest)
        return model
    return None


def pending_by_model(state_db: Path) -> dict[str, int]:
    conn = sqlite3.connect(f"file:{Path(state_db).as_posix()}?mode=ro", uri=True, timeout=120.0)
    try:
        rows = conn.execute(
            """
            SELECT model_slug, COUNT(1)
            FROM queue
            WHERE status = 'PENDING' AND model_slug IS NOT NULL AND trim(model_slug) != ''
            GROUP BY model_slug
            ORDER BY 2 DESC
            """
        ).fetchall()
        return {str(m): int(n) for m, n in rows}
    finally:
        conn.close()


def progress_by_model(state_db: Path) -> dict[str, tuple[int, int]]:
    """Return {model_slug: (pending, visited)} for models with any queue rows."""
    conn = sqlite3.connect(f"file:{Path(state_db).as_posix()}?mode=ro", uri=True, timeout=120.0)
    try:
        rows = conn.execute(
            """
            SELECT model_slug,
                   SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN status = 'VISITED' THEN 1 ELSE 0 END)
            FROM queue
            WHERE model_slug IS NOT NULL AND trim(model_slug) != ''
            GROUP BY model_slug
            """
        ).fetchall()
        return {str(m): (int(p or 0), int(v or 0)) for m, p, v in rows}
    finally:
        conn.close()


def queue_candidates(
    state_db: Path,
    *,
    live_models: set[str] | None = None,
    min_visited: int = 100,
) -> list[str]:
    """Models eligible for workers: pending work and meaningful crawl progress."""
    live_models = live_models or set()
    progress = progress_by_model(state_db)
    eligible = [
        m
        for m, (pending, visited) in progress.items()
        if pending > 0 and visited >= min_visited and m not in live_models
    ]
    eligible.sort(key=lambda m: progress[m][0])
    return eligible


def reorder_queue_nearest_first(
    out_root: Path,
    state_db: Path,
    *,
    live_models: set[str] | None = None,
) -> list[str]:
    """Re-order queue by ascending PENDING (models closest to done first)."""
    live_models = live_models or set()
    pending = pending_by_model(state_db)
    candidates: list[str] = []
    for m in load_queue(out_root):
        if m in pending and m not in live_models and m not in candidates:
            candidates.append(m)
    for m in pending:
        if m not in live_models and m not in candidates:
            candidates.append(m)
    candidates.sort(key=lambda m: pending.get(m, 0))
    save_queue(out_root, candidates)
    return candidates


def seed_from_dead_models(
    out_root: Path,
    state_db: Path,
    *,
    live_models: set[str],
    prefer: list[str] | None = None,
) -> dict[str, Any]:
    """Put unfinished models (not currently live) on the replacement queue.

    Final order is ascending PENDING — nearest to finish is started first.
    """
    pending = pending_by_model(state_db)
    prefer = prefer or []
    prefer_set = set(prefer)
    existing = [m for m in load_queue(out_root) if m in pending and m not in live_models]
    merged: list[str] = []
    for m in existing + prefer + list(pending.keys()):
        if m in pending and m not in live_models and m not in merged:
            merged.append(m)
    if prefer_set:
        prefer_first = sorted(
            (m for m in merged if m in prefer_set),
            key=lambda m: pending.get(m, 0),
        )
        rest = sorted(
            (m for m in merged if m not in prefer_set),
            key=lambda m: pending.get(m, 0),
        )
        final = prefer_first + rest
    else:
        final = sorted(merged, key=lambda m: pending.get(m, 0))
    save_queue(out_root, final)
    return {
        "queued": final,
        "prefer_front": [m for m in final if m in prefer_set][:10],
        "pending_models": len(pending),
    }
