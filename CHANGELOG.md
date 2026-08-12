# Changelog

## Unreleased — 2026-08-12

### Added

- `docs/PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md` — relationship procurement, dual-WH, POS Dial UX, DIAL security (DoD checked).
- `@gtr/procurement` — progress tracker domain; preferred-supplier vocabulary (InvenTree pattern, not runtime).
- Migrations:
  - `20260812010000_relationship_procurement_dual_wh.sql` — preferred suppliers, WH1/WH2, `v_master_stock`, PO fund release on approve.
  - `20260812020000_grn_invoice_dual_write.sql` — GRN invoice attach + `amount_minor` columns.
  - `20260812030000_amount_minor_dual_write.sql` — triggers + approve dual-write of `unit_price_minor` / `amount_minor`.
- Web: `/procurement/suppliers`, `/procurement/orders/new`, `/procurement/grn`, master stock, progress tracker, POS Dial chrome.
- `@gtr/delivery` FIFO helpers + `runSqlDeliveryDispatchCycle` / assign-bridge; edge `delivery-dispatch-cycle`.
- MapLibre `MapLibreJobMap` wired into Android delivery JobDetailScreen (OSRM remains distance SoR).
- `@gtr/payments` PspAdapter stubs + `buildCheckoutDisplay` (D-57) used by web cart checkout.
- Meili dual-read `searchCatalog` (preferMeili default) in `@gtr/supabase-client` + web wrapper.
- Promptfoo outline (`promptfoo/`) for AI CRM/report edges — AI never writes money.
- Semgrep/Checkov CI adapted for GTR (`semgrep-gtr`, root `semgrep.yml`).

### Changed

- Procurement hub no longer RFQ-first; RFQ marked optional spot-buy.
- Android procurement hub copy: preferred manual PO is web-first.
- Android POS wrapped in `GtrTheme` (Dial token pass).
- Living docs introduced (`CHANGELOG.md`, `ENHANCEMENTS.md`, `BUGS.md`); README surfaces delivery app + adoption plan.
