# Dedicated delivery Android app

- Status: draft
- Lane(s): `@backend_agent` → `@hardware_mobile_agent` → `@android_delivery_agent` (new; until `rufler.yaml` updated use `@management_app_agent` + path override) → `@management_app_agent` → `@web_agent` → `@android_agent` + `@ios_agent` → `/security-reviewer` → `/verifier`
- Skills needed: (none required; Bridge-First GPS/camera/signature via `bridges/`)
- Related: `docs/plans/2026-07-24-live-map-delivery-tracking.md`, Phase 10 logistics (`…80000`/`…81000`), ADR `docs/decisions/2026-07-25-dedicated-delivery-app.md`
- Schema reuse: `delivery_jobs`, `delivery_locations`, `ingest_delivery_location`, Realtime publication — **extend, do not redesign**

## Goal

Ship a **driver-only** Android app at `apps/android-delivery/` with always-on Bridge-First GPS; keep **assignment** in management (web + android-management); expose **privacy-safe** customer live tracking for active deliveries only (flip prior AuthZ **(b)** → scoped **(a)**+token).

## Hard rules

- **NO ZIMRA** / **NO payroll tax**.
- **Bridge-First:** GPS, camera (POD photo), signature capture only via `bridges/` — no WebView/HTML5 geo/camera.
- **RLS** on every new table; location least-privilege; no customer trail SELECT.
- Do not break `ingest_delivery_location` contract without a migration path (extend grants/roles; keep ~5s rate limit).
- Map/directions secrets in env only (`NEXT_PUBLIC_MAP_STYLE_URL`, OSRM/MapTiler keys, etc.).

## Feature matrix

| Feature | Pri | Notes |
|---------|-----|--------|
| Dedicated `apps/android-delivery` (login, job list, navigate, always-on location) | **P0** | No POS/warehouse/finance |
| `driver` staff role + presence (available / on_duty / break / offline) | **P0** | `driver_presence` |
| Continuous GPS (FGS + `location-tracker`) + battery-aware cadence (moving vs idle) | **P0** | Harden ingest for `driver` JWT |
| Assignment suggest nearest available + capacity/shift + **manual override** | **P0** | Engine in management only |
| ETA on `delivery_jobs` + recalc on ingest ticks | **P0** | Haversine default; OSRM/MapLibre directions if key |
| Customer live last-point (active job only) + share token / order-scoped RLS | **P0** | Flip AuthZ (b)→scoped (a) |
| Staff live map ETA (web + mgmt assignment UI) | **P0** | Subscribe-only on web |
| Gate/remove driver GPS surface from management app | **P0** | Assignment stays |
| POD: photo + signature (bridges) | **P0** | Required to complete |
| POD OTP | P1 | |
| Geofenced auto-arrive / auto-complete **suggestions** | P1 | Driver confirms |
| Customer deep-link track page + push/SMS “out for delivery” | P1 | Deep-link web can ship thin in P0 if cheap |
| Failed delivery reason + **reattempt** workflow | P1 | P0 keeps `failed` status + notes |
| Multi-stop route order (nearest-neighbor / provider) | P1 | |
| Panic / support contact | P1 | |
| Offline queue: location pings | **P0** | Flush on reconnect |
| Offline queue: POD payloads | P1 | |
| Historical customer stalking / full trail to customer | **Never** | Staff trail only |

## Schema sketch (reuse-first)

**Existing (keep):** `delivery_jobs` (`eta_at` already), `delivery_locations`, `create_delivery_job`, `update_delivery_job_status`, `ingest_delivery_location`, `purge_delivery_locations`.

**Add / alter (single migration batch preferred):**

| Object | Purpose |
|--------|---------|
| `ALTER TYPE staff_role ADD VALUE 'driver'` | Delivery personnel only |
| `driver_presence` | `user_id` PK, `status` enum (`available`\|`on_duty`\|`break`\|`offline`), `last_lat`/`last_lng`, `last_seen_at`, `capacity`, `shift_starts_at`/`shift_ends_at`, RLS: self write + dispatcher/admin read |
| `delivery_jobs` columns | `eta_seconds`, `eta_source` (`haversine`\|`osrm`\|`manual`), `eta_updated_at`, `pickup_lat/lng`, `dropoff_lat/lng` (or resolve from DN address), `failure_reason`, `pod_photo_path`, `pod_signature_path`, `completed_via` |
| `delivery_track_tokens` | `id`, `delivery_job_id`, `token_hash`, `expires_at`, `revoked_at`; mint on dispatch; RLS deny direct SELECT — access via RPC only |
| `suggest_delivery_assignees(job_id, limit)` | SECURITY DEFINER; scores available drivers by Haversine to pickup + capacity + shift; returns ranked candidates (**does not auto-assign**) |
| `assign_delivery_job(job_id, assignee_user_id, override bool)` | Dispatcher/admin; writes `assignee_user_id`, may set `dispatched` |
| `set_driver_presence(...)` | Driver self-service |
| `ingest_delivery_location` (harden) | Allow `driver` **and** `assignee_user_id = auth.uid()`; still rate-limit; optionally update `driver_presence.last_*` + recompute ETA columns |
| `get_delivery_track_point(job_id \| token)` | Customer/auth or token: **last** lat/lng + `eta_at` only when job `dispatched` and not terminal; never full trail |
| `submit_delivery_pod(...)` | Photo/signature storage paths + complete transition |

Realtime: keep `delivery_locations` for staff; customer uses polled/Realtime-safe RPC or a **narrow** `delivery_job_live` view/RPC — **not** raw trail SELECT.

## Acceptance criteria (epic Done)

- [ ] `apps/android-delivery/` builds; driver login; lists assigned jobs; starts FGS location → `ingest_delivery_location` (Bridge-First only)
- [ ] `driver` role + `driver_presence`; dispatcher can suggest assignees and manually override
- [ ] Management Android/web: assignment UI; driver GPS UI **gated/removed** from management
- [ ] ETA fields update on ingest (Haversine at minimum); staff map shows marker + ETA
- [ ] Customer (web + mobile): live last-point **only** while job active; token or order-scoped; no historical trail
- [ ] POD photo + signature via bridges required before `completed`
- [ ] Offline location queue survives brief disconnect; flush respects rate limit
- [ ] RLS + smoke: customer cannot SELECT `delivery_locations`; driver cannot read other drivers’ jobs
- [ ] `/security-reviewer` + `/verifier` PASS (no ZIMRA, no browser GPS, Bridge-First)
- [ ] `rufler.yaml` includes `android-delivery` path (dedicated agent or management path list)

## Paths in scope

| Area | Paths |
|------|--------|
| Backend | `supabase/migrations/` (new), tests under `supabase/tests/`, `packages/supabase-client/` types |
| Hardware | `bridges/android/location-tracker/` (continuous/FGS harden), POD camera/signature bridges if missing |
| Delivery app | `apps/android-delivery/**` (new) |
| Management | `apps/android-management/feature/dispatch/` (assignment; remove driver track) |
| Web | `apps/web` staff logistics assignment + tracking ETA; customer track route |
| Customer mobile | `apps/android-customer/`, `apps/ios/` active-delivery track screens |
| Config | `rufler.yaml`, `docs/decisions/2026-07-25-dedicated-delivery-app.md` |

## Out of scope

- Redesign pick/pack / DN / invoice logistics
- Full OSRM self-host ops (document env fallback only)
- Multi-carrier / 3PL marketplace
- iOS delivery driver app
- ZIMRA, payroll tax, browser QR/GPS
- Customer access to full GPS trail or offline stalking
- P1/P2 rows in the matrix (document only)

## Risks / exclusions

- Prior AuthZ **(b)** must be explicitly superseded in ADR — do not leave customer status-only.
- Broad Realtime on `delivery_locations` + weak RLS = leak; keep staff-only trail policies.
- OEM battery killers — FGS notification + offline queue mandatory for P0 reliability.
- Breaking ingest grants for existing management driver flow — migrate: management stops ingest; delivery app becomes sole producer.

## Lane sequence (Manager invoke order)

1. **`@backend_agent`** — `driver` role, `driver_presence`, ETA columns, track tokens, suggest/assign/POD/get_track RPCs, harden `ingest_delivery_location`, RLS + smoke
2. **`@hardware_mobile_agent`** — continuous tracking FGS contract; POD photo/signature bridges
3. **`@android_delivery_agent`** (or `@management_app_agent` + override) — scaffold `apps/android-delivery`
4. **`@management_app_agent`** — assignment UI; gate driver GPS
5. **`@web_agent`** — staff assignment + ETA map; customer track page
6. **`@android_agent`** + **`@ios_agent`** — customer active-delivery map
7. **`/security-reviewer`** → **`/verifier`** → **`/manager`** done gate

## Handoff

1. Implement per lane sequence above (one coding lane per Manager turn)
2. Update master Immediate handoff after verify
3. No product commits from Planner; coding lanes commit only if user asks
