# Manager SMS key-event notifications

- Date: 2026-07-23
- Lane: `@backend_agent` (events + send), `@web_agent` / `@management_app_agent` (recipient preferences UI)
- Status: accepted

## Decision

The ERP must send **SMS notifications to selected managers** for operational key events, including (non-exhaustive):

| Event | Example trigger |
|-------|-----------------|
| Order received | New sales order / POS checkout created |
| Order completed | Order reaches completed status |
| Payment received | ContiPay (or cash/AR) payment captured |
| Delivery completed | Delivery job marked delivered |

Additional events may be added later (low stock, transfer approved, quarantine return) without changing this decision.

## Rules

1. **Opt-in by manager** — only users flagged as notification recipients for that event type receive SMS (not all staff).
2. **Server-side only** — SMS provider credentials never ship to clients; send via Edge Function / queue worker.
3. **Idempotent** — one SMS per event occurrence (dedupe by `event_id`).
4. **No ZIMRA / fiscal SMS** — messages are operational alerts only, not tax/fiscal payloads.
5. **Audit** — log send attempts (to, template, event, status) for support; do not log full message body with PII beyond necessity.
6. **Local SMS APIs** — prefer Zimbabwe-capable SMS gateways (aligned with blueprint “local SMS APIs”); provider chosen in implementation phase.

## Schema sketch (Phase 13+)

- `manager_sms_preferences` — `user_id`, `event_type`, `phone_e164`, `enabled`
- `sms_outbox` / `sms_delivery_log` — queue + status
- Domain events emitted from sales/payments/logistics modules → outbox

## Why

Managers need real-time ops visibility without living in the app; SMS is the reliable channel for counter/warehouse/dispatch leads.

## Consequences

- Master plan Phase 13 expands beyond marketing SMS to **ops key-event SMS**.
- Sales, payments, and logistics phases must emit domain events (or write outbox rows) even before SMS is live.
- Preference UI lives on management surfaces; defaults off until configured.
