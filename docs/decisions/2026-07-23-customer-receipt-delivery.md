# Customer transaction summaries + PDF receipts

- Date: 2026-07-23
- Lane: `@backend_agent` (outbox + PDF + send), `@web_agent` (signed download URL), `@management_app_agent` (POS collect phone/email/WhatsApp)
- Status: accepted

## Decision

After a customer-facing sale (or other money transaction that produces a receipt), the customer receives:

1. **SMS (text)** — short **transaction summary** (store, date/time, total, currency, payment method, document number). The **last line(s)** must include a **link to download the PDF receipt** (signed, time-limited URL; no auth wall for casual customers if possible).
2. **PDF receipt** delivered by **email and/or WhatsApp** (customer preference / what contact we have at checkout). Same PDF artifact as the SMS download link.

This channel is **separate from manager ops SMS** (`docs/decisions/2026-07-23-manager-sms-key-events.md`). Managers get ops alerts; customers get receipts.

## Content rules

| Channel | Content |
|---------|---------|
| SMS body | Summary only + PDF link at bottom (keep under typical SMS length; link may use short URL) |
| Email | Subject + brief body + **PDF attachment** (and/or link) |
| WhatsApp | Short confirmation + **PDF** (document message or link if provider requires) |
| PDF | Full receipt: lines, core charges, totals USD/ZiG, tender, store identity — **no ZIMRA/fiscal QR or tax authority payloads** |

## Triggers (when to send)

- Sales invoice / POS checkout **posted** (primary)
- Payment captured against invoice (if receipt not already sent with sale — avoid duplicate spam; prefer one receipt per completed sale unless payment-only document)
- Credit note / return posted (credit receipt PDF + summary SMS)
- Optional later: delivery completed with DN copy (not required for v1)

## Preferences / contacts

- Capture at checkout: `phone_e164`, `email`, `whatsapp_e164` (may equal phone)
- Customer profile prefs: `sms_receipts`, `email_receipts`, `whatsapp_receipts` (default: SMS on if phone present; email/WhatsApp on if address present)
- POS must not block sale if send fails — queue to outbox and retry

## Technical shape (implement with Phase 5 emit + Phase 13 send)

- `customer_receipt_outbox` (or extend notification outbox with `audience = customer`) — channels: `sms` \| `email` \| `whatsapp`
- PDF generated server-side → Storage (private bucket) → signed download URL for SMS/WhatsApp link
- Idempotent: one SMS + one email + one WhatsApp per `(document_type, document_id, channel)` unless explicit resend
- Domain emit from Phase 5 sales post: e.g. `customer_receipt_requested`

## Explicitly out of scope

- ZIMRA / FDMS / fiscal device QR on PDF or SMS
- Manager ops alerts (different catalog)
- Marketing / My Garage promo SMS (separate)

## Why

Customers expect a portable proof of purchase; SMS summary + PDF link covers low-email users; email/WhatsApp covers full document delivery.

## Consequences

- Phase **5** must emit receipt-outbox rows (or domain event) when invoice/return posts — even before gateway/PDF worker exists.
- Phase **13** (or dedicated receipts slice) wires SMS + email + WhatsApp providers, PDF render, signed URLs.
- Do not put full line-item tables in SMS; put them in the PDF.
- Never re-litigate “PDF only via email” — WhatsApp + SMS link are required channels when contacts exist.
