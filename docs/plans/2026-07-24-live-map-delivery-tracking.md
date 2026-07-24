# Live map delivery tracking

- Status: done (session slice; AuthZ b)
- Lane(s): `@hardware_mobile_agent` → `@management_app_agent` → `@web_agent` (optional `@backend_agent` only for customer RPC)
- Skills needed: (none required; Bridge-First via `bridges/contracts/gps.ts`)
- Related: `docs/plans/2026-07-24-phase10-logistics-pick-pack-dn.md`, master Phase 10/12 + Immediate handoff
- Schema reuse: `…80000` / `…81000` — **do not redesign** `delivery_jobs`, `delivery_locations`, `ingest_delivery_location`, Realtime publication

## Goal

Delivery-person phone GPS → native `bridges/` → `ingest_delivery_location` → Supabase Realtime → live MapLibre map for staff dispatcher (customer last-point only if AuthZ decision (a) lands).

## Hard rules

- **Bridge-First:** GPS only via `bridges/` — **NO** `navigator.geolocation`, **NO** WebView/HTML5 GPS, **NO** browser QR.
- **NO ZIMRA** / **NO payroll tax**.
- Multi-currency / ledger **unchanged**.
- Web dispatcher map **subscribes** to Realtime `delivery_locations` only — never captures GPS in browser.
- Prefer **MapLibre** (or Mapbox GL) for map UI.

## AuthZ decision (a vs b)

| Option | Meaning |
|--------|---------|
| **(a)** | Minimal customer-safe RPC: own-job **last** location only; RLS + `docs/decisions/` |
| **(b)** | Staff/dispatcher + driver ingest only; customer stays **status-only** on job |

**Recommendation for this session: (b).**

Rationale: Phase 10 RLS already staff-selects `delivery_locations` (`admin`\|`warehouse`\|`dispatcher`); smoke asserts customers cannot read the trail; Realtime is published on the table; customer last-point needs a new SECURITY DEFINER RPC, policy design, and `/security-reviewer` — not clearly low-risk for one session. Ship bridges + driver ingest + staff map first. Revisit (a) only if product requires customer map and AuthZ is reviewed as low-risk.

## Acceptance criteria

- [x] Android `bridges/android/location-tracker/` implements `GpsBridge` (FusedLocationProvider, permissions, foreground service if background watch required)
- [x] iOS `bridges/ios/LocationTracker/` implements `GpsBridge` (CoreLocation, permissions)
- [x] Bridge yields `GpsCoordinate` only — **no** network/Supabase inside bridge; caller maps via `toDeliveryLocationIngest`
- [x] Management Android: start/stop tracking on active delivery job; client-side ≥~5s throttle; call `ingest_delivery_location` (live or fake client)
- [x] Web `/staff/logistics` (or sub-route): MapLibre map for selected job; Realtime filter on `delivery_locations`; staff-gated; **no** browser geolocation
- [x] Customer apps: **skip** live map under (b); status-only remains; if (a) later — thin screen + RPC only
- [x] Master Immediate handoff updated after slice; no commits unless user asks
- [x] `/security-reviewer` on location AuthZ; `/verifier` confirms no HTML5 geolocation / no ZIMRA / Bridge-First

## Lane sequence (one-session priority)

1. **`@hardware_mobile_agent`** — Android + iOS GPS bridge impls per `bridges/contracts/gps.ts`
2. **`@management_app_agent`** — Dispatch UI: bind job → `watchPosition` → ingest RPC; start/stop
3. **`@web_agent`** — Staff live map (MapLibre + Realtime subscribe)
4. **`@backend_agent`** — **Skip this session** under (b); only if flipping to (a): customer last-location RPC + RLS + decision doc
5. Customer `@android_agent` / `@ios_agent` — **Skip** under (b)
6. Update master Immediate handoff → `/security-reviewer` → `/verifier` → `/manager` done gate

## Paths in scope

| Area | Paths |
|------|--------|
| Hardware | `bridges/android/location-tracker/`, `bridges/ios/LocationTracker/`, `bridges/README.md` (impl status note) |
| Contract (read/align only) | `bridges/contracts/gps.ts` |
| Management | `apps/android-management/` dispatch / delivery-job screens + bridge wiring |
| Web | `apps/web/app/(staff)/staff/logistics/` (+ map component under `apps/web/components/` or feature folder) |
| Backend (only if (a)) | new migration under `supabase/migrations/`, `docs/decisions/YYYY-MM-DD-customer-delivery-last-location.md` |
| Docs | `docs/plans/2026-07-23-master-erp-development.md` Immediate handoff |

## Out of scope

- Redesigning Phase 10 logistics schema / pick-pack / DN RPCs
- Customer live map or full trail (under (b))
- Map tile provider billing / production keys (document gap only)
- Offline trail buffer / PowerSync GPS sync
- iOS management app (Android management is the driver surface)
- Ledger, ContiPay/Paynow, ZIMRA, payroll tax, browser QR
- Other bridges (QR, ESC/POS, biometric) unless needed to compile

## Remaining gaps

- **Map tiles:** MapLibre needs a style URL + tile key (MapTiler / Mapbox / self-hosted). Use env (`NEXT_PUBLIC_MAP_STYLE_URL` or similar); do not commit secrets. Local demos may use a public demo style until keys exist.
- Customer (a) RPC deferred; Realtime row filters for staff must not leak other jobs to wrong roles (rely on existing RLS).
- Android background tracking battery / OEM kill policies — foreground service notification required where OS demands it.

## Demo steps

1. Staff creates/dispatches a `delivery_job` (existing Phase 10 RPCs).
2. Driver on management Android: grant location → **Start tracking** on that job → walk/simulate motion.
3. Confirm rows appear in `delivery_locations` via `ingest_delivery_location` (~5s spacing).
4. Dispatcher opens `/staff/logistics`, selects job → MapLibre marker updates via Realtime.
5. Customer app (if checked): job status updates only; **no** lat/lng under (b).
6. `/verifier`: ripgrep for `navigator.geolocation` / HTML5 geo / ZIMRA → none introduced.

## Risks / exclusions

- Accidental browser GPS in web map → reject in review.
- Broad Realtime channel without job filter → noisy UI; filter by `delivery_job_id`.
- Granting customers SELECT on `delivery_locations` without a last-point RPC → **forbidden** (smoke + RLS intent).

## Handoff

1. Implement in order: hardware → management → web
2. `/security-reviewer` (location AuthZ; confirm (b) or (a) migration)
3. `/verifier` (exclusions + Bridge-First)
4. `/manager` for done gate; update master Immediate handoff
