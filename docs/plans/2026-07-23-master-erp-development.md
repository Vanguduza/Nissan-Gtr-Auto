# Master development plan — Nissan GTR Auto ERP

- Status: **active — process source of truth**
- Date: 2026-07-23
- Owner: `/manager` sequences; `/planner` refines per-phase child plans
- Skills: `/erpnext-feature-parity`, `/token-discipline`, `/accounting-ledger`, `/qr-inventory-workflow`, `/nissan-fast-parser`, `/parts-catalog-ingestion`, `/ui-ux-pro-max` (UI phases only)

## Goal

Ship a multi-platform composable ERP for Nissan spare-parts distribution (one Supabase backend, four client surfaces) in **ordered phases**, each with lane ownership, security/test gates, and hard exclusions — maximizing quality and speed while minimizing token burn.

## Standing laws (every phase)

| Law | Rule |
|-----|------|
| No ZIMRA | No FDMS, fiscalisation QR, mTLS fiscal devices, tax-authority payloads |
| No payroll tax | Gross + manual deductions only; no PAYE/NSSA/statutory forms |
| Bridge-First | Camera/QR/Bluetooth/biometric/GPS only via `bridges/` |
| RLS | Every new table ships with RLS in the same migration |
| Ledger | Journal entries append-only; corrections = reversing entries |
| Multi-currency | Explicit `USD` \| `ZIG` + rate at transaction time |
| Token discipline | One primary lane per task; `/planner` child plan before large work; no blueprint re-paste |

## How development follows this plan

```
/manager opens phase
    → /planner writes/refines child plan in docs/plans/
    → coding lane(s) implement (one primary at a time)
    → /security-reviewer (+ /supabase-rls-auditor if migrations)
    → /verifier
    → mark phase exit criteria
    → /manager advances to next phase
```

- **This file** = roadmap and exit criteria.
- **Child plans** = `docs/plans/YYYY-MM-DD-phaseN-*.md` (scoped, ≤1–2 screens).
- **Decisions** = `docs/decisions/` + claude-mem (do not re-litigate).
- **Parity checklist** = `/erpnext-feature-parity` at each milestone.

---

## Phase map

| Phase | Name | Primary lane(s) | Depends on | Status |
|------:|------|-----------------|------------|--------|
| 0 | Orchestration & tooling | repo / docs | — | **Done** |
| 1 | Monorepo + schema foundation | `@backend_agent` | 0 | **Done** (apply DB locally) |
| 2 | Auth, roles, typed client | `@backend_agent` | 1 | Next |
| 3 | Finance core (posting + statements) | `@finance_agent`, `@backend_agent` | 2 | Pending |
| 4 | Inventory ops (receipt, transfer, QR data) | `@backend_agent`, `@hardware_mobile_agent` | 2 | Pending |
| 5 | Sales / POS / cart / invoices | `@backend_agent`, `@management_app_agent` | 3, 4 | Pending |
| 6 | Web storefront + My Garage + catalog UI | `@web_agent` | 2, 4, 5 (read APIs) | Pending |
| 7 | Data pipeline + search index | `@data_pipeline_agent` | 1, 6 (canvas can stub) | Pending |
| 8 | Procurement + suppliers | `@backend_agent`, `@web_agent` (portal) | 4, 5 | Pending |
| 9 | HR / attendance / gross payroll | `@management_app_agent`, `@backend_agent` | 2 | Pending |
| 10 | Logistics / GPS / dispatch | `@management_app_agent`, `@hardware_mobile_agent` | 5 | Pending |
| 11 | Customer mobile (iOS + Android) | `@ios_agent`, `@android_agent` | 6 APIs | Pending |
| 12 | Management Android app + bridges | `@management_app_agent`, `@hardware_mobile_agent` | 4, 5, 10 | Pending |
| 13 | Payments (ContiPay), SMS, forecasting | `@backend_agent`, `@web_agent` | 5, 6 | Pending |
| 14 | Offline sync (PowerSync), hardening, CI | cross-cutting | 11–12 | Pending |
| 15 | ERPNext parity audit + polish | `/manager`, `/verifier` | all | Pending |

Phases **6 ∥ 7** and **9 ∥ 8** may overlap only when file paths do not conflict (use worktrees).

---

## Phase 0 — Orchestration & tooling (done)

**Exit criteria:** `rufler.yaml`, `.cursorrules`, rules/skills/agents, hooks, claude-mem/ui-ux docs, agent team pipeline.

---

## Phase 1 — Monorepo + schema foundation (done)

**Child plan:** `docs/plans/2026-07-23-phase1-monorepo-supabase.md`

**Exit criteria:** pnpm packages; migrations for CoA, ledger, warehouses (Quarantine), inventory, vehicle/PNC/fitment; RLS + seed.  
**Local remaining:** `supabase start && supabase db reset && pnpm db:types`.

---

## Phase 2 — Auth, roles, typed client

- **Lane:** `@backend_agent`
- **Skills:** none required
- **Build:**
  - Auth signup/login hooks → `profiles` row
  - Staff role assignment admin path
  - Regenerate `database.types.ts`
  - Seed script for local admin/finance/warehouse users (dev only)
- **Acceptance (≤8):**
  - [ ] New user gets `profiles` row
  - [ ] `has_staff_role` / `is_staff` work in RLS smoke tests
  - [ ] No service_role in client packages
  - [ ] Types generated and committed process documented
- **Paths:** `supabase/`, `packages/supabase-client/`, optional `supabase/seed.sql`
- **Out of scope:** UI login screens (Phase 6/11)
- **Gate:** `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`

---

## Phase 3 — Finance core

- **Lanes:** `@finance_agent` (logic), `@backend_agent` (migrations/RPC)
- **Skills:** `/accounting-ledger`
- **Build:**
  - Post journal RPC (balanced, append-only)
  - Reversing entry helper
  - Trial Balance, P&L, Balance Sheet, Cash Flow (USD/ZiG consolidation via stored rates)
  - Close-the-books wizard data contract (API first)
- **Acceptance:**
  - [ ] Unbalanced post rejected
  - [ ] UPDATE/DELETE on journal_* still blocked
  - [ ] Statements match sample fixtures
  - [ ] No tax fields anywhere
- **Paths:** `supabase/migrations/`, `supabase/functions/` or RPC, `packages/shared/src/ledger/`
- **Out of scope:** Full finance UI (later on management/web)
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 4 — Inventory operations + QR data model

- **Lanes:** `@backend_agent`; `@hardware_mobile_agent` for bridge **contracts only**
- **Skills:** `/qr-inventory-workflow`
- **Build:**
  - Stock receipt → batch → `inventory_qr_codes` payload `gtr://part/...`
  - Dual-authorization warehouse transfer workflow
  - FIFO/AVG valuation movements
  - Serial numbers for high-value/warranty assemblies
  - Quarantine routing on returns (data path)
- **Acceptance:**
  - [ ] Receipt creates QR rows
  - [ ] Transfer requires dual auth before stock mutates
  - [ ] Returns never exchange into saleable without Quarantine
  - [ ] Valuation method persisted per batch
- **Paths:** `supabase/`, `packages/shared/`, `bridges/**` interfaces only
- **Out of scope:** Physical printer/camera implementation (Phase 12)
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 5 — Sales, cart, invoices

- **Lanes:** `@backend_agent`, `@management_app_agent` (API contracts)
- **Skills:** `/accounting-ledger` (sale/return patterns)
- **Build:**
  - Cart with core-charge parent/child lines
  - Sales invoice + credit note + return-against-invoice
  - Counter/POS scan → resolve part → cart (API)
  - Post sale/return journals via Phase 3 RPC
- **Acceptance:**
  - [ ] Core charge split at cart insert
  - [ ] Return links to originating invoice/batch
  - [ ] Journals posted for sale/return/COGS
  - [ ] Emits domain/outbox events: `order_received`, `order_completed` (for Phase 13 manager SMS)
- **Out of scope:** ContiPay capture (Phase 13), HTML5 QR
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 6 — Web storefront

- **Lane:** `@web_agent`
- **Skills:** `/ui-ux-pro-max` (explicit), `/parts-catalog-ingestion` (consume only)
- **Build:**
  - Next.js App Router scaffold in `apps/web`
  - Route groups: storefront, My Garage, B2B, (staff read-only as needed)
  - Catalog canvas (bounding boxes), 4-way search UI against API/index
  - Auth-facing pages; cart checkout to Phase 5 APIs
- **Acceptance:**
  - [ ] `pnpm --filter web dev` runs
  - [ ] Design tokens from `@gtr/ui` (no Inter+purple default)
  - [ ] My Garage filters search
  - [ ] No browser QR libraries
- **Paths:** `apps/web/`, `packages/ui/`, `pnpm-workspace` include `apps/*`
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 7 — Data pipeline + search

- **Lane:** `@data_pipeline_agent`
- **Skills:** `/nissan-fast-parser`, `/parts-catalog-ingestion`
- **Build:**
  - Python project under `data-pipeline/`
  - FAST parse → fitment JSON; diagram scrape → bbox JSON
  - Import jobs to Supabase Storage + tables
  - Meilisearch (or PG FTS interim) + search API
- **Acceptance:**
  - [ ] Schema-validated pipeline output
  - [ ] Idempotent import
  - [ ] Search supports part / VIN / model / PNC paths
- **Out of scope:** Live production scrapers without rate limits/robots respect
- **Gate:** `/verifier` (pipeline tests)

---

## Phase 8 — Procurement & suppliers

- **Lanes:** `@backend_agent`, `@web_agent` (supplier portal pages)
- **Build:** PO, GRN link to Phase 4 receipt, supplier portal read/reconcile, reorder signals hook (forecast later)
- **Acceptance:** PO → receipt → stock/QR; supplier sees own POs only (RLS)
- **Gate:** `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`

---

## Phase 9 — HR / attendance / gross payroll

- **Lanes:** `@backend_agent`, `@management_app_agent`
- **Build:** Staff identity records, attendance, gross pay from hours/salary, manual deductions, payslip export
- **Acceptance:** No PAYE/NSSA/tax brackets; payslip = gross − manual lines only
- **Out of scope:** Biometric hardware (Phase 12 bridges)
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 10 — Logistics & GPS

- **Lanes:** `@backend_agent`, `@management_app_agent`, `@hardware_mobile_agent` (GPS bridge contract)
- **Build:** Delivery jobs, ~5s location ingest, Realtime for dispatcher/customer maps (MapLibre)
- **Acceptance:** Trail stored with retention policy; role-gated reads; emits `delivery_completed` outbox event for manager SMS
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 11 — Customer mobile (iOS + Android)

- **Lanes:** `@ios_agent`, `@android_agent` (parallel worktrees OK)
- **Build:** Customer apps consuming storefront APIs; My Garage; catalog; orders
- **Acceptance:** Shared logic from `@gtr/shared` / supabase-client; no duplicated pricing
- **Gate:** `/verifier` per platform

---

## Phase 12 — Management Android + hardware bridges

- **Lanes:** `@hardware_mobile_agent`, `@management_app_agent`
- **Skills:** `/qr-inventory-workflow`
- **Build:**
  - iOS AVFoundation + Android CameraX QR bridges
  - ESC/POS Bluetooth printer bridge
  - Biometric/TOTP staff login bridges
  - Management app: receiving, POS, warehouse, dispatch screens
- **Acceptance:** Bridge-First enforced; no HTML5 QR; device smoke tests documented
- **Gate:** `/hardware-bridge-specialist` → `/security-reviewer` → `/verifier`

---

## Phase 13 — Payments, SMS, demand forecasting

- **Lanes:** `@backend_agent`, `@web_agent`, `@management_app_agent` (recipient prefs)
- **Build:**
  - ContiPay (EcoCash, Visa 3DS, ZimSwitch), dual-currency settlement display
  - **Manager key-event SMS** (required): order received/completed, payment received, delivery completed — to **selected managers** only (opt-in prefs). See `docs/decisions/2026-07-23-manager-sms-key-events.md`
  - Marketing SMS promos tied to My Garage (separate from ops alerts)
  - Forecasting → requisition suggestions
- **Acceptance:**
  - [ ] Secrets in server only; no ZIMRA fiscal payloads on receipts
  - [ ] Domain events (or outbox) for order/payment/delivery fire once per occurrence
  - [ ] Only managers with that event enabled receive SMS
  - [ ] Failed sends retried/logged without duplicate spam on success path
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 14 — Offline sync & hardening

- **Lanes:** mobile lanes + `@backend_agent`
- **Build:** PowerSync (or chosen sync) for POS/dispatch; CI (lint/test/migrate); Bugbot required on `main`; performance pass
- **Acceptance:** Offline sale queues and syncs; CI green on PR
- **Gate:** `/verifier` + Bugbot

---

## Phase 15 — Parity audit & polish

- **Invoke:** `/erpnext-feature-parity`, `/manager`, `/verifier`
- **Build:** Close checklist gaps that are in-scope; docs; operator runbooks
- **Acceptance:** Checklist complete except explicit exclusions; exclusion grep clean

---

## Global out of scope (never schedule)

- ZIMRA / FDMS / fiscal device / tax-authority integrations
- Payroll tax engines and statutory remittance forms
- Browser/HTML5 QR scanning
- Replacing Supabase with ERPNext/Frappe

---

## Risks

| Risk | Mitigation |
|------|------------|
| Token burn on mega-prompts | Child plans + one lane; `/manager` enforces pipeline |
| Schema thrash | Decisions in `docs/decisions/`; Phase 1–3 stabilize finance/inventory first |
| Mobile before APIs | Phases 11–12 blocked on 5–6 API contracts |
| Scraper legal/ops issues | Pipeline etiquette rules; prefer licensed FAST sources |
| SMS without events | Phases 5/10/13 emit outbox events early; prefs + send in Phase 13 |

---

## Immediate handoff

1. **`/manager`:** open **Phase 2** (Auth, roles, typed client).
2. **`/planner`:** emit child plan `docs/plans/YYYY-MM-DD-phase2-auth-roles.md` if more detail needed.
3. **`@backend_agent`:** implement Phase 2.
4. Gates: `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`.

Do not start Phase 6 web UI until Phase 2 exit criteria pass (auth + types). Phase 3 and 4 may be sequenced tightly after Phase 2.
