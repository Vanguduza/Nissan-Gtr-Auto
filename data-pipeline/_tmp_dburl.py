from pathlib import Path
from urllib.parse import urlparse

p = Path("../supabase/.temp/start-secrets/supabase_edge_runtime_gylrgwqyuiwkyykardwc/env/docker.env")
for line in p.read_text(encoding="utf-8", errors="ignore").splitlines():
    if line.startswith("SUPABASE_DB_URL="):
        raw = line.split("=", 1)[1].strip().strip('"').strip("'")
        u = urlparse(raw)
        print("db_host", u.hostname, "port", u.port, "user", u.username, "has_pw", bool(u.password))
    if line.startswith("SUPABASE_URL="):
        raw = line.split("=", 1)[1].strip().strip('"')
        print("api_host", urlparse(raw).hostname)
