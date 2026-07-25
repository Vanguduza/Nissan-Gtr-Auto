# POS scan session pairing (tablet + phone companion)

- Date: 2026-07-25
- Lane: `@backend_agent` (schema/RPC); `@management_app_agent` (companion + tablet UI); `@web_agent` (pairing display on `/staff/pos` only)
- Status: accepted

## Decision

Shop-floor dual-device POS uses **`pos_scan_sessions`** scoped to an **open** `pos_carts` row:

1. **Tablet** (Android management POS and/or web `/staff/pos`) owns the cart and calls `create_pos_scan_session` → shows short **pairing code** (and optional QR encoding that code/session id for convenience — **not** inventory QR).
2. **Phone** runs Android management **Scan companion** mode: same sales staff logs in, `claim_pos_scan_session(pairing_code)`, then scans inventory labels via **`bridges/` QR only** and calls existing `add_cart_line_from_qr(cart_id, payload)`.
3. Tablet refreshes lines via **Realtime** on `pos_cart_lines` / cart `updated_at`, or short poll.

**Tablet may be web or native; scanner must always be native.** Web must not use HTML5/browser QR libraries ([web-management-parity-rbac](./2026-07-25-web-management-parity-rbac.md)).

Claim rules: claimant must be authenticated **sales/admin staff**; prefer **same `owner_user_id`** as tablet session (same rep on two devices). One open/claimed session per cart; revoke/expire closes scanner rights. Do not invent a second cart schema.

## Why

Reps need hands-free scanning while the tablet stays the checkout surface. Cart-scoped pairing reuses Phase 5 `add_cart_line_from_qr` and Bridge-First hardware without browser cameras.

## Consequences

- New table + RLS in same migration; RPCs listed in plan `2026-07-25-shop-floor-pos-companion-otp.md`.
- No multi-scanner, no cross-rep claim, no iOS companion in v1.
- Pairing QR ≠ inventory `gtr://part/…` payload; only the phone bridge scans parts.
