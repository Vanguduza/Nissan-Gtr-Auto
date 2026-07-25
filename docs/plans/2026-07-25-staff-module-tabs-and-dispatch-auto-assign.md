# Staff module tabs + dispatch auto-assign / sales prep notify

- Status: draft
- Lane(s): `@backend_agent` → `@web_agent` → `@management_app_agent` (light)
- Skills: `/accounting-ledger` (CoA seeds only); no `/ui-ux-pro-max` unless asked
- Related: phase3-finance-core, phase5-sales-pos, phase10-logistics, dedicated-delivery-app, web-management-parity-rbac, shop-floor-pos-companion-otp

## Goal

Tabbed staff subfeature UX (shared `StaffModuleTabs`) plus auto driver assign + sales-prep notify when online dispatch orders finalize.

## Acceptance criteria

1. Finance / Warehouse / Logistics / POS / Analytics use horizontal button tabs under title; active = brand red; content swaps (no endless scroll hubs).
2. Shared `StaffModuleTabs` drives all five modules; deep links (`?tab=` or existing child routes) still work.
3. CoA seeds exist for petty cash, cash till, online clearing (no tax/ZIMRA); finance tabs can register/filter against them.
4. On dispatch finalize: logistics chain ensured (pick/DN/job if missing) → `suggest_delivery_assignees` → `assign_delivery_job` (fail soft: open unassigned job).
5. `staff_ops_notifications` (+ RLS) inserts sales-prep row; web staff inbox/badge; SMS/outbox enqueue fail-closed without keys.
6. Prep queue lists open online `fulfillment_mode=dispatch` invoices needing prep (POS and/or Logistics tab).
7. Android management: finance/warehouse subnav mirrors tabs where screens exist; POS-first sales home only if already in flight.
8. Smoke SQL covers auto-assign + notify; no secrets in git.

## StaffModuleTabs — file/route map

| Module | Tabs | Prefer keep routes + tab sync |
|--------|------|-------------------------------|
| Finance | Petty cash, Cash sales, Online sales, Journals, Payments, Reports, Bank recon, Periods, Credit (if B2B credit ops present) | `/staff/finance` + `?tab=` (refactor `staff-finance-panel.tsx`) |
| Warehouse | Receive, Transfers, Cycle-count, Bins, Consignment | `/staff/warehouse/{receive,transfers,cycle-count,bins,consignment}` — hub becomes tab shell |
| Logistics | Jobs/pick, Tracking, Panic, **Sales prep** | `/staff/logistics`, `/tracking`, `/panic` + prep tab |
| POS | Cart, Scan pairing, Online prep queue | `/staff/pos` + `?tab=` |
| Analytics | KPIs, Subscriptions | `/staff/analytics`, `/subscriptions` |

Shared: `apps/web/components/staff-module-tabs.tsx` (+ CSS module). Wire via layout/page wrappers; keep `staff-auth.ts` RBAC.

Android (light): subnav chips/tabs in existing finance/warehouse (and POS if present) under `apps/android-management/`.

## Migration outline (`@backend_agent`)

1. **CoA seed** (ON CONFLICT DO NOTHING): e.g. `1110` Petty Cash, `1120` Cash Till, `1130` Online Payment Clearing — assets only; no tax accounts. Wire finance panel filters to these codes.
2. **`staff_ops_notifications`**: `id`, `kind` (`sales_prep` | …), `invoice_id`, `delivery_job_id`, `recipient_role`/`user_id`, `title`, `body`, `read_at`, `created_at`; RLS: staff SELECT own/role; INSERT via SECURITY DEFINER only; UPDATE `read_at` for recipient.
3. **`ensure_dispatch_fulfillment(p_invoice_id)`** (DEFINER): if `posted` + `fulfillment_mode=dispatch`, ensure pick → DN → `create_delivery_job` (idempotent if job exists); then suggest+assign; insert sales_prep notification; optional outbox row only if SMS config present (else skip).
4. **Hook points** (exact): after successful post path in `checkout_pos_cart` / `checkout_customer_cart` when mode=`dispatch`; and payment settle RPCs that first mark invoice paid/posted for dispatch carts (ContiPay/Paynow settle). Do **not** double-fire — guard on existing job/notification.

## Auto-assign trigger points

| Path | When |
|------|------|
| `checkout_customer_cart` → `checkout_pos_cart` | Posted invoice, `fulfillment_mode=dispatch` |
| ContiPay / Paynow settle (service) | If settle is what finalizes unpaid dispatch invoice |
| Manual staff create job | Unchanged; auto-assign only on online finalize helper |

Sequence: ensure job → `suggest_delivery_assignees(job, n)` → top candidate → `assign_delivery_job(job, driver, false)`; on empty suggest → leave open + notify prep only.

## Phase order

1. `@backend_agent` — CoA + notifications + ensure/auto-assign hook + smoke SQL  
2. `@web_agent` — `StaffModuleTabs` + module shells + prep queue + inbox/badge  
3. `@management_app_agent` — light finance/warehouse (POS) subnav mirror  
4. `/security-reviewer` — RLS + DEFINER grants  
5. `/verifier` — exclusions, smoke, lane check  
6. `/manager` done gate — **no commits from planner**

## Out of scope / non-goals

- No ZIMRA / fiscal / payroll tax  
- No full finance rewrite or new ledger posting engine  
- No HTML5 QR; Bridge-First unchanged  
- No dedicated delivery-app rewrite; no iOS/Android customer changes  
- No commits from this plan phase  

## How to test prep notify

1. Seed driver + sales staff; create customer cart `fulfillment_mode=dispatch`; `checkout_customer_cart`.  
2. Assert: delivery job exists; assigned iff suggest non-empty; else status open.  
3. Assert: `staff_ops_notifications` row `kind=sales_prep` for invoice.  
4. Web: sales/admin sees badge + prep queue lists invoice; mark read clears badge.  
5. Without SMS secrets: no enqueue failure; checkout still succeeds.  
6. SQL smoke: extend logistics/storefront smoke or new `dispatch_auto_assign_notify_smoke.sql`.

## Handoff

1. `/manager` sequences lanes above  
2. Implement `@backend_agent` first  
3. Then `@web_agent`, then Android light  
4. `/security-reviewer` → `/verifier` → `/manager` done gate  
