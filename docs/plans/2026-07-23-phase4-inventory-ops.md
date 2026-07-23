# Phase 4 — Inventory operations + QR + UOM

- Status: **done**
- Lane(s): `@backend_agent` (migrations, RPCs, shared); `@hardware_mobile_agent` (bridge **contracts only** under `bridges/**`)
- Skills needed: `/qr-inventory-workflow`
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 4

## Goal

Add receipt→batch→QR, dual-auth transfers, FIFO/AVG movements, serials, quarantine returns, and UOM conversion on top of existing warehouse/inventory tables — no physical hardware.

## Acceptance criteria

- [x] Stock receipt posts batch + creates `inventory_qr_codes` with payload `gtr://part/{OEM}?batch=…&valuation=FIFO|AVG`
- [x] Transfer dual-auth (two distinct staff) required before `stock_levels` mutate
- [x] Returns data path targets Quarantine only (`post_return_to_quarantine`)
- [x] Valuation method persisted per batch; FIFO consume on transfer
- [x] Qty posted in base UOM via `convert_to_base_uom`
- [x] Stock Entry types: `receipt` \| `transfer` \| `issue`
- [x] Serial numbers enforced when `requires_serial`
- [x] RLS on new tables; bridge contracts only; no ZIMRA / payroll tax

## Shipped

| Artifact | Notes |
|----------|-------|
| `20260723220000_inventory_ops.sql` | UOM, batches, entries, RPCs, QR payload |
| `packages/shared/src/inventory/` | QR parse/build + UOM helper |
| `bridges/contracts/qr-inventory.ts` | scan/print interfaces |
| `supabase/tests/phase4_inventory_smoke.sql` | smoke |

## Handoff

Phase 4 complete → **Phase 4b** (cycle count) or **Phase 5** (sales).
