# Cloud multi-make catalog (one path)

Build a **multi-vehicle catalog on a cloud VM**: one PartSouq maker at a time, in **popularity order**, until the queue is finished. No parallel makers, no extra crawl workers.

**Code:** `data-pipeline/partsouq_catalog_orchestrator.py`  
**Maker order:** `data-pipeline/config/makers-by-popularity.json` (edit the `makers` array to reprioritize)

---

## What you get

| Step | What happens |
|------|----------------|
| 1 | VM starts FlareSolverr (Cloudflare bypass) |
| 2 | Orchestrator takes **maker #1** from the popularity file |
| 3 | Crawl + parse run together until that maker’s catalog is done |
| 4 | Outputs land in `out/makers/<slug>/bundle/` |
| 5 | Orchestrator moves to **maker #2**, repeats until the list ends |

Each maker is isolated (own DB, cache, bundle). Safe to stop/restart the VM — work resumes from existing `out/makers/<slug>/` data.

---

## One-time VM setup (Ubuntu 22.04+, 4 GB RAM, 80 GB disk)

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-plugin git python3 python3-pip python3-venv
sudo usermod -aG docker $USER
# log out and back in so docker group applies

git clone <your-repo-url> nissan-gtr && cd nissan-gtr
docker compose -f docker-compose.satellites.yml --profile scrape up -d

cd data-pipeline
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[scraping,dev]"
```

Check FlareSolverr: `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8191/` → `200`

---

## The only command you need

From `data-pipeline/` (inside `tmux` or `screen` so disconnect doesn’t kill it):

```bash
source .venv/bin/activate
python -m data_pipeline.partsouq_catalog_orchestrator \
  --makers-file config/makers-by-popularity.json \
  --parallel-makers 1 \
  2>&1 | tee -a out/makers/orchestrator.log
```

- **`--parallel-makers 1`** — default; never start maker #2 until maker #1 finishes  
- **Popularity** — edit `config/makers-by-popularity.json`; first name in the list runs first  
- **Smoke test one maker:** add `--makers Toyota --max-pages 50` instead of `--makers-file`  

Optional after each maker (or at the end): `--import-dry-run` or `--live-import` if Supabase env vars are set on the VM.

---

## Run untouched after reboot (systemd)

Create `/etc/systemd/system/gtr-catalog.service`:

```ini
[Unit]
Description=GTR multi-make PartSouq catalog orchestrator
After=docker.service
Requires=docker.service

[Service]
Type=simple
User=YOUR_USER
WorkingDirectory=/home/YOUR_USER/nissan-gtr/data-pipeline
Environment=PATH=/home/YOUR_USER/nissan-gtr/data-pipeline/.venv/bin:/usr/bin
ExecStartPre=/usr/bin/docker compose -f /home/YOUR_USER/nissan-gtr/docker-compose.satellites.yml --profile scrape up -d flaresolverr
ExecStart=/home/YOUR_USER/nissan-gtr/data-pipeline/.venv/bin/python -m data_pipeline.partsouq_catalog_orchestrator --makers-file config/makers-by-popularity.json --parallel-makers 1
Restart=on-failure
RestartSec=120

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now gtr-catalog.service
journalctl -u gtr-catalog.service -f
```

---

## Where results live

```text
data-pipeline/out/makers/
  toyota/bundle/          ← import this for Toyota catalog
  nissan/bundle/
  honda/bundle/
  ...
  orchestrator.log
  manifest.json           ← progress / which makers finished
```

Import one maker: `python -m data_pipeline.import_catalog out/makers/toyota/bundle --live`

---

## Change popularity

Open `data-pipeline/config/makers-by-popularity.json` and reorder the `makers` array. Example — Nissan-first for a Nissan parts business:

```json
"makers": ["Nissan", "Toyota", "Honda", "Mazda", ...]
```

Only run the orchestrator **once** on the VM. Do not run a second copy or a separate Nissan crawl on the same maker paths.

---

## Do not

- Run Cursor Cloud Agent for this (no FlareSolverr / no long scrape)  
- Use `--parallel-makers` > 1 unless you accept Cloudflare blocks  
- Raise crawl `--workers` above 1 on PartSouq  
- Run two orchestrators on the same `out/makers/` tree  

Full pipeline details: [partsouq-multimake-catalog-pipeline.md](./partsouq-multimake-catalog-pipeline.md)
