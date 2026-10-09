# Dedicated delivery Android app

- Date: 2026-07-25
- Lane: `@backend_agent` (schema) then `@hardware_mobile_agent` / `@android_delivery_agent` / `@management_app_agent` / `@web_agent` / customer mobile
- Status: accepted
- Related: plan [`dedicated-delivery-app`](../plans/2026-07-25-dedicated-delivery-app.md); prior live-map plan [`live-map-delivery-tracking`](../plans/2026-07-24-live-map-delivery-tracking.md); Phase 10 logistics migrations `…80000` / `…81000`
- Supersedes: AuthZ option **(b)** (customer status-only) from `docs/plans/2026-07-24-live-map-delivery-tracking.md` — replaced by privacy-safe scoped live track

## Decision

1. **Dedicated delivery client:** Drivers use a separate Android app at `apps/android-delivery/` (role `driver` only). It is not a flavor of management POS/warehouse/HR/finance.
2. **Assignment stays in management:** Who is available, closest, on shift, and capacity — plus manual override — lives in **web `/staff/logistics`** and **android-management** dispatch/assignment UI. The delivery app only executes assigned jobs.
3. **Always-on location from delivery app:** Continuous Bridge-First GPS (`bridges/android/location-tracker/` + foreground service) feeds existing `ingest_delivery_location` → `delivery_locations` (Realtime for staff). Management web remains **subscribe-only** (no browser GPS).
4. **Privacy-safe customer track:** Flip prior AuthZ **(b)** → scoped **(a)-style**: customers (or share-token holders) get **last point + ETA** for an **active** (`dispatched`) job only — via SECURITY DEFINER RPC / `delivery_track_tokens`, never raw `SELECT` on `delivery_locations` or historical stalking.

## Why

- Isolation / UX / release: driver UX (always-on GPS, POD, navigation) conflicts with a fat management APK and store/release cadence.
- Prior GPS wave already shipped trail ingest + staff MapLibre; reuse it rather than redesign Phase 10 tables.
- AuthZ **(b)** was a session risk deferral, not a product end-state; product now requires customer live track with least privilege.
- Assignment is a dispatcher concern (proximity, capacity, override); putting it in the driver app would create authority and UX confusion.

## Alternatives rejected

| Alternative | Why rejected |
|-------------|--------------|
| Keep driver GPS inside `android-management` | Couples release/UX; bloats management; harder to strip POS from driver devices |
| Customer full trail Realtime on `delivery_locations` | Enables stalking; fails Phase 10 smoke intent; RLS blast radius |
| Auto-assign without dispatcher override | Ops need manual control for capacity, trust, and exceptions |
| Browser/WebView GPS for delivery client | Violates Bridge-First |

## Schema / contract consequences

- **Reuse:** `delivery_jobs`, `delivery_locations`, `ingest_delivery_location` (~5s), Realtime publication, `eta_at`.
- **Add:** `driver` on `staff_role`; `driver_presence`; track tokens; ETA helper columns; suggest/assign/POD/get_track RPCs; harden ingest to allow assigned `driver` JWT without granting warehouse/dispatcher to drivers.
- **Do not:** grant customers SELECT on `delivery_locations`; break existing ingest signature without migration; put map API secrets in git.

## ETA model

- **Default:** Haversine distance ÷ assumed urban speed → `eta_at` / `eta_seconds`, `eta_source = haversine`, refreshed on ingest ticks.
- **Optional:** OSRM or MapLibre directions when env key/URL present → `eta_source = osrm` (or provider name).
- **Fallback:** if no dropoff coords, leave ETA null or manual dispatcher set (`eta_source = manual`).

## Agent lane consequence

Prefer **`@android_delivery_agent`** with path `apps/android-delivery/**` in `rufler.yaml` (product isolation ↔ lane isolation). Until that entry exists, Manager may route scaffold/work to `@management_app_agent` with an explicit path override — then update `rufler.yaml` in the same epic.

## Exclusions

- No ZIMRA / fiscalisation on POD or receipts from this app.
- No payroll tax.
- No HTML5 / browser geolocation or camera for delivery or staff web maps.
- No iOS driver app in this decision.
- Customer full GPS trail / historical stalking remains **Never**.
- **POD evidence (2026-08-14 verify):** CameraX `pod-camera` + Compose `pod-signature` are **WORKING** (not stubs). App uploads to Storage `delivery-pods` then `submit_delivery_pod`. Gallery ImagePicker intentionally skipped (camera-only Bridge-First).
- Former P1/P2 delivery features (OTP POD, geofence suggestions, multi-stop optimizer, panic, offline POD queue, `/track/[token]`, fail/reattempt) are **Required** under the 2026-07-25 mandate — see plan matrix; not backlog.
