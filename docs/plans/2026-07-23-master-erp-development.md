# Master development plan — Nissan GTR Auto ERP

- Status: **active — process source of truth**
- Date: 2026-07-23 (amended same day — distributor gap intake)
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
| Document discipline | Draft → Submit → Cancel; submitted docs immutable; cancel = reverse/cancel linkage |
| Naming series | Human-readable series per doc type (INV-, PO-, DN-, PAY-, …) |
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

## Distributor gap register (in-scope)

Intake from operational + ERPNext-pattern review. Each item is scheduled below; do not drop at Phase 15 without an explicit decision.

### Must-have (wired into Phases 3–13)

| Gap | Phase | ERPNext analogue |
|-----|------:|------------------|
| Opening balances + period lock | 3 | Opening Invoice / Period Closing |
| Bank reconciliation (USD/ZiG) | 3 | Bank Reconciliation Tool |
| Document naming series | 3 (service), used by 5+ | Naming Series |
| Draft → Submit → Cancel | 3–5 (pattern), all transactional docs | Submit / Cancel |
| UOM conversions | 4 | UOM Conversion |
| Stock reconciliation / cycle count | 4b | Stock Reconciliation |
| Price lists + customer pricing | 5 | Price List / Pricing Rule |
| Credit limit / customer hold | 5 | Credit Limit / Customer Freeze |
| Backorders / partial fulfill | 5 | Sales Order → Delivery partial |
| Warranty / serial claims | 5b | Warranty Claim (light) |
| Landed cost into batch | 8 | Landed Cost Voucher |
| RFQ / supplier quotations | 8b | Request for Quotation |
| Material Request → PO | 8 | Material Request |
| Blanket / contract POs | 8b | Blanket Order |
| Pick / pack / Delivery Note | 10 | Pick List / Delivery Note |
| Payment Entry → invoice allocation | 13 | Payment Entry |
| Store credit (refund path) | 13 | Payment Entry / Credit Note link |

### Later distributor extras (Phase 16)

| Gap | Notes |
|-----|-------|
| Bin / location within warehouse | Shelf/bin for pick speed |
| Kits / BOM sell | Sell assembly as kit; explode or stock kit SKU |
| Consignment stock | Supplier-owned or customer-held stock |
| Loyalty / points | Optional; after store credit works |
| Attachments + doc timeline comments | Soft requirement from Phase 5 onward if cheap |

---

## Phase map

| Phase | Name | Primary lane(s) | Depends on | Status |
|------:|------|-----------------|------------|--------|
| 0 | Orchestration & tooling | repo / docs | — | **Done** |
| 1 | Monorepo + schema foundation | `@backend_agent` | 0 | **Done** |
| 1b | Manager SMS event catalog + outbox | `@backend_agent` | 1 | **Done** (gateway later) |
| 2 | Auth, roles, typed client | `@backend_agent` | 1 | **Done** |
| 3 | Finance core + period/bank/naming | `@finance_agent`, `@backend_agent` | 2 | Next |
| 4 | Inventory ops (receipt, transfer, QR, UOM) | `@backend_agent`, `@hardware_mobile_agent` | 2 | Pending |
| 4b | Stock reconciliation / cycle count | `@backend_agent`, `@management_app_agent` | 4 | Pending |
| 5 | Sales / POS / cart / invoices / commercial | `@backend_agent`, `@management_app_agent` | 3, 4 | Pending |
| 5b | Warranty / serial claims | `@backend_agent` | 4, 5 | Pending |
| 6 | Web storefront + My Garage + catalog UI | `@web_agent` | 2, 4, 5 (read APIs) | Pending |
| 7 | Data pipeline + search index | `@data_pipeline_agent` | 1, 6 (canvas can stub) | Pending |
| 8 | Procurement + suppliers + landed cost | `@backend_agent`, `@web_agent` (portal) | 4, 5 | Pending |
| 8b | RFQ, quotations, blanket POs | `@backend_agent`, `@web_agent` | 8 | Pending |
| 9 | HR / attendance / gross payroll | `@management_app_agent`, `@backend_agent` | 2 | Pending |
| 10 | Logistics / pick-pack / DN / GPS | `@management_app_agent`, `@hardware_mobile_agent` | 5 | Pending |
| 11 | Customer mobile (iOS + Android) | `@ios_agent`, `@android_agent` | 6 APIs | Pending |
| 12 | Management Android app + bridges | `@management_app_agent`, `@hardware_mobile_agent` | 4, 4b, 5, 10 | Pending |
| 13 | Payments allocation, ContiPay, SMS, forecast | `@backend_agent`, `@web_agent` | 5, 6 | Pending |
| 14 | Offline sync (PowerSync), hardening, CI | cross-cutting | 11–12 | Pending |
| 15 | ERPNext parity audit + polish | `/manager`, `/verifier` | 1–14 | Pending |
| 16 | Distributor extras (bins, kits, consignment, loyalty) | `@backend_agent` + UI lanes | 15 or after 8/13 | Pending |

Phases **6 ∥ 7**, **9 ∥ 8**, and **5b ∥ 6** may overlap only when file paths do not conflict (use worktrees). **4b** may start once Phase 4 receipt/transfer APIs are stable.

---

## Phase 0 — Orchestration & tooling (done)

**Exit criteria:** `rufler.yaml`, `.cursorrules`, rules/skills/agents, hooks, claude-mem/ui-ux docs, agent team pipeline.

---

## Phase 1 — Monorepo + schema foundation (done)

**Child plan:** `docs/plans/2026-07-23-phase1-monorepo-supabase.md`

**Exit criteria:** pnpm packages; migrations for CoA, ledger, warehouses (Quarantine), inventory, vehicle/PNC/fitment; RLS + seed.  
**Local remaining:** `supabase start && supabase db reset && pnpm db:types`.

---

## Phase 1b — Manager SMS events (early)

**Decision:** `docs/decisions/2026-07-23-manager-sms-key-events.md`  
**Migration:** `supabase/migrations/20260723110000_manager_sms_events.sql`

**Exit criteria:** Full event catalog seeded; `domain_events` + `sms_outbox` + prefs; RPC `emit_domain_event`; shared `SMS_EVENT_CODES`. SMS provider **not** required yet.

---

## Phase 2 — Auth, roles, typed client (done)

**Child plan:** `docs/plans/2026-07-23-phase2-auth-roles.md`  
**Migrations:** `20260723200000_auth_profiles_roles.sql`, `20260723201000_auth_is_staff_hardening.sql`

**Exit criteria:** Signup → profiles; `assign_staff_role` / `revoke_staff_role`; `is_staff` not client-escalatable; types generated; local seed users.

---

## Phase 3 — Finance core + period, bank, naming

- **Lanes:** `@finance_agent` (logic), `@backend_agent` (migrations/RPC)
- **Skills:** `/accounting-ledger`
- **Build:**
  - Post journal RPC (balanced, append-only); Draft → Submit for journals
  - Reversing entry helper (Cancel path)
  - Trial Balance, P&L, Balance Sheet, Cash Flow (USD/ZiG consolidation via stored rates)
  - **Opening balances** import/post for go-live
  - **Period lock / close-the-books** (block posts into locked periods)
  - **Document naming series** service (configurable prefixes + sequence; used by later modules)
  - **Bank reconciliation** data model + RPC (match ledger cash/bank lines to statement lines; dual currency)
- **Acceptance:**
  - [ ] Unbalanced post rejected
  - [ ] UPDATE/DELETE on journal_* still blocked
  - [ ] Statements match sample fixtures
  - [ ] Opening balances produce correct trial balance
  - [ ] Locked period rejects new posts
  - [ ] Naming series allocates unique numbers under concurrency
  - [ ] Bank recon can clear matched lines without tax fields
  - [ ] No tax fields anywhere
- **Paths:** `supabase/migrations/`, `supabase/functions/` or RPC, `packages/shared/src/ledger/`
- **Out of scope:** Full finance UI (later on management/web)
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 4 — Inventory operations + QR + UOM

- **Lanes:** `@backend_agent`; `@hardware_mobile_agent` for bridge **contracts only**
- **Skills:** `/qr-inventory-workflow`
- **Build:**
  - Stock receipt → batch → `inventory_qr_codes` payload `gtr://part/...`
  - Dual-authorization warehouse transfer workflow
  - FIFO/AVG valuation movements
  - Serial numbers for high-value/warranty assemblies
  - Quarantine routing on returns (data path)
  - **UOM conversions** (e.g. box ↔ each) on receipt, transfer, and sale qty
  - Stock Entry–style movement types: Receipt / Transfer / Issue (data model)
- **Acceptance:**
  - [ ] Receipt creates QR rows
  - [ ] Transfer requires dual auth before stock mutates
  - [ ] Returns never exchange into saleable without Quarantine
  - [ ] Valuation method persisted per batch
  - [ ] Qty posted in base UOM; alternate UOM converts correctly
- **Paths:** `supabase/`, `packages/shared/`, `bridges/**` interfaces only
- **Out of scope:** Physical printer/camera (Phase 12); bin locations (Phase 16)
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 4b — Stock reconciliation / cycle count

- **Lanes:** `@backend_agent`, `@management_app_agent` (API contracts)
- **Skills:** `/qr-inventory-workflow`
- **Build:**
  - Cycle count sessions (full or partial by warehouse/SKU)
  - Variance → adjusting Stock Reconciliation entry + ledger COGS/inventory journals
  - Dual-auth optional for large variance threshold
  - Emit domain events for material variances if needed later
- **Acceptance:**
  - [ ] Count submit adjusts on-hand to counted qty
  - [ ] Journals balanced for write-up/write-down
  - [ ] Submitted count immutable; cancel via reverse
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 5 — Sales, cart, invoices, commercial controls

- **Lanes:** `@backend_agent`, `@management_app_agent` (API contracts)
- **Skills:** `/accounting-ledger` (sale/return patterns)
- **Build:**
  - Cart with core-charge parent/child lines
  - Sales invoice + credit note + return-against-invoice
  - Draft → Submit → Cancel on sales docs; naming series (e.g. `SINV-`)
  - Counter/POS scan → resolve part → cart (API)
  - Post sale/return journals via Phase 3 RPC
  - **Price lists** (retail / B2B / fleet) + customer default price list
  - **Customer-specific pricing** overrides (simple rules; not full ERPNext engine)
  - **Credit limit + credit hold** → blocks submit / emits `order_on_hold`
  - **Backorders / partial fulfill** — order lines open qty; fulfill when stock available
  - Soft: attachments + comment timeline on invoice if low-cost
- **Acceptance:**
  - [ ] Core charge split at cart insert
  - [ ] Return links to originating invoice/batch
  - [ ] Journals posted for sale/return/COGS
  - [ ] Price list resolves before cart total
  - [ ] Over-limit customer cannot submit without override role
  - [ ] Partial fulfill leaves open qty / backorder state
  - [ ] Emits domain events: `order_received`, `order_completed`, `order_on_hold`, …
- **Out of scope:** ContiPay capture (Phase 13), HTML5 QR, warranty UI (Phase 5b)
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 5b — Warranty / serial claims

- **Lane:** `@backend_agent` (+ management UI contracts)
- **Build:**
  - Claim against serial / invoice / batch
  - Status workflow (open → approved/rejected → closed)
  - Link to Quarantine / replacement issue / credit note
  - Emit `serial_moved` / return events as applicable
- **Acceptance:**
  - [ ] Claim requires serial or invoice linkage
  - [ ] Approved replacement does not skip Quarantine when return-to-stock
  - [ ] No fiscal/warranty-authority tax payloads
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 6 — Web storefront

- **Lane:** `@web_agent`
- **Skills:** `/ui-ux-pro-max` (explicit), `/parts-catalog-ingestion` (consume only)
- **Build:**
  - Next.js App Router scaffold in `apps/web`
  - Route groups: storefront, My Garage, B2B, (staff read-only as needed)
  - Catalog canvas (bounding boxes), 4-way search UI against API/index
  - Auth-facing pages; cart checkout to Phase 5 APIs (respect price list + credit hold)
- **Acceptance:**
  - [ ] `pnpm --filter web dev` runs
  - [ ] Design tokens from `@gtr/ui` (no Inter+purple default)
  - [ ] My Garage filters search
  - [ ] B2B sees correct price list
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

## Phase 8 — Procurement, suppliers, landed cost

- **Lanes:** `@backend_agent`, `@web_agent` (supplier portal pages)
- **Build:**
  - PO + GRN link to Phase 4 receipt; Draft → Submit → Cancel; naming series
  - **Material Request → PO** (from reorder / forecast hooks)
  - **Landed cost voucher** — allocate freight/duty/other into batch valuation (USD/ZiG)
  - Supplier portal read/reconcile; reorder signals hook (forecast later)
- **Acceptance:**
  - [ ] PO → receipt → stock/QR
  - [ ] Landed cost updates batch unit cost and inventory valuation journals
  - [ ] Material Request converts to PO without orphan lines
  - [ ] Supplier sees own POs only (RLS)
- **Gate:** `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`

---

## Phase 8b — RFQ, quotations, blanket POs

- **Lanes:** `@backend_agent`, `@web_agent`
- **Build:**
  - Request for Quotation → supplier quotations → compare → create PO
  - **Blanket / contract POs** with call-off releases against remaining qty/value
- **Acceptance:**
  - [ ] Quotation compare selects winner → PO
  - [ ] Blanket release cannot exceed remaining qty/value
  - [ ] RLS: suppliers see only their RFQ/quote rows
- **Gate:** `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`

---

## Phase 9 — HR / attendance / gross payroll

- **Lanes:** `@backend_agent`, `@management_app_agent`
- **Build:** Staff identity records, attendance, gross pay from hours/salary, manual deductions, payslip export
- **Acceptance:** No PAYE/NSSA/tax brackets; payslip = gross − manual lines only
- **Out of scope:** Biometric hardware (Phase 12 bridges)
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 10 — Logistics, pick/pack, Delivery Note, GPS

- **Lanes:** `@backend_agent`, `@management_app_agent`, `@hardware_mobile_agent` (GPS bridge contract)
- **Build:**
  - **Pick list / pack** against sales order or invoice (partial OK)
  - **Delivery Note** (DN) — ship qty; stock issue; link to invoice or bill-from-DN policy (pick one in child plan)
  - Delivery jobs, ~5s location ingest, Realtime for dispatcher/customer maps (MapLibre)
  - Emit `delivery_*` outbox events
- **Acceptance:**
  - [ ] Pick/pack cannot over-pick open qty
  - [ ] DN submit issues stock; cancel reverses
  - [ ] Trail stored with retention policy; role-gated reads
  - [ ] Emits `delivery_dispatched` / `delivery_completed` / `delivery_failed`
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 11 — Customer mobile (iOS + Android)

- **Lanes:** `@ios_agent`, `@android_agent` (parallel worktrees OK)
- **Build:** Customer apps consuming storefront APIs; My Garage; catalog; orders; order/backorder status
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
  - Management app: receiving, POS, warehouse, **cycle count**, pick/pack, dispatch screens
- **Acceptance:** Bridge-First enforced; no HTML5 QR; device smoke tests documented
- **Gate:** `/hardware-bridge-specialist` → `/security-reviewer` → `/verifier`

---

## Phase 13 — Payments allocation, ContiPay, SMS, forecasting

- **Lanes:** `@backend_agent`, `@web_agent`, `@management_app_agent` (recipient prefs)
- **Build:**
  - ContiPay (EcoCash, Visa 3DS, ZimSwitch), dual-currency settlement display
  - **Payment Entry** — allocate cash/bank/ContiPay/store-credit across one or many invoices (partial OK)
  - **Store credit** from refunds / overpayments; redeemable at POS/checkout
  - **Manager key-event SMS** (required): full catalog in `docs/decisions/2026-07-23-manager-sms-key-events.md`
  - Marketing SMS promos tied to My Garage (separate from ops alerts)
  - Forecasting → Material Request / requisition suggestions
- **Acceptance:**
  - [ ] Secrets in server only; no ZIMRA fiscal payloads on receipts
  - [ ] Payment allocation clears AR correctly (multi-invoice, multi-currency)
  - [ ] Store credit issue/redeem posts balanced journals
  - [ ] Domain events for order/payment/delivery fire once per occurrence
  - [ ] Only managers with that event enabled receive SMS
  - [ ] Failed sends retried/logged without duplicate spam on success path
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 14 — Offline sync & hardening

- **Lanes:** mobile lanes + `@backend_agent`
- **Build:** PowerSync (or chosen sync) for POS/dispatch/cycle count; CI (lint/test/migrate); Bugbot required on `main`; performance pass
- **Acceptance:** Offline sale queues and syncs; CI green on PR
- **Gate:** `/verifier` + Bugbot

---

## Phase 15 — Parity audit & polish

- **Invoke:** `/erpnext-feature-parity`, `/manager`, `/verifier`
- **Build:** Close checklist gaps that are in-scope (Phases 1–14 + gap register must-haves); docs; operator runbooks
- **Acceptance:** Checklist complete except explicit exclusions + Phase 16 extras; exclusion grep clean; gap register must-haves checked off or deferred by written decision

---

## Phase 16 — Distributor extras

- **Lanes:** `@backend_agent` + relevant UI lanes
- **Build (any order via child plans):**
  - **Bin / location** within warehouse (pick path hints)
  - **Kits / BOM sell** — kit SKU and/or explode components at sale
  - **Consignment stock** — supplier-owned or customer-held; no premature revenue
  - **Loyalty / points** (optional; after store credit)
  - Attachments + document timeline comments if not already shipped
- **Acceptance:** Each sub-feature has RLS, Draft/Submit where transactional, no tax/ZIMRA; child plan exit criteria met
- **Gate:** `/security-reviewer` → `/verifier` per sub-feature

---

## Global out of scope (never schedule)

- ZIMRA / FDMS / fiscal device / tax-authority integrations
- Payroll tax engines and statutory remittance forms
- Browser/HTML5 QR scanning
- Replacing Supabase with ERPNext/Frappe
- Full ERPNext Manufacturing MRP / Work Orders (kits in Phase 16 only)
- Frappe Website/CMS, Education, Healthcare, Loans modules

---

## Risks

| Risk | Mitigation |
|------|------------|
| Token burn on mega-prompts | Child plans + one lane; `/manager` enforces pipeline |
| Schema thrash | Decisions in `docs/decisions/`; Phase 1–3 stabilize finance/inventory first |
| Scope creep from gap register | Gaps scheduled into phases; Phase 16 holds “nice later”; no ad-hoc inserts mid-phase |
| Mobile before APIs | Phases 11–12 blocked on 5–6 API contracts |
| Scraper legal/ops issues | Pipeline etiquette rules; prefer licensed FAST sources |
| SMS without events | Phases 5/10/13 emit outbox events early; prefs + send in Phase 13 |
| Partial fulfill / DN vs invoice confusion | Child plan for Phase 10 must pick bill-from-DN vs DN-from-invoice once |

---

## Immediate handoff

1. **`/manager`:** open **Phase 2** (Auth, roles, typed client).
2. **`/planner`:** emit child plan `docs/plans/YYYY-MM-DD-phase2-auth-roles.md` if more detail needed.
3. **`@backend_agent`:** implement Phase 2.
4. Gates: `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`.

Do not start Phase 6 web UI until Phase 2 exit criteria pass (auth + types). Phase 3 and 4 may be sequenced tightly after Phase 2. When planning Phase 3+, pull acceptance lines from the **Distributor gap register** so naming series, period lock, and Draft/Submit land before sales volume.
