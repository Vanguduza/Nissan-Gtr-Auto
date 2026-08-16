# Enhancements

Tracked improvements aligned with Dial-a-Spare adoption (`docs/DIAL_SPARE_ADOPTION_PLAN.md`).

| ID | Idea | Status | Notes |
| --- | --- | --- | --- |
| E1 | amountMinor + Brevo + OSRM routing spine | Done (spine) | Packages + helpers; dual-write migration landed |
| POD-evidence | Driver POD photo evidence + signature pad | Done (verified) | CameraX Bridge-First capture→`delivery-pods`→`submit_delivery_pod`; Compose signature WORKING; gallery picker skipped; `PodEvidenceGate` + Fake bridges |
| E-Proc | Relationship procurement + fund release + tracker | Done | Verified: live tracker, fund release smoke, RFQ secondary |
| E-WH | Dual WH1/WH2 + master stock | Done | Verified: `list_master_stock` staff-only; GRN OEM + invoice bind; **web report desk** filters (model/cat/sub) + CSV export (2026-08-15) |
| E-POS | Dial UX web + tablet POS | Done (web); Android → CoolMall←web | Web staff POS is behavior SoT; CoolMall vendor gets cart/prep via Supabase. Plan: `docs/plans/2026-08-14-management-oss-shell-rebuild.md`. **Open POS hub deep-link** still open for web-to-100% item 1 |
| Web-100-acct | My Account photo + business card | Done | Self upload `employee-photos`; business card PDF; path/`module_access` gate parity |
| Mgmt-OSS-1 | CoolMall management from web staff spec | Phase A+ DI (non-POS) | Auth→hub→WH master-stock + change-password via `gtradapter` Fake; POS deferred; Live supabase next |
| E-POS-WH2 | Android POS WH2 storefloor pick (H-PARITY-WH2) | Done | `listSaleableWarehouses` + `isPosSaleableWarehouse`; `PosSaleableWarehouseTest` PASS |
| E-Sec | DIAL AppSec (Semgrep/Checkov CI) | Done | semgrep-gtr hard-fail + Checkov HIGH+; HARDENING §7 synced |
| E2a | Temporal `DeliveryDispatchWorkflow` | Done (bridge) | Package + edge cycle; autoAcceptOffers opt-in; full worker §H |
| H1 | Temporal worker host | Done | `@gtr/delivery-dispatch-worker` — named workflow + SQL activities; Edge bridge remains; `@gtr/delivery` workspace dep for frozen-lockfile hygiene (2026-08-14) |
| H6 | OSRM compose + prepare (B-OSRM-1) | Done | Zimbabwe graph + `gtr-osrm` route smoke `Ok` (2026-08-13); clients prefer `OSRM_URL` |
| Map-tiles | Self-host MapLibre basemap (tileserver-gl) | Done (scaffold) | `infra/satellites/maptiles/` Planetiler + `--profile maptiles`; style URL env; CARTO/demotiles fallback when unset |
| E2b | MapLibre Native courier map | Done | JobDetailScreen MapLibre SoR; Google deprecated fallback |
| B-MAP-1 | Customer Android + iOS MapLibre SoR (H5) | Done | Android AddressPickMap MapLibre primary; Google deprecated. **H5-iOS Done:** `bridges/ios/MapsNav` MapLibre SoR; MapKit deprecated fallback |
| E3 | PspAdapter registry + D-57 checkout FX UX | Done | Registry + stub idempotency; cart `fxRateId`; AI money ban grep; Android/iOS Pay/Cart parity; **WA Flow C7** Python `build_checkout_display` + EcoCash ZiG settle |
| E4 | Promptfoo + human promote for CRM/report AI | Done | Offline safe-narrative gates; human-promote README |
| H3 | Promptfoo real-provider CI | Done | Offline default CI job; optional real model when secrets present; no keys in repo |
| E5 | Ledger/payment `amount_minor` dual-write | Done (PO + cart/invoice + JE/payment + loyalty/credit) | PO/fund + cart/invoice + JE + payment + customers credit + store credit + loyalty `money_value_minor`; cutover later |
| H8 | Fund-release insert-once | Done | Smoke PASS 2026-08-13 (`fund_release_insert_once_smoke`) |
| H4 | B-MONEY-1 dual-read | Done (cutover + loyalty/credit) | API prefer `amountMinor`; loyalty/store-credit/credit-limit `*_minor` dual-write + dual-read; physical NUMERIC drop deferred; see `docs/plans/2026-08-13-h4-money-dual-read-cutover.md` |
| H7 | B-PS-1 PowerSync live SDK | Done (Android mgmt) | `com.powersync:core` + Fake/Live; **infra ready — awaiting `POWERSYNC_URL` for cloud E2E** |
| Shop-rails | Curated shop + anon home rails | Done (web + mobile wire) | Shop stock gate; Featured/Newest/Movers via RPC; staff Product pages; **CRM kits** staff create/list (web + Android); megazip APK still WIP |
| Phase-9-HR-UI | Android mgmt gross payroll desk | Done (slice) | Hours + open lines + manual deductions; no tax; web `/staff/hr` already had deeper desk |
| Phase-9-fund | Fund payslips from cash + schedule PDF | Done | Dr 5200/Cr 2150 → Dr 2150/Cr 1100; on-demand + cron Edge; `/staff/hr?tab=payroll`; no ContiPay/tax; **hosted deploy** needs `supabase login` + existing `WORKER_SHARED_SECRET` (`docs/SUPABASE_REMOTE.md` §2b) |
| Phase-10-desk | Pick/DN desk + delivery-job visibility + exception override | Done | Visibility list + **Override assign** only when unassigned/stuck (`assign_delivery_job` p_override); auto-assign SoR; not happy-path pick-driver |
| H4-del-COD | Driver-scoped settlement RPC | Done | `get_delivery_job_settlement` DEFINER + Android delivery enrich; smoke `delivery_job_settlement_smoke.sql` |
| Del-job-detail | Driver job detail + Complete→signature + Route pins | Done | Receipt/address on detail; Active until POD signature; Failed RPC; Route MapLibre pins + FGS driver; `JobStatusGate` |
| Del-job-nav | Job list/route card → job detail | Done | Whole-card `Surface(onClick)`; `resolveSelectedJob`; Active/Done/Failed + Route stops |
| Bin-QR | Bin-label inventory-QR glyph | Done | ESC/POS `binLabel` + `gtr://bin/{code}` |
| E6 | Meili dual-read default for catalog | Done | preferMeili + FTS; strip invented qty; consignment ADR |
| — | Chatwoot / Metabase | Deferred | Tier-2 satellites |
| — | iOS review Photos→camera prefer | Deferred | Bridge exists; UX prefer deferred — `docs/decisions/2026-08-14-ios-review-photos-camera-defer.md` |
| — | Android native preferred-supplier PO screen (H2) | Done | `PreferredPoScreen` + `listPreferredSuppliers` / `createPurchaseOrder` / `submitPurchaseOrder`; hub entry; `PreferredPoHelpersTest` PASS; Bridge QR OEM; not RFQ-gated |
