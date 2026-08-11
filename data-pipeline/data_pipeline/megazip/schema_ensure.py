"""Ensure live DB columns match vendor-neutral ``external_*`` names when possible.

Bundle readiness does **not** depend on this: transform/filter already emit
``external_*`` via ``prepare_hierarchy_for_supabase_import``. This module is an
optional live-DB repair if an old database still has ``megazip_*`` columns.
"""

from __future__ import annotations

import logging
import os
import re
from pathlib import Path
from typing import Any
from urllib.parse import quote_plus, urlparse

from data_pipeline.import_catalog import load_env_files

logger = logging.getLogger(__name__)

PACKAGE_ROOT = Path(__file__).resolve().parent.parent.parent
REPO_ROOT = PACKAGE_ROOT.parent

# Idempotent renames only — value scrub stays on the REST scrub path / migration.
_RENAME_SQL = """
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'catalog_variants'
      AND column_name = 'megazip_data_id'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'catalog_variants'
      AND column_name = 'external_data_id'
  ) THEN
    ALTER TABLE public.catalog_variants
      RENAME COLUMN megazip_data_id TO external_data_id;
  END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'catalog_diagram_parts'
      AND column_name = 'megazip_item_id'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'catalog_diagram_parts'
      AND column_name = 'external_item_id'
  ) THEN
    ALTER TABLE public.catalog_diagram_parts
      RENAME COLUMN megazip_item_id TO external_item_id;
  END IF;
END $$;
"""


def _load_pipeline_env() -> None:
    load_env_files(
        PACKAGE_ROOT / ".env",
        REPO_ROOT / ".env",
        override=True,
    )


def resolve_database_url(*, env: dict[str, str] | None = None) -> str | None:
    """Resolve Postgres URL for DDL (rename). Prefer explicit URLs, then password+ref."""
    e = env if env is not None else os.environ
    for key in ("DATABASE_URL", "SUPABASE_DB_URL", "POSTGRES_URL", "POSTGRES_PRISMA_URL"):
        raw = (e.get(key) or "").strip()
        if raw:
            return raw

    password = (
        (e.get("SUPABASE_DB_PASSWORD") or e.get("POSTGRES_PASSWORD") or "").strip()
    )
    supabase_url = (e.get("SUPABASE_URL") or e.get("NEXT_PUBLIC_SUPABASE_URL") or "").strip()
    if not password or not supabase_url:
        return None

    host = urlparse(supabase_url).hostname or ""
    # Local CLI: http://127.0.0.1:54321 → db on 54322 by default
    if host in {"127.0.0.1", "localhost"}:
        port = (e.get("SUPABASE_DB_PORT") or "54322").strip()
        user = (e.get("SUPABASE_DB_USER") or "postgres").strip()
        return f"postgresql://{user}:{quote_plus(password)}@{host}:{port}/postgres"

    m = re.match(r"^([a-z0-9-]+)\.supabase\.co$", host, re.I)
    if not m:
        return None
    ref = m.group(1)
    user = (e.get("SUPABASE_DB_USER") or "postgres").strip()
    # Direct DB host (session mode). Pooler also works if user sets DATABASE_URL.
    return (
        f"postgresql://{user}:{quote_plus(password)}"
        f"@db.{ref}.supabase.co:5432/postgres"
    )


def _connect_execute(db_url: str, sql: str) -> None:
    try:
        import psycopg
    except ImportError:
        try:
            import psycopg2 as psycopg  # type: ignore
        except ImportError as exc:
            raise RuntimeError(
                "psycopg required for catalog column rename: "
                "pip install 'psycopg[binary]' (or data_pipeline[supabase])"
            ) from exc

    if not hasattr(psycopg, "connect"):
        raise RuntimeError("unsupported psycopg API")

    with psycopg.connect(db_url) as conn:
        if hasattr(conn, "execute"):
            conn.execute(sql)  # type: ignore[attr-defined]
            conn.commit()
        else:
            with conn.cursor() as cur:
                cur.execute(sql)
            conn.commit()


def ensure_external_catalog_columns(
    *,
    database_url: str | None = None,
    load_env: bool = True,
) -> dict[str, Any]:
    """Rename ``megazip_*`` → ``external_*`` when legacy columns exist.

    Returns a small status dict for import notes. Never raises on "already done"
    or "no database URL" — caller may still dual-map via REST.
    """
    if load_env:
        _load_pipeline_env()
    db_url = (database_url or resolve_database_url() or "").strip()
    if not db_url:
        logger.info(
            "catalog column rename skipped — set DATABASE_URL or "
            "SUPABASE_DB_PASSWORD (+ SUPABASE_URL) for automatic megazip_* → external_*"
        )
        return {"ok": True, "applied": False, "reason": "no_database_url"}

    host = urlparse(db_url).hostname or ""
    try:
        _connect_execute(db_url, _RENAME_SQL)
    except Exception as exc:  # noqa: BLE001
        logger.warning("catalog column rename failed (%s): %s", host, exc)
        return {"ok": False, "applied": False, "reason": "error", "error": str(exc), "host": host}

    logger.info("catalog column rename ensured (host=%s)", host)
    return {"ok": True, "applied": True, "host": host}


def rename_sql() -> str:
    """Expose SQL for tests / optional CLI."""
    return _RENAME_SQL
