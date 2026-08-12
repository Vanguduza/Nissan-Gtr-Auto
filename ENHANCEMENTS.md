# Enhancements

Tracked improvements aligned with Dial-a-Spare adoption (`docs/DIAL_SPARE_ADOPTION_PLAN.md`).

| ID | Idea | Status | Notes |
| --- | --- | --- | --- |
| E1 | amountMinor + Brevo + OSRM routing spine | Done (spine) | Packages + helpers; dual-write migration landed |
| E-Proc | Relationship procurement + fund release + tracker | Done | Verified: live tracker, fund release smoke, RFQ secondary |
| E-WH | Dual WH1/WH2 + master stock | Done | Verified: `list_master_stock` staff-only; GRN OEM + invoice bind |
| E-POS | Dial UX web + tablet POS | Done | Web WH2 + tokens + 1280/390; Android ≥700dp dual-pane / 48dp / Bridge QR |
| E-POS-WH2 | Android POS WH2 storefloor pick (H-PARITY-WH2) | Done | `listSaleableWarehouses` + `isPosSaleableWarehouse`; `PosSaleableWarehouseTest` PASS |
| E-Sec | DIAL AppSec (Semgrep/Checkov CI) | Done | semgrep-gtr hard-fail + Checkov HIGH+; HARDENING §7 synced |
| E2a | Temporal `DeliveryDispatchWorkflow` | Done (bridge) | Package + edge cycle; autoAcceptOffers opt-in; full worker §H |
| E2b | MapLibre Native courier map | Done | JobDetailScreen MapLibre SoR; Google deprecated fallback |
| B-MAP-1 | Customer Android MapLibre SoR | Done | AddressPickMap MapLibre primary; Google deprecated; iOS MapKit remain |
| E3 | PspAdapter registry + D-57 checkout FX UX | Done | Registry + stub idempotency; cart `fxRateId`; AI money ban grep |
| E4 | Promptfoo + human promote for CRM/report AI | Done | Offline safe-narrative gates; human-promote README; real provider §H |
| E5 | Ledger/payment `amount_minor` dual-write | Done (PO path) | PO lines + fund releases; broader ledger cutover later |
| E6 | Meili dual-read default for catalog | Done | preferMeili + FTS; strip invented qty; consignment ADR |
| — | Chatwoot / Metabase / PowerSync live | Deferred | Tier-2 satellites |
| — | Android native preferred-supplier PO screen (H2) | Done | `PreferredPoScreen` + `listPreferredSuppliers` / `createPurchaseOrder` / `submitPurchaseOrder`; hub entry; `PreferredPoHelpersTest` PASS; Bridge QR OEM; not RFQ-gated |
