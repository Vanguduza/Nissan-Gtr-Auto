# Web management parity + RBAC

- Status: done (security PASS + verifier PASS)
- Lane(s): `@web_agent` (primary); `@backend_agent` only if RPC gaps
- Skills: `/token-discipline` (Bridge-First via ADR; no `/ui-ux-pro-max` unless asked)
- ADR: [`docs/decisions/2026-07-25-web-management-parity-rbac.md`](../decisions/2026-07-25-web-management-parity-rbac.md)

## Goal

Role-filter staff UI on web and complete management fallback parity with Android + existing RPCs, absorbing WIP under `apps/web/app/(staff)/`.

## Role → module matrix

| Module | Route(s) | admin | warehouse | finance | sales | dispatcher | hr |
|--------|----------|:-----:|:---------:|:-------:|:-----:|:----------:|:--:|
| Hub | `/staff` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| POS | `/staff/pos` | ✓ | ✓* | — | ✓ | — | — |
| Warehouse | `/staff/warehouse/*` | ✓ | ✓ | —† | — | — | — |
| Finance | `/staff/finance/*` | ✓ | — | ✓ | — | — | — |
| Logistics | `/staff/logistics` | ✓ | ✓ | — | ✓‡ | ✓ | — |
| Live map | `/staff/logistics/tracking` | ✓ | ✓ | — | — | ✓ | — |
| HR | `/staff/hr` | ✓ | — | — | — | — | ✓ |
| Procurement / RFQ | `/procurement` | ✓ | ✓ | ✓ | — | — | — |
| Warranty / returns | `/staff/warranty` (new) | ✓ | ✓ | — | ✓ | — | — |

\* POS RPC `_require_sales_staff` allows admin/sales/**warehouse**.  
† Finance may **read** stock/levels via RLS; mutations are warehouse/admin.  
‡ Pick/DN via `_require_logistics_staff` (includes sales); jobs/GPS ingest need dispatcher/warehouse/admin — web never calls `ingest_delivery_location`.

Any authenticated staff (`is_staff`) may open Hub; module links hidden unless role matches. Direct URL → **403 forbidden** page (RPC still rejects).

## Paths in scope

**Auth / shell**
- `apps/web/components/staff-nav.tsx` — role-aware nav
- `apps/web/app/(staff)/layout.tsx` — session + `is_staff` gate (or middleware if preferred)
- New helpers e.g. `apps/web/lib/staff-auth.ts` — load `staff_roles` for `auth.uid()` (RLS: select own)
- Forbidden page under `(staff)` (e.g. `/staff/forbidden`)
- Optional: storefront chrome link to `/staff` when `profiles.is_staff`

**Absorb / finish WIP**
- `app/(staff)/staff/pos` + `components/staff-pos-panel.tsx` + `lib/staff-pos.ts`
- `app/(staff)/staff/warehouse/**` + receive/transfers/cycle-count panels + `lib/staff-warehouse.ts`
- `staff/hr`, `staff/logistics`, `staff/logistics/tracking` (Realtime map only)

**P2+ new thin shells**
- `/staff/finance` — journals + reports + payments
- `/staff/warranty` — claims + quarantine return entry

## RPC inventory (wire; do not invent)

| Area | Existing RPCs |
|------|----------------|
| POS | `create_pos_cart`, `add_cart_line`, `checkout_pos_cart` — **not** `add_cart_line_from_qr` on web |
| Returns | `post_return_credit_note`, `post_return_to_quarantine` |
| Warehouse | `post_stock_receipt`, `create_stock_transfer`, `approve_stock_transfer`, `reject_stock_transfer` |
| Cycle count | `create_stock_reconciliation_draft`, `upsert_stock_reconciliation_lines`, `submit_stock_reconciliation`, `approve_stock_reconciliation`, `cancel_stock_reconciliation` |
| Finance | `create_journal_draft`, `post_journal`, `post_journal_entry`, `report_profit_and_loss`, `report_balance_sheet`, `report_cash_flow`, `create_payment_entry`, `allocate_payment`, `post_payment_entry`, `cancel_payment_entry`, `issue_store_credit`, `redeem_store_credit` |
| Warranty | `open_warranty_claim`, `approve_warranty_claim`, `reject_warranty_claim`, `close_warranty_claim` |
| Logistics | `create_pick_list`, `confirm_pick_lines`, `create_delivery_note`, `submit_delivery_note`, `cancel_delivery_note`, `create_delivery_job`, `update_delivery_job_status` |
| HR | `clock_attendance`, `attendance_hours_in_period`, `add_payroll_deduction` (manual only) |
| Procurement | existing RFQ/PO RPCs already used under `/procurement` |

**Bridge-only (web must not call):** `ingest_delivery_location`, QR scan/print paths, `add_cart_line_from_qr`.

## Auth checklist

- [ ] Unauthenticated `/staff/*` → sign-in redirect (return URL)
- [ ] Authenticated non-staff → forbidden
- [ ] Staff wrong role for module → forbidden (nav hides link)
- [ ] RPC errors surface as permission failures (no silent success)

## Acceptance criteria

**P0 — RBAC + gate**
- [ ] Nav filtered by roles from `staff_roles`
- [ ] Unauthenticated redirect; wrong role → forbidden page
- [ ] Optional storefront “Staff” link when `is_staff`

**P1 — POS + warehouse shells**
- [ ] POS: create cart → typed OEM lines → checkout (existing `staff-pos`)
- [ ] Warehouse: receive / transfer create+approve / cycle-count draft→submit→approve
- [ ] Copy: “QR / GPS → management device”

**P2 — Finance**
- [ ] Thin finance hub: draft/post journal, run P&amp;L/BS/CF, payment entry post

**P3 — Warranty / polish**
- [ ] Warranty claim open/approve/reject/close + quarantine return path
- [ ] Polish HR / logistics / RFQ; live map linked from logistics hub

## Out of scope

- New backend RPCs (unless gap found during wire-up)
- Browser QR/GPS/camera; ZIMRA; payroll tax UI
- Full Android feature redesign; biometric staff login
- Master plan rewrite during coding (see handoff)

## Backend gaps (current skim)

| Gap | Severity | Notes |
|-----|----------|-------|
| None for P0–P1 RPCs | — | All listed functions exist in migrations |
| Seed: no sales/dispatcher/hr demo users | Low | Only `admin@` / `finance@` / `warehouse@` @gtr.local — optional seed follow-on |
| No `list_my_roles` RPC | None | `staff_roles` SELECT-own RLS is enough |

## Risks / exclusions

- UI hide ≠ security; never trust client role alone
- Dual-auth transfers/recon: second approver must be different user (RPC-enforced)
- Multi-currency: every money field shows explicit `USD` \| `ZIG`

## Handoff

1. Implement in **`@web_agent`**
2. `/security-reviewer` (auth gate + role checks)
3. `/verifier`
4. `/manager` done gate
5. **After verify:** update master handoff in `docs/plans/2026-07-23-master-erp-development.md` — draft bullet:

   > **Web management fallback + RBAC** Done — `/staff` role-filtered nav + auth gate; POS/warehouse RPC shells; finance/warranty thin shells per plan `2026-07-25-web-management-parity-rbac.md`; Bridge-First (no browser QR/GPS).
