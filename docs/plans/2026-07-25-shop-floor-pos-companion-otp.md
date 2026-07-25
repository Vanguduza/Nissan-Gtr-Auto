# Shop-floor POS: standalone sales POS + optional companion + OTP + receipt bind

- Status: done (PASS WITH GAPS — Android compile not run without JDK; hosted Auth must set `enable_signup = false` to match local OTP gate)
- Lane(s): `@backend_agent` → `@management_app_agent` → `@web_agent` (POS workspace + auth) → `@backend_agent` (receipt wire) → `/security-reviewer` → `/verifier`
- Skills needed: `/qr-inventory-workflow` (companion scan only); none for OTP/receipt secrets (env placeholders only)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phases 5 / 13 + web staff POS handoff
- Decisions: [`pos-scan-session-pairing`](../decisions/2026-07-25-pos-scan-session-pairing.md) (amended: pairing optional), [`auth-otp-fail-closed`](../decisions/2026-07-25-auth-otp-fail-closed.md), [`pos-receipt-contact-customer-bind`](../decisions/2026-07-25-pos-receipt-contact-customer-bind.md)
- Prior: Phase 5 POS (`pos_carts`, `add_cart_line_from_qr`, `checkout_pos_cart`, `enqueue_customer_receipts`); Phase 13 `process-customer-receipts`; [`customer-receipt-delivery`](../decisions/2026-07-23-customer-receipt-delivery.md); [`web-management-parity-rbac`](../decisions/2026-07-25-web-management-parity-rbac.md)

## Goal

Sales staff run a **full POS client on a single device** (Android management or web `/staff/pos`) with parts search, catalog browse, add-to-cart, and checkout (receipt contacts + customer bind). Phone QR companion **optionally** pairs to enhance scanning; pairing is never required. Customers **sign up** with email/phone OTP (fail-closed); **later logins** use email/phone + password set at registration.

## Acceptance criteria

- [ ] **Sales role home** = POS-dedicated workspace (not full hub) on android-management and web `/staff/pos`; admin/warehouse keep hub as home
- [ ] Standalone POS (no tablet / no pairing): parts **search**, **catalog browse**, **direct add** to open cart, checkout with WhatsApp/email for PDF receipt + bind to registered company when unique match
- [ ] Optional companion: cart owner shows pairing code → phone claims → bridge scan → `add_cart_line_from_qr` → line visible on POS (Realtime or poll); single-device sales works without claim
- [ ] Checkout accepts `receipt_email` and/or `receipt_whatsapp_e164` → invoice contacts → `enqueue_customer_receipts` (or clear channel fail; sale still posts); same RPC for standalone and paired flows
- [ ] Signup/login email | phone | both OTP; production fail-closed without gateway keys; local stub only behind explicit env flag
- [ ] Bridge-First QR **only when scanning**; no HTML5/browser QR; no ZIMRA; RLS on new tables; no secrets in repo
- [ ] Smoke: standalone search→line→checkout+bind; optional pairing claim/revoke + scan→line; OTP stub gate

## Schema sketch (pairing — optional)

| Object | Notes |
|--------|--------|
| `pos_scan_sessions` | `id`, `cart_id` → `pos_carts`, `pairing_code`, `owner_user_id`, `scanner_user_id` (nullable), `status`, `expires_at` |
| Constraints | One open/claimed session per open cart; claim same-staff preferred; expire ~5–15 min; **cart usable with zero sessions** |
| Line add | Standalone: catalog/search RPCs / existing add-line; Companion: `add_cart_line_from_qr` only via bridge |
| Realtime | Optional for companion refresh; poll fallback OK |

## RPC list

| RPC | Purpose |
|-----|---------|
| Existing cart/line RPCs | Standalone search/catalog → add line to open `pos_carts` (no session required) |
| `create_pos_scan_session(p_cart_id)` | Optional; returns pairing_code |
| `claim_pos_scan_session(p_pairing_code)` | Optional companion claim |
| `revoke_pos_scan_session(p_session_id)` | Close companion rights |
| `add_cart_line_from_qr` (existing) | Companion (or native POS with bridge) scan path |
| `checkout_pos_cart` (extend) | `p_receipt_email` / `p_receipt_whatsapp_e164`; bind helper; post + `enqueue_customer_receipts` — **identical for standalone and paired** |
| `resolve_customer_for_receipt_contacts(...)` | Internal bind helper — see bind ADR |
| Auth Edge / Auth hooks | OTP signup / contact confirm; password login (email or phone); `profiles.phone_e164` |

## Fail-closed OTP / receipt secrets

| Concern | Production | Local / CI |
|---------|------------|------------|
| SMS / Email OTP | Refuse send if keys unset | `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1` + keys unset → stub only |
| Receipt channels | Phase 13 refuse drain without keys unless `WORKER_ALLOW_UNVERIFIED_LOCAL=1` | Checkout never blocks on send |
| Flags | Never default-on in prod | HARDENING + `.env.example` names only |

## Local test / stub OTP

1. Migrations + Edge: `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1`, keys unset → stub OTP verify.
2. **Standalone:** sales login → POS home → search/catalog add lines → checkout contacts → outbox/bind (no `create_pos_scan_session`).
3. **Optional companion:** create session → claim → bridge `add_cart_line_from_qr` → POS sees line → same checkout RPC.
4. Without OTP flag + without keys → fail closed.

## What still needs user secrets

- Production SMS / email / WhatsApp gateway keys (names in HARDENING only)
- Optional `WORKER_SHARED_SECRET` for receipt worker

## Phases (one primary lane each)

1. **Docs** — this plan + ADR amend *(done by /planner)*
2. **`@backend_agent`** — `pos_scan_sessions` + RLS (optional path); checkout contact + bind; OTP fail-closed; ensure cart line-add works without session; smoke SQL
3. **`@management_app_agent`** — **sales role → POS-dedicated home** (search, catalog, cart, checkout); optional Scan companion + pairing UI; admin/warehouse hub unchanged; `:qr-scanner` bridge only for scan
4. **`@web_agent`** — `/staff/pos` as sales default: full standalone POS workspace (search/catalog/add/checkout contacts); optional pairing **display** only (**no** browser QR); admin/warehouse hub nav unchanged
5. **`@web_agent`** — storefront `(auth)` email \| phone OTP
6. **`@backend_agent`** — checkout → invoice contacts → enqueue → worker smoke; types regen
7. **`/security-reviewer`** then **`/verifier`**

## Paths in scope

- `supabase/migrations/*pos_scan*`, `*checkout*receipt*`, `*auth*otp*`; OTP Edge + `process-customer-receipts`
- `supabase/tests/*pos*` (standalone + optional companion)
- `apps/android-management/` — sales home/nav → POS; `feature/pos/**` search/catalog/cart/checkout; optional companion
- `apps/web/app/(staff)/staff/pos/**`, staff nav role home for sales, `(auth)/**`
- `packages/supabase-client/`; optional `packages/shared/` contact normalize
- `docs/HARDENING.md` env names only

## Out of scope

- Browser/HTML5 QR or WebView camera scan
- Requiring a second device / tablet for sales POS
- iOS companion scanner (Android management first)
- ZIMRA / fiscal QR; payroll tax
- New payment rails; secret values in repo
- Auto-creating customers from walk-in contacts (bind only)
- Multi-scanner per cart; cross-rep session claim
- PowerSync offline pairing (Phase 14)
- Changing admin/warehouse default hub to POS

## Risks / exclusions

- Pairing brute-force → TTL + rate limit + staff-only claim
- Ambiguous contact match → do not bind
- OTP stub must not ship enabled in prod
- Companion enhances; **missing pairing must not block** search/catalog/checkout

## Handoff

1. `@backend_agent` Phase 2 (schema + checkout/OTP; cart without session)
2. `@management_app_agent` standalone sales POS + optional companion
3. `@web_agent` sales POS workspace + auth OTP
4. Receipt path smoke / HARDENING
5. `/security-reviewer` → `/verifier` → `/manager` done gate
