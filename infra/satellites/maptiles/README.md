# MapLibre basemap tiles satellite

Self-hosted **MapLibre style + vector tiles** so web / delivery / native stop relying on demotiles or keyed CARTO alone.

**Does not** replace MapLibre as the client render SoR. **Does not** replace OSRM distance SoR. No Google Maps as basemap SoR. No ZIMRA.

Discovery / Adopt-first: [`docs/plans/2026-08-15-maptiles-satellite.md`](../../../docs/plans/2026-08-15-maptiles-satellite.md).

## Stack (Integrate)

| Piece | Role | License |
|-------|------|---------|
| **Planetiler** | Geofabrik OSM extract → OpenMapTiles-schema **MBTiles** | Apache-2.0 |
| **tileserver-gl** | Serve MBTiles + bundled `basic-preview` style / glyphs | BSD-3-Clause |

**Alternative (not scaffolded):** Martin (MIT/Apache) + PMTiles — lighter I/O; needs separate style/fonts packaging. **Protomaps** — good for object-storage later; prefer HTTP style URL for Android MapLibre Native today.

## Layout

| Path | Role |
|------|------|
| `data/` | `basemap.mbtiles` + `sources/` (gitignored binaries) |
| `prepare.sh` / `prepare.ps1` | Planetiler download + build |
| `../README.md` | Satellite profiles overview |
| `../../docker-compose.satellites.yml` | `maptiles` profile → tileserver-gl |

Default full extract: **Zimbabwe** (same Geofabrik habit as OSRM). Override with `MAPTILES_REGION`.  
`MAPTILES_SMOKE=1` downloads a small prebuilt Zurich MBTiles to exercise tileserver-gl without Planetiler’s shared water/NE downloads.

Default water source is **simplified** (~23MB) — enough for landlocked Zimbabwe. Set `MAPTILES_WATER=full` for the ~885MB global water-polygons zip (coastline-grade oceans).

### Size honesty (do not ship a 1GB APK)

| Artifact | Typical size | Git / APK |
|----------|--------------|-----------|
| Zurich smoke MBTiles | ~34 MB | gitignored; smoke only |
| Shared Planetiler sources (`data/sources/`) | NE zip ~413MB + ZW OSM ~171MB + simplified water ~23MB (or full water ~885MB) | gitignored cache |
| Zimbabwe `basemap.mbtiles` (z0–14 OMT, simplified water) | **~129 MB** (2026-08-15 build) | gitignored — **not** an APK asset |
| Delivery debug APK | ~63 MB (no tiles bundled) | points at tileserver URL |

**Delivery / Play:** serve tiles over LAN/VPN (`MAPLIBRE_STYLE_URL`), or later add optional first-run download into app storage. Do **not** force-commit multi-GB blobs; use git-lfs only if the repo already adopts it. `MAPTILES_FORCE=1` passes Planetiler `--force` to overwrite an existing `basemap.mbtiles`.

## One-time prepare (needs Docker)

From **repo root**:

```bash
# Fast smoke (prebuilt Zurich sample MBTiles — exercises tileserver-gl, not ZW geography)
MAPTILES_SMOKE=1 bash infra/satellites/maptiles/prepare.sh

# Full Zimbabwe via Planetiler (first run downloads ~1GB+ shared sources into data/sources/)
bash infra/satellites/maptiles/prepare.sh

# Rebuild / overwrite existing basemap.mbtiles
MAPTILES_FORCE=1 bash infra/satellites/maptiles/prepare.sh

# Windows PowerShell
$env:MAPTILES_SMOKE=1; powershell -File infra/satellites/maptiles/prepare.ps1
powershell -File infra/satellites/maptiles/prepare.ps1
$env:MAPTILES_FORCE=1; powershell -File infra/satellites/maptiles/prepare.ps1
```
Git Bash / MSYS: script sets `MSYS_NO_PATHCONV` so Docker mounts stay correct (same pattern as OSRM).

Produces `infra/satellites/maptiles/data/basemap.mbtiles`.

## Run tileserver

```bash
docker compose -f docker-compose.satellites.yml --profile maptiles up -d
```

| | URL |
|--|-----|
| Viewer | http://127.0.0.1:8081/ |
| **Style JSON** | http://127.0.0.1:8081/styles/basic-preview/style.json |
| Emulator → host | http://10.0.2.2:8081/styles/basic-preview/style.json |
| Wireless phone → PC | `http://<PC_LAN_IP>:8081/styles/basic-preview/style.json` |

Health: `curl -sI "http://127.0.0.1:${MAPTILES_HOST_PORT:-8081}/styles/basic-preview/style.json"` → `200`.

Stop:

```bash
docker compose -f docker-compose.satellites.yml --profile maptiles down
```

## Point apps (no invented secrets)

```bash
# Web (apps/web .env.local)
NEXT_PUBLIC_MAP_STYLE_URL=http://127.0.0.1:8081/styles/basic-preview/style.json

# iOS Secrets.xcconfig / AppEnv
MAPLIBRE_STYLE_URL=http://127.0.0.1:8081/styles/basic-preview/style.json

# Android delivery local.properties
# Emulator — debug BuildConfig defaults here when MAPLIBRE_STYLE_URL unset:
MAPLIBRE_STYLE_URL=http://10.0.2.2:8081/styles/basic-preview/style.json
# Physical device on Wi-Fi (ipconfig / ifconfig on the host):
# MAPLIBRE_STYLE_URL=http://192.168.x.x:8081/styles/basic-preview/style.json
```

Unset → web keeps keyless CARTO Positron; native **release** keeps demotiles as last resort. Delivery **debug** prefers the emulator tileserver URL so Zimbabwe geography works once prepare + compose are up.

## Ports

Default host **8081** (`MAPTILES_HOST_PORT`) — avoids Traccar **8082** and Meili **7700**.

## Without map data

Compose starts but tileserver-gl exits / errors if `data/basemap.mbtiles` is missing. Run `prepare.sh` first (or smoke). Until then, leave style env vars unset (or accept empty map / demotiles).
