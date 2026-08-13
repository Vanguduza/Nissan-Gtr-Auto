# OSRM satellite (B-OSRM-1 / §H H6)

Distance/route **SoR** for delivery when `OSRM_URL` is set. Clients already prefer OSRM (`preferRoutingProvider`, Android `OsrmRouteFetcher`); Google Directions remains deprecated fallback only.

**Does not** replace MapLibre render SoR. No Fleetbase. No Google as primary routing.

## Layout

| Path | Role |
|------|------|
| `data/` | OSM PBF + `osrm-extract` / `partition` / `customize` outputs (gitignored binaries) |
| `prepare.sh` | Download Geofabrik extract + preprocess via official OSRM image |
| `../README.md` | Satellite profiles overview |
| `../../docker-compose.satellites.yml` | `routing` profile → `osrm-routed` |

Default extract: **Zimbabwe** ([Geofabrik](https://download.geofabrik.de/africa/zimbabwe.html)) — matches GTR ops geography. Override with `OSM_PBF_URL` / `OSRM_REGION`.

## One-time prepare (needs Docker)

From **repo root**:

```bash
# Linux / macOS / Git Bash (MSYS_NO_PATHCONV set in script for Windows Git Bash)
bash infra/satellites/osrm/prepare.sh

# Windows PowerShell helper
powershell -File infra/satellites/osrm/prepare.ps1

# Or override region
OSRM_REGION=zimbabwe OSM_PBF_URL=https://download.geofabrik.de/africa/zimbabwe-latest.osm.pbf \
  bash infra/satellites/osrm/prepare.sh
```


Produces `infra/satellites/osrm/data/${OSRM_REGION}-latest.osrm*` (MLD graph).

## Run routed service

```bash
docker compose -f docker-compose.satellites.yml --profile routing up -d
```

Health: `curl "http://127.0.0.1:${OSRM_HOST_PORT:-5000}/route/v1/driving/31.05,-17.83;31.06,-17.84?overview=false"`

Point apps at:

```bash
OSRM_URL=http://127.0.0.1:5000
# Android emulator → host
# OSRM_URL=http://10.0.2.2:5000
```

Stop:

```bash
docker compose -f docker-compose.satellites.yml --profile routing down
```

## Port note vs Traccar

Traccar’s compose publishes host **5000–5150** for device protocols. **Do not** run `--profile gps` and `--profile routing` together on the same host without remapping — set `OSRM_HOST_PORT=5001` (and matching `OSRM_URL`) if both must coexist.

## Without map data

Compose will start but `osrm-routed` exits if `/data/${OSRM_REGION}-latest.osrm` is missing. Run `prepare.sh` first. Until then, clients keep haversine / Google deprecated fallback when `OSRM_URL` is unset.
