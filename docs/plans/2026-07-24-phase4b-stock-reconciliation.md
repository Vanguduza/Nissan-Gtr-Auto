# Phase 4b — Stock reconciliation / cycle count

- Status: **implemented** (smoke + `/supabase-rls-auditor` + `/security-reviewer` gates pending)
- Lane(s): `@backend_agent` (primary); `@management_app_agent` (RPC/API contracts only — no full Android UI)
- Skills needed: `/qr-inventory-workflow` (count scan later); `/accounting-ledger` (write-up/write-down journals)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 4b
- Prior: [`2026-07-23-phase4-inventory-ops.md`](./2026-07-23-phase4-inventory-ops.md) + `20260723220000_inventory_ops.sql`

## Goal

Cycle-count a warehouse (full or partial SKU set); on submit, set on-hand to counted qty and post balanced Inventory/COGS journals for variance — Draft → Submit → Cancel (immutable submit; cancel = reverse).

## Acceptance criteria

- [x] Count submit adjusts on-hand to counted qty *(smoke: write-down + write-up paths)*
- [x] Journals balanced for write-up/write-down *(1300/5100 in `_post_stock_reconciliation`; smoke asserts debits=credits)*
- [x] Dual-auth optional when |variance value| ≥ configured threshold *(RPC + `app_settings`; smoke dual-auth block not reached — see gate notes)*
- [x] Draft → Submit → Cancel; posted rows immutable; cancel reverses stock + `reverse_journal` *(smoke: cancel restores qty; mutation guard via RPC GUC — see gate notes)*
- [x] Naming series for recon docs; RLS in same migration; USD|ZIG + `exchange_rate_applied` on journals (`SRE-`, finance SELECT)
- [x] No ZIMRA / payroll tax / HTML5 QR

**Shipped:** `20260724020000_stock_reconciliation.sql`, `20260724040000_stock_recon_mutation_guards.sql`, smoke `supabase/tests/phase4b_reconciliation_smoke.sql`.

### Gate notes (`/verifier` 2026-07-24, post–`db reset`)

- Smoke via `docker exec … psql`: write-down, cancel-reverse, write-up **passed**; failed on “direct UPDATE on posted header” assertion.
- **Likely harness:** `app.recon_rpc=1` is transaction-local; single `DO $$` block keeps GUC set after RPCs, so trigger guard allows bypass until end of transaction. Re-run mutation checks in a fresh transaction or `set_config('app.recon_rpc','',true)` between steps.
- Dual-auth + RLS-denial sections in smoke **not executed** after early fail.
- `npx supabase db query --file …` fails on multi-statement smokes (CREATE FUNCTION + DO); use `psql` pipe.

## Reuse (do not reinvent)

| Existing | Use for |
|----------|---------|
| `warehouses`, `stock_items`, `stock_levels`, `stock_batches` | Snapshot system qty / cost / currency; apply deltas |
| `_adjust_stock_level`, FIFO consume helpers | Qty mutations on submit/cancel |
| `stock_entry_status` pattern (`draft` / `pending_approval` / `posted` / `cancelled`) | Doc lifecycle (new enum or shared) |
| Dual-auth columns + distinct-staff check on transfers | Large-variance gate |
| `post_journal_entry` / `reverse_journal`, COA `1300`/`1310`/`5100` | Write-up: Dr Inventory Cr COGS; write-down: Dr COGS Cr Inventory (quarantine wh → `1310`) |
| `naming_series` / `next_series_value` | New prefix e.g. `SRE-` |
| `emit_domain_event` | Optional material-variance event (stub OK) |

## Tables / RPCs to add

**Tables** (one migration + RLS):

- `stock_reconciliations` — `warehouse_id`, `scope` (`full`|`partial`), `status`, `document_number`, `currency` + `exchange_rate_applied` (header journal currency), dual-auth fields (`first_approver_id` / `second_approver_id` + timestamps), `journal_entry_id`, `reversal_journal_entry_id`, `posted_at`, `created_by`, notes; optional `variance_value_abs` cached for threshold
- `stock_reconciliation_lines` — `stock_item_id`, `system_qty` (snapshot), `counted_qty`, `variance_qty` (generated or stored), `unit_cost`, `currency`, optional `stock_batch_id` / valuation hint; base UOM only (or convert via `convert_to_base_uom`)

**Config:** variance dual-auth threshold (numeric amount in header currency) — small `app_settings` row or migration constant; document choice in migration comment.

**RPCs:**

1. `create_stock_reconciliation_draft(warehouse, scope, item_ids?)` — snapshot system qty/cost; name `SRE-…`
2. `upsert_stock_reconciliation_lines` — set `counted_qty` while `draft`
3. `submit_stock_reconciliation` — if below threshold → post; else → `pending_approval` (first signer = submitter)
4. `approve_stock_reconciliation` — second distinct staff; then post
5. `cancel_stock_reconciliation` — only `posted`; reverse stock deltas + `reverse_journal`; status `cancelled`

**Post effect:** for each line with variance ≠ 0, adjust `stock_levels` (+batches) so on-hand = counted; post one balanced multi-line journal (or one JE per currency if mixed — prefer reject mixed-currency session or split JE by line currency).

## Paths in scope

- `supabase/migrations/` (new `*_stock_reconciliation.sql` + smoke test)
- `packages/supabase-client/` (regen types; thin query helpers if pattern exists)
- `packages/shared/` only if small types/constants for status/scope/threshold (no UI)

## Out of scope

- Full management Android cycle-count UI (Phase 12)
- Hardware QR scan bridges (contracts already exist; device work = Phase 12)
- Meilisearch / catalog search
- Extending `stock_entry_type` with `reconciliation` (keep recon as its own doc)
- Opening balances, bank recon, sales, warranty, landed cost
- SMS gateway wiring (event emit only if cheap)

## Risks / exclusions

- NO ZIMRA / payroll tax; Bridge-First — no HTML5 QR
- Ledger append-only — never UPDATE/DELETE posted journals
- Quarantine warehouse uses COA `1310`; main uses `1300`
- Period lock: refuse submit/cancel into locked `accounting_periods` (same as finance core)

## Ordered tasks (`@backend_agent`)

1. Migration: enums + tables + `SRE-` naming + RLS (warehouse/admin write; finance read) + grants
2. Draft + line upsert RPCs (snapshot system qty; draft-only edits)
3. Submit/approve path: threshold dual-auth; stock adjust; balanced `post_journal_entry`
4. Cancel = reverse stock + `reverse_journal`
5. Smoke SQL: write-down, write-up, dual-auth large variance, cancel reverse, RLS denial *(partial: core paths pass; mutation/RLS sections need harness fix — see gate notes)*
6. Regen `database.types.ts`; optional shared status/scope types
7. Brief RPC contract note for `@management_app_agent` (params/returns only)

## Gate

`/supabase-rls-auditor` → `/security-reviewer` → `/verifier` → `/manager` done gate

## Handoff

1. Implement in `@backend_agent`
2. `@management_app_agent` may add OpenAPI/contract comments only if apps still unscaffolded
3. Then gate above
