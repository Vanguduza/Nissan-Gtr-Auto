# Fleet management (company vehicles) MVP

- Status: draft
- Lane(s): `@backend_agent` → `@web_agent` → `@management_app_agent`
- Skills needed: `/token-discipline` (none of accounting / FAST / QR / design suite)
- Related: Phase 10 logistics (`…80000`/`…81000`); dedicated delivery ADR [`docs/decisions/2026-07-25-dedicated-delivery-app.md`](../decisions/2026-07-25-dedicated-delivery-app.md); web RBAC [`docs/plans/2026-07-25-web-management-parity-rbac.md`](./2026-07-25-web-management-parity-rbac.md); live-map Bridge-First [`docs/plans/2026-07-24-live-map-delivery-tracking.md`](./2026-07-24-live-map-delivery-tracking.md)
- Naming collision: B2B `price_lists` code `FLEET` and `customer_garage_vehicles` are **not** this module

## Goal

Give staff a thin vertical slice to register and maintain **company delivery/ops vehicles** (plate, status, optional driver assignee) on web `/staff/fleet` and android-management `:feature:fleet`, backed by RLS’d `fleet_vehicles` + RPCs — without telematics, fuel, or browser GPS.

## Non-goals / out of scope

- Fuel logs, odometer programs, maintenance schedules, inspections, insurance docs
- Telematics / OBD / third-party trackers; Realtime vehicle GPS
- Browser or WebView geolocation (Bridge-First: GPS stays in `apps/android-delivery` + `bridges/`)
- Changing `customer_garage_vehicles` or B2B `FLEET` price list semantics
- Redesigning `delivery_jobs` / pick-pack / DN / POD / `driver_presence`
- Wiring fleet into suggest/assign ranking (follow-on only if cheap)
- iOS management; android-delivery UI for fleet CRUD
- ZIMRA / payroll tax

## Acceptance criteria

1. Migration creates `fleet_vehicles` (+ enums as needed) with **RLS in the same file**; customers/drivers have no write; `driver` may SELECT own assigned vehicle only (optional) or none — staff `admin`|`warehouse`|`dispatcher` CRUD via RPCs.
2. SECURITY DEFINER RPCs: list (or PostgREST SELECT under RLS), create, update, soft-retire/set status; mutate guards block direct table writes if following logistics pattern.
3. Web: `/staff/fleet` list + create/edit (plate, label, status, optional `assigned_driver_user_id`); nav + gate per RBAC matrix (`admin`|`warehouse`|`dispatcher`); **no** GPS APIs.
4. Android management: `:feature:fleet` module + MainActivity hub button; `FakeRpcClient` + live `SupabaseRpcClient` / `RpcNames` parity with dispatch pattern; unit tests for Fake CRUD.
5. Smoke/tests: non-staff denied; sales/finance/hr cannot mutate; no ZIMRA/payroll/browser-GPS introduced.
6. Types regenerated into `packages/supabase-client` after migration.
7. Dispatch suggest/assign **unchanged** in v1 (document follow-on: optional `fleet_vehicle_id` on `delivery_jobs` or filter by assignee’s vehicle).

## Schema sketch + RLS notes

**New (do not overload garage / price list):**

| Object | Notes |
|--------|--------|
| `fleet_vehicle_status` enum | e.g. `active`, `in_service`, `retired` |
| `fleet_vehicles` | `id`, `plate` (unique, normalized upper), `label`/`nickname`, `status`, `assigned_driver_user_id` → `auth.users` nullable (prefer users with `staff_roles.role = driver`), `notes`, `created_by`, `created_at`, `updated_at` |
| Optional later | `fleet_vehicle_id` on `delivery_jobs` — **not** in MVP |

**RLS (same migration):**

- `ENABLE ROW LEVEL SECURITY`
- SELECT: `has_staff_role(['admin','warehouse','dispatcher'])` (align logistics job read; sales **out** of fleet mutate — keep matrix tight)
- INSERT/UPDATE/DELETE: prefer **RPC-only** + deny direct writes (mirror `delivery_jobs` mutation guards) OR staff write for same three roles
- No customer policies; no open table
- Do **not** publish Realtime on `fleet_vehicles` in MVP

**RPCs (suggested names):**

- `list_fleet_vehicles()` / rely on PostgREST SELECT under RLS
- `upsert_fleet_vehicle(...)` — create/update; validate plate uniqueness; optional check assignee has `driver` role
- `set_fleet_vehicle_status(id, status)` — retire/in_service without full update blast

**Roles:** reuse existing `staff_role` values — no new role. Matrix row: Fleet → `admin` | `warehouse` | `dispatcher`.

## Surfaces

### Web (`@web_agent`)

- Route: `apps/web/app/(staff)/staff/fleet/page.tsx` + panel/lib helpers (pattern: logistics / warehouse)
- `staff-nav.tsx` + hub card; `staff-gate` / RBAC matrix update in parity ADR/plan if needed
- Fields: plate, label, status, assignee (driver user picker from existing presence/staff list if available — else UUID/email search already used elsewhere)
- Explicit: subscribe-only maps elsewhere; fleet page never calls geolocation

### Android management (`@management_app_agent`)

- New `:feature:fleet` (Compose) mirroring `:feature:dispatch` module wiring
- Hub button in `MainActivity`; Fake + live RPC parity; tests under `feature/fleet/src/test`
- **No** `:location-tracker` dependency; no `ingest_delivery_location`

### Delivery app

- Out of scope for CRUD; drivers continue GPS via Bridge-First only

## Phased checklist

### Phase A — Backend (`@backend_agent`)

- [ ] Migration `YYYYMMDDHHMMSS_fleet_vehicles.sql`: table, indexes (plate unique), RLS, RPCs, grants, mutation guards
- [ ] Smoke/SQL tests for role deny + plate uniqueness
- [ ] Regenerate `packages/supabase-client` types
- [ ] `/supabase-rls-auditor` + `/security-reviewer` on migration

### Phase B — Web (`@web_agent`)

- [ ] `/staff/fleet` panel CRUD
- [ ] Nav + role gate (`admin`|`warehouse`|`dispatcher`)
- [ ] Manual smoke: dispatcher can CRUD; finance gets forbidden

### Phase C — Android management (`@management_app_agent`)

- [ ] `:feature:fleet` + hub + RpcNames/Fake/live
- [ ] Unit tests Fake CRUD parity
- [ ] Confirm GPS producer still gated off management

### Phase D — Done gate

- [ ] `/verifier` (exclusions + lane)
- [ ] `/manager` marks epic Done
- [ ] Optional follow-on ticket: link `fleet_vehicle_id` → assign UI / suggest filter (only if low-cost)

## Risks / exclusions

| Risk | Mitigation |
|------|------------|
| Confusing with B2B “Fleet” price list or garage vehicles | Name tables/UI **Company fleet** / `fleet_vehicles`; never touch `FLEET` price list |
| Scope creep into telematics | Hard out-of-scope; reuse delivery GPS trail only |
| Browser GPS temptation on “where is the van” | Staff live map remains job-based on `delivery_locations`; fleet CRUD is metadata only |
| Assignee ≠ driver role | RPC validates `driver` (or allow null unassigned) |

## Paths in scope

- `supabase/migrations/` (+ tests)
- `packages/supabase-client/` (generated types)
- `apps/web/app/(staff)/staff/fleet/`, `components/staff-nav.tsx`, staff auth/RBAC helpers
- `apps/android-management/feature/fleet/`, `core/rpc/`, `app/.../MainActivity.kt`
- This plan; optional one-line roadmap pointer in master plan (docs only)

## Handoff

1. Implement Phase A in `@backend_agent`
2. `/security-reviewer` + `/supabase-rls-auditor` after migration
3. `@web_agent` Phase B → `@management_app_agent` Phase C
4. `/verifier` → `/manager` done gate

Invoke `/manager` to sequence, or start coding with `@backend_agent` on the migration.
