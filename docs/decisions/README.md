# Architecture & product decisions (claude-mem backup)

Agents: before re-deriving schema or conventions from blueprint PDFs, check this folder
and claude-mem (`search` → `timeline` → `get_observations`).

## How to add a decision

Create `YYYY-MM-DD-short-title.md`:

```markdown
# Title

- Date:
- Lane: @backend_agent | @web_agent | ...
- Status: accepted | superseded

## Decision
One paragraph.

## Why
Constraints / alternatives rejected.

## Consequences
What agents must not re-litigate.
```

## Seed decisions

| File | Topic |
|------|--------|
| `2026-07-23-orchestration-baseline.md` | Agent pipeline, exclusions |
| `2026-07-23-remote-supabase-project.md` | Hosted Supabase |
| `2026-07-23-manager-sms-key-events.md` | Manager ops SMS catalog |
| `2026-07-23-customer-receipt-delivery.md` | Customer SMS summary + PDF via email/WhatsApp |
| `2026-07-23-company-domain.md` | Public domain `nissangtrauto.co.zw` |
| `2026-07-23-storefront-autodoc-logo.md` | AutoDoc-inspired shop IA + official logo |
| `2026-07-23-autodoc-shop-features.md` | AutoDoc shop adopt / later / skip + phases |
| `2026-07-24-paynow-payment-rail.md` | Paynow + ContiPay payment rails (Phase 13) |
| `2026-07-24-customer-self-pay.md` | Customer ContiPay/Paynow self-pay (not counter-only) |
| `2026-07-25-web-management-parity-rbac.md` | Web `/staff` management fallback + nav RBAC; Bridge-First |
| `2026-07-25-in-app-live-chat.md` | In-app live chat (Realtime); WA optional |
| `2026-07-25-dedicated-delivery-app.md` | Dedicated driver Android app; assignment in management; privacy-safe customer track |
| `2026-07-24-whatsapp-parts-finder-bot.md` | WhatsApp parts-finder bot (optional channel) |