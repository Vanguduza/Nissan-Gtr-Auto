# Phase 5b — Warranty / serial claims

- Status: **implemented** (smoke blocked + gate agents pending)
- Lane(s): `@backend_agent` (primary); `@management_app_agent` (RPC/API contracts only — full UI Phase 12)
- Skills needed: (none required; `/qr-inventory-workflow` only if serial lookup via QR payload)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 5b
- Prior: Phase 4 serials/quarantine (`20260723220000_inventory_ops.sql`); Phase 5 invoices/CN (`20260723230000_sales_pos.sql`)
- Decisions: [`2026-07-23-manager-sms-key-events.md`](../decisions/2026-07-23-manager-sms-key-events.md) (`serial_moved`, `return_*`, `quarantine_received`)
- RPC contract: [`docs/contracts/warranty-claims-rpc.md`](../contracts/warranty-claims-rpc.md)

## Goal

Light warranty claim against a sold serial and/or sales invoice (optional batch hint): open → approved|rejected → closed, with approved return-to-stock always hopping Quarantine and optional replacement issue / credit note — no fiscal or warranty-authority tax payloads.

## Acceptance criteria

- [x] Claim create requires `stock_serial_id` **or** `sales_invoice_id` (or both); reject if neither
- [x] Status workflow: `open` → `approved` | `rejected` → `closed` (no skip; reject cannot approve later without new claim)
- [x] Approved return-to-stock uses `post_return_to_quarantine` / CN Quarantine path — never MAIN/saleable direct
- [x] Approved replacement issues from saleable stock (separate movement); returned unit still Quarantine-first
- [x] Optional link to `post_return_credit_note` when financial credit chosen
- [x] RLS on claim tables in same migration; staff write, finance read
- [x] Emit cheap domain events: claim open/approve/reject; reuse `quarantine_received` / `serial_moved` / `return_*` on stock/CN side
- [x] No ZIMRA / fiscal / warranty-authority tax payloads; no payroll tax; Bridge-First

## Reuse (do not reinvent)

| Existing | Use for |
|----------|---------|
| `stock_serials` (+ statuses) | Link claim; mark quarantine/sold transitions |
| `post_return_to_quarantine`, `post_return_credit_note` | Return-to-stock / CN — Quarantine only |
| Sales invoice + lines (`SINV-` / `CN-`) | Invoice linkage + optional credit |
| Stock issue / transfer RPCs from Phase 4 | Replacement outbound (saleable → customer) |
| `emit_domain_event`, event catalog | Claim + reuse inventory/return codes |
| `naming_series` / `next_series_value` | Prefix e.g. `WC-` |

## Tables / RPCs

**Tables** (one migration + RLS):

- `warranty_claims` — `document_number`, `status` (`open`|`approved`|`rejected`|`closed`), `stock_serial_id` nullable, `sales_invoice_id` nullable, optional `stock_batch_id` / `stock_item_id`, `customer_id`, `resolution` (`replacement`|`credit_note`|`reject_only`|null until decide), FKs to resulting `credit_note_id` / replacement `stock_entry_id` / quarantine movement id, `currency` if credit, notes, `created_by`, `decided_by`, timestamps; CHECK: serial **or** invoice present
- `warranty_claim_events` (optional audit) — status transitions + actor; or rely on `domain_events` only if cheaper

**RPCs:**

1. `open_warranty_claim(serial?, invoice?, batch?, notes)` — validate linkage; name `WC-…`; status `open`; emit claim-opened (or reuse high-signal existing code + payload)
2. `approve_warranty_claim(claim_id, resolution, lines?)` — `open`→`approved`; if return-to-stock → Quarantine RPC; if replacement → issue saleable; if credit → `post_return_credit_note`; emit events
3. `reject_warranty_claim(claim_id, reason)` — `open`→`rejected`
4. `close_warranty_claim(claim_id)` — from `approved`|`rejected` → `closed` (idempotent guard)

**Shipped:** migration `20260724030000_warranty_claims.sql`; helper `post_stock_issue` (ISS-) for replacement; smoke `supabase/tests/phase5b_warranty_smoke.sql`.

## Paths in scope

- `supabase/migrations/` (new `*_warranty_claims.sql` + smoke test)
- `packages/supabase-client/` (regen types)
- `packages/shared/` only if tiny status/resolution constants
- Brief RPC contract note for management app (params/returns) — no Android UI

## Out of scope

- Full management / POS warranty UI (Phase 12)
- Manufacturer / OEM warranty portal, claim forms to Nissan, RMA logistics carriers
- Fiscal, FDMS, ZIMRA, or any warranty-authority tax payload
- New COA accounts; ledger posts only via existing CN / stock paths
- SMS gateway (emit only); customer self-serve claims on web
- Cycle count (4b), procurement, storefront changes

## Risks / exclusions

- NO ZIMRA / payroll tax; Bridge-First — no HTML5 QR
- Quarantine Returns Protocol: never exchange direct into saleable MAIN
- Serial already `quarantine`/`scrapped` — refuse duplicate open claim or document override rule in migration comment
- Do not mutate posted invoices/journals; CN + reversing paths only

## Ordered tasks (`@backend_agent`)

1. Migration: enum/status + `warranty_claims` (+ optional events) + `WC-` naming + CHECK (serial|invoice) + RLS/grants
2. `open_warranty_claim` RPC — linkage validation, series, `open`, domain emit
3. `approve_warranty_claim` — Quarantine-first return-to-stock; optional replacement issue +/or CN; wire FKs + events
4. `reject_warranty_claim` + `close_warranty_claim` status guards
5. Smoke SQL: open without linkage fails; approve→quarantine; replacement does not skip QUAR; reject→close; RLS denial; assert no fiscal columns
6. Regen `database.types.ts`; optional shared status constants
7. One-pager RPC contract note for `@management_app_agent` (Phase 12 UI later)

## Gate

`/supabase-rls-auditor` → `/security-reviewer` → `/verifier` → `/manager` done gate

## Handoff

1. Implement in `@backend_agent`
2. Management UI contracts notes only — full UI Phase 12
3. Then gate above
