# Nissan GTR Auto — orchestration inventory dump
**Root:** `C:\Users\j\Desktop\nissan gtr`

---

## 1. Authority docs

| Path | Purpose |
| --- | --- |
| `C:\Users\j\Desktop\nissan gtr\AGENTS.md` | Agent entrypoint: hard exclusions, DIAL engineering adoption, lanes, PR checklist |
| `C:\Users\j\Desktop\nissan gtr\docs\DIAL_SPARE_ADOPTION_PLAN.md` | Dial-a-Spare → GTR adoption plan (principal ≠ agency); phases E1–E6, defer list |
| `C:\Users\j\Desktop\nissan gtr\docs\PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md` | Founder 2026-08-12: preferred suppliers, dual WH, POS Dial UX, security DoD |
| `C:\Users\j\Desktop\nissan gtr\docs\decisions\2026-08-12-principal-vs-dial-agency.md` | ADR: adopt DIAL engineering patterns; reject agency marketplace locks |
| `C:\Users\j\Desktop\nissan gtr\docs\HARDENING.md` | Phase-14 security runbook + §7 DIAL-aligned AppSec baseline |
| `C:\Users\j\Desktop\nissan gtr\docs\plans\2026-08-12-pos-dial-ux-redesign.md` | POS Dial UX redesign notes (CoolMall/Nimara patterns; web+Android) |
| `C:\Users\j\Desktop\nissan gtr\CHANGELOG.md` | Living changelog (2026-08-12 landing) |
| `C:\Users\j\Desktop\nissan gtr\ENHANCEMENTS.md` | Epic/status tracker (E1…E6, E-Proc/WH/POS/Sec, deferred) |
| `C:\Users\j\Desktop\nissan gtr\README.md` | Architecture overview, surfaces, hard exclusions, AppSec pointers |
| `C:\Users\j\Desktop\nissan gtr\BUGS.md` | Known gaps (money NUMERIC, Google tiles, Brevo fallback, PowerSync, OSRM) |
| `C:\Users\j\Desktop\nissan gtr\docs\plans\2026-07-23-master-erp-development.md` | Master ERP roadmap / process SoR (phases, standing laws) |
| `C:\Users\j\Desktop\nissan gtr\docs\AGENT_TEAM.md` | On-demand specialist playbook (`/manager`→`/planner`→lane→security→verifier) |
| `C:\Users\j\Desktop\nissan gtr\rufler.yaml` | Ruflo path-routed agent lanes + routing map |
| `C:\Users\j\Desktop\nissan gtr\docs\TOOLING_SETUP.md` | External tooling install status (claude-mem, ui-ux-pro-max, MCP, satellites) |
| Related: `docs\CURSOR_ERP_SAAS_SETUP_GUIDE.md`, `docs\decisions\README.md`, `docs\plans\README.md` | Setup playbook / decisions index / plans index |

---

## 2. Migrations `20260812*`

### `20260812010000_relationship_procurement_dual_wh.sql`
- **Columns:** `warehouses.role_code` (WH1/WH2/QUARANTINE/OTHER); `suppliers.is_preferred`, `relationship_notes`, `address_text`, `tax_id`, `payment_terms`, `product_categories`; `purchase_orders.funds_released_at`, `progress_step`
- **Tables:** `supplier_preferred_skus` (preferred SKU roster); `procurement_fund_releases` (auto fund release on PO approve)
- **Seed/ops:** ensure WH1/WH2 warehouse rows
- **View:** `v_master_stock` (qty_total / qty_wh1 / qty_wh2)
- **Functions/RPCs:** `_po_quoted_total`, `approve_purchase_order` (approve + insert fund release + domain events), `upsert_preferred_supplier`, `deactivate_preferred_supplier`, `list_master_stock`, `resolve_stock_item_by_oem`
- **Triggers:** none in this file
- **Buckets:** none

### `20260812020000_grn_invoice_dual_write.sql`
- **Columns:** `goods_receipts.supplier_invoice_path`, `supplier_invoice_uploaded_at`; `purchase_order_lines.unit_price_minor`; `procurement_fund_releases.amount_minor`
- **Bucket:** `procurement-invoices` (+ RLS policy `procurement_invoices_staff_rw`)
- **Functions:** `attach_goods_receipt_invoice`, `_po_set_progress_on_submit`
- **Trigger:** `purchase_orders_progress_submit` → `_po_set_progress_on_submit`

### `20260812030000_amount_minor_dual_write.sql`
- **Functions:** `_major_to_minor`, `_po_line_dual_write_minor`, `approve_purchase_order` (rewritten with `amount_minor` dual-write)
- **Trigger:** `purchase_order_lines_dual_write_minor` → `_po_line_dual_write_minor`
- **Backfills:** PO line `unit_price_minor`; fund release `amount_minor`

---

## 3. Packages

### `@gtr/procurement` — `packages\procurement\`
- **Entry:** `package.json` exports `"."` → `src/index.ts`
- **Exports:** `PROCUREMENT_STEP_LABELS`, `PROCUREMENT_TRACKER_STEPS`, `WAREHOUSE_ROLE`, `completedTrackerCount`, `resolveProcurementProgress`, `trackerIndex`, types `GrnFastLine`, `MasterStockRow`, `PreferredSupplierInput`, `ProcurementProgressStep`, `WarehouseRoleCode`
- **Key fns:** `resolveProcurementProgress`, `trackerIndex`, `completedTrackerCount` (`src/types.ts`)
- **Tests:** `packages\procurement\src\types.test.ts`

### `@gtr/payments` — `packages\payments\`
- **Entry:** `src/index.ts`
- **Exports:** `PspRegistry`, `buildCheckoutDisplay`, `createStubPspAdapter`, `defaultPspRegistry`, types `CheckoutDisplay`, `PspAdapter`, `PspInitiateRequest`, `PspInitiateResult`, `PspMethod`, `PspWebhookResult`
- **Key fns:** `PspRegistry`, `createStubPspAdapter`, `defaultPspRegistry`, `buildCheckoutDisplay` (`src/psp.ts`)
- **Tests:** `packages\payments\src\psp.test.ts`

### `@gtr/delivery` — `packages\delivery\`
- **Entry:** `src/index.ts`
- **Exports:** `DELIVERY_DISPATCH_WORKFLOW`, `preferRoutingProvider`, types (offer/dispatch/route); `fetchOsrmRoute`, `parseOsrmRouteJson`; `applyOfferDecision`, `selectNextCourierOffer`; `runDeliveryDispatchCycle`; `candidatesFromSuggestRows`, `createSqlDispatchActivities`, `runSqlDeliveryDispatchCycle`, `trySqlAutoAssign`
- **Key modules:** `osrm.ts`, `dispatch.ts`, `temporal.ts`, `assign-bridge.ts`, `types.ts`
- **Tests:** `assign-bridge.test.ts`, `dispatch.test.ts`, `delivery.test.ts`

### `@gtr/shared` money — `packages\shared\`
- **Entry:** `src/index.ts` (re-exports money)
- **Source:** `src/money.ts`
- **Key exports:** `CurrencyCode`, `Money`, `MoneyMinor`, `MINOR_PER_MAJOR`, `assertCurrency`, `assertAmountMinor`, `toAmountMinor`, `fromAmountMinor`, `moneyToMinor`, `minorToMoney`, `majorToMinorNumber`, `dualWriteMoney`, `moneyMinorToJson`, `moneyMinorFromJson`
- **Tests:** `packages\shared\src\money.test.ts` (+ unrelated `catalog-*.test.ts`)

### `@gtr/supabase-client` catalog-search — `packages\supabase-client\`
- **Entry:** `src/index.ts` re-exports from `src/catalog-search.ts`
- **Key exports:** `CATALOG_SEARCH_MEILI_FN` (`catalog-search-meili`), `SEARCH_CATALOG_RPC` (`search_catalog`), `searchCatalog`, `searchCatalogMeili`, `searchCatalogFts`, `searchCatalogRpcArgs`, `isSearchMode`, `normalizeSearchMode`, `partHref`, (+ loyalty/return helpers colocated)
- **Tests:** package script `"no tests yet"` — no catalog-search test file

---

## 4. Web routes / pages (apps/web)

| Concern | Route | Page path | Supporting UI/lib |
| --- | --- | --- | --- |
| Preferred suppliers | `/procurement/suppliers` | `apps\web\app\(b2b)\procurement\suppliers\page.tsx` | `components\preferred-suppliers-panel.tsx` |
| Manual PO | `/procurement/orders/new` | `apps\web\app\(b2b)\procurement\orders\new\page.tsx` | `lib\preferred-po.ts` |
| GRN | `/procurement/grn` | `apps\web\app\(b2b)\procurement\grn\page.tsx` | `components\goods-receipt-panel.tsx` |
| Tracker / hub | `/procurement` | `apps\web\app\(b2b)\procurement\page.tsx` | `procurement-progress-tracker.tsx`, `procurement-nav.tsx`, `procurement-tracker.module.css` |
| Approvals (fund release) | `/procurement/approvals` | `apps\web\app\(b2b)\procurement\approvals\page.tsx` | `staff-procurement-approvals-panel.tsx`, `lib\procurement-approvals.ts` |
| Master stock | `/staff/warehouse/master-stock` | `apps\web\app\(staff)\staff\warehouse\master-stock\page.tsx` | `components\master-stock-panel.tsx` |
| POS | `/staff/pos` | `apps\web\app\(staff)\staff\pos\page.tsx` | `staff-pos-shell.tsx`, `staff-pos-panel.tsx`, `lib\staff-pos.ts`, `lib\staff-pos-realtime.ts` |
| Cart | `/cart` | `apps\web\app\(storefront)\cart\page.tsx` | `components\cart-checkout.tsx` (`buildCheckoutDisplay`) |
| Checkout return/cancel | `/checkout/return`, `/checkout/cancel` | `app\(storefront)\checkout\return\page.tsx`, `...\cancel\page.tsx` | — |
| Related receive | `/staff/warehouse/receive` | `app\(staff)\staff\warehouse\receive\page.tsx` | linked from procurement hub |
| RFQ (optional spot-buy) | `/procurement/rfqs*` | `app\(b2b)\procurement\rfqs\**` | not preferred-supplier SoR |
| Public delivery track | `/track/[token]` | `app\track\[token]\page.tsx` | — |

---

## 5. Android

| Item | Path |
| --- | --- |
| MapLibreJobMap | `apps\android-delivery\feature\tracking\src\main\java\co\zw\nissangtr\delivery\tracking\MapLibreJobMap.kt` |
| JobDetailScreen | `apps\android-delivery\feature\jobs\src\main\java\co\zw\nissangtr\delivery\jobs\JobsScreen.kt` (`JobDetailScreen` + MapLibre wiring) |
| MainActivity → JobDetailScreen | `apps\android-delivery\app\src\main\java\co\zw\nissangtr\delivery\MainActivity.kt` |
| PosScreen | `apps\android-management\feature\pos\src\main\java\co\zw\nissangtr\management\pos\PosScreen.kt` |
| PosScreen host | `apps\android-management\app\src\main\java\co\zw\nissangtr\management\MainActivity.kt` |
| GtrTheme | `packages\android-ui\src\main\java\co\zw\nissangtr\ui\theme\GtrTheme.kt` (+ `GtrColors.kt`, `GtrTypography.kt`, `GtrShapes.kt`, `GtrChrome.kt`) |
| Delivery modules | `feature\jobs`, `feature\tracking`, `feature\pod`, `feature\auth`, `core\rpc` under `apps\android-delivery\` |
| Delivery RpcNames | `apps\android-delivery\core\rpc\src\main\java\co\zw\nissangtr\delivery\rpc\RpcNames.kt` |
| Management procurement | `apps\android-management\feature\procurement\...\ProcurementModule.kt` (web-first preferred PO) |

---

## 6. Edge functions

### Dispatch / payments / AI (relevant)
| Function | Path | Auth |
| --- | --- | --- |
| `delivery-dispatch-cycle` | `supabase\functions\delivery-dispatch-cycle\index.ts` | `assertWorkerSecret` |
| `paynow-initiate` / `paynow-webhook` | `supabase\functions\paynow-*\index.ts` | payment webhook HMAC / initiate secrets |
| `contipay-initiate` / `contipay-webhook` | `supabase\functions\contipay-*\index.ts` | ContiPay HMAC |
| `ecocash-initiate` / `ecocash-webhook` | `supabase\functions\ecocash-*\index.ts` | EcoCash |
| `process-ai-reports` | `supabase\functions\process-ai-reports\index.ts` | `assertWorkerSecret` |
| `process-crm-promos` | `supabase\functions\process-crm-promos\index.ts` | `assertWorkerSecret` |
| `analytics-insights` / `demand-forecast` / `stores-insights` | respective dirs | demand-forecast uses worker secret |
| `catalog-search-meili` | `supabase\functions\catalog-search-meili\index.ts` | JWT + Meili server-side |

### `assertWorkerSecret` pattern
- **Impl:** `supabase\functions\_shared\worker_auth.ts`
- **Header:** `x-worker-secret` vs env `WORKER_SHARED_SECRET`
- **Fail-closed:** 401 if secret unset (unless `WORKER_ALLOW_UNVERIFIED_LOCAL=1`)
- **Consumers:** `delivery-dispatch-cycle`, `process-sms-outbox`, `process-customer-receipts`, `demand-forecast`, `process-ai-reports`, `process-crm-promos`, `chat-notify-on-message`
- **Docs:** `supabase\functions\README.md`

`delivery-dispatch-cycle` RPCs: `suggest_delivery_assignees` → `assign_delivery_job`, or mode `sql_auto` → `_try_auto_assign_delivery_job`.

---

## 7. Security CI

| Artifact | Path |
| --- | --- |
| Root Semgrep config | `C:\Users\j\Desktop\nissan gtr\semgrep.yml` |
| Custom rules | `semgrep\rules\no-body-identity.yaml`, `no-client-secrets.yaml`, `no-raw-sql-concat.yaml`, `no-unofficial-whatsapp.yaml`, `webhook-signature.yaml` |
| Semgrep workflow | `.github\workflows\semgrep.yml` — jobs `semgrep-gtr` (hard-fail), `semgrep-community` (advisory) |
| Checkov | `.github\workflows\checkov.yml` — hard-fail HIGH+ |
| Main CI | `.github\workflows\ci.yml` — quality / exclusions / db-smoke |
| Promptfoo | `promptfoo\promptfoo.config.yaml`, `promptfoo\README.md` |
| Hardening checklist | `docs\HARDENING.md` §7 |

---

## 8. RPC / DB function names (PO / GRN / approve / fund / invoice / master / dispatch)

| Use | Name |
| --- | --- |
| Create/submit PO | `create_purchase_order`, `submit_purchase_order` |
| Cancel/reject PO | `cancel_purchase_order`, `reject_purchase_order` |
| Approve + fund release | `approve_purchase_order` → writes `procurement_fund_releases` |
| Helpers | `_po_quoted_total`, `_major_to_minor`, `_po_line_dual_write_minor`, `_po_set_progress_on_submit` |
| Preferred suppliers | `upsert_preferred_supplier`, `deactivate_preferred_supplier` |
| Master stock | `list_master_stock`, view `v_master_stock`, `resolve_stock_item_by_oem` |
| GRN | `create_goods_receipt`, `submit_goods_receipt`, `attach_goods_receipt_invoice` |
| MR approvals (same panel) | `approve_material_request`, `reject_material_request` |
| Dispatch assign | `suggest_delivery_assignees`, `assign_delivery_job`, `_try_auto_assign_delivery_job` |
| Events (from approve) | `emit_domain_event` (`po_approved`, `procurement_funds_released`) |
| Optional RFQ path (not preferred SoR) | `create_rfq`, `award_quotation_to_po` |

---

## 9. Explicit deferred items

**From `DIAL_SPARE_ADOPTION_PLAN` §6 Defer:**
- Full DB bigint money cutover
- Full MapLibre Compose UI (after OSRM default) — MapLibreJobMap landed; broader Compose UI may remain
- ZIMRA/FDMS (exclusion until counsel)
- Chatwoot / Metabase / PowerSync live SDK
- Marketplace agency features

**From `PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN` §5 optional follow-ups:**
- Full Temporal worker binary
- Android native preferred-PO screen
- Regenerate `database.types.ts` from live DB after migrate
- Promptfoo CI job with real model provider

**From `ENHANCEMENTS.md`:**
- Chatwoot / Metabase / PowerSync live — Deferred
- Android native preferred-supplier PO screen — Deferred (web SoR)
- E2a full Temporal binary — later
- E4 Promptfoo real provider in CI — later
- E5 broader ledger `amount_minor` cutover — later

**From `docs\plans\2026-08-12-pos-dial-ux-redesign.md` Next:**
- Larger touch targets / dual-pane tablet QA; WH2 stock source for POS picks
- QA checkboxes still open (desktop 1280 / mobile 390 / tablet landscape)

**From `BUGS.md` (open architecture gaps):**
- B-MONEY-1 NUMERIC/JS number still on payable paths
- B-MAP-1 Google Maps tiles still on some Android surfaces
- B-PS-1 PowerSync rules without mobile SDK wiring
- B-OSRM-1 OSRM compose commented until map data

---

## 10. Hard exclusions (verbatim)

### From `AGENTS.md` — Hard Exclusions (Standing Rules)
- **NO ZIMRA** — no FDMS, fiscalisation, mTLS fiscal devices, tax-authority payloads.
- **NO payroll tax** — no PAYE, NSSA, statutory remittance forms. Gross pay + manual deductions only.
- **NO RFQ-win as supplier SoR** — preferred supplier roster authorizes replenishment POs; RFQ is optional spot-buy only (`docs/PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md`).
- **AI never writes payable amounts / never auto-creates POs** — forecast suggestions only.

### From ADR `docs\decisions\2026-08-12-principal-vs-dial-agency.md` — Decision
1. Adopt Dial-a-Spare **engineering patterns** (integer money, MapLibre+OSRM delivery SoR, Temporal dispatch contracts, Resend/Brevo split, AI never writes payable amounts, D-57 FX display habits).
2. **Do not** force agency marketplace product locks (D-49 informal→B2B hide, “Sold by {Supplier}”, SUPPLIER_COOP, Mercur multi-vendor cart as primary UX, DIAL_OWNED semantics).
3. Keep Nissan ZIMRA/FDMS **hard exclusion** until an explicit counsel ticket reopens it (differs from DIAL D-40a/D-59).
4. Pattern-copy into `@gtr/*` packages — no fourth money/delivery stack; do not submodule unfinished DIAL packages.

### Also in `README.md` Hard Exclusions (permanent)
- **No ZIMRA integration** — no FDMS, fiscalisation QR codes, mTLS fiscal device logic, or tax-authority payloads in checkout/invoicing/receipts.
- **No payroll tax computation** — no PAYE, NSSA POBS/APWCS/ZIMDEF, statutory remittance forms (P4/P4A), or tax brackets. Payroll computes gross pay from attendance/hours/salary only, with manual/custom deduction line items.

---

## Quick cross-links for orchestrator
- **Roadmap SoR:** `docs\plans\2026-07-23-master-erp-development.md`
- **Adoption SoR:** `docs\DIAL_SPARE_ADOPTION_PLAN.md` + ADR principal-vs-dial
- **This landing SoR:** `docs\PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md`
- **Lanes:** `rufler.yaml` + `AGENTS.md` Agent Lanes table
- **Money habit:** `@gtr/shared` amountMinor + migrations `2026081202*` / `2026081203*`