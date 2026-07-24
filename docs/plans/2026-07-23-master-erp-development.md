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
| **Customer receipt SMS + PDF (email/WhatsApp)** | 5 emit / 13 send | — (not ERPNext; local requirement) |

### Later distributor extras (Phase 16)

| Gap | Notes |
|-----|-------|
| Bin / location within warehouse | Shelf/bin for pick speed |
| Kits / BOM sell | Sell assembly as kit; explode or stock kit SKU |
| Consignment stock | Supplier-owned or customer-held stock |
| Loyalty / points | Optional; after store credit works |
| Attachments + doc timeline comments | Soft requirement from Phase 5 onward if cheap |

### AutoDoc shop adopt (customer storefront)

Source of truth: `docs/decisions/2026-07-23-autodoc-shop-features.md` (do not re-derive).

| Bucket | Items | Phases |
|--------|-------|--------|
| Adopt soon | Fitment browse + sticky garage vehicle; make/model/engine + VIN; OEM search + OE cross-refs; PDP photos/specs/OE/fitment; honest stock; brand/category facets; core-charge on PDP; USD\|ZiG; click & collect vs dispatch; order status/tracking; WhatsApp/ask-counter CTA | **UI in Phase 6**; live data **7** / **10** / **13** |
| Later | Alternatives strip; wishlist/back-in-stock; returns portal; garage service reminders; compare; kits; loyalty; reviews; customer apps | **UI under `/account` + `/kits`**; live **11** / **15** / **16** |
| Skip | UK plate lookup; marketplace; pan-EU logistics branding; huge DIY Club; browser QR; ZIMRA | Never |

---

## Phase map

| Phase | Name | Primary lane(s) | Depends on | Status |
|------:|------|-----------------|------------|--------|
| 0 | Orchestration & tooling | repo / docs | — | **Done** |
| 1 | Monorepo + schema foundation | `@backend_agent` | 0 | **Done** |
| 1b | Manager SMS event catalog + outbox | `@backend_agent` | 1 | **Done** (gateway later) |
| 2 | Auth, roles, typed client | `@backend_agent` | 1 | **Done** |
| 3 | Finance core + period/bank/naming | `@finance_agent`, `@backend_agent` | 2 | **Done** |
| 4 | Inventory ops (receipt, transfer, QR, UOM) | `@backend_agent`, `@hardware_mobile_agent` | 2 | **Done** |
| 4b | Stock reconciliation / cycle count | `@backend_agent`, `@management_app_agent` | 4 | **Done** (smokes PASS) |
| 5 | Sales / POS / cart / invoices / commercial | `@backend_agent`, `@management_app_agent` | 3, 4 | **Done** |
| 5b | Warranty / serial claims | `@backend_agent` | 4, 5 | **Done** (smokes PASS) |
| 6 | Web storefront + My Account (Garage) + AutoDoc IA | `@web_agent` | 2, 4, 5 (read APIs) | **Done** (+ live `/search` bind) |
| 7 | Data pipeline + search index | `@data_pipeline_agent` | 1, 6 (canvas can stub) | **Done** (PG FTS interim; Meili later) |
| 8 | Procurement + suppliers + landed cost | `@backend_agent`, `@web_agent` (portal) | 4, 5 | **Done** (smokes PASS; mutation guards `…61000`/`…63000`) |
| 8b | RFQ, quotations, blanket POs | `@backend_agent`, `@web_agent` | 8 | **Done (backend + thin web RFQ/quote)** — guards `…62000`/`…63000`; `/procurement` + `/supplier`; blanket web UI follow-on |
| 9 | HR / attendance / gross payroll | `@management_app_agent`, `@backend_agent` | 2 | **Done (backend)** — `…70000`/`…71000`; security+verifier PASS; management UI follow-on |
| 10 | Logistics / pick-pack / DN / GPS | `@management_app_agent`, `@hardware_mobile_agent` | 5 | **Done (backend)** — `…80000`/`…81000`; security+verifier PASS; bridge/UI follow-on |
| 11 | Customer mobile (iOS + Android) | `@ios_agent`, `@android_agent` | 6 APIs | **Thin screens Done** — Fake RPC; live SDK follow-on |
| 12 | Management Android app + bridges | `@management_app_agent`, `@hardware_mobile_agent` | 4, 4b, 5, 10 | **Thin HR/dispatch Done** — Fake RPC; bridge impl deferred |
| 13 | Payments, ContiPay **+ Paynow**, manager SMS, **customer receipts**, forecast | `@backend_agent`, `@web_agent` | 5, 6 | **Done (backend)** — `…90000`–`…100000`; security+verifier PASS; UI/real PSP keys follow-on |
| 14 | Offline sync (PowerSync), hardening, CI | cross-cutting | 11–12 | **Done (must-now)** — CI + PowerSync stubs + hardening docs; mobile SDK deferred to 11–12 |
| 15 | ERPNext parity audit + polish | `/manager`, `/verifier` | 1–14 | **Done** (audit) — `docs/parity/`, `docs/runbooks/`; plan `…phase15-parity-audit.md` |
| 16 | Distributor extras (bins, kits, consignment, loyalty) | `@backend_agent` + UI lanes | 15 or after 8/13 | **Done (backend)** — plan `…phase16-distributor-extras.md`; UI follow-on |

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

## Phase 3 — Finance core + period, bank, naming (done)

**Child plan:** `docs/plans/2026-07-23-phase3-finance-core.md`  
**Migrations:** `20260723210000_finance_core.sql`, `20260723211000_finance_report_auth.sql`

**Exit criteria:** Draft/post/reverse RPCs; TB/P&L/BS/CF; opening balances; period lock; naming series; bank recon model; no tax fields.

---

## Phase 4 — Inventory operations + QR + UOM (done)

**Child plan:** `docs/plans/2026-07-23-phase4-inventory-ops.md`  
**Migration:** `20260723220000_inventory_ops.sql`

**Exit criteria:** Receipt→batch→QR `gtr://part/…`; dual-auth transfer; UOM; serials; quarantine return path; bridge contracts only.

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

## Phase 5 — Sales, cart, invoices, commercial controls (done)

**Child plan:** `docs/plans/2026-07-23-phase5-sales-pos.md`  
**Migration:** `20260723230000_sales_pos.sql`

**Exit criteria:** Cart + core charges; invoice/CN; price lists/credit hold; QR→cart RPC; journals; customer_receipt_outbox enqueue; manager order/return events.

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
- **Status:** **Done** (scaffold + AutoDoc chrome; adopt-soon + adopt-later IA under My Account; live data = Phases 7/10/13/16)
- **Skills:** `/ui-ux-pro-max` (explicit), `/parts-catalog-ingestion` (consume only)
- **Domain:** Production host `https://nissangtrauto.co.zw` (`docs/decisions/2026-07-23-company-domain.md`); local `http://127.0.0.1:3000`
- **Design:** `docs/decisions/2026-07-23-storefront-autodoc-logo.md` — AutoDoc-inspired IA + official logo
- **Feature adopt list:** `docs/decisions/2026-07-23-autodoc-shop-features.md` — soon+later UI committed; skip plate/marketplace/DIY Club/browser QR/ZIMRA
- **Fonts:** Titillium Web (display) + Source Sans 3 (body); steel / red / silver tokens in `@gtr/ui`
- **Build:**
  - Next.js App Router scaffold in `apps/web` (`@gtr/web`)
  - Route groups: `(storefront)`, `(account)`, `(b2b)`, `(auth)`; `/garage` → `/account/garage`
  - Dense shop chrome, sticky garage bar, PLP facets, PDP, vehicle selector, kits stub
  - **My Account** hub: garage, orders/tracking, wishlist, returns, compare, loyalty, reviews, apps
  - Cart fulfillment choice (click & collect vs dispatch); WhatsApp CTA on PDP
  - Auth-facing pages; `NEXT_PUBLIC_SITE_URL` for absolute links
- **Acceptance:**
  - [x] `pnpm --filter @gtr/web dev` runs
  - [x] Design tokens from `@gtr/ui` (Titillium + Source Sans 3; steel / silver / `#C8102E`)
  - [x] My Garage under My Account (`/account/garage`)
  - [x] Adopt-soon + adopt-later routes scaffolded (stub data until Phase 7+)
  - [x] B2B documents price list / USD|ZiG
  - [x] No browser QR libraries
  - [x] Prod config documents `nissangtrauto.co.zw`
- **Paths:** `apps/web/`, `packages/ui/`, `pnpm-workspace` include `apps/*`
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 7 — Data pipeline + search

- **Status:** **Done** (first slice — pipeline + PG FTS RPC; Meili deferred; storefront bind follow-on)
- **Child plan:** [`docs/plans/2026-07-24-phase7-data-pipeline-search.md`](./2026-07-24-phase7-data-pipeline-search.md)
- **Lane:** `@data_pipeline_agent` (+ thin `@backend_agent` for Storage/FTS/OE schema if needed)
- **Skills:** `/nissan-fast-parser`, `/parts-catalog-ingestion`
- **Shop adopt (Phase 7 slice):** fitment-aware browse data, VIN/make/model/engine search, OEM + OE cross-refs, PDP specs/fitment payloads, brand/category facets — see `docs/decisions/2026-07-23-autodoc-shop-features.md`
- **Search decision (child):** PG FTS interim; Meilisearch deferred — ADR `docs/decisions/2026-07-24-search-index-interim-pg-fts.md` during implement
- **Build:**
  - Python project under `data-pipeline/`
  - FAST parse → fitment JSON; diagram scrape → bbox JSON
  - Import jobs to Supabase Storage + tables
  - Meilisearch (or PG FTS interim) + search API
- **Acceptance:**
  - [x] Schema-validated pipeline output
  - [x] Idempotent import
  - [x] Search supports part / VIN / model / PNC paths (PG FTS interim)
- **Out of scope:** Live production scrapers without rate limits/robots respect; AutoDoc skip list (plate lookup, marketplace, browser QR, ZIMRA)
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
  - [x] Quotation compare selects winner → PO
  - [x] Blanket release cannot exceed remaining qty/value
  - [x] RLS: suppliers see only their RFQ/quote rows
- **Gate:** `/supabase-rls-auditor` → `/security-reviewer` → `/verifier` — **backend gate PASS** (2026-07-24); direct-table mutation guards still **pending** (same track as Phase 8 `…61000`)

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
  - [x] Pick/pack cannot over-pick open qty
  - [x] DN submit issues stock; cancel reverses
  - [x] Trail stored with retention policy; role-gated reads
  - [x] Emits `delivery_dispatched` / `delivery_completed` / `delivery_failed`
- **Gate:** `/security-reviewer` → `/verifier` (UI + GPS bridge still follow-on)

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

## Phase 13 — Payments, ContiPay + Paynow, manager SMS, customer receipts, forecasting

- **Lanes:** `@backend_agent`, `@web_agent`, `@management_app_agent` (recipient prefs)
- **Build:**
  - ContiPay (EcoCash, Visa 3DS, ZimSwitch) **and Paynow** (ZW mobile money / card), dual-currency settlement display — decision `docs/decisions/2026-07-24-paynow-payment-rail.md`
  - **Payment Entry** — allocate cash/bank/ContiPay/Paynow/store-credit across one or many invoices (partial OK)
  - **Store credit** from refunds / overpayments; redeemable at POS/checkout
  - **Manager key-event SMS** (required): full catalog in `docs/decisions/2026-07-23-manager-sms-key-events.md`
  - **Customer transaction receipts** (required): `docs/decisions/2026-07-23-customer-receipt-delivery.md`
    - SMS: transaction **summary** + **PDF download link at bottom**
    - Same PDF via **email and/or WhatsApp**
    - Server-side PDF (no ZIMRA/fiscal); signed Storage URL; outbox retry; idempotent per doc+channel
  - Marketing SMS promos tied to My Garage (separate from ops alerts and receipts)
  - Forecasting → Material Request / requisition suggestions
- **Acceptance:**
  - [ ] Secrets in server only; no ZIMRA fiscal payloads on receipts
  - [ ] Payment allocation clears AR correctly (multi-invoice, multi-currency)
  - [ ] Store credit issue/redeem posts balanced journals
  - [ ] Domain events for order/payment/delivery fire once per occurrence
  - [ ] Only managers with that event enabled receive ops SMS
  - [ ] Customer SMS summary includes PDF link; email/WhatsApp deliver PDF when contact present
  - [ ] Failed sends retried/logged without duplicate spam on success path
- **Gate:** `/security-reviewer` → `/verifier`

---

## Phase 14 — Offline sync & hardening

- **Lanes:** mobile lanes + `@backend_agent`
- **Build:** PowerSync (or chosen sync) for POS/dispatch/cycle count; CI (lint/test/migrate); Bugbot required on `main`; performance pass
- **Acceptance:** Offline sale queues and syncs; CI green on PR
- **Split Done:** must-now (CI + stubs + hardening) landed — see [`2026-07-24-phase14-offline-ci-hardening.md`](./2026-07-24-phase14-offline-ci-hardening.md); E2E offline sale + device performance wait on Phases 11–12
- **Gate:** `/verifier` + Bugbot

---

## Phase 15 — Parity audit & polish

- **Status:** **Done** (docs-first audit 2026-07-24)
- **Invoke:** `/erpnext-feature-parity`, `/manager`, `/verifier`
- **Build:** Close checklist gaps that are in-scope (Phases 1–14 + gap register must-haves); docs; operator runbooks
- **Acceptance:** Checklist complete except explicit exclusions + Phase 16 extras; exclusion grep clean; gap register must-haves checked off or deferred by written decision
- **Artifacts:** `docs/parity/erpnext-checklist.md`, `docs/parity/exclusion-evidence.md`, `docs/parity/polish-backlog.md`, `docs/runbooks/README.md`

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
| Customer receipts forgotten | Phase 5 enqueues; Phase 13 sends SMS+PDF link and email/WhatsApp PDF (`customer-receipt-delivery` decision) |
| Partial fulfill / DN vs invoice confusion | Child plan for Phase 10 must pick bill-from-DN vs DN-from-invoice once |

---

## Immediate handoff

**Master plan status:** Backend + polish + thin UI + **live mobile Supabase clients** Done. DB through `20260724130000`.

**Done this wave**
1. Android customer + management: `SupabaseRpcClient` (supabase-kt BOM 3.1.1) + `RpcClientFactory`; Fake when URL/anon empty or `rpc.forceFake=true`
2. iOS: `LiveStorefrontApi` via URLSession PostgREST/edge (Windows-friendly); Fake when unset or `STOREFRONT_FORCE_FAKE`
3. Verifier PASS — exclusions clean; no PSP crypto/secrets; Fake vs Live documented

**Still follow-on**
1. Customer/staff **sign-in UI** on mobile to supply user JWT (AuthZ needs session beyond anon)
2. Real ContiPay/Paynow HMAC + merchant secrets (env only)
3. `assembleDebug` / Xcode on hosts with JDK 17+ / macOS
4. PDP photos / Meili / PowerSync / native bridge impls

**In progress:** None.

**Blockers / notes**
- No commits (user did not request).
- Live mode needs `SUPABASE_URL` + anon in `local.properties` / env; customer RPCs need user JWT after login.

Commands: `pnpm dev:web`; see `apps/*/README.md` for Fake vs Live.