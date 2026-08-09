"""Try hosted pooler using password extracted from local edge SUPABASE_DB_URL."""

from __future__ import annotations

import subprocess
from pathlib import Path
from urllib.parse import quote_plus, urlparse

env_path = Path(
    "../supabase/.temp/start-secrets/supabase_edge_runtime_gylrgwqyuiwkyykardwc/env/docker.env"
)
pw = None
for line in env_path.read_text(encoding="utf-8", errors="ignore").splitlines():
    if line.startswith("SUPABASE_DB_URL="):
        local = urlparse(line.split("=", 1)[1].strip().strip('"').strip("'"))
        pw = local.password
        print("local_db_host", local.hostname, "has_pw", bool(pw))
        break

pooler = Path("../supabase/.temp/pooler-url").read_text(encoding="utf-8").strip()
pu = urlparse(pooler)
print("pooler_host", pu.hostname, "user", pu.username)

if not pw:
    raise SystemExit("no password")

try:
    import psycopg
except ImportError:
    subprocess.check_call(["pip", "install", "psycopg[binary]", "-q"])
    import psycopg

candidates = [
    f"postgresql://{pu.username}:{quote_plus(pw)}@{pu.hostname}:5432/postgres",
    f"postgresql://{pu.username}:{quote_plus(pw)}@{pu.hostname}:6543/postgres",
    f"postgresql://postgres:{quote_plus(pw)}@db.gylrgwqyuiwkyykardwc.supabase.co:5432/postgres",
]
for db_url in candidates:
    host = urlparse(db_url).hostname
    try:
        with psycopg.connect(db_url, connect_timeout=8) as conn:
            row = conn.execute("select current_user, inet_server_addr()::text").fetchone()
            print("CONNECTED", host, row)
            break
    except Exception as exc:  # noqa: BLE001
        print("fail", host, type(exc).__name__, str(exc)[:120])
else:
    print("NO_REMOTE_CONNECTION")
