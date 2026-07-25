# Web GPS tracking discoverability + UX harden

- Status: done
- Lane(s): `@web_agent` (primary); `@backend_agent` **only if** flipping AuthZ (a)
- Skills needed: (none)
- Parent: [`2026-07-24-live-map-delivery-tracking.md`](./2026-07-24-live-map-delivery-tracking.md) (done, AuthZ b)
- Coordinate with: [`2026-07-25-web-management-parity-rbac.md`](./2026-07-25-web-management-parity-rbac.md) + ADR [`../decisions/2026-07-25-web-management-parity-rbac.md`](../decisions/2026-07-25-web-management-parity-rbac.md)

## Goal

Make staff live delivery tracking first-class on the web hub/nav (role-filtered), tighten MapLibre panel UX (picker, marker/trail, empty/error, Android-GPS note), and **keep customer last-point deferred** this pass.

## Customer AuthZ (a vs b) — this pass

| Option | Meaning |
|--------|---------|
| **(a)** | Customer-safe RPC: own-job **last** point only |
| **(b)** | Staff/dispatcher Realtime map only; customer status-only |

**Decision: (b) again.** No existing last-point RPC; Phase 10 smoke forbids customer SELECT on `delivery_locations`. (a) needs new SECURITY DEFINER + decision doc + `/security-reviewer` — not low-risk. Document (a) as follow-on only.

## Acceptance criteria

- [x] `/staff` hub has a dedicated **Live tracking** card → `/staff/logistics/tracking` (alongside Logistics)
- [x] Staff nav Live map item role-gated per RBAC matrix: `admin` | `warehouse` | `dispatcher` (extend same `staff_roles` / `has_staff_role` wiring as parity plan — do not fork a second auth helper)
- [x] Tracking panel: job picker; live marker + trail; clear empty / no-points / error / auth states; explicit copy that drivers use **Android management** for GPS (web subscribe-only)
- [x] Zero `navigator.geolocation` / browser GPS / web ingest of `ingest_delivery_location`
- [x] No customer order-detail map or last-point UI this pass
- [ ] `/verifier`: no HTML5 geo, no ZIMRA; `/security-reviewer` only if (a) is unexpectedly opened

## Paths in scope

- `apps/web/app/(staff)/staff/page.tsx` — hub Live tracking card
- `apps/web/components/staff-nav.tsx` — role-filter Live map (align with RBAC plan helpers)
- `apps/web/components/staff-delivery-tracking-panel.tsx` (+ live-map / `lib/staff-delivery-tracking.ts` as needed)
- Shared staff-auth helper **only if** introduced by RBAC parity work (reuse, don’t duplicate)

## Out of scope

- Browser GPS, web location ingest, Map tile billing keys beyond existing env pattern
- Customer last-point RPC / order-detail map (AuthZ a)
- Android/iOS bridge or management ingest changes
- Schema redesign, ZIMRA, payroll tax, HTML5 QR

## Coordination (RBAC ADR)

Parity plan owns role-filtered nav + gates. This plan **extends** that shell: Live map stays in matrix (`admin`/`warehouse`/`dispatcher`); hub card visibility matches. If parity lands first, only add hub card + UX harden; if this lands first, declare required roles on the Live map nav item in the same shape parity will use.

**Landed first:** introduced `apps/web/lib/staff-auth.ts` (`fetchMyStaffRoles`, `hasAnyStaffRole`, `STAFF_MODULE_ROLES`, `rpcHasStaffRole`). Parity should **reuse** this — do not invent a second helper.

## Demo steps

1. Sign in as dispatcher (or admin/warehouse) → hub shows **Live tracking** → open map.
2. Sales/hr-only staff: Live map link/card hidden (or forbidden on direct URL once parity gate exists).
3. Select job with pings → marker + trail update via Realtime; empty job shows empty state; note mentions Android GPS.
4. Confirm no browser geolocation prompt; customer order detail still status-only.
5. `/verifier` ripgrep: no `navigator.geolocation` / ZIMRA.

## Handoff

1. `@web_agent` implement (coordinate with RBAC parity — same session or after) — **done**
2. Skip `@backend_agent` under (b)
3. `/verifier` → `/manager` done gate; revisit (a) only on explicit product ask
