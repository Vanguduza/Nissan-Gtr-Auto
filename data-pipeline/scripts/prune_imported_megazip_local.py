"""Delete local Megazip files for models already present in Supabase."""

from __future__ import annotations

import sqlite3
import sys
from pathlib import Path

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    load_env_files(root / ".env", root.parent / ".env", override=True)
    from supabase import create_client

    client = create_client(*resolve_supabase_credentials())
    models = (
        client.table("catalog_models")
        .select("slug")
        .eq("maker_slug", "nissan")
        .execute()
        .data
        or []
    )
    imported = sorted({str(m["slug"]) for m in models if m.get("slug")})
    if not imported:
        print("no imported nissan models in supabase — nothing to delete")
        return 0

    print("imported models:", ", ".join(imported))

    nissan = root / "out" / "megazip" / "nissan"
    state_db = nissan / "megazip_state.db"
    cache_dir = nissan / "cache"
    diagrams_dir = nissan / "diagrams"
    bundle_dir = nissan / "bundle"

    deleted_cache = 0
    missing_cache = 0
    deleted_bytes = 0
    cache_paths: list[str] = []

    if state_db.is_file():
        conn = sqlite3.connect(f"file:{state_db.as_posix()}?mode=ro", uri=True, timeout=30.0)
        try:
            placeholders = ",".join("?" * len(imported))
            rows = conn.execute(
                f"""
                SELECT DISTINCT c.cache_path
                FROM page_cache c
                JOIN queue q ON q.url = c.url
                WHERE q.model_slug IN ({placeholders})
                  AND q.status = 'VISITED'
                  AND c.cache_path IS NOT NULL
                  AND trim(c.cache_path) != ''
                """,
                imported,
            ).fetchall()
            cache_paths = [r[0] for r in rows]
        finally:
            conn.close()

    for raw in cache_paths:
        p = Path(raw)
        if not p.is_file():
            # also try relative under cache_dir by name
            alt = cache_dir / p.name
            p = alt if alt.is_file() else p
        if p.is_file():
            deleted_bytes += p.stat().st_size
            p.unlink()
            deleted_cache += 1
        else:
            missing_cache += 1

    deleted_diagrams = 0
    if diagrams_dir.is_dir():
        for p in diagrams_dir.iterdir():
            if p.is_file():
                deleted_bytes += p.stat().st_size
                p.unlink()
                deleted_diagrams += 1

    deleted_bundle = 0
    if bundle_dir.is_dir():
        for p in bundle_dir.rglob("*"):
            if p.is_file():
                deleted_bytes += p.stat().st_size
                p.unlink()
                deleted_bundle += 1

    print(
        {
            "deleted_cache_html": deleted_cache,
            "missing_cache_html": missing_cache,
            "deleted_diagram_files": deleted_diagrams,
            "deleted_bundle_files": deleted_bundle,
            "freed_gb": round(deleted_bytes / (1024**3), 2),
            "kept": "megazip_state.db + PENDING/non-imported cache (crawl continues)",
        }
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
