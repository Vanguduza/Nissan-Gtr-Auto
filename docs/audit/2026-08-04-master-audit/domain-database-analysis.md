# Domain & Database Analysis — vs Odoo / Medusa / Bagisto concepts

> Read-only. Every claim below is either a direct citation of a file in this repo, or
> explicitly marked as drawn from general/public knowledge of the reference project
> (Medusa, Bagisto, Odoo) because live GitHub/docs fetches for the reference-research
> workstream had not completed at the time this was written. Where that applies it is
> marked **[reference knowledge]** rather than presented as a fresh citation.

## 0. Scale (verified via ripgrep over `supabase/migrations/*.sql`)

- **~95 migration files**, dated `20260723100000` → `20260803270000` (11 days of
  continuous schema growth as one flat migration stream — no folder-per-domain split).
- **120+ `CREATE TABLE` statements** (direct count of grep matches across all migration
  files — see raw list captured during this audit).
- Every table found via `CREATE TABLE` grep has a matching `ALTER TABLE ... ENABLE ROW
  LEVEL SECURITY` somewhere in the migration stream (cross-checked table-by-table; no
  orphan table found in this pass — full detail in `security-findings.md` §1).
- SQL function count is large: representative high-density files include
  `20260724063000_procurement_end_rpc.sql` (24 function definitions in one file),
  `20260724061000_procurement_mutation_guards.sql` (23), `20260724070000_hr_gross_payroll.sql`
  (20), `20260723210000_finance_core.sql` (18) — extrapolated across ~95 files this is
  **several hundred SQL functions**, the large majority `SECURITY DEFINER`.

## 1. Stock/inventory ledger — vs Odoo `stock.move` / `stock.quant`

**[reference knowledge]** Odoo's inventory model separates an append-only movement
journal (`stock.move`, grouped into `stock.picking`) from a materialized on-hand balance
(`stock.quant`), so `stock.quant` is always derivable by replaying `stock.move`, and
`stock.move` is the audit trail.

**This repo's actual shape** (`supabase/migrations/20260723220000_inventory_ops.sql`):

- `stock_entries` / `stock_entry_lines` — the movement journal (lines 115-166).
- `stock_levels` — the balance table (defined in
  `20260723100200_warehouses_inventory.sql:20`).

This is **structurally the right pattern** (journal + derived balance), which is a
genuine strength worth preserving, not replacing. However:

- Direct `UPDATE`/`INSERT INTO public.stock_levels` statements exist in **three**
  migration files outside the core movement path:
  `20260724120000_warehouse_bins.sql`, `20260725201000_wishlist_back_in_stock_move_to_cart.sql`,
  and `20260723220000_inventory_ops.sql` itself. The first two are evidence that at least
  two RPC surfaces (`warehouse_bins`-related, and wishlist "move to cart") can mutate the
  balance table **without going through `stock_entries`**, which breaks the "journal is
  the only writer" invariant Odoo relies on for auditability. **Risk**: a stock count
  discrepancy investigation cannot fully reconstruct history from `stock_entries` alone
  if any of these paths wrote balance changes without a corresponding entry row.
  Confirming whether these paths *also* insert a `stock_entries` row (making them safe)
  requires reading each function body — **not yet done in this pass**; flagged as
  UNKNOWN requiring follow-up, not asserted as a defect.
- Stock is further fragmented by `consignment_stock_levels` (a **parallel** balance
  table for consigned stock, `20260724122000_consignment_stock.sql:31`) and
  `inventory_abc_snapshots` (a derived analytics table, not a ledger). Odoo instead
  models consignment as a stock **location** (`stock.location`) inside the *same*
  `stock.quant`/`stock.move` model, so consigned vs owned stock is a dimension, not a
  parallel table. Here it is a structurally separate table with its own
  `consignment_entries`/`consignment_entry_lines` mini-ledger
  (`20260724122000_consignment_stock.sql:65-129`) — a second, smaller version of the same
  journal+balance pattern, duplicated rather than reused.

**Preserve/refactor recommendation**: Preserve `stock_entries`/`stock_levels` as the
authoritative pattern. Refactor consignment stock to be a `warehouses`-style location
flag consumed by the *same* `stock_entries` ledger rather than a parallel schema,
next time consignment logic is touched (do not do this as a standalone migration without
a plan — per the non-negotiable rules, this is a proposal, not an instruction to act).

## 2. Party model — vs Odoo `res.partner`

**[reference knowledge]** Odoo unifies customers, suppliers, and contacts into one
`res.partner` model; a partner can hold multiple roles (customer AND supplier)
simultaneously without duplicated address/contact schema.

**This repo**: `customers` (`20260723230000_sales_pos.sql:12-30`) and `suppliers`
(`20260724050000_procurement.sql:9-25`) are **fully separate tables** with independently
declared, near-identical contact columns (`name`/`display_name`, `email`, `phone_e164`).
Neither references the other. A business entity that is both a customer and a supplier
(plausible for a parts distributor doing trade-ins or consignment with the same garage)
would need **two rows with no link between them** — the schema cannot express that
relationship today.

This is a genuine domain-modeling gap relative to the reference pattern, but it is
**consistent with a common, defensible ERP shortcut** (many systems keep customer/vendor
separate for simpler RLS and access-control reasoning — a unified partner table needs
either row-level type discrimination or careful policy design to avoid a sales rep
seeing supplier banking details via a customer-scoped RLS policy, or vice versa). This is
a **trade-off**, not an unambiguous defect — recorded here as a target-architecture
candidate (Phase K of the master audit prompt), not an immediate fix.

## 3. Financial ledger — vs Odoo `account.move`/`account.move.line`

**[reference knowledge]** Odoo posts every financial event as a balanced
`account.move` with `account.move.line` rows; correction is via reversal, never edit.

**This repo**: `chart_of_accounts`, `journal_entries`, `journal_entry_lines`
(`20260723100100_chart_of_accounts_ledger.sql:3-27`) is a **direct structural match** to
that pattern — a single authoritative double-entry ledger. Balance enforcement exists in
both layers:

- Client-side pre-check: `packages/shared/src/ledger/journal.ts:19-33`
  (`assertBalanced` — throws if `|debit - credit| > 0.01`, and if any line has both a
  debit and a credit).
- The comment at `journal.ts:40` ("posting is append-only in DB") documents intent, but
  **this TypeScript file cannot enforce database-level immutability** — that must be a
  Postgres constraint/trigger or a `REVOKE UPDATE/DELETE` grant on `journal_entries`.
  Verifying that a `REVOKE UPDATE, DELETE ON journal_entries FROM ...` (or an
  immutability trigger) actually exists in `20260723100100_chart_of_accounts_ledger.sql`
  or `20260723210000_finance_core.sql` was **not completed in this pass** — flagged as
  **UNKNOWN, high-priority to verify**, since "Ledger Immutability" is one of the
  project's own hard global laws (`.cursorrules`) and this is the one place its actual
  enforcement should be confirmed at the database (not just client TypeScript) layer.
- Positive finding: `finance_requisitions`, `finance_refunds` and
  `finance_audit_log` (`20260803160000_batch1_finance_refunds_requisitions_audit.sql`)
  show a deliberate audit-trail pattern for corrections, consistent with a
  reversing-entry philosophy rather than editing posted invoices — this aligns with the
  project's own "Ledger Immutability" law and the offline-POS ADR's explicit statement
  ("Corrections to synced sales remain ledger-immutable... never edit posted invoices
  from the tablet outbox" — `docs/decisions/2026-08-03-offline-sqlcipher-pos-cache.md:48`).

## 4. Procurement — vs Odoo `purchase.order`/`purchase.requisition`

**[reference knowledge]** Odoo splits requisition (internal ask) from purchase order
(commitment to a specific vendor), with RFQ/quotation comparison in between.

**This repo** (`supabase/migrations/20260724050000_procurement.sql`,
`20260724060000_rfq_blanket.sql`): `material_requests` → `rfqs`/`rfq_lines` →
`supplier_quotations` → `purchase_orders` → `goods_receipts` → `landed_cost_vouchers`.
This is a **structurally faithful, arguably more complete** mapping of the Odoo
procure-to-pay flow than most from-scratch ERPs attempt (it includes landed-cost
allocation, which many simpler builds skip). This is a genuine strength — call this out
explicitly as something to **preserve**, not rebuild, when a "modernize the backend"
initiative eventually happens.

## 5. Commerce/cart/checkout — vs Medusa module boundaries

**[reference knowledge]** Medusa treats cart, pricing, promotion, fulfillment, and order
as **separate modules** behind a workflow-orchestration layer (the "workflows SDK"),
each independently versionable and testable, with the storefront/admin UI as a pure API
consumer with no direct DB access.

**This repo**: `pos_carts`/`pos_cart_lines` (`20260723230000_sales_pos.sql:78-108`) are
shared between the **storefront checkout and the staff POS till** (channel-discriminated,
per the web audit's finding that `apps/web/lib/customer-storefront.ts` and
`apps/web/lib/staff-pos.ts` both operate on `pos_carts`). This is actually a *positive*
architectural decision relative to Medusa's separate-module ideal in one respect — it
avoids the "duplicate implementations of ... stock, ... order state" that the project's
own non-negotiable rule #9 explicitly warns against — cart is genuinely one system, not
two. The trade-off is that a single table now serves two very different UX/latency
profiles (customer web checkout vs. a busy physical till), which is why POS-specific
concerns (`pos_scan_sessions`, `pos_action_audit`, `pos_quotations`,
`pos_offline_sale_receipts`) keep being bolted on as *additional* tables rather than
being absorbed into a POS-specific extension of the same cart concept — see
`structural-critique.md` §1 for the resulting migration/RPC sprawl this produces.

There is **no workflow-orchestration layer** equivalent to Medusa's workflows-SDK
anywhere in this repo — multi-step business processes (e.g. `replay_offline_pos_sale`
calling `create_pos_cart` → `add_cart_line` → `checkout_pos_cart_with_tenders` in
sequence, `supabase/migrations/20260803270000_pos_offline_sync.sql:229-275`) are
hand-composed inside a single PL/pgSQL function rather than expressed as a named,
independently retryable workflow with defined compensation/rollback steps. For a system
this size, PL/pgSQL orchestration inside one transaction is arguably *simpler and safer*
than introducing a workflow engine (fewer moving parts, real ACID guarantees) — this is
flagged as a documented trade-off, not a defect to fix.

## 6. AI/CRM/Stores autonomous layer — does it introduce a second source of truth?

Direct read of `supabase/migrations/20260803150000_ai_autonomous_crm_stores_finance.sql`
and `20260803220000_ai_worker_schedules.sql` confirms:

- **No**, it does not. `kpi_finance_performance_v1()` (lines 334-415) reads from
  `report_profit_and_loss()` (an existing reporting function) and returns a JSON
  aggregate — it does not create a parallel finance table. `run_inventory_abc_classification()`
  (lines 420-507) writes to a new `inventory_abc_snapshots` table, but that table is
  explicitly a **derived analytics snapshot** (has `as_of`, gets `DELETE`d and
  regenerated per run at line 443-444), not a competing stock-ledger.
- CRM promo eligibility (`list_crm_promo_candidates`, lines 640-781) correctly gates on
  `customers.marketing_opt_in` and a cooldown timestamp, matching the project's own ADR
  (`docs/decisions/2026-08-03-ai-autonomous-layer-constraints.md`) — this is a case where
  the ADR's stated intent and the actual shipped SQL match, which is not guaranteed in
  fast-moving codebases and is worth noting as a **positive integrity signal**.
- Every mutating function in this migration (`insert_ai_promo_run`,
  `finalize_ai_promo_run`, `insert_ai_promo_delivery`) hard-checks
  `auth.role() = 'service_role'` (lines 792, 816, 852) — these are correctly
  worker-only, not exposed to any authenticated user, which is the correct authorization
  posture for a function that writes on behalf of an autonomous cron worker.

**Net assessment**: the AI-autonomous layer is the **best-integrated bolt-on** module
found in this schema — it reuses existing tables/report functions rather than
duplicating them, and its access control matches its own governing ADR. This should be
called out to the user as a genuine strength (see `structural-critique.md`'s "what to
preserve" framing), in contrast to the POS/offline surface (§7 below).

## 7. POS/offline migration risk (cross-checked against client expectations)

`supabase/migrations/20260803270000_pos_offline_sync.sql` is the **server side** of the
tablet offline cache described in `docs/decisions/2026-08-03-offline-sqlcipher-pos-cache.md`.
Direct comparison of the ADR's documented protocol against the shipped SQL:

| ADR says | Migration delivers | Match? |
|---|---|---|
| `pull_pos_offline_snapshot(warehouse_id)` returns `{warehouse_id, pulled_at, currency, items[...]}` | Exact shape at lines 112-118 | ✅ |
| `replay_offline_pos_sale(client_sale_id, payload)`, idempotent on `client_sale_id` unique receipt row | `pos_offline_sale_receipts.client_sale_id UNIQUE` (line 11) + early-return on existing receipt (lines 174-180) + `unique_violation` exception handler as a second idempotency net for concurrent replay (lines 296-306) | ✅ — **double-layered** idempotency (check-then-insert *and* a catch-all exception handler) is a genuinely careful pattern |
| "Price drift / stock shortfalls surface as conflicts" | `offline_price_conflict` raised when `abs(resolved_price - expected_price) > 0.05` (lines 261-264) | ✅ |
| "Online-only gates: ... EcoCash/Paynow/ContiPay... Named credit customers... offline is walk-in cash only" | Tender loop rejects any non-`cash` tender with `offline_tender_not_allowed` (lines 208-218); cart created with `NULL` customer (walk-in only, line 231) | ✅ |
| — (not explicitly specified in the ADR) | **Exchange rate is accepted from the client payload with no server-side cross-check against `daily_exchange_rates`** — `v_rate := COALESCE((p_payload->>'exchange_rate')::numeric, 1)` only validates `> 0` (line 191-194) | ⚠️ **Gap** — see `security-findings.md` for severity/remediation |

**Migration ordering**: `20260803270000_pos_offline_sync.sql` depends on
`create_pos_cart`, `add_cart_line`, `checkout_pos_cart_with_tenders`, and
`resolve_item_price` — all of which are defined in *earlier*-dated migrations
(`20260723230000_sales_pos.sql` and predecessors), so there is **no forward-reference
ordering risk** in this specific migration. It does *not* conflict with or duplicate
`20260803140000_batch1_pos_companion_realtime.sql`, `20260803151000_batch1_pos_park_cart.sql`,
or `20260803251000_pos_quotations.sql` — those add orthogonal POS capabilities
(companion-device pairing, parked carts, quotations) rather than competing offline paths.

## 8. Summary table

| Domain | Verdict | Evidence |
|---|---|---|
| Inventory ledger | **Structurally sound pattern, minor bypass risk** | `stock_entries`/`stock_levels` correct; 2 non-core writers to `stock_levels` need follow-up |
| Customer/supplier | **Valid trade-off, not unified** | Separate `customers`/`suppliers` tables, no link |
| Finance ledger | **Sound pattern; DB-level immutability unverified** | `journal_entries` matches `account.move`; append-only enforcement needs a follow-up read of grants/triggers |
| Procurement | **Strong, faithful to reference pattern** | Full requisition→RFQ→PO→GRN→landed-cost chain |
| Commerce/cart | **Deliberately unified, POS-adjacent sprawl** | One `pos_carts` for storefront+till; 5+ satellite POS tables added incrementally |
| AI autonomous layer | **Best-integrated bolt-on found in this audit** | Reuses existing tables/reports; ADR and code match; worker-only mutation functions |
| Offline POS sync | **Well-engineered idempotency; one real gap** | Double-layered idempotency; exchange-rate trust gap (see security findings) |
