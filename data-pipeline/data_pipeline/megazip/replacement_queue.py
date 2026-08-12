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


def seed_from_dead_models(
    out_root: Path,
    state_db: Path,
    *,
    live_models: set[str],
    prefer: list[str] | None = None,
) -> dict[str, Any]:
    """Put unfinished models (not currently live) on the replacement queue.

    ``prefer`` (e.g. known crashed workers) are placed at the front, ordered by
    PENDING descending within that set.
    """
    pending = pending_by_model(state_db)
    prefer = prefer or []
    prefer_set = set(prefer)
    prefer_ordered = sorted(
        (m for m in prefer if m in pending and m not in live_models),
        key=lambda m: pending.get(m, 0),
        reverse=True,
    )
    others = [
        m
        for m, _n in sorted(pending.items(), key=lambda kv: kv[1], reverse=True)
        if m not in live_models and m not in prefer_set
    ]
    merged = prefer_ordered + others
    # Keep any existing queue entries that still have PENDING, then append new.
    existing = [m for m in load_queue(out_root) if m in pending and m not in live_models]
    final: list[str] = []
    for m in existing + merged:
        if m not in final:
            final.append(m)
    save_queue(out_root, final)
    return {
        "queued": final,
        "prefer_front": prefer_ordered,
        "pending_models": len(pending),
    }
