"""Durable discovery of chassis codes missing from CHASSIS_CATALOG.

When parse/enrich sees an unknown chassis, record it (deduped) and warn once
per process so VIN-prefix curation is not silent. Does NOT invent vin_prefixes.

Usage (from data-pipeline/)::

  python -m data_pipeline.chassis_discovery --list
  python -m data_pipeline.chassis_discovery --export-stubs
  python -m data_pipeline.chassis_discovery --db out/cache_parse_state.db --list
"""

from __future__ import annotations

import argparse
import json
import logging
import sqlite3
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

DEFAULT_DISCOVERY_DB = Path("out/unmapped_chassis.db")

_lock = threading.Lock()
_discovery_db: Path | None = None
_warned_chassis: set[str] = set()


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def get_discovery_db() -> Path:
    return _discovery_db if _discovery_db is not None else DEFAULT_DISCOVERY_DB


def set_discovery_db(path: Path | None) -> None:
    """Point discovery at a SQLite file (e.g. cache_parse_state.db). None → default."""
    global _discovery_db
    with _lock:
        _discovery_db = path


def reset_warning_state() -> None:
    """Clear in-process warn set (tests)."""
    with _lock:
        _warned_chassis.clear()


def ensure_schema(db_path: Path | None = None) -> Path:
    path = db_path or get_discovery_db()
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path, timeout=60.0)
    try:
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS unmapped_chassis (
                chassis_code TEXT PRIMARY KEY,
                first_seen TEXT NOT NULL,
                last_seen TEXT NOT NULL,
                example_vid TEXT,
                example_url TEXT,
                model_variant TEXT,
                hit_count INTEGER NOT NULL DEFAULT 0
            )
            """
        )
        conn.commit()
    finally:
        conn.close()
    return path


def note_unmapped_chassis(
    chassis_code: str,
    *,
    example_vid: str | None = None,
    example_url: str | None = None,
    model_variant: str | None = None,
    db_path: Path | None = None,
) -> bool:
    """Record an unmapped chassis. Returns True if a WARNING was emitted this call.

    Dedupes by chassis_code in SQLite. Logs WARNING at most once per chassis
    per process. On repeats: bump hit_count / last_seen only; keep the first
    non-null example_vid, example_url, and model_variant (fill blanks later).
    """
    code = (chassis_code or "").strip().upper()
    if not code:
        return False

    path = ensure_schema(db_path)
    now = _utc_now()
    vid = str(example_vid).strip() if example_vid else None
    url = str(example_url).strip() if example_url else None
    model = str(model_variant).strip() if model_variant else None

    is_new_row = False
    conn = sqlite3.connect(path, timeout=60.0)
    try:
        row = conn.execute(
            "SELECT 1 FROM unmapped_chassis WHERE chassis_code = ?",
            (code,),
        ).fetchone()
        if row is None:
            is_new_row = True
            conn.execute(
                """
                INSERT INTO unmapped_chassis (
                    chassis_code, first_seen, last_seen,
                    example_vid, example_url, model_variant, hit_count
                ) VALUES (?, ?, ?, ?, ?, ?, 1)
                """,
                (code, now, now, vid, url, model),
            )
        else:
            conn.execute(
                """
                UPDATE unmapped_chassis SET
                    last_seen = ?,
                    hit_count = hit_count + 1,
                    example_vid = COALESCE(example_vid, ?),
                    example_url = COALESCE(example_url, ?),
                    model_variant = COALESCE(model_variant, ?)
                WHERE chassis_code = ?
                """,
                (now, vid, url, model, code),
            )
        conn.commit()
    finally:
        conn.close()

    should_warn = False
    with _lock:
        if code not in _warned_chassis:
            _warned_chassis.add(code)
            should_warn = True

    if should_warn:
        logger.warning(
            "Unmapped chassis %s — not in curated catalog; "
            "epc_stub may supply a garage key (curate vin_prefixes for accuracy). "
            "example_vid=%s example_url=%s%s",
            code,
            vid or "?",
            url or "?",
            " [first durable sighting]" if is_new_row else "",
        )
    return should_warn


def list_unmapped_chassis(db_path: Path | None = None) -> list[dict[str, Any]]:
    path = ensure_schema(db_path)
    conn = sqlite3.connect(path, timeout=60.0)
    try:
        rows = conn.execute(
            """
            SELECT chassis_code, first_seen, last_seen, example_vid, example_url,
                   model_variant, hit_count
            FROM unmapped_chassis
            ORDER BY hit_count DESC, chassis_code
            """
        ).fetchall()
    finally:
        conn.close()
    return [
        {
            "chassis_code": r[0],
            "first_seen": r[1],
            "last_seen": r[2],
            "example_vid": r[3],
            "example_url": r[4],
            "model_variant": r[5],
            "hit_count": int(r[6]),
        }
        for r in rows
    ]


def curation_stubs(db_path: Path | None = None) -> list[dict[str, Any]]:
    """Export stub catalog entries for curation — vin_prefixes intentionally empty."""
    stubs: list[dict[str, Any]] = []
    for row in list_unmapped_chassis(db_path):
        stubs.append(
            {
                "chassis_code": row["chassis_code"],
                "model_variant": row.get("model_variant")
                or f"Nissan {row['chassis_code']} (UNMAPPED — curate)",
                "vin_prefixes": [],  # do not invent; fill after research
                "engines": [],
                "year_range": None,
                "_discovery": {
                    "hit_count": row["hit_count"],
                    "example_vid": row.get("example_vid"),
                    "example_url": row.get("example_url"),
                    "first_seen": row.get("first_seen"),
                    "last_seen": row.get("last_seen"),
                },
            }
        )
    return stubs


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="List / export chassis codes missing from CHASSIS_CATALOG"
    )
    parser.add_argument(
        "--db",
        type=Path,
        default=None,
        help=f"SQLite path (default: {DEFAULT_DISCOVERY_DB} or cache_parse_state.db)",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="Print unmapped chassis rows (default action)",
    )
    parser.add_argument(
        "--export-stubs",
        action="store_true",
        help="Print JSON stubs with empty vin_prefixes for curation",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Write export to file instead of stdout",
    )
    args = parser.parse_args(argv)

    db = args.db
    if db is None:
        parse_db = Path("out/cache_parse_state.db")
        db = parse_db if parse_db.exists() else DEFAULT_DISCOVERY_DB

    if args.export_stubs:
        payload: Any = curation_stubs(db)
    else:
        payload = list_unmapped_chassis(db)

    text = json.dumps(payload, indent=2, ensure_ascii=False)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text + "\n", encoding="utf-8")
        print(f"Wrote {len(payload)} entries → {args.output}")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
