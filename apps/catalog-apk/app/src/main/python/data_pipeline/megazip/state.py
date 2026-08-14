"""SQLite crawl/parse state for Megazip — enables re-run without re-crawl."""

from __future__ import annotations

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

_SCHEMA = """
CREATE TABLE IF NOT EXISTS queue (
  url TEXT PRIMARY KEY,
  status TEXT NOT NULL DEFAULT 'PENDING',
  page_type TEXT,
  maker_slug TEXT,
  model_slug TEXT,
  variant_slug TEXT,
  section_slug TEXT,
  chassis_code TEXT,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  updated_at TEXT DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS queue_status_idx ON queue(status);

CREATE TABLE IF NOT EXISTS page_cache (
  url TEXT PRIMARY KEY,
  cache_path TEXT NOT NULL,
  content_hash TEXT,
  fetched_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS parsed_pages (
  url TEXT PRIMARY KEY,
  page_type TEXT NOT NULL,
  maker_slug TEXT,
  payload_json TEXT NOT NULL,
  parsed_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS worker_leases (
  model_slug TEXT PRIMARY KEY,
  worker_id TEXT NOT NULL,
  leased_at TEXT NOT NULL DEFAULT (datetime('now')),
  heartbeat_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS worker_leases_worker_idx ON worker_leases(worker_id);
CREATE INDEX IF NOT EXISTS worker_leases_heartbeat_idx ON worker_leases(heartbeat_at);
"""

# Leases older than this (no heartbeat) are treated as free.
LEASE_TTL_SECONDS = 180


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


def init_db(db_path: Path) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = connect(db_path)
    try:
        conn.executescript(_SCHEMA)
        conn.commit()
    finally:
        conn.close()


def expire_stale_leases(db_path: Path, *, ttl_seconds: int = LEASE_TTL_SECONDS) -> int:
    """Drop leases whose heartbeat is older than ttl. Returns rows deleted."""
    conn = connect(db_path)
    try:
        conn.execute("BEGIN IMMEDIATE")
        cur = conn.execute(
            """
            DELETE FROM worker_leases
            WHERE heartbeat_at < datetime('now', ?)
            """,
            (f"-{int(ttl_seconds)} seconds",),
        )
        conn.commit()
        return int(cur.rowcount or 0)
    finally:
        conn.close()


def list_active_leases(db_path: Path, *, ttl_seconds: int = LEASE_TTL_SECONDS) -> dict[str, str]:
    """Return {model_slug: worker_id} for non-expired leases."""
    expire_stale_leases(db_path, ttl_seconds=ttl_seconds)
    conn = connect(db_path)
    try:
        rows = conn.execute(
            """
            SELECT model_slug, worker_id FROM worker_leases
            WHERE heartbeat_at >= datetime('now', ?)
            """,
            (f"-{int(ttl_seconds)} seconds",),
        ).fetchall()
        return {str(m): str(w) for m, w in rows}
    finally:
        conn.close()


def acquire_model_leases(
    db_path: Path,
    worker_id: str,
    model_slugs: frozenset[str] | set[str] | list[str],
    *,
    ttl_seconds: int = LEASE_TTL_SECONDS,
) -> tuple[frozenset[str], dict[str, str]]:
    """Exclusively lease models for ``worker_id``.

    Returns ``(acquired, blocked)`` where ``blocked`` maps model → other worker_id.
    """
    expire_stale_leases(db_path, ttl_seconds=ttl_seconds)
    wanted = sorted({m for m in model_slugs if m})
    acquired: list[str] = []
    blocked: dict[str, str] = {}
    conn = connect(db_path)
    try:
        conn.execute("BEGIN IMMEDIATE")
        for model in wanted:
            row = conn.execute(
                "SELECT worker_id FROM worker_leases WHERE model_slug = ?",
                (model,),
            ).fetchone()
            if row and row[0] != worker_id:
                blocked[model] = str(row[0])
                continue
            conn.execute(
                """
                INSERT INTO worker_leases (model_slug, worker_id, leased_at, heartbeat_at)
                VALUES (?, ?, datetime('now'), datetime('now'))
                ON CONFLICT(model_slug) DO UPDATE SET
                  worker_id = excluded.worker_id,
                  heartbeat_at = datetime('now')
                WHERE worker_leases.worker_id = excluded.worker_id
                """,
                (model, worker_id),
            )
            check = conn.execute(
                "SELECT worker_id FROM worker_leases WHERE model_slug = ?",
                (model,),
            ).fetchone()
            if check and check[0] == worker_id:
                acquired.append(model)
            else:
                blocked[model] = str(check[0]) if check else "unknown"
        conn.commit()
    finally:
        conn.close()
    return frozenset(acquired), blocked


def heartbeat_leases(db_path: Path, worker_id: str) -> int:
    conn = connect(db_path)
    try:
        cur = conn.execute(
            """
            UPDATE worker_leases
            SET heartbeat_at = datetime('now')
            WHERE worker_id = ?
            """,
            (worker_id,),
        )
        conn.commit()
        return int(cur.rowcount or 0)
    finally:
        conn.close()


def release_leases(db_path: Path, worker_id: str, model_slugs: frozenset[str] | None = None) -> int:
    def _once() -> int:
        conn = connect(db_path)
        try:
            if model_slugs is None:
                cur = conn.execute("DELETE FROM worker_leases WHERE worker_id = ?", (worker_id,))
            else:
                models = sorted(model_slugs)
                if not models:
                    return 0
                placeholders = ",".join("?" for _ in models)
                cur = conn.execute(
                    f"DELETE FROM worker_leases WHERE worker_id = ? AND model_slug IN ({placeholders})",
                    (worker_id, *models),
                )
            conn.commit()
            return int(cur.rowcount or 0)
        finally:
            conn.close()

    return with_retry(_once, label="release_leases")


def reclaim_stale_processing(db_path: Path, *, older_than_seconds: int = 600) -> int:
    """Re-queue PROCESSING rows stuck longer than ``older_than_seconds``."""
    conn = connect(db_path)
    try:
        cur = conn.execute(
            """
            UPDATE queue SET status = 'PENDING', updated_at = datetime('now')
            WHERE status = 'PROCESSING'
              AND updated_at < datetime('now', ?)
            """,
            (f"-{int(older_than_seconds)} seconds",),
        )
        conn.commit()
        return int(cur.rowcount or 0)
    finally:
        conn.close()


def enqueue_url(
    db_path: Path,
    url: str,
    *,
    page_type: str = "",
    maker_slug: str = "",
    model_slug: str = "",
    variant_slug: str = "",
    section_slug: str = "",
    chassis_code: str = "",
) -> None:
    conn = connect(db_path)
    try:
        conn.execute(
            """
            INSERT INTO queue (url, status, page_type, maker_slug, model_slug, variant_slug, section_slug, chassis_code)
            VALUES (?, 'PENDING', ?, ?, ?, ?, ?, ?)
            ON CONFLICT(url) DO NOTHING
            """,
            (url, page_type, maker_slug, model_slug, variant_slug, section_slug, chassis_code),
        )
        conn.commit()
    finally:
        conn.close()


_SHALLOW_PAGE_TYPES = ("maker_hub", "model_catalog", "model_hub", "variant_list")
_DEEP_PAGE_TYPES = ("section_list", "diagram")


def _queue_scope_where(
    *,
    maker_slug: str | None,
    model_slugs: frozenset[str] | None,
    exclude_leased: bool,
    lease_owner: str | None,
) -> tuple[list[str], list[Any]]:
    where = ["status = 'PENDING'"]
    params: list[Any] = []
    if maker_slug:
        where.append("maker_slug = ?")
        params.append(maker_slug)
    if model_slugs:
        placeholders = ",".join("?" for _ in model_slugs)
        where.append(f"model_slug IN ({placeholders})")
        params.extend(sorted(model_slugs))
    if exclude_leased:
        if lease_owner:
            where.append(
                """
                model_slug NOT IN (
                  SELECT model_slug FROM worker_leases
                  WHERE worker_id != ?
                    AND heartbeat_at >= datetime('now', ?)
                )
                """
            )
            params.extend([lease_owner, f"-{LEASE_TTL_SECONDS} seconds"])
        else:
            where.append(
                """
                model_slug NOT IN (
                  SELECT model_slug FROM worker_leases
                  WHERE heartbeat_at >= datetime('now', ?)
                )
                """
            )
            params.append(f"-{LEASE_TTL_SECONDS} seconds")
    return where, params


def select_focus_chassis(
    db_path: Path,
    *,
    maker_slug: str | None = None,
    model_slugs: frozenset[str] | None = None,
    exclude_leased: bool = False,
    lease_owner: str | None = None,
) -> str | None:
    """Chassis to deep-crawl next: in-progress first, else stable sort by code."""
    if exclude_leased:
        expire_stale_leases(db_path)

    where, params = _queue_scope_where(
        maker_slug=maker_slug,
        model_slugs=model_slugs,
        exclude_leased=exclude_leased,
        lease_owner=lease_owner,
    )
    deep_placeholders = ",".join("?" for _ in _DEEP_PAGE_TYPES)
    where.append(f"page_type IN ({deep_placeholders})")
    params.extend(_DEEP_PAGE_TYPES)
    where.append("chassis_code IS NOT NULL AND trim(chassis_code) != ''")

    sql = f"""
        SELECT q.chassis_code
        FROM queue q
        WHERE {' AND '.join(where)}
        ORDER BY
          CASE WHEN EXISTS (
            SELECT 1 FROM queue v
            WHERE v.chassis_code = q.chassis_code
              AND v.status IN ('VISITED', 'PROCESSING')
              AND v.page_type IN ('section_list', 'diagram')
          ) THEN 0 ELSE 1 END,
          q.chassis_code
        LIMIT 1
        """
    conn = connect(db_path)
    try:
        row = conn.execute(sql, params).fetchone()
    finally:
        conn.close()
    return str(row[0]) if row and row[0] else None


def claim_next_url(
    db_path: Path,
    *,
    maker_slug: str | None = None,
    model_slugs: frozenset[str] | None = None,
    exclude_leased: bool = False,
    lease_owner: str | None = None,
    chassis_deep_first: bool = True,
) -> dict[str, Any] | None:
    """Claim next PENDING URL.

    ``model_slugs`` — only claim these models (worker scope).
    ``exclude_leased`` — skip models leased by other workers (main crawler).
    ``lease_owner`` — when excluding, still allow models leased by this worker_id.
    ``chassis_deep_first`` — finish shallow discovery globally, then drain
    section/diagram pages for one chassis at a time (usable chassis slice before
    the next variant's deep cache accumulates).
    """
    if exclude_leased:
        expire_stale_leases(db_path)

    def _claim_row(conn: sqlite3.Connection, where: list[str], params: list[Any]) -> dict[str, Any] | None:
        sql = f"""
            SELECT url, page_type, maker_slug, model_slug, variant_slug, section_slug, chassis_code
            FROM queue
            WHERE {' AND '.join(where)}
            ORDER BY
              CASE COALESCE(page_type, '')
                WHEN 'maker_hub' THEN 0
                WHEN 'model_catalog' THEN 1
                WHEN 'model_hub' THEN 1
                WHEN 'variant_list' THEN 2
                WHEN 'section_list' THEN 3
                WHEN 'diagram' THEN 4
                ELSE 5
              END,
              url
            LIMIT 1
            """
        row = conn.execute(sql, params).fetchone()
        if not row:
            return None
        conn.execute(
            "UPDATE queue SET status = 'PROCESSING', attempts = attempts + 1, "
            "updated_at = datetime('now') WHERE url = ?",
            (row[0],),
        )
        keys = (
            "url",
            "page_type",
            "maker_slug",
            "model_slug",
            "variant_slug",
            "section_slug",
            "chassis_code",
        )
        return dict(zip(keys, row, strict=True))

    def _once() -> dict[str, Any] | None:
        conn = connect(db_path)
        try:
            conn.execute("BEGIN IMMEDIATE")
            base_where, base_params = _queue_scope_where(
                maker_slug=maker_slug,
                model_slugs=model_slugs,
                exclude_leased=exclude_leased,
                lease_owner=lease_owner,
            )

            if chassis_deep_first:
                shallow_ph = ",".join("?" for _ in _SHALLOW_PAGE_TYPES)
                shallow_where = list(base_where) + [f"page_type IN ({shallow_ph})"]
                shallow_params = list(base_params) + list(_SHALLOW_PAGE_TYPES)
                claimed = _claim_row(conn, shallow_where, shallow_params)
                if claimed:
                    conn.commit()
                    return claimed

                deep_ph = ",".join("?" for _ in _DEEP_PAGE_TYPES)
                focus = None
                focus_sql = f"""
                    SELECT q.chassis_code
                    FROM queue q
                    WHERE {' AND '.join(base_where)}
                      AND q.page_type IN ({deep_ph})
                      AND q.chassis_code IS NOT NULL AND trim(q.chassis_code) != ''
                    ORDER BY
                      CASE WHEN EXISTS (
                        SELECT 1 FROM queue v
                        WHERE v.chassis_code = q.chassis_code
                          AND v.status IN ('VISITED', 'PROCESSING')
                          AND v.page_type IN ('section_list', 'diagram')
                      ) THEN 0 ELSE 1 END,
                      q.chassis_code
                    LIMIT 1
                    """
                focus_row = conn.execute(
                    focus_sql, list(base_params) + list(_DEEP_PAGE_TYPES)
                ).fetchone()
                if focus_row and focus_row[0]:
                    focus = str(focus_row[0])

                if focus:
                    deep_where = list(base_where) + [
                        f"page_type IN ({deep_ph})",
                        "chassis_code = ?",
                    ]
                    deep_params = list(base_params) + list(_DEEP_PAGE_TYPES) + [focus]
                    claimed = _claim_row(conn, deep_where, deep_params)
                    if claimed:
                        conn.commit()
                        return claimed

                # Deep pages missing chassis_code — drain before other chassis work.
                empty_where = list(base_where) + [
                    f"page_type IN ({deep_ph})",
                    "(chassis_code IS NULL OR trim(chassis_code) = '')",
                ]
                empty_params = list(base_params) + list(_DEEP_PAGE_TYPES)
                claimed = _claim_row(conn, empty_where, empty_params)
                if claimed:
                    conn.commit()
                    return claimed

            claimed = _claim_row(conn, base_where, base_params)
            if not claimed:
                conn.commit()
                return None
            conn.commit()
            return claimed
        finally:
            conn.close()

    return with_retry(_once, label="claim_next_url")


def mark_url(db_path: Path, url: str, *, ok: bool, error: str | None = None) -> None:
    status = "VISITED" if ok else "ERROR"

    def _once() -> None:
        conn = connect(db_path)
        try:
            conn.execute(
                "UPDATE queue SET status = ?, last_error = ?, updated_at = datetime('now') WHERE url = ?",
                (status, error, url),
            )
            conn.commit()
        finally:
            conn.close()

    with_retry(_once, label="mark_url")


def save_cache(db_path: Path, url: str, cache_path: str, content_hash: str) -> None:
    conn = connect(db_path)
    try:
        conn.execute(
            """
            INSERT INTO page_cache (url, cache_path, content_hash)
            VALUES (?, ?, ?)
            ON CONFLICT(url) DO UPDATE SET cache_path = excluded.cache_path, content_hash = excluded.content_hash
            """,
            (url, cache_path, content_hash),
        )
        conn.commit()
    finally:
        conn.close()


def upsert_parsed(db_path: Path, url: str, page_type: str, maker_slug: str, payload: dict[str, Any]) -> None:
    conn = connect(db_path)
    try:
        conn.execute(
            """
            INSERT INTO parsed_pages (url, page_type, maker_slug, payload_json)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(url) DO UPDATE SET
              page_type = excluded.page_type,
              payload_json = excluded.payload_json,
              parsed_at = datetime('now')
            """,
            (url, page_type, maker_slug, json.dumps(payload, ensure_ascii=False)),
        )
        conn.commit()
    finally:
        conn.close()


def load_all_parsed(db_path: Path, *, maker_slug: str | None = None) -> list[dict[str, Any]]:
    conn = connect(db_path)
    try:
        if maker_slug:
            rows = conn.execute(
                "SELECT url, page_type, maker_slug, payload_json FROM parsed_pages WHERE maker_slug = ?",
                (maker_slug,),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT url, page_type, maker_slug, payload_json FROM parsed_pages"
            ).fetchall()
        out: list[dict[str, Any]] = []
        for url, page_type, slug, payload_json in rows:
            out.append(
                {
                    "url": url,
                    "page_type": page_type,
                    "maker_slug": slug,
                    "payload": json.loads(payload_json),
                }
            )
        return out
    finally:
        conn.close()


def queue_stats(db_path: Path) -> dict[str, int]:
    conn = connect(db_path)
    try:
        rows = conn.execute("SELECT status, COUNT(*) FROM queue GROUP BY status").fetchall()
        return {str(s): int(c) for s, c in rows}
    finally:
        conn.close()


def reset_url_pending(db_path: Path, url: str) -> None:
    """Mark a queued URL PENDING again (nissan-remaining pass)."""
    conn = connect(db_path)
    try:
        conn.execute(
            "UPDATE queue SET status = 'PENDING', updated_at = datetime('now') WHERE url = ?",
            (url,),
        )
        conn.commit()
    finally:
        conn.close()


def pending_count(
    db_path: Path,
    *,
    maker_slug: str | None = None,
    model_slugs: frozenset[str] | None = None,
    exclude_leased: bool = False,
    lease_owner: str | None = None,
) -> int:
    if exclude_leased:
        expire_stale_leases(db_path)
    conn = connect(db_path)
    try:
        where = ["status = 'PENDING'"]
        params: list[Any] = []
        if maker_slug:
            where.append("maker_slug = ?")
            params.append(maker_slug)
        if model_slugs:
            placeholders = ",".join("?" for _ in model_slugs)
            where.append(f"model_slug IN ({placeholders})")
            params.extend(sorted(model_slugs))
        if exclude_leased:
            if lease_owner:
                where.append(
                    """
                    model_slug NOT IN (
                      SELECT model_slug FROM worker_leases
                      WHERE worker_id != ?
                        AND heartbeat_at >= datetime('now', ?)
                    )
                    """
                )
                params.extend([lease_owner, f"-{LEASE_TTL_SECONDS} seconds"])
            else:
                where.append(
                    """
                    model_slug NOT IN (
                      SELECT model_slug FROM worker_leases
                      WHERE heartbeat_at >= datetime('now', ?)
                    )
                    """
                )
                params.append(f"-{LEASE_TTL_SECONDS} seconds")
        row = conn.execute(
            f"SELECT COUNT(*) FROM queue WHERE {' AND '.join(where)}",
            params,
        ).fetchone()
        return int(row[0]) if row else 0
    finally:
        conn.close()


def leased_pending_count(db_path: Path) -> int:
    """PENDING rows whose model is currently leased by any worker."""
    expire_stale_leases(db_path)
    conn = connect(db_path)
    try:
        row = conn.execute(
            """
            SELECT COUNT(*) FROM queue q
            WHERE q.status = 'PENDING'
              AND q.model_slug IN (
                SELECT model_slug FROM worker_leases
                WHERE heartbeat_at >= datetime('now', ?)
              )
            """,
            (f"-{LEASE_TTL_SECONDS} seconds",),
        ).fetchone()
        return int(row[0]) if row else 0
    finally:
        conn.close()
