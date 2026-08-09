"""Apply megazip scrub SQL to hosted Postgres (DATABASE_URL from repo .env)."""

from __future__ import annotations

import os
import sys
from pathlib import Path
from urllib.parse import urlparse

from data_pipeline.import_catalog import load_env_files


def main() -> int:
    repo = Path(__file__).resolve().parents[2]
    pipe = Path(__file__).resolve().parents[1]
    load_env_files(pipe / ".env", repo / ".env", override=True)
    db_url = os.environ.get("DATABASE_URL") or os.environ.get("SUPABASE_DB_URL")
    if not db_url:
        print("DATABASE_URL not set", file=sys.stderr)
        return 2
    host = urlparse(db_url).hostname or ""
    print("db_host", host)
    if host in {"127.0.0.1", "localhost"}:
        print("refusing local db — set hosted DATABASE_URL", file=sys.stderr)
        return 3

    sql_path = (
        repo / "supabase" / "migrations" / "20260809120000_scrub_megazip_from_catalog.sql"
    )
    sql = sql_path.read_text(encoding="utf-8")

    try:
        import psycopg
    except ImportError:
        try:
            import psycopg2 as psycopg  # type: ignore
        except ImportError:
            print("pip install psycopg[binary]", file=sys.stderr)
            return 4

    # psycopg3 vs psycopg2
    if hasattr(psycopg, "connect"):
        with psycopg.connect(db_url) as conn:
            conn.execute(sql) if hasattr(conn, "execute") else None
            if hasattr(conn, "execute"):
                conn.commit()
            else:
                with conn.cursor() as cur:
                    cur.execute(sql)
                conn.commit()
    print("applied", sql_path.name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
