# Changelog

## Unreleased — 2026-08-16

### Fixed

- **Supabase Database Advisors (hosted `gylrgwqyuiwkyykardwc`):** Migration `20260816040000_advisor_security_performance_harden.sql` — `v_master_stock` `security_invoker` (clears ERROR); `search_path` on 110 functions; revoke PUBLIC/anon EXECUTE on SD funcs (7 intentional anon RPCs re-granted); revoke authenticated EXECUTE on internal SD helpers; wrap RLS `auth.*` / `current_setting` in `(select …)` (clears `auth_rls_initplan`). Pushed via `db push`. Remaining WARN rationale in `docs/HARDENING.md` §8.

### Added

- **Staff My Account polish:** Self ID-photo upload on `/staff/account` (`employee-photos` Storage + `update_my_staff_profile` `p_photo_storage_path`); business card PDF alongside ID card via `render-branded-doc`. Migration `20260816030000_employee_photos_self_upload.sql`. File input only (no HTML5 QR/camera).

- **Staff path gates ↔ module_access:** `canAccessPath` applies organogram `module_access` like hub/sidebar; module header href rewrites to first role-visible child when overview is forbidden; finance desk tiles use `filterNavTreeForModuleAccess`. Asserts updated.

- **Hosted payroll Edge ops docs:** `process-payroll-schedules` deploy + existing `WORKER_SHARED_SECRET` set instructions in `docs/SUPABASE_REMOTE.md` §2b (aligned with `ai_worker_schedules`). No secret values in git.

### Fixed

- **Staff web P0 RBAC consistency:** `/staff/warehouse/master-stock` `pathAccessFor` now allows admin|warehouse|sales|finance (same as `list_master_stock` / nav) so sales/finance are not false-forbidden. Staff hub `/staff` tiles use `filterNavTreeForModuleAccess` like the sidebar. Asserts: `assert-master-stock-report.mjs`, `assert-staff-hub-module-access.mjs`. CoA **2150** upsert forces `display_name` on conflict in payroll fund migration.

### Added

- **Staff My Account (web):** `/staff/account` for any staff — editable address / email / phone (`update_my_staff_profile` + GoTrue `updateUser` for email); read-only emp#, role/grade, photo path; module-access chips; change-password + sign-out; ID card PDF; own payslip history (`list_my_payslip_history`, all submitted/cancelled lines — not funded-only) with Storage or branded gross PDF download (USD|ZIG). No tax / no role-wage edits. Plan: `docs/plans/2026-08-16-staff-my-account.md`. Migration `20260816020000_staff_my_account.sql`; smoke `staff_my_account_smoke.sql`; assert `node apps/web/scripts/assert-staff-my-account.mjs`.

- **HR payroll fund + PDF payslips (schedule & on-demand):** Admin/HR fund submitted gross payroll from cash GL (default **1100**): append-only JEs Dr **5200** / Cr **2150** then Dr **2150** / Cr cash; `export_payslip` + branded PDF. CoA **2150 Salaries Payable**. Schedule via `hr_payslip_schedules` + Edge `process-payroll-schedules` (worker secret). Web `/staff/hr?tab=payroll`. No PAYE/NSSA/ContiPay API. Plan: `docs/plans/2026-08-16-payroll-fund-payslip-schedule.md`. Migration `20260816010000_payroll_fund_payslip_schedule.sql`; smoke `payroll_fund_payslip_schedule_smoke.sql`.

### Changed

- **HR ID card redesign (front + back):** CR80 card uses GTR steel/chalk/primary accents. **Front** — photo left, logo right, name / position·staff role / employee # centered. **Back** — employee QR (`…/staff/verify/{token}` preferred, else `gtr://employee/{code}`; Bridge-scanned, no fiscal). Sources: `@gtr/documents` (`renderIdCardHtml`, `buildEmployeeQrPayload`), Edge `branded_docs_pdf` (2-page PDF), preview `pnpm preview:hr-id-card` → `docs/previews/hr-onboarding-id-card-driver.html`.

## Unreleased — 2026-08-15

### Added

- **HR onboarding → staff_roles:** Completing onboarding resolves a coarse `staff_role` (explicit form field → organogram `hr_roles.default_staff_role` → safe title/module heuristic; never invents `admin`) and assigns it when Auth is linked (`apply_hr_onboarding_staff_role` from `complete_hr_onboarding` / `link_employee_auth_user`). ID card PDF/HTML shows a **Driver**/role badge. Preview: `docs/previews/hr-onboarding-id-card-driver.html` (`pnpm preview:hr-id-card`). Migration `20260815250000_hr_onboarding_staff_role.sql`; smoke `supabase/tests/hr_onboarding_staff_role_smoke.sql`.

- **MapLibre basemap satellite:** `infra/satellites/maptiles/` — Planetiler (Apache-2.0) Zimbabwe MBTiles + tileserver-gl (BSD) profile `maptiles` on port **8081**. Style `http://127.0.0.1:8081/styles/basic-preview/style.json` (emulator `10.0.2.2`; wireless phone → PC LAN IP). Env: `NEXT_PUBLIC_MAP_STYLE_URL` / `MAPLIBRE_STYLE_URL`. Discovery: `docs/plans/2026-08-15-maptiles-satellite.md`. Full ZW prepare (2026-08-15): Geofabrik `zimbabwe-260814.osm.pbf` (~171MB) + simplified water (~23MB) → gitignored `basemap.mbtiles` **~129MB** SHA256 `5FDB57DCE54E31EA7C45D3CDEDE62C6E08D99278AB7B95FBF82B9EFF15F34F0A` — **not** APK-bundled. Defaults: `MAPTILES_WATER=simplified`, `MAPTILES_FORCE=1` → Planetiler `--force`; Windows junction `C:\gtr-maptiles-data` when repo path has spaces. Delivery **debug** defaults style URL to emulator tileserver + cleartext; APK ~63MB unchanged. Smoke: `MAPTILES_SMOKE=1`. Unset release → CARTO / demotiles last resort.


### Changed

- **Android delivery receipt copy:** Job detail no longer shows invoice/doc number alone — **Items bought** banner lists DN/invoice lines (qty × OEM/description · amount via `get_delivery_job_lines`); **Notes** sit in a separate **white** banner. Fake seeds + Live RPC wired. Migration `20260815210000_delivery_job_lines_driver_rpc.sql`.

### Added

- **Android delivery job detail + route map:** Job list (Active/Done/Failed) opens detail with receipt copy + delivery address; **Complete job** reveals existing Bridge-First POD signature pad → `submit_delivery_pod` (Active→Done); **Mark job Failed** → `fail_delivery_job`. Jobs stay Active until signature (`JobStatusGate`). Route tab MapLibre multi-pin (dropoffs + live driver via FGS). Fake GoTrue skip + sign-out toggle. Tests: `JobStatusGateTest`, `FakeJobStatusTransitionTest`.
- **Android delivery job-entry navigation:** Card tap (Jobs Active/Done/Failed + Route stop list) opens job detail; removed expand-only “Show details” that looked like navigation; `ShopOrderBox` uses `Surface(onClick)`; shell derives selection via `resolveSelectedJob(state)`.

### Changed

- **Staff master stock report:** `/staff/warehouse/master-stock` is a full-width desk report (StaffNav + wide shell) with model (chassis), merchandising category/subcategory, and OEM search filters; sticky dense WH1/WH2 table; CSV export for current filters or whole stock (up to 5k). RPC `list_master_stock` accepts chassis + category needles (`20260815120000_list_master_stock_filters.sql`). Assert: `node apps/web/scripts/assert-master-stock-report.mjs`.

### Fixed

- **Staff idle lock survives reload (B-STAFF-1):** Web `/staff` idle lock (3 min) previously reset on full page reload while Supabase `autoRefreshToken` rehydrated the session — UI unlocked without password. Persist `lastActiveAt` + `locked` in `sessionStorage`; boot gate before rendering staff chrome; clear on sign-out / fresh login. Assert: `node apps/web/scripts/assert-staff-idle-lock.mjs`.
- **Customer vehicle cascade / catalog browse (B-CAT-1):** Android + iOS `VehicleCascade.deriveMaker` now matches web multi-make brand prefixes, regional Nissan WMIs (`MNT`/`SJN`/…), and bare model tokens (`NAVARA`, `X-TRAIL`, …) — Fake seed NAVARA was previously dropped. Raised `vehicle_master` fetch cap 500→2000; Android shop browse oversamples before stock/price gate; chassis `part_fitment` OEM window 200→2000.

### Added

- **CoolMall staff shell (non-POS):** Wired `vendor/coolmall-gtr/gtradapter` into Hilt DI — Fake GoTrue/staff login (`signInWithStaffIdentifier`), hub from web `STAFF_NAV_TREE` with POS excluded, change-password account tab, warehouse master-stock desk (`list_master_stock` Fake). Bottom nav Hub · Warehouse · Account. Plan: `docs/plans/2026-08-14-management-oss-shell-rebuild.md`.

## Unreleased — 2026-08-14

### Added

- **Android management (corrected):** **Web staff = behavioral SoT** (`apps/web` `STAFF_NAV_TREE` + `lib/staff-*.ts`); **CoolMall fork = UX shell** (`vendor/coolmall-gtr/`, GTR colours applied); Supabase adapters inject domain. Legacy Android = RPC discovery only — **no UI port**. Inventree Flutter not required (web warehouse pages define WH). Plan: `docs/plans/2026-08-14-management-oss-shell-rebuild.md`.

## Unreleased — 2026-08-12

### Added

- **CRM kits (staff):** migration `20260813120000_crm_kits_staff_create.sql` — `_require_kit_staff` (admin|sales|warehouse), RLS write widen, `create_kit_with_components` (≥2 components, optional chassis → `part_fitment`), edit/add/remove gates. Web `/staff/crm/kits` list+create+edit; Android management CRM module (Kits + Product pages) + Fake/Live RPC. Plan: `docs/plans/2026-08-13-crm-kits-create.md`.
- **Shop / home rails (storefront):** anon-safe `list_storefront_home_rails` + `stock_item_shop_merch` / product images; Featured · Newest · Top movers without sign-in; `/shop` stock gate (qty>0 + priced); staff **Product pages** for price/discount/photos. Migrations `20260813100000_stock_item_shop_merch`, `20260813200000_storefront_home_rails_anon`. Mobile customer browse gate + home-rail RPC wiring (Android/iOS). Decision: `docs/decisions/2026-08-13-customer-epc-shop-stock-context.md`.
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

- **Driver POD evidence (photo + signature):** Confirmed working Bridge-First path — CameraX `pod-camera` capture → Storage `delivery-pods` → `submit_delivery_pod`; Compose `ComposeSignaturePad` / `CanvasPodSignatureBridge` stroke→PNG. Gate: photo + signature + verified OTP (`PodEvidenceGate`). Gallery ImagePicker intentionally skipped (camera-only). Fakes: `FakePodCameraBridge` / `FakePodSignatureBridge`. Tests: `:feature:pod` gate + `:pod-camera` / `:pod-signature` / `:core:rpc` Fake upload.
- **Phase 10 exception override assign:** Desk/tracking manual assign is **unassigned/stuck only** (not happy-path pick-driver). Android Dispatch + web `/staff/logistics` (+ tracking) call `assign_delivery_job(p_override=true)` after confirm; auto-assign SQL/FIFO remains SoR. Gate: `DeliveryOverrideAssignGate` / `canOverrideAssignDeliveryJob`. Tests: `DeliveryOverrideAssignGateTest` + `delivery-override-assign.test.ts`.
- **Secrets / Mac infra readiness:** Root `.env.example` + HARDENING + LOCAL_DEVELOPMENT name ContiPay→Temporal/PowerSync/map tiles/Promptfoo; Temporal worker refuse-without-`TEMPORAL_ADDRESS` + service_role-only; package/promptfoo `.env.example`. Blocked items labeled **infra ready — awaiting secrets** (or Mac `xcodebuild`). Catalog diagram scrape closed as ops runbook (`data-pipeline/docs/catalog-diagram-scrape-runbook.md`). iOS Photos→camera prefer deferred (`docs/decisions/2026-08-14-ios-review-photos-camera-defer.md`).
- **Phase 10 delivery-job visibility:** Android Dispatch + web `/staff/logistics` read-only job list (status · DN link · unassigned). Auto-assign remains SoR — not an assignment picker. Fake parity: `deliveryJobDeskListsUnassigned`.
- **Driver COD settlement RPC:** `20260814300000_delivery_job_settlement_driver_rpc.sql` — `get_delivery_job_settlement` DEFINER for assignee/admin; Android delivery Live enrich; smoke `delivery_job_settlement_smoke.sql`.
- **Bin-label QR glyph:** ESC/POS `EscPosCommands.binLabel` embeds `gtr://bin/{code}`; BinsViewModel prints via `printRaw`.
- **Phase 10 logistics UI (web Cancel DN parity):** Staff `/staff/logistics` wires `cancel_delivery_note` beside Submit DN (`staff-logistics.ts` + panel). Same authz as Android — route roles admin|warehouse|sales|dispatcher; RPC `_require_logistics_staff`. UI enables Cancel for draft/submitted only. No browser GPS. Backend smoke already covers cancel in `phase10_logistics_smoke.sql`.
- **Phase 10 logistics UI (Android management):** Dispatch pick/DN desk deepen — `listDispatchInvoices` + `listPickListLines` (Fake/Live PostgREST), tap invoice/pick lines with qty drafts, confirm-all + DN-from-lines, Cancel DN. UUID paste remains fallback. Fake parity: `PickPackDeskFakeRpcParityTest`. GPS still delivery-app-only.
- **Phase 9 HR UI (Android management):** Gross payroll desk — period hours (`attendance_hours_in_period`), open `payroll_lines` (gross/deductions/net), manual `add_payroll_deduction` only (no PAYE/NSSA). Hub: Clock · gross payroll · onboarding. Fake/Live RPC + `GrossPayrollFakeRpcParityTest`.
- **B-MONEY-1 loyalty / credit-limit:** migration `20260814200000_loyalty_credit_amount_minor_dual_write.sql` — `customers.credit_limit_minor` / `open_balance_minor`, `store_credit_*` minors, `loyalty_ledger.money_value_minor` + triggers + null-only append-only backfill; `get_loyalty_balance` / `set_customer_credit` return `*_minor`. Dual-read web (cart/B2B/staff credit/loyalty), `@gtr/shared` display helpers, Android management credit + customer loyalty. Smoke: `loyalty_credit_amount_minor_dual_write_smoke.sql` **PASS** local (`B-MONEY-1 loyalty/credit amount_minor dual-write smoke OK`, 2026-08-14). Physical NUMERIC drop deferred.
- **H1 lockfile hygiene:** `@gtr/delivery-dispatch-worker` declares `@gtr/delivery` `workspace:*` so `pnpm install --frozen-lockfile` matches `package.json` (CI-safe). Activities still import `../delivery/src/*.ts` for Node strip-types. Verifier: `pnpm --filter @gtr/delivery-dispatch-worker test` 2/2 PASS.
- **D-57 WA Flow C7:** FastAPI `build_checkout_display` parity (USD browse; ZiG + `fx_rate_id` at EcoCash settle; MoneyMinor; fail closed). Migration `20260814100000_whatsapp_flow_d57_settle.sql`. Tests: `services/whatsapp-flows` `test_checkout_display` + `test_checkout_flow_d57`.
- **D-57 checkout parity (Android customer + iOS):** browse/cart USD; ZiG only at settle/pay via `CheckoutDisplayBuilder` (MoneyMinor + ops `fxRateId`); fail-closed when daily rate missing. Android `fetchZigExchangeRateId` + cart/pay UI; iOS CartScreen/PayScreen + SwiftPM `CheckoutDisplayTests`. Unit: android-customer `:core:rpc` `CheckoutDisplayTest`.
- **H4 follow-on (Android delivery COD dual-read):** `MoneyDualRead` + `DeliveryJobSettlement` prefer `*_minor`; Fake COD seed; Jobs UI `formatAmountDueLabel`. Unit: `MoneyDualReadTest` (android-delivery `:core:rpc`). Live path uses `get_delivery_job_settlement`.
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
