# In-app live chat (all platforms)

- Status: draft
- Lane(s): `@backend_agent` → `@web_agent` → `@ios_agent` + `@android_agent` + `@management_app_agent`
- Skills needed: (none); Bridge-First N/A for chat
- Decision: [`in-app-live-chat`](../decisions/2026-07-25-in-app-live-chat.md) (Accepted — **overrides** live-chat exclusion in WhatsApp thin-surfaces plan)
- Related: staff RBAC [`web-management-parity-rbac`](../decisions/2026-07-25-web-management-parity-rbac.md); Realtime pattern in `delivery_locations` publication

## Goal

Ship **in-app live support/parts chat** (threads + messages + Realtime) on web customer + staff inbox and equivalent mobile screens; keep WhatsApp CTA/bot as an optional channel.

## Reality check (scaffold)

| Surface | Fit |
|---------|-----|
| Backend | Migrations + RLS + `has_staff_role` mature; Realtime pub pattern exists (`delivery_locations`) |
| Web | Storefront + `/staff/*` with `STAFF_NAV_ITEMS` / `StaffGate` — add Chat nav like other modules |
| iOS / Android customer | Thin Live shells (auth, cart, orders…) — add chat feature screens |
| Android management | Thin staff features — add staff inbox/reply module |

## Acceptance criteria

- [ ] Customer can start a support/parts thread (auth preferred; guest only if trivial)
- [ ] Staff (`admin`, `sales`, `warehouse` as appropriate) see open threads, claim, reply, close
- [ ] Real-time updates via Supabase Realtime on `chat_messages` (and threads status if needed)
- [ ] Tables: `chat_threads`, `chat_messages` (+ `chat_participants` only if claim/unread needs it)
- [ ] RLS: customers own threads only; staff with chat roles see open/assigned; `service_role` for optional notify edge
- [ ] Unread counts desirable; soft presence/typing optional (defer if blocks slice)
- [ ] WhatsApp `wa.me` CTA kept; Chat entry points in header / account / PDP secondary
- [ ] UX min: thread list + bubbles + composer; staff filters open / mine / closed
- [ ] Vertical slice locally: customer web → staff web Realtime → reply → customer; mobiles have functional equivalent screens
- [ ] Shared types in `packages/shared/` and/or `packages/supabase-client/`; no secrets in git
- [ ] Exclusion grep clean (no ZIMRA / payroll tax / browser QR)

## Paths in scope

- `supabase/migrations/*_live_chat.sql` (tables, RLS, Realtime `ALTER PUBLICATION`)
- Optional: `supabase/functions/` notify-on-message edge (staff/customer push or outbox hook)
- `packages/supabase-client/` types + thin query helpers; optional DTOs in `packages/shared/`
- `apps/web/` customer chat UI + `/staff/chat` (+ `staff-auth` nav roles: admin/sales/warehouse)
- `apps/ios/`, `apps/android-customer/` customer thread list + composer
- `apps/android-management/` staff inbox / reply

## Out of scope

- Replacing WhatsApp bot/receipts; Meta ads/broadcast; end-to-end encryption product
- File/image attachments v1 (text-first unless already trivial via Storage)
- AI auto-reply / chatbot inside in-app thread
- ZIMRA / fiscal content; payroll tax; browser QR/GPS

## Risks / exclusions

- **RLS leaks:** staff must not read other customers’ closed threads unless role-allowed; customers never see staff-only metadata beyond messages
- **Realtime:** publish only chat tables needed; rely on RLS for channel auth
- **Guest threads:** prefer auth; if guest, bind via secure token / later claim — do not open anon INSERT broadly
- No secrets in git; Bridge-First N/A for chat UI

## Implementation order

1. **`@backend_agent`**: migration + RLS + Realtime publication + optional notification edge
2. **`@web_agent`**: customer chat UI + `/staff/chat` inbox (RBAC like other staff pages)
3. **`@ios_agent` + `@android_agent` + `@management_app_agent`**: equivalent screens
4. **`/security-reviewer`** then **`/verifier`**
5. **`/manager`** done gate

## Handoff

1. Accept ADR → implement backend slice first
2. Web vertical slice before mobile parity
3. `/supabase-rls-auditor` on new tables; `/security-reviewer`; `/verifier`
4. Do not re-litigate “WhatsApp instead of live chat” — see ADR
