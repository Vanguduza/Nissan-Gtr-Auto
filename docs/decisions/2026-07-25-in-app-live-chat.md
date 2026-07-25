# In-app live chat

- Date: 2026-07-25
- Lane: `@backend_agent` (schema/RLS/Realtime) then `@web_agent` / mobile lanes for UI
- Status: Accepted
- Related: plan [`in-app-live-chat`](../plans/2026-07-25-in-app-live-chat.md); staff RBAC [`web-management-parity-rbac`](./2026-07-25-web-management-parity-rbac.md)
- Supersedes (partial): live-chat "out of scope / WhatsApp substitute" language in [`whatsapp-parts-finder-bot`](./2026-07-24-whatsapp-parts-finder-bot.md) and plan [`thin-surfaces-and-whatsapp-bot`](../plans/2026-07-24-thin-surfaces-and-whatsapp-bot.md)

## Decision

Ship **in-app live chat** across web (customer + staff inbox), iOS customer, Android customer, and Android management (staff reply). Transport is **Supabase Realtime** on `chat_threads` / `chat_messages` (participants if needed) with RLS. **WhatsApp bot and `wa.me` CTA remain optional channels** — not a substitute for in-app chat.

## Why

- Product mandate: customers and staff need same-thread support/parts chat inside each app surface.
- Prior thin-surfaces / WhatsApp ADR treated live chat as out of scope and used WA human handoff as substitute; that exclusion is **overridden**.
- Backend already has `has_staff_role`, staff web RBAC, and a Realtime publication pattern (`delivery_locations`).

## Consequences

- Agents must implement chat tables + RLS before UI; do not re-open "WhatsApp-only support."
- Keep WhatsApp parts-finder bot and receipt WhatsApp delivery as separate optional paths.
- Prefer authenticated customers; guest threads only if cheap and RLS-safe.
- Shared types in `packages/shared/` or `packages/supabase-client/`; no secrets in git.
- Bridge-First N/A for chat; still no ZIMRA / payroll tax / browser QR.
- Optional notify Edge Function may use the privileged server role; never expose that key to clients.

## Exclusions

- No ZIMRA / fiscalisation in chat content or notifications.
- No payroll tax.
- Bridge-First still required for device QR/print/GPS — not for chat messaging.
