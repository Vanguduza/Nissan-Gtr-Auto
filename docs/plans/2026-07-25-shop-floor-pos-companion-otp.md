# Shop-floor POS companion scanner + OTP auth + receipt contact bind

- Status: draft
- Lane(s): `@backend_agent` → `@management_app_agent` → `@web_agent` (POS + auth) → `@backend_agent` (receipt wire) → `/security-reviewer` → `/verifier`
- Skills needed: `/qr-inventory-workflow` (companion scan); none for OTP/receipt secrets (env placeholders only)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phases 5 / 13 + web staff POS handoff
- Decisions: [`pos-scan-session-pairing`](../decisions/2026-07-25-pos-scan-session-pairing.md), [`auth-otp-fail-closed`](../decisions/2026-07-25-auth-otp-fail-closed.md), [`pos-receipt-contact-customer-bind`](../decisions/2026-07-25-pos-receipt-contact-customer-bind.md)
- Prior: Phase 5 POS (`pos_carts`, `add_cart_line_from_qr`, `checkout_pos_cart`, `enqueue_customer_receipts`); Phase 13 `process-customer-receipts`; [`customer-receipt-delivery`](../decisions/2026-07-23-customer-receipt-delivery.md); [`web-management-parity-rbac`](../decisions/2026-07-25-web-management-parity-rbac.md)

## Goal

Enable a sales rep to run POS on a tablet while a paired phone (native bridge QR only) adds lines to the same open cart; checkout captures email/WhatsApp for receipt outbox + optional `customer_id` bind; customers sign up/login with email and/or phone OTP under fail-closed secret rules.

## Acceptance criteria

- [ ] Tablet opens cart → shows pairing code/QR → phone claims session as scanner → bridge scan → `add_cart_line_from_qr` on shared cart → line visible on tablet (Realtime or poll)
- [ ] Checkout accepts `receipt_email` and/or `receipt_whatsapp_e164` → invoice contact columns set → `enqueue_customer_receipts` rows (or clear channel fail if no gateway keys; sale still posts)
- [ ] Unique match to registered customer (profile-linked and/or non-RETAIL price list) binds `customer_id`; UI surfaces bind vs walk-in
- [ ] Signup/login email | phone | both requests OTP; production fail-closed without SMS/email gateway keys; local stub only behind explicit env flag (never default-on in prod)
- [ ] No HTML5/browser QR; no ZIMRA; RLS on all new tables; no secrets in repo
- [ ] Smoke covers pairing claim/revoke, scan→line, checkout contact+bind, OTP stub gate

## Schema sketch (pairing)

| Object | Notes |
|--------|--------|
| `pos_scan_sessions` | `id`, `cart_id` → `pos_carts`, `pairing_code` (short, unique while open), `owner_user_id` (tablet staff), `scanner_user_id` (nullable until claim), `status` (`open`\|`claimed`\|`closed`\|`expired`), `expires_at`, `created_at` |
| Constraints | One **open/claimed** session per open cart; claim only by same staff user (or sales role) as owner; expire ~5–15 min idle |
| Realtime | Publish `pos_cart_lines` (or cart `updated_at`) for tablet subscribe; poll fallback OK |

## RPC list

| RPC | Purpose |
|-----|---------|
| `create_pos_scan_session(p_cart_id)` | Staff; returns session id + pairing_code; closes prior open session for cart |
| `claim_pos_scan_session(p_pairing_code)` | Staff phone; sets `scanner_user_id`, status=`claimed` |
| `revoke_pos_scan_session(p_session_id)` | Owner or admin; status=`closed` |
| `add_cart_line_from_qr` (existing) | Scanner calls with claimed session’s `cart_id` + bridge payload |
| `checkout_pos_cart` (extend) | Add `p_receipt_email`, `p_receipt_whatsapp_e164` (optional `p_receipt_phone_e164`); write invoice contacts; call `resolve_customer_for_receipt_contacts` then set `customer_id` when bind rules pass; then existing post + `enqueue_customer_receipts` |
| `resolve_customer_for_receipt_contacts(...)` | Internal/security definer helper — see bind ADR |
| Auth Edge (or Supabase Auth hooks) | `request_signup_otp` / `verify_signup_otp` / login OTP — email and/or phone; persist `profiles.phone_e164` |

## Fail-closed OTP / receipt secrets

| Concern | Production | Local / CI |
|---------|------------|------------|
| SMS OTP | Require `SMS_GATEWAY_API_KEY` (or Supabase phone Auth config); **503 / refuse send** if unset | `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1` **and** secret unset → documented stub code only |
| Email OTP | Require `EMAIL_API_KEY` + `EMAIL_FROM` (or Supabase email OTP); refuse if unset | Same flag; stub only |
| Receipt channels | Existing `process-customer-receipts`: refuse drain without keys unless `WORKER_ALLOW_UNVERIFIED_LOCAL=1` | Reuse Phase 13 pattern; checkout **never blocks** on send |
| Flags | Never default-on in prod; CI asserts flag off when secrets present | Document in `docs/HARDENING.md` + `.env.example` names only |

## Local test / stub OTP

1. `supabase start` / apply migrations; set Edge: `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1`, leave SMS/email keys unset.
2. Signup with email and/or E.164 phone → OTP request returns stub hint (fixed test code, e.g. `000000`, **logged only in local Edge**, never in client bundle as default).
3. Verify → session issued; `profiles.phone_e164` set when phone used.
4. Without flag and without keys → OTP request fails closed (clear error).
5. POS: create cart → `create_pos_scan_session` → second device `claim_pos_scan_session` → `add_cart_line_from_qr` → tablet sees line → checkout with contacts → outbox rows; drain with `WORKER_ALLOW_UNVERIFIED_LOCAL=1` for stub send.

## What still needs user secrets

- Production SMS gateway key (and/or Supabase Auth phone provider)
- Production email API key + from-address (and/or Supabase Auth SMTP)
- WhatsApp Cloud: `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` (receipt PDF channel)
- Optional: real `WORKER_SHARED_SECRET` for receipt worker (already in HARDENING)

## Phases (one primary lane each)

1. **Docs** — this plan + ADRs *(done by /planner)*
2. **`@backend_agent`** — migrations: `pos_scan_sessions` + RLS; `profiles.phone_e164`; checkout RPC contact params + bind helper; OTP Edge/auth wiring fail-closed; smoke SQL
3. **`@management_app_agent`** — companion Scan mode + tablet pairing UI; reuse `:qr-scanner` bridge; Realtime/poll cart refresh
4. **`@web_agent`** — `/staff/pos` pairing code display + checkout receipt contact fields + bind messaging (**no** browser QR)
5. **`@web_agent`** — storefront `(auth)` signup/login email \| phone OTP flows
6. **`@backend_agent`** — ensure checkout → invoice contacts → `enqueue_customer_receipts` → worker path documented/smoke; types regen
7. **`/security-reviewer`** then **`/verifier`** — RLS, pairing hijack, OTP flag abuse, Bridge-First, exclusions

## Paths in scope

- `supabase/migrations/*pos_scan*`, `*checkout*receipt*`, `*auth*otp*`; `supabase/functions/` OTP + existing `process-customer-receipts`
- `supabase/tests/*pos_companion*` (or extend phase5/13 smokes)
- `apps/android-management/feature/pos/**`, RPC client names
- `apps/web/app/(staff)/staff/pos/**`, `StaffPosPanel`, `(auth)/**`
- `packages/supabase-client/` types; optional `packages/shared/` contact normalize helpers
- `docs/HARDENING.md` env rows (names only)

## Out of scope

- Browser/HTML5 QR or WebView camera scan
- iOS companion scanner (Android management first; iOS later)
- ZIMRA / fiscal QR; payroll tax
- New payment rails; ContiPay/Paynow credential values
- Auto-creating customers from walk-in contacts (bind only; no silent customer insert unless already decided elsewhere)
- Multi-scanner per cart; cross-rep session claim
- PowerSync offline pairing (Phase 14)
- Thermal reprint / ESC/POS changes beyond existing receipt print

## Risks / exclusions

- Pairing code brute-force → short TTL + rate limit + staff-only claim
- Ambiguous email/phone match → do not bind; show message
- OTP stub must not ship enabled in prod builds
- Web tablet POS is display-only for QR; scanner must be native (`bridges/`)

## Handoff

1. Implement Phase 2 in `@backend_agent`
2. `@management_app_agent` companion + tablet pairing
3. `@web_agent` staff POS + auth OTP UI
4. Receipt path smoke / HARDENING env docs
5. `/security-reviewer` → `/verifier` → `/manager` done gate
