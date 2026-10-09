# Epic B · B7 — Responsive staff web tracking DoD

- Status: **closed** (web lane; screenshots N/A — CSS/recon evidence below)
- Lane: `@web_agent` (`apps/web/**`; MapLibre SoR)
- Surface: `/staff/logistics/tracking`
- Related: `docs/DIAL_SPARE_ADOPTION_PLAN.md` §8 Epic Delivery; Android `RouteEtaSource` honesty

## DoD

| Item | Evidence |
|------|----------|
| MapLibre staff tracking surface | `StaffDeliveryLiveMap` on `/staff/logistics/tracking` — MapLibre GL JS only; no Fleetbase; no `navigator.geolocation` / HTML5 hardware |
| Responsive desktop + narrow | Breakpoints below; geo uses shared `.formGrid`; map frame scales on narrow |
| `eta_source` honesty | `formatEtaSourceLabel` — `eta_source=osrm` vs `eta_source=google_directions (deprecated)` (Android parity); shown on job status row + map status |

## Responsive recon (breakpoints used)

| Width | Behavior |
|-------|----------|
| **≤800px** | Staff `.shell` nav stacks above panel (`account.module.css`) |
| **≤640px** | Pickup/dropoff `.formGrid` → single column; map `min-height: 16rem` / `height: min(50vh, 22rem)`; tighter status row (`staff-delivery-live-map.module.css`) |
| Resize | MapLibre `ResizeObserver` → `map.resize()` so canvas fills after layout change |

## Files touched

- `apps/web/lib/staff-delivery-tracking.ts` — `formatEtaSourceLabel`
- `apps/web/components/staff-delivery-tracking-panel.tsx` — honest ETA label + `.formGrid`
- `apps/web/components/staff-delivery-live-map.tsx` — `etaSourceLabel` prop
- `apps/web/components/staff-delivery-live-map.module.css` — narrow map + recon comment

## Out of scope (this line)

Android / `@gtr/delivery` / Edge functions — unchanged.
