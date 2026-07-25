# Web management parity + RBAC

- Date: 2026-07-25
- Lane: `@web_agent` (primary); `@backend_agent` only if RPC gaps
- Status: accepted

## Decision

The Next.js `/staff/*` surface is the **management fallback** for staff when the Android management device is unavailable. Nav and route gates **mirror** `staff_roles` / `has_staff_role` (and `profiles.is_staff`), but **UI hide ≠ security** — RLS and SECURITY DEFINER RPCs remain the source of truth.

Hardware stays **Bridge-First**: web must not use browser GPS, HTML5 QR, or camera APIs. Warehouse QR on web = “use management device / bridge” or typed OEM/manual entry; live map = Realtime **subscribe-only** (no `ingest_delivery_location` from browser). Prefer wiring existing RPCs over new APIs.

## Why

Android management is thin (HR/dispatch live; POS/warehouse placeholders). Web already hosts staff shells (POS, warehouse receive/transfers/cycle-count, logistics, live map) and can call the same Postgres RPCs. Role-filtered nav improves UX without duplicating authz in the client.

## Consequences

- Staff nav items declare required roles; layout/gate redirects unauthenticated users and shows forbidden for wrong role.
- Do not invent browser QR/GPS; do not call bridge-only RPCs from web.
- No ZIMRA; HR stays gross attendance/payroll only (no PAYE/NSSA UI).
- Master handoff update happens **after** implementation + verify (not in this ADR).
