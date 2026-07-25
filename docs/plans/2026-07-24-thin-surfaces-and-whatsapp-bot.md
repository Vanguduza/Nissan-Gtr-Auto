# Thin surfaces backlog + WhatsApp parts-finder bot

- Status: draft
- Lane(s): `@backend_agent` (WhatsApp bot primary); then ordered follow-ons per gap (see Handoff)
- Skills needed: (none for bot messaging); `/qr-inventory-workflow` only when QR/print work starts; Bridge-First N/A for WA Cloud API
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md)
- Decision: [`whatsapp-parts-finder-bot`](../decisions/2026-07-24-whatsapp-parts-finder-bot.md) (Accepted)
- Sources: thin-audit agent `d1fd3e85`; WhatsApp agent `62bed301`

> **Supersession (2026-07-25):** In-app live chat is **in scope** and ships per ADR [`in-app-live-chat`](../decisions/2026-07-25-in-app-live-chat.md) and plan [`2026-07-25-in-app-live-chat`](./2026-07-25-in-app-live-chat.md). Gap #9 and the out-of-scope line that excluded an in-app chat widget are **overridden**. WhatsApp bot/CTA content in this plan remains valid as an **optional channel**.

## Goal

Close the gap between a largely live backend (Phases 1–16) / configurable web commerce and thin client surfaces — and ship a Meta Cloud API WhatsApp parts-finder bot that searches catalog and deep-links PDP, with human handoff, without conflating receipt delivery.

## Thin-surface snapshot (do not re-audit)

| Area | State |
|------|--------|
| Backend Phases 1–16 | Largely live |
| Web commerce | Live when env configured |
| Many UIs | Thin / stub / empty |
| WhatsApp today | `wa.me` CTA only + receipt channel stub |

**Top gaps (priority order for product fill-in):**

1. Real PSP secrets + non-stub ContiPay/Paynow workers
2. Receipt PDF + SMS/WhatsApp **delivery** workers (outbox drain)
3. PDP photos / visual canvas
4. GPS map delivery product UI
5. Mobile Live default (Fake still default in places)
6. POS / warehouse management screens (empty)
7. QR / print / biometric native bridges
8. Finance UI
9. Live chat (none) → ~~addressed here via WhatsApp bot + human handoff~~ **OVERRIDDEN** — see [`in-app-live-chat`](../decisions/2026-07-25-in-app-live-chat.md)

## WhatsApp bot — accepted approach

- **Stack:** Meta Cloud API → `whatsapp-webhook` Edge Function → `search_catalog` via privileged Edge server role + rate limits
- **Modes:** `part` \| `vin` \| `model` \| `pnc`
- **UX:** reply with top hits + deep-link to web PDP; escalate to human on same/counter number
- **Separation:** customer **receipt** WhatsApp channel stays in `process-customer-receipts` / outbox — **not** bot dialog
- **AuthZ:** do **not** `GRANT EXECUTE` on `search_catalog` to `anon`; bot only via privileged Edge server role

### Meta setup checklist (ops; secrets in Edge env only)

- [ ] Meta Business + WhatsApp Cloud API app / phone number
- [ ] Verify webhook (`WHATSAPP_VERIFY_TOKEN`)
- [ ] `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, app secret for signature verify
- [ ] Rate-limit / abuse budget per WA `from` id
- [ ] Human handoff number (counter/sales) documented for agents and bot copy
- [ ] Keep receipt sender identity/config distinct from bot dialog number if product requires it

## Acceptance criteria

- [ ] Edge `whatsapp-webhook`: Meta verify + signed inbound; mode parse; `search_catalog` (privileged Edge role); outbound text + PDP deep-links; rate-limited; no anon RPC grant
- [ ] Human handoff path (keyword or empty-results) points to configured counter/sales number
- [ ] Receipt WhatsApp delivery unchanged and separate from bot conversation flow
- [ ] Decision stub accepted or superseded before prod secrets
- [ ] Exclusion grep clean (no ZIMRA / payroll tax / browser QR)
- [ ] Thin-surface follow-ons tracked below remain out of this bot PR unless explicitly scoped

## Paths in scope (bot slice)

- `supabase/functions/whatsapp-webhook/` (new)
- Optional thin shared types under `packages/shared/` for WA inbound/outbound DTOs (no secrets)
- `docs/decisions/2026-07-24-whatsapp-parts-finder-bot.md`
- Edge env placeholders only (never commit tokens)
- Smoke: webhook verify + mocked catalog hit + rate-limit deny

## Out of scope

- Full POS/warehouse/finance UI builds; QR/print/biometric bridge impls; GPS map product; canvas/photos pipeline
- Turning Fake→Live defaults across mobile (separate bind work)
- ~~Live in-app chat widget~~ (**OVERRIDDEN** — in-app chat ships; see ADR); Meta ads / broadcast / commerce catalog sync
- Granting `search_catalog` to anon or embedding catalog search in client WA SDKs
- Mixing receipt PDF send into bot dialog turns; ZIMRA / fiscal QR on any WA message
- Real PSP secret values in repo

## Risks / exclusions

- **Abuse:** open webhook without signature verify or per-sender rate limits → catalog scrape
- **PII:** log WA `from` carefully; no secrets in logs
- **Confusion:** one number for bot + receipts may mix threads — document ops choice
- **Bridge-First:** N/A for Cloud API messaging; still required for device QR/print/GPS when those gaps are filled
- No ZIMRA; invoices/receipts remain tax-agnostic

## Suggested follow-on epics (separate plans / PRs)

| Gap | Lane |
|-----|------|
| PSP + receipt/SMS workers | `@backend_agent` |
| Photos / canvas | `@web_agent` + pipeline as needed |
| GPS map product | `@management_app_agent` / mobile lanes |
| Mobile Live default | `@android_agent` / `@ios_agent` |
| POS / warehouse / finance UI | `@management_app_agent` (+ `@finance_agent` for finance bind) |
| QR / print / biometric | `@hardware_mobile_agent` |
| In-app live chat | `@backend_agent` → `@web_agent` → mobile lanes — see [`2026-07-25-in-app-live-chat`](./2026-07-25-in-app-live-chat.md) |

## Handoff

1. Accept decision stub → implement bot in **`@backend_agent`**
2. `/supabase-rls-auditor` if any new tables; `/security-reviewer` (webhook auth, privileged Edge role, rate limits)
3. `/verifier`
4. `/manager` sequences thin-surface epics above one lane at a time
5. Do not start product UI fill-ins in the same PR as the WhatsApp bot
6. In-app live chat is a **separate** epic (not deferred to WhatsApp) — [`2026-07-25-in-app-live-chat`](./2026-07-25-in-app-live-chat.md)
