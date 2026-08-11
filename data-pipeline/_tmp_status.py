import sqlite3
from pathlib import Path

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials

p = Path("out/megazip/nissan/megazip_state.db")
print("state_db_gb", round(p.stat().st_size / 1e9, 2) if p.exists() else None)
c = sqlite3.connect(f"file:{p.as_posix()}?mode=ro", uri=True, timeout=60)
print("queue:")
for r in c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status ORDER BY 1"):
    print(" ", r)
print("pending types:")
for r in c.execute(
    "SELECT page_type, COUNT(1) FROM queue WHERE status='PENDING' GROUP BY page_type ORDER BY 2 DESC"
):
    print(" ", r)
print("visited types:")
for r in c.execute(
    "SELECT page_type, COUNT(1) FROM queue WHERE status='VISITED' GROUP BY page_type ORDER BY 2 DESC"
):
    print(" ", r)
try:
    print("leases", c.execute("SELECT COUNT(1) FROM worker_leases").fetchone()[0])
    for r in c.execute("SELECT model_slug, worker_id FROM worker_leases ORDER BY 1"):
        print(" ", r)
except Exception as e:
    print("leases n/a", e)

cache = Path("out/megazip/nissan/cache")
print("cache_html", len(list(cache.glob("*.html"))) if cache.exists() else 0)
diag = Path("out/megazip/nissan/diagrams")
print("diagrams", len(list(diag.iterdir())) if diag.exists() else 0)

load_env_files(Path(".env"), Path("../.env"), override=True)
from supabase import create_client

sb = create_client(*resolve_supabase_credentials())
print(
    "supabase makers",
    sb.table("catalog_makers").select("slug,source").execute().data,
)
print(
    "supabase models",
    sb.table("catalog_models")
    .select("slug", count="exact")
    .eq("maker_slug", "nissan")
    .limit(1)
    .execute()
    .count,
)
print(
    "supabase variants",
    sb.table("catalog_variants")
    .select("slug", count="exact")
    .eq("maker_slug", "nissan")
    .limit(1)
    .execute()
    .count,
)
print(
    "supabase diagrams",
    sb.table("catalog_diagrams")
    .select("slug", count="exact")
    .eq("maker_slug", "nissan")
    .limit(1)
    .execute()
    .count,
)
print(
    "supabase fitments",
    sb.table("part_fitment").select("oem_part_number", count="exact").limit(1).execute().count,
)
