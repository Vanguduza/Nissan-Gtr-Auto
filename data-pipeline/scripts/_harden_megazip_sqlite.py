"""Harden megazip state.py SQLite access for multi-worker contention."""

from __future__ import annotations

from pathlib import Path

p = Path(__file__).resolve().parents[1] / "data_pipeline" / "megazip" / "state.py"
text = p.read_text(encoding="utf-8")

old = """from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from typing import Any
"""
new = """from __future__ import annotations

import json
import logging
import sqlite3
import time
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

# Long wait + WAL: many model-scoped workers share one state DB.
SQLITE_TIMEOUT_SECONDS = 300.0
SQLITE_BUSY_TIMEOUT_MS = 300_000
SQLITE_LOCK_RETRIES = 8
"""
if old not in text:
    raise SystemExit("header mismatch")
text = text.replace(old, new, 1)

marker = "LEASE_TTL_SECONDS = 180\n\n\ndef init_db"
helper = '''LEASE_TTL_SECONDS = 180


def connect(db_path: Path, *, write: bool = True) -> sqlite3.Connection:
    """Open Megazip state DB with WAL + long busy wait (multi-worker safe)."""
    conn = sqlite3.connect(str(db_path), timeout=SQLITE_TIMEOUT_SECONDS)
    conn.execute(f"PRAGMA busy_timeout={SQLITE_BUSY_TIMEOUT_MS}")
    if write:
        try:
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute("PRAGMA synchronous=NORMAL")
        except sqlite3.OperationalError:
            pass
    return conn


def with_retry(fn, *, label: str = "sqlite"):
    """Retry fn() on database is locked."""
    delay = 0.5
    last: Exception | None = None
    for attempt in range(1, SQLITE_LOCK_RETRIES + 1):
        try:
            return fn()
        except sqlite3.OperationalError as exc:
            last = exc
            if "locked" not in str(exc).lower():
                raise
            logger.warning(
                "%s locked (attempt %s/%s): %s",
                label,
                attempt,
                SQLITE_LOCK_RETRIES,
                exc,
            )
            time.sleep(delay)
            delay = min(delay * 1.7, 8.0)
    assert last is not None
    raise last


def init_db'''
if marker not in text:
    raise SystemExit("marker missing")
text = text.replace(marker, helper, 1)
text = text.replace("sqlite3.connect(db_path, timeout=60.0)", "connect(db_path)")
text = text.replace("sqlite3.connect(db_path, timeout=30.0)", "connect(db_path)")
left = text.count("sqlite3.connect(")
p.write_text(text, encoding="utf-8")
print("wrote", p, "raw_connects_left", left)
