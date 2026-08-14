# Management Android — CoolMall shell customized from **web staff** behavior

- **Date:** 2026-08-14 (second correction, same day)
- **Lane:** `@management_app_agent`
- **Status:** Spec authoritative; Phase A vendor started (`vendor/coolmall-gtr/`)
- **Supersedes:** earlier same-day drafts that (1) “inspired shell → port legacy UI” and (2) treated inventree-app as a co-equal product shell without web as behavioral SoT

## Sources of truth (locked)

| Concern | Source of truth |
|---------|-----------------|
| **What the management app must do** (modules, roles, actions, RPCs) | **`apps/web` staff surfaces** — `lib/staff-auth.ts` (`STAFF_NAV_TREE` / `pathAccessFor`) + staff/procurement pages + `lib/staff-*.ts` |
| **How it should look / feel** | **Vendored CoolMallKotlin** (`vendor/coolmall-gtr/`, MIT) — navigation density, commerce chrome, Compose components |
| **Brand** | **GTR colours only** — `packages/ui/brand-tokens.json` / `packages/android-ui` (`GtrColors`) |
| **Backend** | **Supabase** (GoTrue + RPCs + PostgREST) injected via adapters replacing CoolMall’s API |
| **Legacy Android** (`android-management-legacy`) | **Optional RPC discovery aid only** — never UI/IA to port. Prefer web `lib/staff-*.ts` as the contract |

### Explicitly withdrawn mistakes

- CoolMall-“inspired” thin GTR scaffold, then **port Phase 2/3 from legacy** → recreates the discarded app.
- Treating inventree-app Flutter as a required co-shell without checking whether **web warehouse pages** already define the warehouse product.

## Success definition

A staff Android APK whose **module map and behaviors match web staff**, rendered through a **CoolMall fork** with GTR theme, talking only to Supabase adapters (Fake/Live). No legacy Compose screens copied. No CoolMall/InvenTree server as SoR. No ZIMRA. Bridge-First for QR/printer/biometric/GPS. No `catalog-apk` changes.

---

## Web staff module inventory (behavioral / product spec)

Canonical nav/roles: [`apps/web/lib/staff-auth.ts`](../../apps/web/lib/staff-auth.ts).  
Primary libs: `staff-pos`, `staff-warehouse`, `staff-bins`, `staff-consignment`, `staff-finance`, `staff-credit`, `staff-hr`, `staff-ops`, plus logistics/fleet/warranty/chat/analytics/procurement helpers.

### Auth & shell

| Web route | Roles | Behavior | Key APIs |
|-----------|-------|----------|----------|
| Login → staff | any staff | Emp#/email/phone → `resolve_staff_login_email` → GoTrue password; `must_change_password` gate; idle lock 3 min | `resolve_staff_login_email`, `profiles`, `staff_roles`, `my_module_access`, `has_staff_role` |
| `/staff` | any staff | Role + `module_access` filtered hub; sales-only prefers POS home | (context only) |
| `/staff/change-password` | any staff | Force password change | GoTrue `updateUser` |
| `/staff/forbidden` | — | RBAC deny | — |

### POS

| Web route | Roles | Behavior | Key APIs |
|-----------|-------|----------|----------|
| `/staff/pos?tab=cart` | admin, warehouse, sales | WH2 storefloor cart; OEM/catalog search; add lines; park/resume; USD/ZIG tenders; checkout; companion scan session (no browser camera) | `create_pos_cart`, `park_pos_cart`, `resume_pos_cart`, `add_cart_line`, `checkout_pos_cart` / `_with_tenders`, `create_pos_scan_session`, `revoke_pos_scan_session`, `search_catalog` / Meili edge |
| `/staff/pos?tab=prep` | same | Online prep queue + ops notifications | `list_online_prep_queue`, `list_staff_ops_notifications`, `mark_staff_ops_notification_read` |

### Warehouse

| Web route | Roles | Behavior | Key APIs |
|-----------|-------|----------|----------|
| `/staff/warehouse` | admin, warehouse | Thin hub (links only) | — |
| `/staff/warehouse/receive` | admin, warehouse | Ad-hoc receipt lines → post | `post_stock_receipt` |
| `/staff/warehouse/master-stock` | path: admin, warehouse | OEM qty WH1/WH2 read | `list_master_stock` |
| `/staff/warehouse/transfers` | admin, warehouse | WH1→WH2 create / approve / reject | `create_stock_transfer`, `approve_stock_transfer`, `reject_stock_transfer` |
| `/staff/warehouse/cycle-count` | admin, warehouse | Reconciliation draft → lines → submit/approve/cancel | `create_stock_reconciliation_draft`, `upsert_stock_reconciliation_lines`, `submit_stock_reconciliation`, `approve_stock_reconciliation`, `cancel_stock_reconciliation` |
| `/staff/warehouse/bins` | admin, warehouse | Bin CRUD, assign stock level, pick-path hints | `create_warehouse_bin`, `update_warehouse_bin`, `deactivate_warehouse_bin`, `set_stock_level_bin`, `get_pick_path_hints` |
| `/staff/warehouse/consignment` | admin, warehouse | Consignment draft/lines/submit/cancel | `create_consignment_entry_draft`, `add_consignment_entry_line`, `submit_consignment_entry`, `cancel_consignment_entry` |
| `/staff/warehouse/insights` | admin, warehouse, finance | AI restock suggestions only (no auto-PO) | Edge `stores-insights` |

### Finance (`/staff/finance?tab=…`) — roles: admin, finance

| Tab | Behavior | Key APIs |
|-----|----------|----------|
| accounts | CoA register + statement PDF | `report_account_register` |
| statements | Bank statement browse/export | `bank_statements` / lines |
| petty-cash, cash-sales, online-sales, contipay, paynow, ecocash | Per-register open/close period + journals | `open_account_period`, `close_account_period`, `create_journal_draft`, `post_journal`, petty-cash helpers |
| exchange-rate | ZiG rates | `list_zig_exchange_rates`, `set_zig_exchange_rate` |
| journals | Draft / post / reverse | `create_journal_draft`, `post_journal`, `reverse_journal` |
| requisitions | Create → submit → approve/reject/disburse | `create_finance_requisition`, `set_finance_requisition_lines`, `submit_finance_requisition`, … |
| payments | Create / allocate / post / cancel | `create_payment_entry`, `allocate_payment`, `post_payment_entry`, `cancel_payment_entry` |
| reports | P&L, BS, CF, TB, AR aging | `report_*`, `kpi_ar_aging_snapshot` |
| bank-recon | Match / clear | `clear_bank_matches` + tables |
| periods | Lock periods | `lock_accounting_period` |

### CRM

| Web route | Roles | Behavior | Key APIs |
|-----------|-------|----------|----------|
| `/staff/crm/credit` | admin, sales, finance | Credit limit/hold; marketing opt-in | `set_customer_credit`, `set_customer_marketing_opt_in` |
| `/staff/crm/reviews` | admin, sales | Moderate reviews | `moderate_customer_product_review` |
| `/staff/crm/product-pages` | admin, sales, warehouse | Price/discount/images | `list_staff_product_pages`, `upsert_staff_product_page`, image RPCs |
| `/staff/crm/kits` | admin, sales, warehouse | Kit CRUD + components | `create_kit_with_components`, `update_item_kit`, `add_kit_component`, `remove_kit_component` |

### Logistics / fleet

| Web route | Roles | Behavior | Key APIs |
|-----------|-------|----------|----------|
| `/staff/logistics` | admin, warehouse, sales, dispatcher | Pick list → DN → delivery job; override assign when stuck | `create_pick_list`, `confirm_pick_lines`, `create_delivery_note`, `submit_delivery_note`, `cancel_delivery_note`, `create_delivery_job`, `suggest_delivery_assignees`, `assign_delivery_job` |
| `/staff/logistics/prep` | same | Same prep queue as POS prep | ops RPCs above |
| `/staff/logistics/tracking` | admin, warehouse, dispatcher | MapLibre job tracking; assign; optimize stops (no browser GPS ingest) | `update_delivery_job_status`, `set_delivery_job_geo`, `optimize_driver_stops`, `get_delivery_track_point` |
| `/staff/logistics/panic` | admin, warehouse, dispatcher | Ack panic events | `panic_events` PostgREST |
| `/staff/fleet` | admin, warehouse, dispatcher | Vehicle metadata CRUD (no geolocation) | `list_fleet_vehicles`, `upsert_fleet_vehicle`, `set_fleet_vehicle_status` |

### HR / warranty / chat / analytics

| Web route | Roles | Behavior | Key APIs |
|-----------|-------|----------|----------|
| `/staff/hr` | admin, hr | Clock, hours, payroll lines, manual deductions, payslip (gross-only; no tax) | `current_employee_id`, `clock_attendance`, `attendance_hours_in_period`, `add_payroll_deduction`, `export_payslip` |
| `/staff/hr?tab=organogram` | admin, hr | Grades/roles/`module_access` | `create_hr_grade`, `create_hr_role`, `archive_hr_role` |
| `/staff/hr?tab=onboarding` | admin, hr | Multi-stage onboarding; web=file upload; Android=Bridge biometric-photo | `save_hr_onboarding_stage`, `complete_hr_onboarding`, Edge `hr-onboarding-create-auth` |
| `/staff/warranty` | admin, warehouse, sales | Claims + quarantine return | `open_warranty_claim`, approve/reject/close, `post_return_to_quarantine` |
| `/staff/chat` | admin, sales, warehouse | Claim/close threads, messages | `claim_chat_thread`, `close_chat_thread`, `post_chat_message`, … |
| `/staff/analytics` | admin, finance, sales | KPI dashboards + AI narrative | Edge `analytics-insights` |
| `/staff/analytics/subscriptions` | same | AI report subscription CRUD | `ai_report_subscriptions` |

### Procurement (`/procurement/**`)

| Web route | Roles | Behavior | Key APIs |
|-----------|-------|----------|----------|
| `/procurement` | admin, warehouse, finance | Preferred-supplier SoR overview + recent PO progress | `purchase_orders` helpers |
| `/procurement/suppliers` | same | Preferred roster upsert/deactivate | `upsert_preferred_supplier`, `deactivate_preferred_supplier` |
| `/procurement/orders/new`, `/orders/[id]` | same | Manual PO create/submit (never AI auto-PO) | `create_purchase_order`, `submit_purchase_order` |
| `/procurement/grn` | same | PO receive + OEM resolve + invoice attach | `resolve_stock_item_by_oem`, `create_goods_receipt`, `attach_goods_receipt_invoice`, `submit_goods_receipt` |
| `/procurement/approvals` | admin, finance | Approve/reject PO & material requests | `approve_purchase_order`, `reject_purchase_order`, … |
| `/procurement/blankets` | admin, warehouse, finance | Blanket + call-off | `create_blanket_purchase_order`, `create_blanket_release` |
| `/procurement/rfqs*` | same | Optional spot-buy RFQ (not preferred SoR) | `create_rfq`, `award_quotation_to_po`, … |

### Bridge-First notes from web (Android must honor)

- POS: no HTML5 camera — companion / Bridge QR.
- GRN / receive: QR→OEM via `bridges/`, not browser APIs.
- Fleet / tracking: no browser geolocation; GPS stays delivery app.
- HR onboarding biometric photo: Android Bridge `biometric-photo`.

---

## CoolMall → web module mapping

CoolMall is a **commerce** app (goods / cart / order / user / auth / cs). GTR management **repurposes** those surfaces and adds staff modules for desks web has that CoolMall lacks.

| Web staff capability | CoolMall surface to customize | Adapter work |
|----------------------|-------------------------------|--------------|
| Auth + change-password + idle lock | `feature:auth`, `feature:launch` | Replace CoolMall auth API with GoTrue + `resolve_staff_login_email`; Fake bypass for local smoke |
| Hub + role/`module_access` gates | `feature:main` home / nav | Drive destinations from web `STAFF_NAV_TREE` + `my_module_access` |
| POS cart | `feature:goods` + cart/order chrome | Bind `staff-pos` RPCs; WH2 dual-pane / Lock Task tablet |
| POS online prep | New destination under main/order | Ops notification RPCs |
| Warehouse receive / master / transfers / cycle / bins / consignment / insights | New `feature:warehouse*` (CoolMall has no WH desk) — **driven by web pages**, not inventree-app | Same RPCs as `lib/staff-warehouse*.ts` |
| Finance tabs | New `feature:finance` | `lib/staff-finance.ts` RPC set |
| CRM credit / reviews / product pages / kits | Stretch `feature:goods` + new CRM destinations | Web CRM RPCs |
| Logistics / fleet | New `feature:logistics` / fleet | Web logistics RPCs; MapLibre where web uses it; Bridge for device GPS elsewhere |
| HR / warranty / chat / analytics | New destinations | Web libs; Bridge biometric on onboarding |
| Procurement | New `feature:procurement` | Web preferred-PO / GRN / RFQ RPCs |
| Customer CS chat (CoolMall `feature:cs`) | Rebind to staff chat inbox | `claim_chat_thread` etc. |

**Injection pattern (required):** keep CoolMall navigation + Compose chrome; replace `core:network` / repositories with Supabase adapters; bind ViewModels to web RPC contracts.

**Forbidden:** copy Compose screens from `android-management-legacy/`.

---

## InvenTree decision

**Do not vendor inventree-app (Flutter) for Phase A–D.**

Web warehouse already specifies receive, master stock, WH1→WH2 transfers, cycle count, bins (+ pick hints), consignment, and AI insights. That is the warehouse product. Implement those flows as CoolMall Compose features with GTR theme + Supabase adapters.

Revisit inventree-app **only** if a later UX review finds web warehouse IA insufficient on tablet (e.g. scan→bin density). Even then prefer Compose recreation of that IA inside CoolMall (single Kotlin runtime + existing bridges) over embedding Flutter.

---

## Phased work

### Phase A — Vendor + GTR theme + auth/hub stub (this pass / next PR)

- [x] Correct plan: web = behavior, CoolMall = UX
- [x] Shallow-vendor CoolMallKotlin → `vendor/coolmall-gtr/` (MIT; upstream [Joker-x-dev/CoolMallKotlin](https://github.com/Joker-x-dev/CoolMallKotlin); demo `docs/images` stripped)
- [x] Apply GTR brand tokens to CoolMall `designsystem` / `ThemeColorOption`
- [x] Stub Supabase auth + module hub adapters (`vendor/coolmall-gtr/gtr-adapter/`)
- [ ] Wire stubs into CoolMall DI / nav; discard thin inspired scaffold as product UX
- [ ] Smoke `assembleDebug` on vendor tree when toolchain ready

### Phase B — Network swap

- Replace CoolMall HTTP with `RpcClient` / supabase-kt (transplant from `apps/android-management/core/rpc` or shared module)
- Fake smoke: sign-in bypass → hub filtered like web

### Phase C — POS parity with web cart/prep

- Map CoolMall goods/cart to `staff-pos` behaviors; Bridge companion scan

### Phase D+ — Remaining web modules

- Warehouse (web-driven) → Finance → CRM → Logistics/Fleet → HR/Warranty/Chat/Analytics → Procurement  
- Priority follows web hub usage; each module must cite the web route it mirrors

### Cancelled

- ~~Port UI from `android-management-legacy`~~
- ~~CoolMall-inspired scaffold as long-term product~~
- ~~Mandatory inventree-app Flutter shell~~

## Risks

| Risk | Mitigation |
|------|------------|
| ~70MB CoolMall tree bloating monorepo | Vendor under `vendor/`; strip nested `.git`; consider LFS for huge assets; next PR may slim `docs/`/`template/` |
| Reverting to legacy UI port | This plan + README ban |
| Dual Flutter+Kotlin | Rejected for warehouse unless explicit revisit |
| Missing web RPC on Android | Prefer `apps/web/lib/staff-*.ts` over legacy when wiring adapters |
| Catalog-apk / dirty tree | Do not stage those paths |

## Immediate next build step

1. Finish GTR colour override + stub auth/hub in `vendor/coolmall-gtr/`.
2. Wire Fake session + `my_module_access` / roles like web hub.
3. Next PR: Phase B network swap for POS cart RPCs only.

## Out of scope

- Porting legacy Android UI/IA  
- Flutter inventree-app embed (unless later decision)  
- CoolMall / InvenTree backends as SoR  
- Any `apps/catalog-apk/**` change  
- ZIMRA / payroll tax
