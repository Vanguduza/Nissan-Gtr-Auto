# Dedicated delivery Android app

- Status: **done** (2026-07-25 Manager close)
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
| Dedicated `apps/android-delivery` (login, job list, navigate, always-on location) | **Required** | **DONE** | `assembleDebug` OK; Fake/Live RPC |
| `driver` staff role + presence (available / on_duty / break / offline) | **Required** | **DONE** | Migration + delivery app UI |
| Continuous GPS (FGS + `location-tracker`) + battery-aware cadence | **Required** | **DONE** | Delivery app sole producer |
| Assignment suggest nearest + capacity/shift + **manual override** | **Required** | **DONE** | Management + web staff UI |
| ETA on `delivery_jobs` + recalc on ingest ticks | **Required** | **DONE** | Haversine; staff map/panel |
| Customer live last-point (active job only) + share token | **Required** | **DONE** | RPC + `/track/[token]` + mobile |
| Staff live map ETA (web + mgmt assignment UI) | **Required** | **DONE** | Suggest/assign + live point |
| Gate/remove driver GPS surface from management app | **Required** | **DONE** | `ALLOW_DRIVER_GPS_PRODUCER=false` |
| POD: photo + signature (bridges) | **Required** | **DONE** | `pod-camera` + `pod-signature` |
| POD OTP (generate/verify; SMS/WA optional) | **Required** | **DONE** | `…120000` + delivery app |
| Geofenced auto-arrive / auto-complete **suggestions** | **Required** | **DONE** | Confirm-only; never auto |
| Customer deep-link `/track/[token]` + push/SMS out-for-delivery | **Required** | **DONE** | SMS outbox; WA N/A (no outbox) |
| Failed delivery reason + **reattempt** workflow | **Required** | **DONE** | Enum + `reattempt_of` |
| Multi-stop route order (`optimize_driver_stops`) | **Required** | **DONE** | NN + mgmt/web/driver |
| Panic / support contact | **Required** | **DONE** | `panic_events` + inboxes |
| Offline queue: location pings | **Required** | **DONE** | Flush on reconnect ≥5s |
| Offline queue: POD payloads (photo/signature/OTP) | **Required** | **DONE** | Flush on reconnect |
| Historical customer stalking / full trail to customer | **Never** | — | Staff trail only |

## Gap audit close-out (2026-07-25 Manager)

### Shipped

- Migrations: `…110000` P0, `…120000` P1, `…130000` POD storage, `…140000` absolute track URL, `…150000` geo/dispatch token, `…160000` `active_delivery_job_id` on `get_customer_order`
- Bridges: `location-tracker`, `pod-camera`, `pod-signature`
- `apps/android-delivery/` full driver app
- Management: assign/suggest/optimize/panic; GPS producer gated
- Web: `/track/[token]`, staff assign/ETA/panic, SMS fail-closed
- Customer android + iOS last-point track
- `rufler.yaml` `android_delivery_agent`
- `/security-reviewer` **PASS** (warnings: OTP entropy, panic UPDATE scope, presence role RLS)
- `/verifier` **PASS** (APK + 4 SQL smokes)

### Deferred / by-design

- WhatsApp out-for-delivery: no `whatsapp_outbox` — SMS only
- Security warnings (non-blocking): CSPRNG OTP; narrow panic UPDATE; require `driver` on presence write RLS

## Acceptance criteria (epic Done)

- [x] `apps/android-delivery/` builds; driver login; jobs; FGS → ingest; navigate; presence; POD photo+sig+OTP; geofence suggestions; fail/reattempt; stop order; panic; offline location+POD queues
- [x] `driver` role + `driver_presence`; suggest + manual assign in management/web
- [x] Management: assignment + ordered stops; driver GPS producer **gated/removed**; panic inbox
- [x] ETA on ingest (Haversine min); staff map marker + ETA
- [x] Customer web `/track/[token]` + mobile active track: last-point only; no historical trail
- [x] Out-for-delivery SMS when keys present; silent skip / fail closed otherwise (WA skipped — no outbox)
- [x] POD bridges required; OTP verify before complete (per RPC rules)
- [x] Offline queues flush on reconnect (respect rate limits)
- [x] RLS + smoke: customer cannot SELECT `delivery_locations`; driver cannot read other drivers’ jobs
- [x] `rufler.yaml` includes `android_delivery_agent` → `apps/android-delivery/**`
- [x] `/security-reviewer` + `/verifier` PASS

## Paths in scope

| Area | Paths |
|------|--------|
| Backend | `supabase/migrations/` (P0–P1 + notify + active job), tests, `packages/supabase-client/` |
| Hardware | `bridges/android/location-tracker/`, `pod-camera/`, `pod-signature/` |
| Delivery app | `apps/android-delivery/**` |
| Management | `apps/android-management/feature/dispatch/` |
| Web | staff logistics; `app/track/[token]` |
| Customer mobile | `apps/android-customer/`, `apps/ios/` |
| Config | `rufler.yaml`, ADR |

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
- Breaking ingest for management driver flow — migrated: management stops ingest; delivery app sole producer.
- SMS/WA without keys must fail closed (no secret stubs in git).

## Lane sequence (completed)

1. Plan/config — `rufler.yaml` `android_delivery_agent`
2. `@backend_agent` — P1 + notify URL + `active_delivery_job_id`
3. `@hardware_mobile_agent` — POD camera/signature + FGS
4. `@android_delivery_agent` — full `apps/android-delivery`
5. `@management_app_agent` — assign/route/panic; gate GPS
6. `@web_agent` — `/track/[token]`, staff UI, SMS path
7. `@android_agent` + `@ios_agent` — customer track
8. `/security-reviewer` → `/verifier` → **Done**

## Handoff

1. No product commits unless user asks
2. Optional follow-ups: CSPRNG OTP; narrow panic UPDATE; presence write requires `driver` role
