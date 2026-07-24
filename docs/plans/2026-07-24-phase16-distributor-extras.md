# Phase 16 — Distributor extras (bins, kits, consignment, loyalty)

- Status: **slices 1–3 done** (bins, kits, consignment); slices 4–5 pending
- Lane(s): `@backend_agent` (primary — schema/RPC/RLS); UI follow-on `@web_agent` / `@management_app_agent` via child tickets
- Skills needed: `/token-discipline`; `/accounting-ledger` for consignment revenue timing + loyalty liability if points post to CoA
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) § Phase 16 + Later distributor extras
- Depends: Phase 15 audit ideally; **may start after Phase 8/13** per master — migrations **after Phase 14 timestamps**

## Goal

Ship ordered backend child slices for bin locations, kit/BOM sell, consignment stock, and optional loyalty — each with RLS and Draft/Submit where transactional — no ZIMRA/tax.

## Ordered child slices

| Order | Slice | Migration slot (suggested) | Notes |
|------:|-------|----------------------------|-------|
| 1 | Bin / location within warehouse | `20260724120000_warehouse_bins.sql` | **Done** — RLS + CRUD RPCs + pick-path hints; smoke `phase16_bins_smoke.sql` |
| 2 | Kits / BOM sell | `20260724121000_kits_bom_sell.sql` (+ `…20500_pick_path_hints_authz.sql`) | **Done** — stocked vs explode; no double stock/COGS; smoke `phase16_kits_smoke.sql` |
| 3 | Consignment stock | `20260724122000_consignment_stock.sql` | **Done** — supplier-owned + customer-held; revenue only on `recognize_sale`; smoke `phase16_consignment_smoke.sql` |
| 4 | Loyalty / points (optional) | last | Only after store credit (Phase 13) proven; liability account if ledger-backed |
| 5 | Attachments + doc timeline (soft) | anytime if cheap | Skip if not needed for AC |

One child plan or ticket per slice; do not combine all four in one mega-migration.

### Slice 1 acceptance (bins)

- [x] Migration after `20260724110000`: `supabase/migrations/20260724120000_warehouse_bins.sql` (RLS in same file)
- [x] Master-data CRUD via RPCs (`create_warehouse_bin` / `update_warehouse_bin` / `deactivate_warehouse_bin` / `set_stock_level_bin`); Draft→Submit N/A (bins are not transactional docs)
- [x] Optional `bin_id` on `stock_levels` + `stock_entry_lines`; receipt lines accept `bin_id`; cross-warehouse bin rejected; Quarantine return path unchanged
- [x] Pick-path hints: `pick_path_seq` + `get_pick_path_hints`; `pick_list_lines.suggested_bin_id` stamped on create
- [x] Smoke: `supabase/tests/phase16_bins_smoke.sql` (PASS via docker exec)
- [x] Types regen note: `supabase gen types typescript --local > packages/supabase-client/src/database.types.ts` (follow-on)
- [x] No ZIMRA / payroll tax / HTML5 QR
- [x] UI follow-on: `@management_app_agent` — bin CRUD + pick-path hints on pick lists (not blocking backend)

### Slice 2 acceptance (kits / BOM)

- [x] Migrations: `supabase/migrations/20260724120500_pick_path_hints_authz.sql` (DEFINER staff gate) + `supabase/migrations/20260724121000_kits_bom_sell.sql` (RLS in same file)
- [x] `item_kits` / `item_kit_components` + RPCs (`create_item_kit` / `update_item_kit` / `add_kit_component` / `remove_kit_component`); Draft→Submit N/A (BOM master data)
- [x] `sell_mode=explode`: cart header (revenue, `issues_stock=false`) + zero-price component lines (`issues_stock=true`); kit SKU stock untouched; COGS from components only
- [x] `sell_mode=stocked`: kit SKU line only (BOM not exploded); COGS from kit SKU only; components unchanged
- [x] Core-charge parent-child unchanged (core attaches to kit header)
- [x] `add_cart_line` / `checkout_pos_cart` / `create_pick_list` honour `issues_stock` (no double-count)
- [x] Smoke: `supabase/tests/phase16_kits_smoke.sql` (PASS via docker exec)
- [x] Types regen note: `supabase gen types typescript --local > packages/supabase-client/src/database.types.ts` (follow-on)
- [x] No ZIMRA / payroll tax / HTML5 QR; no consignment/loyalty
- [x] UI follow-on: `@web_agent` / `@management_app_agent` — kit BOM CRUD + sell (not blocking backend)

### Slice 3 acceptance (consignment)

- [x] Migration after kits: `supabase/migrations/20260724122000_consignment_stock.sql` (RLS in same file)
- [x] CoA `1320` Inventory (Customer Consignment); supplier-owned memo qty until `take_ownership` (Dr 1300 / Cr 2100 — **no revenue**)
- [x] Customer-held: `place_at_customer` reclass Dr 1320 / Cr 1300; `recognize_sale` is the **only** path that credits `4100`
- [x] Draft → Submit → Cancel RPCs (`create_consignment_entry_draft` / `add_consignment_entry_line` / `submit_consignment_entry` / `cancel_consignment_entry`); ledger append-only via `_post_journal_entry_inventory` / `_reverse_journal_inventory`
- [x] Smoke: `supabase/tests/phase16_consignment_smoke.sql` (PASS via docker exec)
- [x] Types regen note: `supabase gen types typescript --local > packages/supabase-client/src/database.types.ts` (follow-on)
- [x] No ZIMRA / payroll tax / HTML5 QR; no loyalty
- [x] UI follow-on: `@management_app_agent` — consignment receive / place / take_ownership / sale (not blocking backend)

## Acceptance criteria (remaining slices 4–5)

- [ ] Migration(s) after consignment (`20260724122000`); RLS in same file(s)
- [ ] Transactional docs use Draft → Submit → Cancel pattern where applicable; ledger append-only (reversing entries only)
- [ ] Money fields carry `USD`\|`ZIG` + `exchange_rate_applied` when converted
- [ ] Smoke SQL for the slice; types regen note for `packages/supabase-client/`
- [ ] No ZIMRA / payroll tax / HTML5 QR
- [ ] UI follow-on ticket named (web and/or management) — not blocking backend Done if API complete

## Paths in scope

- `supabase/migrations/2026072412****_*.sql` (and later) — bins → kits → consignment → loyalty
- `supabase/tests/phase16_*_smoke.sql`
- `packages/shared/src/**` — kit/consignment/loyalty types if needed
- `packages/supabase-client/src/database.types.ts` (regen)
- UI (follow-on): `apps/web/` kits/loyalty; `apps/android-management/` bin pick hints — **separate lane invocations**

## Out of scope

- Full MRP / Work Orders / manufacturing
- ZIMRA, tax engines, fiscal QR
- Customer mobile feature bind (unless thin read of kit SKU later)
- Replacing FIFO/valuation policy wholesale
- Ad-hoc inserts mid-Phase 14/15 without `/manager`

## Risks / exclusions

- Consignment: do not recognize sales revenue until ownership transfer rules fire
- Kits: explode vs stocked-kit must not double-count stock/COGS
- Loyalty: optional — skip entirely if product defers via decision
- Quarantine returns protocol unchanged; bins must not bypass quarantine warehouse rules

## Handoff

1. `/manager` unlocks slice 1 after Phase 15 (or explicit waive)
2. `@backend_agent` per slice → `/security-reviewer` → `/verifier`
3. UI lanes only after RPC contracts stable
4. `/manager` done gate per slice; update master Phase 16 checklist
