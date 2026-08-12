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

### Changed

- Procurement hub live PO list (no theater draft tracker); RFQ copy reframed as optional spot-buy.
- Android procurement hub copy: preferred manual PO is web-first.
- Courier map SoR: MapLibre primary; Google Directions/tiles deprecated fallback only.
- Living docs honesty: E-Proc/E-WH/E-Del Done with evidence; later epics remain In progress.
