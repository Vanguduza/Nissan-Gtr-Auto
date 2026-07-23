# Manager SMS key-event notifications

- Date: 2026-07-23 (updated same day — full catalog + early schema)
- Lane: `@backend_agent` (events + outbox + send), `@management_app_agent` (prefs UI)
- Status: accepted

## Decision

Operational **SMS to selected managers** (opt-in per event). Schema and outbox ship **early** (with Phase 1 follow-on migration); SMS gateway wiring remains Phase 13.

## Full key-event catalog

Default **enabled for subscription UI** (managers still must opt in). Priority hints delivery urgency for future rate-limiting.

### Sales / orders

| Code | When to text | Priority |
|------|----------------|----------|
| `order_received` | New sales order / POS checkout created | high |
| `order_completed` | Order marked completed / fulfilled | high |
| `order_cancelled` | Order cancelled after acceptance | high |
| `order_on_hold` | Credit/hold block on order | normal |
| `large_order` | Order total ≥ configured threshold | high |
| `return_initiated` | Return / credit note opened | high |
| `return_completed` | Return posted; stock to Quarantine | high |

### Payments

| Code | When to text | Priority |
|------|----------------|----------|
| `payment_received` | Payment captured (ContiPay / cash / bank) | high |
| `payment_failed` | Payment attempt failed | high |
| `payment_partial` | Partial payment on invoice | normal |
| `refund_issued` | Refund / store credit issued | high |
| `ar_overdue` | Invoice past due (digest-capable) | normal |

### Inventory / warehouse

| Code | When to text | Priority |
|------|----------------|----------|
| `stock_received` | Goods receipt posted | normal |
| `low_stock` | On-hand ≤ reorder point | high |
| `stockout` | On-hand hit zero on active SKU | high |
| `transfer_pending_approval` | Dual-auth transfer awaiting second signature | high |
| `transfer_completed` | Warehouse transfer finalized | normal |
| `transfer_rejected` | Transfer denied | normal |
| `quarantine_received` | Item moved into Quarantine | high |
| `serial_moved` | High-value/serialized assembly transferred/sold | normal |

### Procurement

| Code | When to text | Priority |
|------|----------------|----------|
| `po_created` | Purchase order created | normal |
| `po_approved` | PO approved for send | normal |
| `po_received` | GRN / PO receipt against PO | high |
| `po_overdue` | Expected delivery date passed | normal |
| `supplier_mismatch` | Receipt qty/price vs PO variance over threshold | high |

### Logistics / delivery

| Code | When to text | Priority |
|------|----------------|----------|
| `delivery_dispatched` | Delivery job left warehouse | normal |
| `delivery_completed` | Delivered to customer | high |
| `delivery_failed` | Failed / returned to depot | high |
| `delivery_delayed` | ETA slipped past threshold | normal |

### Finance / ops

| Code | When to text | Priority |
|------|----------------|----------|
| `journal_post_rejected` | Unbalanced/unauthorized post attempt (ops alert) | high |
| `day_close_completed` | Books close wizard finished | normal |
| `cash_drawer_variance` | POS drawer variance over threshold | high |

### HR / access (sparse — only actionable)

| Code | When to text | Priority |
|------|----------------|----------|
| `staff_no_show` | Rostered staff missed clock-in window | normal |
| `payroll_run_ready` | Gross payroll run ready for review (no tax) | normal |

### Explicitly NOT SMS (noise / wrong channel)

- Every catalog browse, cart line add, GPS ping, successful login
- Marketing/My Garage promos (separate marketing SMS channel)
- ZIMRA / fiscal / tax messages (forbidden)

## Rules

1. **Opt-in by manager** — per `user_id` + `event_code`; default **off**.
2. **Server-side only** — provider secrets never in clients.
3. **Idempotent** — unique `(event_code, dedupe_key)` on outbox.
4. **No ZIMRA / fiscal SMS.**
5. **Audit** — log recipient, event, status; minimize PII in body logs.
6. **Emit early** — domain modules write `domain_events` / call `enqueue_manager_sms` even before gateway is live.

## Schema (implemented early)

- `sms_event_catalog` — code, description, priority, is_active
- `manager_sms_preferences` — user_id, event_code, phone_e164, enabled
- `domain_events` — append-only event log
- `sms_outbox` — pending/sent/failed queue for manager SMS

## Why

Managers need ops visibility; catalog + outbox early avoids retrofit when sales/inventory/logistics land.

## Consequences

- Migration `20260723110000_manager_sms_events.sql` is required after Phase 1.
- Phase 13 wires SMS provider + worker; prefs UI can land with management app.
- Child modules must use catalog codes above — no ad-hoc string events.
