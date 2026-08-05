# Offline SQLCipher POS cache

- Date: 2026-08-03
- Lane: `@management_app_agent` (tablet local DB); `@backend_agent` (sync RPCs)
- Status: **accepted**
- Plan catalog: features **#57** + **#59** in [`docs/plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md`](../plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md)
- Related: **#58** offline login remains **Later** (not authorized by this ADR)

## Decision

Ship an encrypted offline POS path for the **tablet** management APK:

1. **Local store** — SQLCipher database; passphrase wrapped via Android Keystore (`EncryptedSharedPreferences`). Cache retail catalog + warehouse stock snapshot and pending offline sales only. **Never** cache manager / Admin|shop-manager approval tokens or Argon2 password hashes (that is #58).
2. **Pull** — when online, sales staff call `pull_pos_offline_snapshot(warehouse_id)` and replace the local catalog/stock rows for that warehouse.
3. **Queue** — when offline (or RPC unreachable), counter sales at snapshot list price with **cash** tender are recorded locally with a client-generated `client_sale_id` (UUID) and decremented local saleable qty.
4. **Replay** — when online, `replay_offline_pos_sale(client_sale_id, payload)` creates cart → lines → `checkout_pos_cart_with_tenders` via existing SoR. Idempotent on `client_sale_id` (unique receipt row). Price drift / stock shortfalls surface as conflicts; failed rows stay queued for attendant review.
5. **WorkManager / foreground drain** — on reconnect and POS open, drain the pending queue; WorkManager backs the same engine.

## Sync protocol (summary)

| Direction | RPC / action | Payload |
|-----------|--------------|---------|
| Pull | `pull_pos_offline_snapshot(p_warehouse_id)` | `{ warehouse_id, pulled_at, currency, items[{stock_item_id, oem, uom_id, unit_price, core_charge, saleable_qty, description}] }` |
| Replay | `replay_offline_pos_sale(p_client_sale_id, p_payload)` | Payload: warehouse, currency, exchange_rate, device_id, lines[{stock_item_id, uom_id, qty, expected_unit_price}], tenders[{tender, amount, currency}], optional receipt contacts, sold_at |
| Idempotency | `pos_offline_sale_receipts.client_sale_id` UNIQUE | Duplicate replay returns existing `invoice_id` |
| Conflict | `offline_price_conflict` / checkout stock errors | Client marks sale `conflict` / `failed`; does not invent ledger rows |

## Online-only gates (deliberate)

- Discount, void, refund, price override (manager reauth + live RPC).
- EcoCash / Paynow / ContiPay / live payment intents.
- Companion scan pairing / Realtime cart poll.
- Named credit customers (credit_hold / credit_limit) — offline is walk-in cash only.
- Quotations create/send/convert.
- Offline login / Argon2 cache (#58).
- Any action needing live Admin|shop-manager approval tokens.

## Why (adopt-first)

- Prior stub deferred this for privilege-escalation risk; this ADR accepts offline **sales recording** with a hard privilege boundary (no manager tokens, cash walk-in only).
- TailPOS (GPLv3 / abandoned ERPNext sync) skipped per OSS audit — **Build** thin SQLCipher + replay into existing cart/checkout SoR.
- Online RPC + RLS remains SoR; local DB is a cache + outbox, not a second ledger.

## Consequences

- Agents **may** implement SQLCipher POS cache, sale queue, WorkManager/foreground sync, and the pull/replay RPCs under this ADR.
- Agents **must not** treat this as authorization for PIN/NFC/badge/biometric stubs or Argon2 offline login (#58).
- Corrections to synced sales remain ledger-immutable (reversing entries / finance refund) — never edit posted invoices from the tablet outbox.
