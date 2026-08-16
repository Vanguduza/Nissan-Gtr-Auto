# CoolMall ← web staff feature injection (audit + design)

- **Date:** 2026-08-16
- **Branch:** `cursor/management-oss-shell-rebuild-ad25`
- **Lane:** `@management_app_agent` (design); implementation later same lane + `@backend_agent` only if RPC gaps
- **Status:** Audit + design only (no full parity build this pass)
- **Parent / prior:** [2026-08-14-management-oss-shell-rebuild.md](./2026-08-14-management-oss-shell-rebuild.md) — this doc is the **fresh** inventory + injection map after My Account, payroll fund, master-stock RBAC, and advisor harden landed on web
- **Related:** [staff My Account](./2026-08-16-staff-my-account.md), [payroll fund + payslips](./2026-08-16-payroll-fund-payslip-schedule.md)

## Sources of truth (unchanged)

| Concern | Source of truth |
|---------|-----------------|
| Behavior / roles / RPCs | **`apps/web`** — `lib/staff-auth.ts` (`STAFF_NAV_TREE`, `pathAccessFor`, `filterNavTreeForModuleAccess`) + `lib/staff-*.ts` / `preferred-po.ts` |
| Look / chrome | **`vendor/coolmall-gtr/`** (CoolMallKotlin MIT fork) — nav density, Compose shells |
| Brand | **GTR only** — `packages/ui/brand-tokens.json` → CoolMall `designsystem` / `GtrColors` |
| Backend | **Supabase** via `gtradapter` Fake → Live (supabase-kt) |
| Legacy Android | **RPC discovery only** — never port Compose/IA from `android-management-legacy` |
| Counter POS | **`apps/android-pos`** — web `/staff/pos` redirects to hub; **not** a CoolMall till |

### Explicit non-goals this pass

- No full CoolMall parity implementation
- No POS cart/prep in CoolMall (deferred / stays `android-pos`)
- No inventree-app Flutter shell
- No ZIMRA / payroll tax / HTML5 QR

---

## Feature count (web staff)

| Bucket | Count | Notes |
|--------|------:|-------|
| `STAFF_NAV_TREE` destinations | **48** | 2 links (Hub, My Account) + 46 module leaves |
| Modules (hub tiles) | **10** | warehouse, finance, crm, logistics, fleet, hr, warranty, chat, analytics, procurement |
| Cross-cutting shell | **+6** | Login, change-password, forbidden, idle lock, `module_access` gates, Open POS → android-pos |
| Detail / sub-routes (not separate nav leaves) | **+5** | Petty-cash request/expense, finance transaction detail, PO detail, RFQ detail |
| **Total product surfaces to eventually inject** | **~59** | CoolMall excludes POS till; Open POS = deep-link only |

POS is **removed from web nav** (redirect in `next.config.ts`). CoolMall `StaffNavTree` may still list `pos` for documentation / future deep-link — keep **excluded** from destinations until explicitly requested.

---

## Full inventory — status: Shipped on web

Canonical tree: `apps/web/lib/staff-auth.ts` → `STAFF_NAV_TREE` (branch HEAD).  
Libs: `staff-account`, `staff-auth`, `staff-idle-lock-state`, `staff-warehouse`, `staff-bins`, `staff-consignment`, `staff-stores-insights`, `staff-finance`, `staff-credit`, `staff-product-pages`, `staff-kits`, `staff-logistics`, `staff-ops`, `staff-delivery-tracking`, `staff-fleet`, `staff-hr`, `staff-warranty`, `staff-analytics`, `preferred-po`, `procurement-approvals`.

### Auth & shell

| Web surface | Roles | Shipped behavior | Key RPCs / APIs | CoolMall today |
|-------------|-------|------------------|-----------------|----------------|
| Staff login | any staff | Emp# / email / phone → resolve → GoTrue password; non-enumerating errors | `resolve_staff_login_email`, GoTrue | Fake `signInWithStaffIdentifier` |
| Idle lock | any | 3 min; survives reload via `sessionStorage` | client + GoTrue reauth | Not yet |
| `/staff/change-password` | any | Force / voluntary password change | GoTrue `updateUser` | Account tab stub |
| `/staff` Hub | any | Role + `module_access` filtered tiles; sales no longer POS-home (till = android-pos) | `my_module_access`, `staff_roles` | Fake hub (POS excluded) |
| `/staff/forbidden` | — | RBAC deny | — | Later |
| `/staff/account` My Account | any | Edit phone/address/email; self photo → `employee-photos`; module chips; payslip history + PDF; ID + business card PDFs; sign-out | `get_my_staff_profile`, `update_my_staff_profile`, `list_my_payslip_history`, `payslip_render_payload`, Storage, `render-branded-doc` | Change-password only — **expand in Phase B** |
| Open POS | sales+ | Deep-link / handoff to **android-pos** (not web till) | session extras | Out of CoolMall scope |

### Warehouse

| Leaf | Roles | Shipped | Key RPCs | CoolMall today |
|------|-------|---------|----------|----------------|
| Overview | admin, warehouse | Thin hub | — | Via hub tile |
| Receive | admin, warehouse | Ad-hoc receipt post | `post_stock_receipt` | — |
| Master stock | admin, warehouse, **sales, finance** | Full-width filters (chassis/cat/sub/OEM); WH1/WH2; CSV | `list_master_stock` (+ needles) | Fake desk (OEM query); Live + filter parity Phase B |
| Transfers WH1→WH2 | admin, warehouse | Create / approve / reject | `create_stock_transfer`, `approve_*`, `reject_*` | — |
| Cycle count | admin, warehouse | Draft → lines → submit/approve/cancel | reconciliation RPCs | — |
| Bins | admin, warehouse | CRUD, assign level, pick-path hints | bin RPCs + `get_pick_path_hints` | — |
| Consignment | admin, warehouse | Draft / lines / submit / cancel | consignment RPCs | — |
| AI insights | admin, warehouse, finance | Suggestions only (never auto-PO) | Edge `stores-insights` | — |

### Finance (`/staff/finance?tab=…`) — admin, finance

| Tab | Shipped | Key RPCs |
|-----|---------|----------|
| Online sales (accounts) | CoA / register | `report_account_register` |
| Petty cash (+ request/expense subpages) | Periods + journals | petty-cash helpers, `open/close_account_period` |
| Cash | Cash sales register | same pattern |
| ContiPay / Paynow / EcoCash | PSP registers | period + journal helpers |
| ZiG rate | FX table | `list_zig_exchange_rates`, `set_zig_exchange_rate` |
| Manual journals | Draft / post / reverse | `create_journal_draft`, `post_journal`, `reverse_journal` |
| Requisitions | Create → approve → disburse | finance requisition RPCs |
| Payments | Allocate / post / cancel | payment entry RPCs |
| Reports & statements | P&L, BS, CF, TB, AR aging | `report_*`, `kpi_ar_aging_snapshot` |
| Bank recon | Match / clear | `clear_bank_matches` |
| Periods | Lock | `lock_accounting_period` |

### CRM

| Leaf | Roles | Shipped | Key RPCs |
|------|-------|---------|----------|
| Customer credit | admin, sales, finance | Limit / hold / marketing opt-in | `set_customer_credit`, `set_customer_marketing_opt_in` |
| Review moderation | admin, sales | Moderate | `moderate_customer_product_review` |
| Product pages | admin, sales, warehouse | Price / discount / images | `list/upsert_staff_product_page` |
| Kits | admin, sales, warehouse | Kit BOM CRUD | kit RPCs |

### Logistics / Fleet

| Leaf | Roles | Shipped | Key RPCs |
|------|-------|---------|----------|
| Jobs / pick | admin, warehouse, sales, dispatcher | Pick → DN → job; override assign when stuck | pick/DN/job RPCs |
| Sales prep | same | Online prep queue | ops notification RPCs |
| Live tracking | admin, warehouse, dispatcher | MapLibre desk (no browser GPS ingest) | job geo / optimize / track |
| Panic inbox | same | Ack panic | `panic_events` |
| Company fleet | admin, warehouse, dispatcher | Vehicle metadata CRUD (no geolocation) | fleet RPCs |

### HR

| Leaf | Roles | Shipped | Key RPCs |
|------|-------|---------|----------|
| HR desk | admin, hr | Clock, hours, open lines, manual deductions | attendance / payroll line RPCs |
| Payroll & payslips | admin, hr | **Fund** submitted lines from cash GL (Dr 5200/Cr 2150 → Dr 2150/Cr cash); schedules; branded PDF | `fund_payroll_lines`, `fund_payroll_run`, `export_payslip`, `upsert_hr_payslip_schedule`, Edge `process-payroll-schedules` |
| Organogram | admin, hr | Grades / roles / `module_access` | HR grade/role RPCs |
| Onboarding | admin, hr | Multi-stage; web file upload | onboarding RPCs + Edge auth create |

### Warranty / Chat / Analytics

| Leaf | Roles | Shipped | Key RPCs |
|------|-------|---------|----------|
| Warranty claims | admin, warehouse, sales | Open / approve / reject / close + quarantine return | warranty + `post_return_to_quarantine` |
| Customer chat | admin, sales, warehouse | Claim / close / message | chat thread RPCs |
| KPIs | admin, finance, sales | Dashboards + AI narrative | Edge `analytics-insights` |
| Report subscriptions | same | CRUD | `ai_report_subscriptions` |

### Procurement (`/procurement/**`) — preferred-supplier SoR; RFQ optional

| Leaf | Roles | Shipped | Key RPCs |
|------|-------|---------|----------|
| Overview | admin, warehouse, finance | PO progress | PO helpers |
| Preferred suppliers | same | Roster upsert / deactivate | preferred supplier RPCs |
| New PO (+ detail) | same | Manual create/submit (**never AI auto-PO**) | `create_purchase_order`, `submit_purchase_order` |
| GRN | same | Receive + OEM resolve + invoice | GRN RPCs |
| Approvals | admin, finance | Approve/reject | approval RPCs |
| Blankets | same | Blanket + call-off | blanket RPCs |
| RFQs / New RFQ | same | Optional spot-buy | RFQ RPCs |

### Platform (not a nav leaf; CoolMall must inherit)

| Item | Shipped on web / backend | CoolMall note |
|------|--------------------------|---------------|
| Advisor harden | `v_master_stock` security_invoker; function `search_path`; RLS initplan; grants | Live clients call same hardened RPCs/views |
| Multi-currency | Explicit `USD`\|`ZIG` + rate at post | All money desks |
| Ledger immutability | Reverse via contra | Finance / payroll fund |
| No payroll tax | Gross + manual deductions only | HR + My Account payslips |

---

## CoolMall injection map

**Pattern:** keep CoolMall Compose chrome + navigation; replace network/repos with `gtradapter`; bind ViewModels to web RPC contracts. New desks = new `feature:*` modules (or destinations under `feature:main`) — **not** legacy screen ports.

Existing CoolMall feature modules to **repurpose**:

| CoolMall module | GTR use |
|-----------------|---------|
| `feature:auth` / `feature:launch` | Staff identifier login + splash |
| `feature:main` | Hub tiles, bottom tabs (Hub · Warehouse · Account), master-stock, account |
| `feature:user` | Stretch toward My Account profile/photo (or keep under `main` Account) |
| `feature:goods` | CRM product pages / kits catalog chrome (later) |
| `feature:order` | Logistics job lists / prep queues (later) |
| `feature:cs` | Staff chat inbox rebind |
| `feature:market` / `feedback` | Low priority — hide or repurpose analytics/feedback |

**New GTR feature modules (add when Phase D hits):** `feature:warehouse` (deepen beyond master-stock), `feature:finance`, `feature:crm`, `feature:logistics`, `feature:fleet`, `feature:hr`, `feature:warranty`, `feature:analytics`, `feature:procurement`.

### Destination ↔ adapter matrix

| Web desk | CoolMall nav / feature | `gtradapter` contract (target) | Phase |
|----------|------------------------|--------------------------------|-------|
| Login + context | `feature:auth` | `GtrStaffAuthAdapter` **Live** | **B** |
| Change password | Account tab | `GtrPasswordAdapter` Live | **B** |
| Idle lock | app shell | Session + reauth (mirror web 3 min) | **B** |
| Hub + `module_access` | `feature:main` Hub | `GtrStaffHubAdapter` + `StaffNavTree` (POS excluded) | **B** (Fake→Live) |
| My Account | Account tab / `feature:user` | `GtrMyAccountAdapter` (new): profile, photo, payslips, cards | **B** |
| Master stock | Warehouse tab | `GtrWarehouseAdapter.listMasterStock` (+ chassis/cat filters) Live | **B** |
| Receive / transfers / cycle / bins / consignment / insights | `feature:warehouse` leaves | `GtrWarehouseAdapter` expand | **D1** |
| Finance tabs | `feature:finance` | `GtrFinanceAdapter` ← `staff-finance.ts` | **D2** |
| CRM leaves | `feature:crm` (+ goods chrome) | `GtrCrmAdapter` | **D3** |
| Logistics + prep + tracking + panic | `feature:logistics` / order | `GtrLogisticsAdapter` + MapLibre | **D4** |
| Fleet | `feature:fleet` | `GtrFleetAdapter` | **D4** |
| HR desk + payroll fund + organogram + onboarding | `feature:hr` | `GtrHrAdapter` (fund/schedule/payslip) | **D5** |
| Warranty | `feature:warranty` | `GtrWarrantyAdapter` | **D6** |
| Chat | `feature:cs` rebind | `GtrChatAdapter` | **D7** |
| Analytics + subscriptions | `feature:analytics` | `GtrAnalyticsAdapter` | **D8** |
| Procurement | `feature:procurement` | `GtrProcurementAdapter` ← `preferred-po.ts` | **D9** |
| Open POS | Hub deep-link only | Intent → `apps/android-pos` | Deferred (not CoolMall till) |

---

## Phased build order

### Phase A — Done (shell)

- Vendor CoolMall → `vendor/coolmall-gtr/`
- GTR colours on design system
- Fake auth / hub / change-password / master-stock Fake
- Bottom nav Hub · Warehouse · Account; POS excluded

### Phase B — Live Supabase inject (recommended next)

Order matters — auth first, then self-service, then first real desk:

1. **Auth Live** — supabase-kt GoTrue + `resolve_staff_login_email` + `loadStaffContext` (`profiles` / `staff_roles` / `my_module_access`) + `must_change_password` gate + **idle lock** parity  
2. **My Account Live** — mirror `/staff/account` (profile update, photo Storage, payslip list/PDF, ID/business card, sign-out)  
3. **Warehouse master-stock Live** — `list_master_stock` with web filter parity (chassis/category/OEM) + CSV optional later  

Still **no POS** in CoolMall.

### Phase C — POS in CoolMall

**Cancelled / deferred.** Counter till = `apps/android-pos`. CoolMall may later show an **Open POS** hub tile that launches the POS app with secure session extras — only when asked.

### Phase D — Remaining desks (no POS)

Recommended order (warehouse ops density first, then money, then people/ops satellites):

| Slice | Modules | Rationale |
|-------|---------|-----------|
| **D1** | Warehouse deepen (receive → transfers → cycle → bins → consignment → insights) | Completes WH tab already started; Bridge QR on receive/bins |
| **D2** | Finance (all tabs) | Highest money risk; after Live auth solid |
| **D3** | CRM (credit → reviews → product pages → kits) | Sales desk |
| **D4** | Logistics + Fleet | MapLibre tracking desk; GPS stays delivery app / Bridge |
| **D5** | HR (desk → **payroll fund/schedules** → organogram → onboarding) | Payslip/fund already on web; Bridge biometric-photo on onboarding |
| **D6** | Warranty | Quarantine returns |
| **D7** | Chat | Rebind `feature:cs` |
| **D8** | Analytics + subscriptions | Edge insights |
| **D9** | Procurement (preferred SoR → GRN → approvals → blankets → optional RFQ) | Bridge QR OEM on GRN |

Each slice: Fake smoke → Live RPC → cite web route in PR → `/security-reviewer` if authz → `/verifier`.

---

## Brand, Supabase inject, Bridge-First

### GTR colours only

- Tokens: primary `#C8102E`, steel `#12151C`, chalk `#F4F5F7`, silver, accent `#0B6E4F` — from `packages/ui/brand-tokens.json`
- CoolMall fashion palette must not remain as product chrome
- No CoolMall demo imagery as SoR branding

### Supabase inject

- Single SoR: hosted/project Supabase (GoTrue + RPC + PostgREST + Storage + selected Edge)
- `GtrAdapterModule`: Fake default for CI/smoke; Live behind build flavor / `local.properties`
- Prefer transplanting RPC client patterns from `apps/android-management/core/rpc` or shared Android RPC kit — do not invent a second wire format
- Advisor-hardened views/RPCs are the Live contract (`v_master_stock` invoker, etc.)

### Bridge-First

| Flow | Rule |
|------|------|
| Receive / GRN / bin labels | QR via `bridges/` — never ML Kit-in-Compose camera shortcuts that bypass Bridge |
| HR onboarding biometric photo | Bridge `biometric-photo` |
| Logistics live map | MapLibre desk OK; **device GPS ingest** stays delivery FGS / Bridge — not CoolMall browser-style geolocation |
| Panic / fleet | No HTML5 geolocation |
| POS scan | Not CoolMall; android-pos Bridge companion |
| Employee / bin QR verify | Bridge scan of `gtr://` / verify URLs — no fiscal QR |

---

## Risks & guards

| Risk | Mitigation |
|------|------------|
| Reverting to legacy UI port | This plan + `gtradapter` README ban |
| Building POS inside CoolMall | Phase C deferred; Open POS deep-link only |
| Stale CoolMall `StaffNavTree` vs web (POS / leaf roles) | Sync from `STAFF_NAV_TREE` each Phase B/D PR; unit test parity |
| Dual money paths | Always currency + rate; mirror web finance/payroll fund |
| Dirty tree noise | Do not stage screenshots, `tools/flaresolverr`, catalog-apk |

---

## Immediate next step (implementation, separate pass)

1. Phase **B1** Auth Live in `vendor/coolmall-gtr/gtradapter`  
2. Phase **B2** My Account adapter + Account UI parity with web  
3. Phase **B3** Master-stock Live + filter parity  
4. Then **D1** warehouse deepen  

Do not start D2–D9 until B Live auth is green on device.

## Out of scope

- Porting `android-management-legacy` UI  
- CoolMall / InvenTree backends as SoR  
- `apps/catalog-apk/**`  
- ZIMRA / payroll tax  
- CoolMall POS till chrome  
- Implementing full parity in the same PR as this plan  
