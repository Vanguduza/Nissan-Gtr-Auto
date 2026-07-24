# Phase 10 — Logistics / pick-pack / DN / GPS

- Status: draft
- Lane(s): `@backend_agent` (primary); `@hardware_mobile_agent` (GPS bridge contract follow-on); `@management_app_agent` (UI follow-on)
- Skills needed: (none for schema slice; `/qr-inventory-workflow` only if pick confirms via QR — defer to Phase 12 UI)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 10
- Prior: Phase 5 sales (`20260723230000_sales_pos.sql` — invoices, `qty_fulfilled`, stock issue at checkout); SMS catalog `delivery_*` in `docs/decisions/2026-07-23-manager-sms-key-events.md`

## Goal

Ship pick/pack and Delivery Notes against **posted sales invoices**, with partial ship, stock issue on DN for dispatch, delivery jobs + GPS trail schema (bridge ingest later), and `delivery_*` domain events — no ZIMRA, no browser geolocation.

## Policy choice (explicit)

**DN-from-invoice** (not bill-from-DN).

- No `docs/decisions/` entry existed; Phase 5 already owns commercial docs (`sales_invoices` / lines, COGS, core charges). Bill-from-DN would invent a pre-invoice commercial path.
- Add `fulfillment_mode` on cart/invoice: `immediate` | `dispatch`.
  - **immediate** (POS / click & collect): keep Phase 5 behavior — stock + `qty_fulfilled` at checkout; DN optional/N/A.
  - **dispatch**: checkout posts invoice + revenue/AR journals; **defer** FIFO stock issue + COGS until DN submit (partial OK); cancel DN reverses stock/COGS via reversing movements/journals (ledger immutability).
- Pick/pack open qty = invoice line `qty_base − qty_fulfilled` (core-charge lines never pick).

## Acceptance criteria

- [x] Pick/pack cannot over-pick open qty vs posted invoice lines
- [x] DN submit (dispatch) issues stock + increments `qty_fulfilled`; cancel reverses stock (and COGS via reverse JE)
- [x] Immediate fulfillment unchanged: issue-at-checkout; no double-issue if DN created in error
- [x] Delivery jobs link DN → driver/dispatcher; status → dispatched / completed / failed
- [x] `delivery_locations` trail (~5s ingest contract); retention policy; role-gated reads (`dispatcher`|`warehouse`|`admin`; customer own-job only if exposed)
- [x] Emits `delivery_dispatched` / `delivery_completed` / `delivery_failed` via `emit_domain_event` (SMS send stays Phase 13)
- [x] RLS on every new table in same migration(s); no browser/WebView geolocation APIs
- [x] Smoke SQL: partial pick → DN → stock delta; over-pick denied; cancel reverses; event rows present

## Paths in scope

- `supabase/migrations/20260724080000_logistics_pick_pack_dn.sql` — schema, RPCs, RLS, Realtime pub for locations
- `supabase/migrations/20260724081000_logistics_mutation_guards.sql` — AuthZ / over-pick / double-issue guards (if not fully inlined)
- `supabase/tests/phase10_logistics_smoke.sql`
- Patch Phase 5 checkout RPC path (same migrations): `fulfillment_mode` + deferred issue for `dispatch`
- `packages/supabase-client/` types regen; `packages/shared/` fulfillment/DN status types if needed
- **Follow-on:** `bridges/contracts/gps-delivery.ts` + native GPS (`@hardware_mobile_agent`); pick/pack/dispatch screens (`@management_app_agent`); MapLibre UI later

## Tables / RPCs (sketch)

| Object | Notes |
|--------|--------|
| `pick_lists` / `pick_list_lines` | Against `sales_invoice_id` + line refs; status draft→done/cancelled; partial OK |
| `delivery_notes` / `delivery_note_lines` | Ship qty; FK invoice (+ optional pick_list); `DN-` series |
| `delivery_jobs` | DN, assignee (`dispatcher`/driver profile), status, ETA fields |
| `delivery_locations` | job_id, lat/lng, recorded_at, source=`bridge`; Realtime; retention (e.g. 90d job or purge RPC) |
| Cart/invoice col | `fulfillment_mode` `immediate`\|`dispatch` |
| RPCs | `create_pick_list`, `confirm_pick_lines`, `create_delivery_note`, `submit_delivery_note`, `cancel_delivery_note`, `create_delivery_job`, `update_delivery_job_status`, `ingest_delivery_location` (service/bridge role; rate ~5s) |

## Out of scope

- Bill-from-DN / standalone sales orders as commercial source
- ZIMRA, fiscal QR, payroll tax
- Browser/HTML5 geolocation or MapLibre UI polish (schema + Realtime only this slice)
- Native GPS bridge implementation (contract note + Phase 12 hardware)
- Customer receipt PDF/SMS send (Phase 13); DN-as-receipt copy
- ContiPay / payment allocation; store credit
- Full management Android pick-pack screens (API-first; UI follow-on)

## Risks / exclusions

- **Stock double-issue:** dispatch must not call FIFO at checkout; immediate must not issue again on DN
- **Bridge-First:** GPS only via `bridges/`; backend stores points from authenticated bridge/service ingest
- Multi-currency: DN inherits invoice currency; any JE reverse uses original rate
- Quarantine returns remain Phase 5 path — DN fail ≠ automatic quarantine (status + event only unless explicit return)
- Do not invent ZIMRA or tax authority payloads

## Smoke expectations

1. Seed posted **dispatch** invoice with `qty_fulfilled=0` and stock still on hand → pick partial → submit DN → stock down, `qty_fulfilled` up, COGS posted.
2. Second DN/pick exceeding open qty → exception.
3. Cancel DN → stock restored; reverse JE; events consistent.
4. **immediate** checkout still issues stock once; creating DN against it is blocked or no-op stock.
5. `ingest_delivery_location` inserts trail; non-dispatcher SELECT denied; `delivery_dispatched|completed|failed` in `domain_events`.

## Handoff

1. Implement schema/RPCs/smoke in `@backend_agent`
2. `/supabase-rls-auditor` → `/security-reviewer` (locations PII + RLS)
3. `/verifier` (exclusions + no geolocation API in web)
4. `/manager` done gate → `@hardware_mobile_agent` GPS contract → `@management_app_agent` UI
