import sqlite3
import subprocess
from pathlib import Path

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials
from data_pipeline.megazip.replacement_queue import load_queue
from data_pipeline.megazip.state import list_active_leases

p = Path("out/megazip/nissan/megazip_state.db")
print("state_db_gb", round(p.stat().st_size / 1e9, 2) if p.exists() else None)
c = sqlite3.connect(f"file:{p.as_posix()}?mode=ro", uri=True, timeout=120)
print("journal", c.execute("PRAGMA journal_mode").fetchone())
print("queue:")
for r in c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status ORDER BY 1"):
    print(" ", r)
print("visited types:")
for r in c.execute(
    "SELECT page_type, COUNT(1) FROM queue WHERE status='VISITED' GROUP BY page_type ORDER BY 2 DESC"
):
    print(" ", r)
c.close()

cache = Path("out/megazip/nissan/cache")
print("cache_html", len(list(cache.glob("*.html"))) if cache.exists() else 0)

try:
    print("leases", list_active_leases(p))
except Exception as e:
    print("leases err", e)

print("replacement_queue_front", load_queue(Path("out/megazip"))[:8])
print("replacement_queue_len", len(load_queue(Path("out/megazip"))))

ps = r"""
$w = Get-CimInstance Win32_Process -Filter "name='python.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -match 'megazip_crawl_worker' }
$s = Get-CimInstance Win32_Process -Filter "name='python.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -match 'megazip_worker_supervisor' }
Write-Output "workers=$($w.Count) supervisor=$($s.Count)"
$w | ForEach-Object {
  if ($_.CommandLine -match '--models\s+(\S+)') { $Matches[1].Split(',')[0] }
}
"""
raw = subprocess.check_output(["powershell", "-NoProfile", "-Command", ps], text=True, stderr=subprocess.DEVNULL)
print("processes")
print(raw.strip())

load_env_files(Path(".env"), Path("../.env"), override=True)
from supabase import create_client

sb = create_client(*resolve_supabase_credentials())
print(
    "supabase",
    {
        "models": sb.table("catalog_models")
        .select("slug", count="exact")
        .eq("maker_slug", "nissan")
        .limit(1)
        .execute()
        .count,
        "variants": sb.table("catalog_variants")
        .select("slug", count="exact")
        .eq("maker_slug", "nissan")
        .limit(1)
        .execute()
        .count,
        "diagrams": sb.table("catalog_diagrams")
        .select("slug", count="exact")
        .eq("maker_slug", "nissan")
        .limit(1)
        .execute()
        .count,
        "fitments": sb.table("part_fitment")
        .select("oem_part_number", count="exact")
        .limit(1)
        .execute()
        .count,
    },
)
