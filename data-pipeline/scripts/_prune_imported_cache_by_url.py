"""Second pass: delete cache HTML whose URL path contains an imported model slug."""

from __future__ import annotations

import sqlite3
from pathlib import Path

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials

IMPORTED = [
    "altima-2135",
    "dualis-2097",
    "frontier-2140",
    "murano-2120",
    "pathfinder-2142",
    "serena-2090",
    "tiida-tiida-latio-2092",
    "versa-2151",
    "x-trail-2064",
]


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    load_env_files(root / ".env", root.parent / ".env", override=True)
    # confirm still in supabase
    from supabase import create_client

    c = create_client(*resolve_supabase_credentials())
    live = {
        m["slug"]
        for m in (
            c.table("catalog_models").select("slug").eq("maker_slug", "nissan").execute().data
            or []
        )
    }
    imported = [m for m in IMPORTED if m in live]
    print("confirmed imported", imported)

    state_db = root / "out" / "megazip" / "nissan" / "megazip_state.db"
    conn = sqlite3.connect(f"file:{state_db.as_posix()}?mode=ro", uri=True, timeout=60.0)
    try:
        rows = conn.execute(
            "SELECT url, cache_path FROM page_cache WHERE cache_path IS NOT NULL"
        ).fetchall()
    finally:
        conn.close()

    deleted = 0
    freed = 0
    for url, cache_path in rows:
        u = (url or "").lower()
        if not any(f"/{slug}/" in u or u.endswith(f"/{slug}") for slug in imported):
            continue
        p = Path(cache_path)
        if not p.is_file():
            continue
        freed += p.stat().st_size
        p.unlink()
        deleted += 1

    print({"deleted_cache_html": deleted, "freed_gb": round(freed / (1024**3), 2)})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
