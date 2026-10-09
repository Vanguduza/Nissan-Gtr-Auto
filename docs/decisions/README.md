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
| `2026-08-03-ecocash-direct-c2b.md` | EcoCash direct C2B (WhatsApp accepted; cross-platform planned) |
| `2026-08-03-offline-sqlcipher-pos-cache.md` | Offline SQLCipher POS cache — **accepted** (#57/#59; #58 login still Later) |
| `2026-08-12-principal-vs-dial-agency.md` | Principal distributor vs DIAL Dial-a-Spare agency — adopt patterns, not marketplace locks |
| `2026-07-24-customer-self-pay.md` | Customer ContiPay/Paynow self-pay (not counter-only) |
| `2026-07-25-web-management-parity-rbac.md` | Web `/staff` management fallback + nav RBAC; Bridge-First |
| `2026-07-25-in-app-live-chat.md` | In-app live chat (Realtime); WA optional |
| `2026-07-25-dedicated-delivery-app.md` | Dedicated driver Android app; assignment in management; privacy-safe customer track |
| `2026-07-24-whatsapp-parts-finder-bot.md` | WhatsApp parts-finder bot (optional channel) |
| `2026-07-25-pos-scan-session-pairing.md` | Tablet/phone POS scan session pairing (Bridge-First) |
| `2026-07-25-auth-otp-fail-closed.md` | Email/phone OTP signup/login; fail-closed + local stub flag |
| `2026-07-25-pos-receipt-contact-customer-bind.md` | Checkout receipt contacts + customer_id bind |
| `2026-08-04-gsf-ux-behaviour-specification.md` | GSF Car Parts APK reverse-eng UX/IA spec (reference only; no code/assets reused) |
| `2026-08-13-customer-epc-shop-stock-context.md` | Shared OEM context: customer EPC/search/shop ↔ staff add-stock / item edit |
| `2026-08-15-standalone-adaptive-pos.md` | Greenfield `apps/android-pos` till; keep sales SoR; vehicle-latched lookup |
| `2026-08-15-staff-edge-auth-google-only.md` | Staff middleware cookie gate; Google-only OAuth; password-reset UI |