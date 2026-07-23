# Phase 4 — Inventory operations + QR + UOM

- Status: draft
- Lane(s): `@backend_agent` (migrations, RPCs, shared); `@hardware_mobile_agent` (bridge **contracts only** under `bridges/**`)
- Skills needed: `/qr-inventory-workflow`
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 4

## Goal

Add receipt→batch→QR, dual-auth transfers, FIFO/AVG movements, serials, quarantine returns, and UOM conversion on top of existing warehouse/inventory tables — no physical hardware.

## Already shipped (do not recreate)

| Artifact | Notes |
|----------|-------|
| `20260723100200_warehouses_inventory.sql` | `warehouses` (`is_quarantine`), `stock_items`, `stock_levels`, `inventory_qr_codes` + RLS |
| `valuation_method` enum | `FIFO` \| `AVG` (`20260723100000`) |
| `20260723100400_seed_coa_warehouses.sql` | `MAIN`, `QUAR`; CoA `1300` / `1310` |
| SMS catalog | `stock_received`, `transfer_*`, `quarantine_received`, `serial_moved` |

## Acceptance criteria

- [ ] Stock receipt posts batch + creates `inventory_qr_codes` with payload `gtr://part/{OEM}?batch=…&valuation=FIFO|AVG`
- [ ] Transfer dual-auth (two distinct staff) required before `stock_levels` mutate
- [ ] Returns data path credits Quarantine only — never saleable `MAIN` without Quarantine hop
- [ ] Valuation method persisted per batch; movements consume FIFO layers or AVG cost correctly
- [ ] All qty posted in base UOM; box↔each (and peers) convert on receipt / transfer / sale qty
- [ ] Stock Entry–style types: `Receipt` \| `Transfer` \| `Issue`
- [ ] Serial numbers required for flagged high-value/warranty assemblies on receipt/transfer/issue
- [ ] RLS on every new table; no ZIMRA / payroll tax; Bridge-First (contracts only)

## Paths in scope

- `supabase/migrations/` — deltas only (batches, UOM, stock entries, serials, transfer auth, QR FK/payload, return→QUAR RPC)
- `packages/shared/` — QR payload builder, UOM convert, valuation helpers
- `packages/supabase-client/` — regenerated types
- `bridges/**` — TypeScript/Kotlin/Swift **interface stubs** for scan/print (no CameraX/AVFoundation impl)
- `supabase/tests/` — smoke for receipt→QR, dual-auth gate, quarantine return, UOM

## Out of scope

- Physical printer/camera (Phase 12); bin locations (Phase 16); cycle count (Phase 4b)
- PO/GRN UI & landed cost (Phase 8); sales/cart UI (Phase 5); ledger COGS journals beyond inventory qty/cost fields (hook points only if needed)
- Management/web app screens

## Risks / exclusions

- Dual-auth must reject same-user double-sign; stock mutate only in approved RPC
- Quarantine Returns Protocol: contra-revenue path later (Phase 5); this phase = stock routing to `QUAR` only
- Do not widen `inventory_qr_codes` UNIQUE without migrating existing rows
- No HTML5 QR; no browser camera APIs

## Ordered implementation (max 8)

1. **UOM foundation** — `uoms`, `item_uom_conversions` (factor→base); `stock_items.base_uom_id` + `requires_serial`
2. **Batches + receipt** — `stock_batches` (valuation, unit_cost, currency, qty_base); `stock_entries` / lines (`Receipt`\|`Transfer`\|`Issue`); `post_stock_receipt` RPC mutates levels + layers
3. **QR on receipt** — ALTER `inventory_qr_codes`: `stock_receipt_item_id` / `batch_id` FK, `payload` text; auto-insert on receipt commit
4. **FIFO/AVG movements** — layer table or batch qty consumption; Issue/Transfer deplete per method; persist method on batch (not only `stock_levels`)
5. **Dual-auth transfer** — draft transfer entry → `request` + `approve` (distinct `staff_roles`) → then mutate; emit `transfer_*` SMS events
6. **Serials** — `stock_serials` (unique serial ↔ item/batch/warehouse); enforce on flagged SKUs for Receipt/Transfer/Issue
7. **Quarantine returns (data)** — `post_return_to_quarantine` forces destination `warehouses.is_quarantine`; block direct return→saleable
8. **Shared + bridges** — `packages/shared` QR/UOM helpers; `bridges/**` scan/print contract stubs only

## Handoff

1. `@backend_agent` implements 1–7; `@hardware_mobile_agent` stubs contracts in step 8
2. `/security-reviewer` (RLS + dual-auth RPCs)
3. `/verifier`
4. `/manager` done gate → then Phase 4b when receipt/transfer APIs stable
