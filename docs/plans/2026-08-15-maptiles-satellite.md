# MapLibre basemap tiles satellite (self-host)

**Date:** 2026-08-15  
**Lane:** infra / `@backend_agent` + delivery/web docs  
**Status:** Scaffold + smoke path

## Discovery (Adopt-first)

| Candidate | License | Fit | Decision |
|-----------|---------|-----|----------|
| **Planetiler** → MBTiles + **tileserver-gl** | Apache-2.0 + BSD-3-Clause | Zimbabwe extract (same Geofabrik habit as OSRM); ships `style.json` + glyphs/sprites | **Integrate** (chosen) |
| **Martin** + PMTiles + hand-rolled OMT style | Apache-2.0 / MIT (MapLibre org) | Lighter tile I/O; needs fonts/sprites/style packaging | Documented alternative — revisit if we outgrow tileserver-gl |
| **Protomaps** basemaps + `pmtiles` | BSD (code); ODbL (data) | Excellent CDN/static story; native Android prefers HTTP style/tiles over `pmtiles://` | Optional later for object-storage deploy |
| MapTiler / Mapbox cloud | Proprietary / keyed | Fine as optional keyed override | Not SoR — env override only |
| Google Maps tiles | Proprietary | Explicitly excluded as basemap SoR | **Never** |

**AGPL note:** none in the chosen path. Flag if adopting AGPL tile stacks later.

**MapLibre remains client SoR.** This satellite only serves style + vector tiles. OSRM stays distance SoR (`infra/satellites/osrm/`).

## Layout

- `infra/satellites/maptiles/` — prepare scripts, `data/` (gitignored binaries), README
- `docker-compose.satellites.yml` profile `maptiles`
- Smoke: `MAPTILES_SMOKE=1` → small prebuilt MBTiles (not Zimbabwe). Full: Planetiler `--area=zimbabwe` (first run caches ~1GB+ sources under `data/sources/`).

## Style URL (local)

```text
http://127.0.0.1:8081/styles/basic-preview/style.json
# Android emulator → host
http://10.0.2.2:8081/styles/basic-preview/style.json
```

Env: `NEXT_PUBLIC_MAP_STYLE_URL`, `MAPLIBRE_STYLE_URL` / delivery `local.properties`. Unset → web CARTO Positron / native demotiles last resort.
