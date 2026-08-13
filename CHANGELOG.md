# Changelog

## Unreleased — 2026-08-12

### Added

- `docs/PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md` — relationship procurement + dual-WH DoD verified; E-POS/E-Del/E-Sec remain open gates.
- `@gtr/procurement` — progress tracker domain; preferred-supplier vocabulary (InvenTree pattern, not runtime).
- Migrations:
  - `20260812010000_relationship_procurement_dual_wh.sql` — preferred suppliers, WH1/WH2, `v_master_stock`, PO fund release on approve.
  - `20260812020000_grn_invoice_dual_write.sql` — GRN invoice attach + `amount_minor` columns.
  - `20260812030000_amount_minor_dual_write.sql` — triggers + approve dual-write of `unit_price_minor` / `amount_minor`.
  - `20260812040000_procurement_funds_released_event.sql` — register `procurement_funds_released` in SMS event catalog.
  - `20260812050000_attach_grn_invoice_guard_fix.sql` — attach invoice under procurement RPC mutation guard.
  - `20260812060000_attach_grn_invoice_storage_bind.sql` — draft-only attach; require `procurement-invoices` storage object + PO path prefix.
  - `20260812070000_v_master_stock_staff_only.sql` — revoke authenticated SELECT on `v_master_stock`; admin-only invoice DELETE.
- Web: `/procurement/suppliers`, `/procurement/orders/new`, `/procurement/grn`, `/procurement/orders/[id]`, master stock; live progress tracker bind; GRN `resolve_stock_item_by_oem`.
- SQL smokes: `supabase/tests/epic_a_procurement_wh_smoke.sql` + extended `procurement_approve_smoke.sql`.
- Candidate (not Epic A Done): `@gtr/payments` + D-57 cart; Meili `searchCatalog`; Promptfoo outline; Semgrep/Checkov CI; POS Dial chrome.
- Epic B delivery: `@gtr/delivery` autoAcceptOffers opt-in + timeout→requeue tests; edge `delivery-dispatch-cycle` parity; MapLibre JobDetail SoR; staff tracking B7 (`docs/plans/2026-08-12-epic-b7-staff-web-tracking-dod.md`).
- Epic C–G: payments D-57 `fxRateId`; Meili qty strip; Promptfoo offline gates; HARDENING §7 Semgrep/Checkov; POS WH2 + tablet Dial QA.

### Changed

- Procurement hub live PO list (no theater draft tracker); RFQ copy reframed as optional spot-buy.
- Android procurement hub copy: preferred manual PO is web-first.
- Courier map SoR: MapLibre primary; Google Directions/tiles deprecated fallback only.
- Living docs: Epics A–G Done with verifier evidence; §H items remain deferred.
- **H4 follow-on (Android customer cart dual-read):** `MoneyDualRead` + `getOpenCart` selects `unit_price_minor`/`line_total_minor`; Fake seeds minors; cart UI `displayUnitPrice` / `displaySubtotal`. Unit: `MoneyDualReadTest` (android-customer `:core:rpc`).
- **H8 Done:** `20260812080000_fund_release_insert_once.sql` — approve insert-once on `procurement_fund_releases` (no money rewrite on conflict). Smoke: `fund_release_insert_once_smoke.sql` PASS (`H8 fund-release insert-once smoke OK`, 2026-08-13).
- **H7 Done (Android management):** `com.powersync:core:1.8.1` (Kotlin 2.2.10 metadata pin) + `GtrPowerSyncSchema` / `GtrPowerSyncConnector` (no journal upload; CRUD discarded → OfflinePos RPC intents); Fake when `POWERSYNC_URL` unset; `LivePowerSyncClient.openDatabase` when set via BuildConfig/`local.properties`. Unit: `PowerSyncOfflineContractTest` PASS. Plan: `docs/plans/2026-08-14-h7-powersync-live-sdk.md`. Cloud sync E2E needs secrets (not in repo).
- **H4 Done (cutover habit):** shared API cutover — `Money`/`LegacyMoney` deprecated; `ApiMoney` + `dualWrite*RpcFields` / `settlementMoneyRpcFields` / `cartLineMoneyDto`; `PaymentAllocationInput` prefers `amountMinor`; web PO create sends `unit_price_minor`; storefront ContiPay/Paynow/EcoCash settlement dual-writes `settlement_amount_minor`. Tests: `@gtr/shared` 37/37 + `@gtr/payments` 11/11 PASS (2026-08-14). Physical NUMERIC column drop deferred. Plan: `docs/plans/2026-08-13-h4-money-dual-read-cutover.md`.
- **H4 (slice 6):** iOS money dual-read — `MoneyDualRead.swift` (prefer `*_minor`); cart lines select `unit_price_minor`/`line_total_minor`; cart/order/pay display helpers; Fake seeds minors; SwiftPM `GTRCustomerCoreTests` / `MoneyDualReadTests` (macOS CI).
- **H4 (slice 5):** Ledger JE + payment_entry `*_minor` dual-write — migration `20260813400000_ledger_payment_amount_minor_dual_write.sql` adds `debit_minor`/`credit_minor` on `journal_entry_lines` and `amount_minor`/`settlement_amount_minor` on `payment_entries` with BEFORE INSERT/UPDATE triggers + null-only backfill; posted immutability keeps majors frozen (null→minor fill allowed). Smoke: `ledger_payment_amount_minor_dual_write_smoke.sql` PASS (`H4 ledger/payment amount_minor dual-write smoke OK`, 2026-08-13). Not full H4 Done (iOS / cutover remain).
- **H4 (slice 4):** Android management POS dual-read — `MoneyDualRead` helpers (prefer `*_minor`); `PosCartLineSummary` + `listPosCartLines` select `unit_price_minor` / `line_total_minor`; cart total / line display. Unit: `MoneyDualReadTest` + `PosCartLineOpsTest` BUILD SUCCESSFUL (2026-08-13).
- **H4 (slice 3):** Cart/invoice `*_minor` dual-write — migration `20260813300000_cart_invoice_amount_minor_dual_write.sql` adds `unit_price_minor` / `line_total_minor` on `pos_cart_lines` + `sales_invoice_lines` with BEFORE INSERT/UPDATE triggers + backfill. Smoke: `cart_invoice_amount_minor_dual_write_smoke.sql` PASS (`H4 cart/invoice amount_minor dual-write smoke OK`, 2026-08-13). Not full H4 Done (Android POS dual-read / ledger remain).
- **H4 (slice 2):** Web cart/checkout + staff POS dual-read line/totals (`sumPreferAmountMinor` / `displayLineTotalMajor`); D-57 ZiG settlement from `MoneyMinor` payable. Shared helpers + tests. Plan: `docs/plans/2026-08-13-h4-money-dual-read-cutover.md`.
- **H4 (slice 1):** `@gtr/shared` `preferAmountMinor` / `displayMajorFromDual`; web procurement `listPoLines` dual-reads `unit_price_minor`. Plan: `docs/plans/2026-08-13-h4-money-dual-read-cutover.md`.
- **H-PARITY-WH2 Done:** Android management POS warehouse picker uses `listSaleableWarehouses` / `isPosSaleableWarehouse` (WH2-only; WH1/quarantine excluded). Unit: `PosSaleableWarehouseTest` BUILD SUCCESSFUL.
- **H2 Done:** Android management native preferred-supplier PO (`PreferredPoScreen` / ViewModel → `listPreferredSuppliers` + `createPurchaseOrder` / `submitPurchaseOrder`); hub → Preferred supplier PO; not RFQ-gated; Bridge-First QR OEM. Unit: `PreferredPoHelpersTest` + `:feature:procurement:compileDebugKotlin` BUILD SUCCESSFUL.
- **H5 Done (Android + iOS):** Customer Android address pick MapLibre SoR (`AddressPickMap` → `MapLibreAddressPickMap`, `useMapLibre` default true; Google deprecated). **H5-iOS:** `bridges/ios/MapsNav` SPM (MapLibre Native) + Address/Track screens; MapKit deprecated (`USE_MAPLIBRE=false` / style load fail). Unit: `AddressPickMapCaptionTests`. Mac verify: `xcodebuild` MapsNav test + GTRCustomer build (Windows coding host: no Xcode).
- **H3 Done:** Promptfoo CI workflow — offline safe-narrative default (no secrets); optional real-provider job when API key secrets present; human-promote unchanged (`promptfoo/README.md`).
- **H6 Done:** OSRM Zimbabwe graph prepared; `gtr-osrm` Up; sample Harare route returns `code=Ok` (2026-08-13). Set `OSRM_URL=http://127.0.0.1:5000`.
- **H1 Done:** `@gtr/delivery-dispatch-worker` Temporal host for `DeliveryDispatchWorkflow` (SQL assign-bridge activities); Edge `delivery-dispatch-cycle` remains; no Fleetbase. Tests: package `test` 2/2 PASS.
