# Dedicated delivery Android app

- Status: **in-progress**
- Lane(s): `@backend_agent` → `@hardware_mobile_agent` → `@android_delivery_agent` → `@management_app_agent` → `@web_agent` → `@android_agent` + `@ios_agent` → `/security-reviewer` → `/verifier`
- Skills needed: (none required; Bridge-First GPS/camera/signature via `bridges/`)
- Related: `docs/plans/2026-07-24-live-map-delivery-tracking.md`, Phase 10 logistics (`…80000`/`…81000`), ADR `docs/decisions/2026-07-25-dedicated-delivery-app.md`
- Schema reuse: `delivery_jobs`, `delivery_locations`, `ingest_delivery_location`, Realtime publication — **extend, do not redesign**
- Mandate (2026-07-25): implement **ALL** matrix rows except **Never** (former P0+P1+P2). No backlog deferral.

## Goal

Ship a **driver-only** Android app at `apps/android-delivery/` with always-on Bridge-First GPS; keep **assignment** in management (web + android-management); expose **privacy-safe** customer live tracking for active deliveries only (flip prior AuthZ **(b)** → scoped **(a)**+token). Include POD OTP, geofence suggestions, fail/reattempt, multi-stop order, panic, offline POD queue, and customer `/track/[token]` + out-for-delivery notify.

## Hard rules

- **NO ZIMRA** / **NO payroll tax**.
- **Bridge-First:** GPS, camera (POD photo), signature capture only via `bridges/` — no WebView/HTML5 geo/camera.
- **RLS** on every new table; location least-privilege; no customer trail SELECT.
- Do not break `ingest_delivery_location` contract without a migration path (extend grants/roles; keep ~5s rate limit).
- Map/directions secrets in env only (`NEXT_PUBLIC_MAP_STYLE_URL`, OSRM/MapTiler keys, SMS/WA keys). Fail closed without keys.
- No commits unless user asks.

## Feature matrix (ALL Required except Never)

| Feature | Pri | Status | Notes |
|---------|-----|--------|-------|
| Dedicated `apps/android-delivery` (login, job list, navigate, always-on location) | **Required** | **DONE (P0)** | Scaffold builds; Fake/Live RPC; GPS FGS + POD + presence |
| `driver` staff role + presence (available / on_duty / break / offline) | **Required** | Migration+types | Wire clients |
| Continuous GPS (FGS + `location-tracker`) + battery-aware cadence | **Required** | Bridge partial | Delivery app consumer missing |
| Assignment suggest nearest + capacity/shift + **manual override** | **Required** | RPCs exist | Management/web UI incomplete |
| ETA on `delivery_jobs` + recalc on ingest ticks | **Required** | Migration | Haversine; OSRM if env |
| Customer live last-point (active job only) + share token | **Required** | RPCs exist | No `/track/[token]` page yet |
| Staff live map ETA (web + mgmt assignment UI) | **Required** | Partial | Tracking panel exists; assignment suggest UI TBD |
| Gate/remove driver GPS surface from management app | **Required** | Incomplete | Dispatch still references ingest |
| POD: photo + signature (bridges) | **Required** | RPC exists | Camera/signature bridges missing |
| POD OTP (generate/verify; SMS/WA optional) | **Required** | **Gap** | New migration + clients |
| Geofenced auto-arrive / auto-complete **suggestions** | **Required** | **Gap** | Driver must confirm |
| Customer deep-link `/track/[token]` + push/SMS out-for-delivery | **Required** | **Gap** | Fail closed without gateway keys |
| Failed delivery reason + **reattempt** workflow | **Required** | Partial | `failure_reason` TEXT only; need enum + `reattempt_of` |
| Multi-stop route order (`optimize_driver_stops`) | **Required** | **Gap** | Nearest-neighbor minimum |
| Panic / support contact | **Required** | **Gap** | `panic_events` + Realtime; support phone from env |
| Offline queue: location pings | **Required** | TBD in delivery app | Flush on reconnect |
| Offline queue: POD payloads (photo/signature/OTP) | **Required** | **Gap** | Flush on reconnect |
| Historical customer stalking / full trail to customer | **Never** | — | Staff trail only |

## Gap audit (2026-07-25 Manager)

### Present (backend P0 sketch)

- Migration `supabase/migrations/20260725110000_dedicated_delivery_app.sql`: `driver` role, `driver_presence`, ETA/POD/geo columns, `delivery_track_tokens`, `suggest_delivery_assignees`, `assign_delivery_job`, `set_driver_presence`, hardened `ingest_delivery_location`, `get_delivery_track_point`, `submit_delivery_pod`, `mint_delivery_track_token`
- Types + helpers: `packages/supabase-client` (`database.types.ts`, `delivery.ts`)
- Bridge: `bridges/android/location-tracker/` (FGS service exists)
- Web: staff logistics tracking panel (subscribe-oriented)
- Management: dispatch module (still GPS-ingest oriented — must gate)

### Missing / incomplete

| Area | Gap |
|------|-----|
| **P0 critical** | `apps/android-delivery/` — **0 files** |
| Backend P1 | OTP generate/verify; geofence helpers; `failure_reason` enum; `reattempt_of`; `panic_events`; `optimize_driver_stops` |
| Hardware | POD photo + signature bridges (no dedicated modules) |
| Management | Suggest/assign UI; route order; panic inbox; remove driver GPS producer |
| Web | `/track/[token]`; assignment suggest; panic inbox; out-for-delivery notify edge |
| Customer mobile | Active-delivery track screens (android + ios) |
| Config | `rufler.yaml` + `AGENTS.md` include `android_delivery_agent` → `apps/android-delivery/**` |

## Schema sketch (reuse-first)

**Existing (keep):** Phase 10 + `20260725110000_*` objects above.

**Add (new migration batch — `@backend_agent`):**

| Object | Purpose |
|--------|---------|
| `delivery_pod_otps` / generate+verify RPCs | Hash-only OTP; required (or optional flag) to complete; SMS/WA via existing outbox patterns, fail closed |
| Geofence helper RPC(s) | Distance vs dropoff; returns suggest_arrive / suggest_complete flags — **never auto-mutate status** |
| `delivery_failure_reason` enum + tighten `failure_reason` | Stable reason codes |
| `delivery_jobs.reattempt_of` UUID FK | Linked reattempt job; fail RPC creates child job |
| `panic_events` + `raise_delivery_panic` | Driver alerts dispatchers; Realtime; RLS staff read / driver insert own |
| `optimize_driver_stops(driver_id)` | Nearest-neighbor stop order; returns ordered job ids + sequence; optional provider later |
| Notify “out for delivery” | Edge or RPC enqueue to SMS/WA outbox when status → dispatched; fail closed without keys |

Realtime: staff `delivery_locations` + `panic_events`; customer via token RPC only — **not** raw trail.

## Acceptance criteria (epic Done)

- [ ] `apps/android-delivery/` builds; driver login; jobs; FGS → ingest; navigate; presence; POD photo+sig+OTP; geofence suggestions; fail/reattempt; stop order; panic; offline location+POD queues
- [ ] `driver` role + `driver_presence`; suggest + manual assign in management/web
- [ ] Management: assignment + ordered stops; driver GPS producer **gated/removed**; panic inbox
- [ ] ETA on ingest (Haversine min); staff map marker + ETA
- [ ] Customer web `/track/[token]` + mobile active track: last-point only; no historical trail
- [ ] Out-for-delivery SMS/WA when keys present; silent skip / fail closed otherwise
- [ ] POD bridges required; OTP verify before complete (per RPC rules)
- [ ] Offline queues flush on reconnect (respect rate limits)
- [ ] RLS + smoke: customer cannot SELECT `delivery_locations`; driver cannot read other drivers’ jobs
- [ ] `rufler.yaml` includes `android_delivery_agent` → `apps/android-delivery/**`
- [ ] `/security-reviewer` + `/verifier` PASS

## Paths in scope

| Area | Paths |
|------|--------|
| Backend | `supabase/migrations/` (new P1), tests, `packages/supabase-client/`, notify edge if needed |
| Hardware | `bridges/android/location-tracker/`, new POD camera/signature bridges |
| Delivery app | `apps/android-delivery/**` (scaffold + full feature set) |
| Management | `apps/android-management/feature/dispatch/` (+ panic/route UI) |
| Web | staff logistics assignment/ETA/panic; `app/.../track/[token]` |
| Customer mobile | `apps/android-customer/`, `apps/ios/` active-delivery track |
| Config | `rufler.yaml`, ADR note that P1 is now Required |

## Out of scope

- Redesign pick/pack / DN / invoice logistics
- Full OSRM self-host ops (document env fallback only)
- Multi-carrier / 3PL marketplace
- iOS delivery driver app
- ZIMRA, payroll tax, browser QR/GPS
- Customer access to full GPS trail or offline stalking (**Never**)

## Risks / exclusions

- Prior AuthZ **(b)** superseded in ADR — do not leave customer status-only.
- Broad Realtime on `delivery_locations` + weak RLS = leak; keep staff-only trail.
- OEM battery killers — FGS + offline queues mandatory.
- Breaking ingest for management driver flow — migrate: management stops ingest; delivery app sole producer.
- SMS/WA without keys must fail closed (no secret stubs in git).

## Lane sequence (Manager invoke order)

1. **Plan/config** — this doc + `rufler.yaml` `android_delivery_agent` (Manager)
2. **`@backend_agent`** — P1 migration(s): OTP, geofence, failure enum + reattempt, panic, optimize_driver_stops, notify hook; types; RLS smoke
3. **`@hardware_mobile_agent`** — FGS harden if needed; POD photo + signature bridges
4. **`@android_delivery_agent`** — scaffold + complete `apps/android-delivery` (all Required rows)
5. **`@management_app_agent`** — assignment, route order, panic inbox; gate driver GPS
6. **`@web_agent`** — `/track/[token]`, staff assignment/ETA/panic, out-for-delivery notify wiring
7. **`@android_agent`** + **`@ios_agent`** — customer active-delivery track
8. **`/security-reviewer`** → **`/verifier`** → Manager done gate

## Handoff

1. One coding lane per Manager turn (no parallel writers on same paths)
2. Update master Immediate handoff after verify
3. No product commits unless user asks
