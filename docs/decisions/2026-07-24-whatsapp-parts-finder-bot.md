# WhatsApp parts-finder bot

- Date: 2026-07-24
- Lane: `@backend_agent` (Edge webhook + service_role catalog search)
- Status: Accepted (WhatsApp bot); live-chat-substitute rationale **partially superseded** by [`in-app-live-chat`](./2026-07-25-in-app-live-chat.md)
- Related: [`customer-receipt-delivery`](./2026-07-23-customer-receipt-delivery.md), plan [`thin-surfaces-and-whatsapp-bot`](../plans/2026-07-24-thin-surfaces-and-whatsapp-bot.md)

> **Supersession (2026-07-25):** The implication that human handoff / WhatsApp substitutes for a full live-chat product is **overridden**. In-app live chat **ships** — see [`in-app-live-chat`](./2026-07-25-in-app-live-chat.md). This ADR remains **Accepted** for the WhatsApp parts-finder bot itself; WA stays an optional channel alongside in-app chat.

## Decision

Accept an **automated WhatsApp parts-finder bot** (Meta Cloud API → `whatsapp-webhook` → `search_catalog` with service_role + rate limits) with modes part/vin/model/pnc, PDP deep-links, and **human handoff** to the same/counter sales number. Keep **receipt WhatsApp delivery** on the existing outbox/worker path — separate from bot dialog. Do **not** grant `search_catalog` to `anon`.

## Why

- Storefront exposes `wa.me` CTA; in-app live chat is a separate product (see [`in-app-live-chat`](./2026-07-25-in-app-live-chat.md)).
- Catalog search already exists server-side; exposing it only through a verified, rate-limited Edge webhook avoids opening RPC to anonymous clients.
- Human handoff on WhatsApp remains useful as an optional channel alongside in-app chat.

## Consequences

- Meta secrets live only in Edge env; webhook must verify signatures.
- Bot replies must not carry fiscal/ZIMRA content; Bridge-First is N/A for Cloud API messaging.
- Receipt channel workers remain independent of bot conversation state.
- Prod go-live waits on Meta setup checklist in the thin-surfaces plan.

## Exclusions

- No ZIMRA / fiscalisation on WA messages or receipts.
- Bridge-First N/A for WhatsApp Cloud API messaging (device QR/print/GPS still bridge-only when implemented).
