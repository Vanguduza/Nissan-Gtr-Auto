# Changelog

## Unreleased — 2026-08-12

### Added

- `docs/PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md` — relationship procurement, dual-WH, POS Dial UX, DIAL security.
- `@gtr/procurement` — progress tracker domain; preferred-supplier vocabulary (InvenTree pattern, not runtime).
- Migration `20260812010000_relationship_procurement_dual_wh.sql` — preferred suppliers, WH1/WH2 roles, `v_master_stock`, PO fund release on approve.
- Web: `/procurement/suppliers`, `/staff/warehouse/master-stock`, procurement progress tracker UI, POS shell Dial UX chrome.
- `@gtr/delivery` FIFO / offer-cycle helpers (`selectNextCourierOffer`).
- `docs/HARDENING.md` §7 DIAL AppSec baseline; AGENTS procurement/AI locks.

### Changed

- Procurement hub no longer RFQ-first; RFQ marked optional spot-buy.
- Living docs introduced (`CHANGELOG.md`, `ENHANCEMENTS.md`, `BUGS.md`); README surfaces delivery app + adoption plan.
