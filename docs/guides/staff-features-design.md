# Staff features design guide

How **staff / employee / management** surfaces are designed to work in the Nissan GTR Auto ERP monorepo. Use this when changing UX, roles, RLS, or APIs without rediscovering the system.

**Audience:** engineers and agents editing web staff UI, Android management, delivery, Supabase authz, or hardware bridges.  
**Honesty rule:** UI hide ≠ security — RLS and `SECURITY DEFINER` RPCs are the source of truth. This doc marks **implemented** vs **thin scaffold / stub** vs **backend-only**.

Related: [`AGENTS.md`](../../AGENTS.md), [`rufler.yaml`](../../rufler.yaml), ADR [`2026-07-25-web-management-parity-rbac`](../decisions/2026-07-25-web-management-parity-rbac.md), master plan [`2026-07-23-master-erp-development`](../plans/2026-07-23-master-erp-development.md), overview [`IMPLEMENTATION-AND-DESIGN-GUIDE`](../IMPLEMENTATION-AND-DESIGN-GUIDE.md).

---

## 1. Apps and entry points

| Surface | Path | Who | Role |
|---------|------|-----|------|
| **Web staff (management fallback)** | `apps/web/app/(staff)/` → `/staff/*` | Staff browsers | Full staff IA when Android tablet unavailable |
| **Web customer storefront** | `apps/web/app/(storefront)/`, account, catalog | Customers | Not staff; staff may still use password email/phone login |
| **Web procurement (staff RFQ)** | `apps/web/app/(b2b)/procurement/*` | Staff with warehouse/finance/admin | Gated like staff modules; lives outside `/staff` prefix |
| **Android management** | `apps/android-management/` | Counter / warehouse / HR / dispatch | Primary POS + Bridge-First hardware |
| **Android delivery** | `apps/android-delivery/` | Drivers only | Jobs, GPS FGS, POD — **not** a management flavor |
| **Android / iOS customer** | `apps/android-customer/`, `apps/ios/` | Retail / B2B customers | No staff modules |

### 1.1 Web staff vs customer

- **Staff routes:** under route group `(staff)` — layout wraps everything in `StaffGate` (`apps/web/app/(staff)/layout.tsx` → `components/staff-gate.tsx`).
- **No Next.js middleware staff check** today — gating is client-side via `loadStaffContext` + `canAccessPath` (still fail-closed at RPC/RLS).
- **Nav / role matrix:** `apps/web/lib/staff-auth.ts` — `STAFF_NAV_TREE`, `pathAccessFor`, `STAFF_MODULE_ROLES`, organogram `moduleAccess` filter.
- **Forbidden:** `/staff/forbidden` when role does not match path.
- **Password change gate:** `profiles.must_change_password` → force `/staff/change-password` before other staff surfaces.

**Login** (`apps/web/app/(auth)/login/page.tsx`):

| Method | Intended for | Mechanism |
|--------|--------------|-----------|
| Email + password | Customers **or** staff | GoTrue password; no OTP on login |
| Phone + password | Customers **or** staff | Resolve phone → email via profiles, then password |
| **Employee #** | Staff only | `resolve_staff_login_email` (anon) → GoTrue email + password; also accepts staff email/phone via same RPC |

- Staff deep-link (`?next=/staff…` or `/procurement…`) defaults the login tab to **employee**.
- **Google / Apple OAuth** shown only when method ≠ employee (`showCustomerOAuth`). Staff are **not** provisioned via customer OAuth; HR onboarding uses Admin `createUser` with `app_metadata.gtr_provisioned_via=hr_onboarding`.
- `ensure_own_customer()` **denies** staff (`is_staff`, `staff_roles`, `employees`, HR provision metadata) — see `20260806140000_customer_oauth_otp_mint_harden.sql` and [`CUSTOMER_OAUTH_SETUP.md`](../CUSTOMER_OAUTH_SETUP.md).

**OTP** (ADR `2026-07-25-auth-otp-fail-closed`): signup / contact verify only — **never** returning login.

### 1.2 Android management (`apps/android-management/`)

See app README. Thin Compose scaffolds over `RpcClient` (Live Supabase or Fake).

| Flavor | `applicationId` | Notes |
|--------|-----------------|-------|
| `phone` | `co.zw.nissangtr.management` | Portable; no Device Owner |
| `tablet` | `co.zw.nissangtr.management.tablet` | Kiosk: Lock Task, Device Admin, idle lock — [`android-management-kiosk-device-owner`](./android-management-kiosk-device-owner.md) |

**Hub modules (implemented screens):** POS, Warehouse (+ Bins, Consignment), Procurement blankets, CRM credit, HR (clock + onboarding), Logistics/dispatch, Fleet, Chat.  
**Not on Android hub:** full Finance desk, Analytics, Warranty panel, web-only finance tabs — use **web `/staff/finance`** (and related) as fallback.

**Landing:** sales-only → POS; admin / warehouse / finance / hr / dispatcher → hub (`ManagementHomeRoles` in `RpcModels.kt`). Live with no recognized staff role → **deny**. Organogram `my_module_access` / `my_default_landing` refine tiles.

**Auth:** GoTrue email/password via `SignInScreen` / `AuthGate`; staff identifier resolve RPC for emp# parity with web (`RESOLVE_STAFF_LOGIN_EMAIL`).

### 1.3 Delivery vs management

| Concern | Management | Delivery app |
|---------|------------|--------------|
| Assign jobs, panic inbox, live track **view** | Yes | No (execute only) |
| GPS producer (`ingest_delivery_location`) | **Forbidden** (`ALLOW_DRIVER_GPS_PRODUCER = false`) | **Sole** producer via FGS + `bridges/android/location-tracker` |
| POS / warehouse / HR / finance | Yes (partial) | **Never** |
| Role | dispatcher / warehouse / sales / admin | `driver` (+ admin for debug) |

ADR: [`2026-07-25-dedicated-delivery-app`](../decisions/2026-07-25-dedicated-delivery-app.md).

### 1.4 Auth model summary

```
auth.users
    └── profiles (id = auth.uid(), is_staff bool, must_change_password, …)
            ├── staff_roles (user_id, role) ──► syncs profiles.is_staff
            └── employees (user_id?, employee_code GTR…, hr_role_id, …)
                    └── hr_roles.module_access (JSON module ids) ──► UI filter only
```

| Concept | Meaning |
|---------|---------|
| `profiles.is_staff` | Synced from any `staff_roles` row; **not** client-writable |
| `staff_role` enum | Coarse RBAC used in RLS / RPCs |
| `hr_roles.module_access` | Fine UX filter (`pos`, `finance`, …); admins bypass; empty = staff_roles-only |
| Employee code | `GTR{grade}{3-digit}` from onboarding; login via `resolve_staff_login_email` |
| Customer OAuth | Customers only; staff denied from retail customer mint |

---

## 2. Roles and permissions

### 2.1 Enum and tables

**Migration:** `supabase/migrations/20260723100000_foundation_roles.sql`

```text
staff_role: admin | finance | warehouse | sales | dispatcher | hr
```

**Later:** `driver` added in `20260725110000_dedicated_delivery_app.sql` (`ALTER TYPE … ADD VALUE 'driver'`).

| Object | Purpose |
|--------|---------|
| `profiles` | App user row; `is_staff` |
| `staff_roles` | `(user_id, role)` PK; multi-role allowed |
| `is_staff()` | SECURITY DEFINER: current user staff? |
| `has_staff_role(roles[])` | SECURITY DEFINER: membership check |
| `assign_staff_role` / `revoke_staff_role` | Admin or `service_role` only (`20260723201000_auth_is_staff_hardening.sql`) |

**Hardening:** `protect_profile_staff_flag` + column grants block client escalation of `is_staff`. Prefer RPCs over direct `staff_roles` writes (RLS still allows admin ALL).

### 2.2 What each role is for (product intent)

| Role | Typical work | Primary surfaces |
|------|--------------|------------------|
| **admin** | Everything; role assignment; organogram grades | All `/staff/*`, management hub |
| **sales** | Till, named customers, CRM credit/reviews, pick/prep, chat | POS home by default; CRM; logistics pick |
| **warehouse** | Receive, transfer, cycle count, bins, consignment, POS, catalog hierarchy write | Warehouse + POS + logistics |
| **finance** | Journals, payments, periods, bank recon, payment intents, analytics | Web `/staff/finance`, analytics |
| **dispatcher** | Assign/route delivery, live map, panic, fleet | Logistics tracking/panic, fleet |
| **hr** | Attendance, onboarding, organogram (with admin) | `/staff/hr`, management HR |
| **driver** | Execute delivery jobs only | `apps/android-delivery` only |

`admin` bypasses role checks in **UI** (`rolesAllow`); RPCs usually treat admin as allowed via `has_staff_role(ARRAY['admin', …])`.

### 2.3 Web module ↔ role matrix

Canonical copy lives in `STAFF_NAV_TREE` / `pathAccessFor` (`apps/web/lib/staff-auth.ts`). Plan matrix: [`docs/plans/2026-07-25-web-management-parity-rbac.md`](../plans/2026-07-25-web-management-parity-rbac.md).

| Module | Routes | Roles |
|--------|--------|-------|
| Hub | `/staff` | any staff |
| POS | `/staff/pos` | admin, warehouse, sales |
| Warehouse | `/staff/warehouse/*` | admin, warehouse (+ finance on insights) |
| Finance | `/staff/finance` | admin, finance |
| CRM credit | `/staff/crm/credit` | admin, sales, finance |
| CRM reviews | `/staff/crm/reviews` | admin, sales |
| Logistics jobs/prep | `/staff/logistics`, `/prep` | admin, warehouse, sales, dispatcher |
| Live map / panic | `/staff/logistics/tracking`, `/panic` | admin, warehouse, dispatcher |
| Fleet | `/staff/fleet` | admin, warehouse, dispatcher |
| HR | `/staff/hr` | admin, hr |
| Warranty | `/staff/warranty` | admin, warehouse, sales |
| Chat | `/staff/chat` | admin, sales, warehouse |
| Analytics | `/staff/analytics*` | admin, finance, sales |
| Procurement | `/procurement*` | admin, warehouse, finance (approvals: admin, finance) |

### 2.4 Key migrations that define authz

| Migration | Defines |
|-----------|---------|
| `20260723100000_foundation_roles.sql` | Enum, profiles, staff_roles, helpers, base RLS |
| `20260723200000_auth_profiles_roles.sql` | Signup trigger, assign/revoke RPCs, is_staff sync |
| `20260723201000_auth_is_staff_hardening.sql` | Fail-closed is_staff + admin RPCs |
| `20260723220000_inventory_ops.sql` | Receive/transfer + `post_return_to_quarantine` |
| `20260723230000_sales_pos.sql` | POS cart/invoices + `post_return_credit_note` |
| `20260724070000_hr_gross_payroll.sql` | Employees, attendance, gross payroll (**no tax**) |
| `20260725110000_dedicated_delivery_app.sql` | `driver` role + delivery presence/assign |
| `20260725100000_live_chat.sql` | Chat staff roles helper |
| `20260803120000_batch1_hr_organogram_grades_roles.sql` | Grades, `hr_roles.module_access` |
| `20260803170000_batch1_hr_onboarding_password.sql` | `must_change_password` |
| `20260803252000_staff_login_resolve_identifier.sql` | Emp# \| email \| phone → login email |
| `20260803260000_hr_default_landing_login_lockout_price_override.sql` | Landing + login lockout |
| `20260806140000_customer_oauth_otp_mint_harden.sql` | Staff deny on customer mint |

When changing “who can do X”, edit the **RPC/RLS migration** (or a new follow-up migration), then mirror UI gates in `staff-auth.ts` and Android `*StaffRoles` objects.

---

## 3. Feature modules (design + status)

Legend: **Implemented** = usable UI + RPC; **Thin** = scaffold / Fake-capable but not polished; **Backend** = migrations/RPCs exist, UI partial or web-only; **Gap** = design intent without full UI.

### 3.1 POS / till

| | |
|--|--|
| **Purpose** | Counter sales: cart → invoice; optional companion scan; multi-tender; park/quote/void/refund |
| **Web** | `/staff/pos` → `StaffPosShell` / `staff-pos-panel.tsx` + `lib/staff-pos.ts` — cart, catalog search, park/resume, companion **pairing code** (Realtime refresh), checkout / tenders. **No browser QR.** |
| **Android** | `feature/pos` — `PosScreen`, offline SQLCipher cache (ADR `2026-08-03-offline-sqlcipher-pos-cache`), Bridge QR + ESC/POS print on checkout |
| **Tables** | `pos_carts`, `pos_cart_lines`, `sales_invoices`, `price_lists`, scan sessions, quotations |
| **Core RPCs** | `create_pos_cart`, `add_cart_line`, `add_cart_line_from_qr` (**native only**), `checkout_pos_cart`, `checkout_pos_cart_with_tenders`, `park_pos_cart` / `resume_pos_cart`, `create_pos_scan_session` / `claim_*` / `revoke_*`, `apply_pos_cart_discount`, `apply_pos_line_price_override`, `void_pos_cart`, `post_pos_refund` / `post_finance_refund`, quotation RPCs, `pull_pos_offline_snapshot` / `replay_offline_pos_sale` |
| **Workflow** | Open cart (optional `p_customer_id`) → add OEM/UUID or QR → optional park/quote → checkout (+ tenders) → receipt contact bind → optional Bluetooth print |
| **Gaps** | Web cannot scan inventory QR (Bridge-First); use companion phone or typed OEM. Approver discount/void/refund: richer on tablet plan than every web control. |

Migrations: `20260723230000_sales_pos.sql`, `20260725190000_pos_scan_sessions_checkout_receipt_bind.sql`, `20260803110000_*_tenders.sql`, `20260803140000_*_companion_realtime.sql`, `20260803151000_*_park_cart.sql`, `20260803250000_pos_approver_void_discount_refund.sql`, `20260803251000_pos_quotations.sql`, `20260803270000_pos_offline_sync.sql`.

Decisions: [`pos-scan-session-pairing`](../decisions/2026-07-25-pos-scan-session-pairing.md), [`pos-receipt-contact-customer-bind`](../decisions/2026-07-25-pos-receipt-contact-customer-bind.md), [`offline-sqlcipher-pos-cache`](../decisions/2026-08-03-offline-sqlcipher-pos-cache.md).

### 3.2 Inventory receiving / QR / quarantine returns

| | |
|--|--|
| **Purpose** | Post stock receipts; QR lifecycle; faulty returns → **Quarantine warehouse** + contra-revenue CN (never silent restock to saleable) |
| **Web** | `/staff/warehouse/receive` — typed OEM/manual; label prompts to use management device for QR |
| **Android** | `WarehouseScreen` — Bridge QR → OEM → `lookupStockItemByOem` / receive RPCs |
| **RPCs** | `post_stock_receipt`; returns: `post_return_to_quarantine`, `post_return_credit_note`; warranty path may call both |
| **Tables** | `stock_entries` / levels, `warehouses.is_quarantine`, `inventory_qr_codes` (see skill), warranty claim quarantine FK |
| **Gaps** | Full “scan return sticker → auto CN” UX is thinner than skill ideal; warranty desk on web wires claims |

Skill: [`.cursor/skills/qr-inventory-workflow`](../../.cursor/skills/qr-inventory-workflow/SKILL.md).  
Migrations: `20260723220000_inventory_ops.sql`, `20260723230000_sales_pos.sql` (CN), `20260724030000_warranty_claims.sql`.

### 3.3 Warehouse / stock / transfers

| | |
|--|--|
| **Purpose** | Transfers (dual approve), cycle count, bins/pick-path, consignment |
| **Web** | `/staff/warehouse`, `/transfers`, `/cycle-count`, `/bins`, `/consignment`, `/insights` |
| **Android** | `WarehouseScreen`, `BinsScreen`, `ConsignmentScreen` |
| **RPCs** | `create_stock_transfer`, `approve_*` / `reject_*`; recon draft/submit/approve/cancel; `create_warehouse_bin`, `set_stock_level_bin`, `get_pick_path_hints`; consignment draft/line/submit/cancel |
| **Status** | **Implemented** thin UIs + Live/Fake RPCs; ESC/POS bin labels on Android |

### 3.4 Catalog admin / pricing

| | |
|--|--|
| **Purpose** | Hierarchy browse metadata, price lists, core charges — SoR in Postgres |
| **Staff UI** | **No dedicated “catalog admin” app module.** POS uses `search_catalog` + Megazip browse RPCs. Price lists seeded (`RETAIL`/`B2B`/`FLEET`) in sales migration; B2B net pricing on customer/B2B surfaces |
| **Write RLS** | Catalog hierarchy / diagram parts: `admin` \| `warehouse` (`20260807120000_*`, `20260807130000_*`) |
| **Gaps** | Bulk price-list editor for staff is not a first-class `/staff` screen — change via migrations/admin SQL/pipeline or extend web if needed |
| **Pipeline** | `data-pipeline/` + `@data_pipeline_agent` — not staff UX |

### 3.5 Finance / ledger

| | |
|--|--|
| **Purpose** | Append-only double-entry; multi-currency `USD` \| `ZIG` with `exchange_rate_applied`; payments, store credit, bank recon, periods |
| **Web** | `/staff/finance?tab=…` — accounts, statements, petty cash, cash/online sales, ContiPay/Paynow/EcoCash, ZiG rate, journals, requisitions, payments, reports, bank recon, periods (`staff-finance-panel.tsx` + `lib/staff-finance.ts`) |
| **Android** | **No finance module** — use web |
| **Tables** | `chart_of_accounts`, `journal_entries`, `journal_entry_lines` (UPDATE/DELETE forbidden triggers) |
| **RPCs** | `create_journal_draft`, `post_journal`, `reverse_journal`, report_* , payment entry/allocate/post/cancel, store credit issue/redeem, period lock, bank import/match helpers |
| **Rules** | Corrections = reversing entries only; never edit posted lines |

Skill: [`.cursor/skills/accounting-ledger`](../../.cursor/skills/accounting-ledger/SKILL.md).  
Migration: `20260723100100_chart_of_accounts_ledger.sql` (+ later payment/batch1 CoA tender GL migrations).  
Lane: `@finance_agent` for ledger schema; `@web_agent` for finance UI.

### 3.6 HR / payroll

| | |
|--|--|
| **Purpose** | Employees, organogram, onboarding → emp# + Auth user, attendance clock, **gross** payroll + **manual** deductions only |
| **Web** | `/staff/hr` — desk, organogram tab, onboarding wizard |
| **Android** | `ClockAttendanceScreen`, `HrOnboardingScreen` (+ biometric **photo** bridge for profile capture — not fingerprint matching) |
| **Tables** | `employees`, `salary_structures`, `attendance_events`, `payroll_runs` / lines / deductions, `hr_grades`, `hr_roles`, onboarding drafts |
| **RPCs** | `clock_attendance`, `save_hr_onboarding_stage`, `complete_hr_onboarding`; Edge `hr-onboarding-create-auth`; `clear_must_change_password` |
| **Hard exclusion** | **No PAYE, NSSA, POBS, ZIMDEF, tax brackets, P4 forms, ZIMRA** |

Migrations: `20260724070000_hr_gross_payroll.sql`, `20260724071000_hr_attendance_hours_authz.sql`, Batch1 `20260803120000_*` … `20260803260000_*`.

### 3.7 Customer service / orders (staff-facing)

| Area | Status | Where |
|------|--------|-------|
| Live chat inbox | **Implemented** | Web `/staff/chat`, Android `ChatScreen` — claim/reply/close |
| Online order prep | **Implemented** | `/staff/pos?tab=prep`, `/staff/logistics/prep` |
| Review moderation | **Implemented** | `/staff/crm/reviews` |
| B2B credit | **Implemented** | `/staff/crm/credit`, Android `CreditScreen` — `set_customer_credit` |
| Warranty claims | **Desk** | `/staff/warranty` |
| Customer storefront orders | Customer apps + WhatsApp Flows satellite — staff ops via prep/logistics |

### 3.8 Reports / analytics

| | |
|--|--|
| **Web** | `/staff/analytics` KPIs; `/staff/analytics/subscriptions` |
| **RPCs / Edge** | KPI helpers; `analytics-insights`, `process-ai-reports` — aggregates only, no PII dumps (ADR `2026-07-25-ai-report-schema-privacy`) |
| **Finance reports** | P&amp;L, BS, cash flow, trial balance, AR aging under finance tabs |
| **Android** | No analytics module |

Plan: [`2026-07-25-ai-analytics-staff-reports`](../plans/2026-07-25-ai-analytics-staff-reports.md).

### 3.9 Logistics / fleet / fleet (staff)

| | |
|--|--|
| **Web** | Logistics pick/DN/jobs, prep, MapLibre **subscribe-only** tracking, panic inbox; fleet panel |
| **Android management** | `DispatchScreen`, `FleetScreen` — assign, POD OTP generate, optimize stops, panic ack; **no GPS ingest** |
| **Delivery app** | Job execution + GPS + POD |

---

## 4. Hardware / Bridge-First

**Law:** camera, QR, Bluetooth printer, biometric, GPS → only `bridges/` native modules. **Never** HTML5 QR, Web Bluetooth, or `navigator.geolocation` on staff web.

| Concern | Android module | Used by |
|---------|----------------|---------|
| QR scan | `bridges/android/qr-scanner` | Management POS / warehouse |
| ESC/POS print | `bridges/android/escpos-printer` | Management POS checkout, bin labels |
| GPS | `bridges/android/location-tracker` | **Delivery only** |
| POD camera / signature | `pod-camera`, `pod-signature` | Delivery |
| Biometric photo (HR) | `biometric-photo` | Management onboarding |
| Biometric auth | stub contracts | Deferred |

**Management wiring:** `MainActivity` owns `CameraxQrScannerBridge` + `BluetoothEscPosPrinterBridge` (+ biometric photo); attaches in lifecycle; ViewModels call bridges — Compose never talks to CameraX/BluetoothAdapter directly.

**Web:** typed OEM / UUID; companion pairing for remote scans; live map = Realtime subscribe only. See [`bridges/README.md`](../../bridges/README.md).

Route hardware work to `@hardware_mobile_agent`.

---

## 5. Hard exclusions (do not implement)

| Exclusion | Applies to |
|-----------|------------|
| **No ZIMRA / FDMS / fiscalisation / fiscal QR / mTLS fiscal devices** | Checkout, invoices, receipts, POD, Edge |
| **No payroll tax authority integration** | No PAYE, NSSA POBS/APWCS/ZIMDEF, tax brackets, P4/P4A — gross + manual deductions only |
| **No HTML5 / WebView QR or browser GPS** | Web staff, WebViews |
| **No driver GPS inside management** | Use `apps/android-delivery` |

Invoices remain tax-agnostic commercial documents.

---

## 6. How to change things safely

### 6.1 “I need to change who can access a module”

1. **Backend first:** new migration adjusting `has_staff_role(…)` in RLS policies and SECURITY DEFINER RPCs for that domain.
2. **Web UI:** update `STAFF_NAV_TREE` + `pathAccessFor` / `STAFF_MODULE_ROLES` in `apps/web/lib/staff-auth.ts`.
3. **Android:** update matching `*StaffRoles` / hub filters in `RpcModels.kt` + `MainActivity` module visibility; honor `moduleAllowed` for organogram.
4. Run `/supabase-rls-auditor` after migrations; `/verifier` after UI.

Do **not** rely on hiding nav alone.

### 6.2 “I need to change POS UX”

| Change | Where |
|--------|-------|
| Web cart / checkout UI | `apps/web/components/staff-pos-*.tsx`, `lib/staff-pos.ts` — `@web_agent` |
| Android till / offline / companion | `apps/android-management/feature/pos/` — `@management_app_agent` |
| Shared money/cart helpers | `packages/shared/` — do not duplicate pricing in app modules |
| RPC behavior (tenders, void, QR add) | `supabase/migrations/*pos*` — `@backend_agent` |
| QR / printer | `bridges/` — `@hardware_mobile_agent` |

Web must not call `add_cart_line_from_qr` with a browser scanner.

### 6.3 “I need to change roles / RLS helpers”

| Edit | Location |
|------|----------|
| Enum values | New migration `ALTER TYPE … ADD VALUE` (never rewrite history casually) |
| Helpers | Prefer new migration replacing `is_staff` / `has_staff_role` if needed |
| Assign roles | `assign_staff_role` / admin tools; HR onboarding links employee ↔ user |
| Organogram modules | `hr_roles.module_access` + `my_module_access` RPC — UX only |

### 6.4 “I need to change finance / HR”

| Domain | Owner lane | Key paths |
|--------|------------|-----------|
| Ledger schema / statements | `@finance_agent` | `supabase/migrations/*ledger*`, `packages/shared/src/ledger/**` |
| Finance web UI | `@web_agent` | `staff-finance-panel.tsx`, `lib/staff-finance.ts` |
| HR schema / payroll RPCs | `@backend_agent` | `*hr*`, `*payroll*` migrations |
| HR UI | `@web_agent` / `@management_app_agent` | `/staff/hr`, `feature/hr` |

Never add tax engines. Ledger: append-only + reverse.

### 6.5 Agent lane map (quick)

| Lane | Paths |
|------|-------|
| `@web_agent` | `apps/web/`, `packages/ui/` |
| `@management_app_agent` | `apps/android-management/` |
| `@android_delivery_agent` | `apps/android-delivery/` |
| `@hardware_mobile_agent` | `bridges/` |
| `@backend_agent` | `supabase/`, `packages/supabase-client/` |
| `@finance_agent` | ledger/finance migrations + shared ledger |
| `@data_pipeline_agent` | `data-pipeline/` (catalog ingest, not staff UI) |

Pipeline for non-trivial features: `/manager` → `/planner` → one coding lane → `/security-reviewer` → `/verifier`.

### 6.6 Pointers (adopt-first)

| Need | Look here first |
|------|-----------------|
| Prior decisions | `docs/decisions/` (RBAC, delivery app, POS pairing, OTP, offline POS, OAuth) |
| Phase status | `docs/plans/2026-07-23-master-erp-development.md` |
| Web RBAC plan | `docs/plans/2026-07-25-web-management-parity-rbac.md` |
| Batch1 POS/HR/finance | `docs/plans/2026-08-03-erp-batch1-commerce-pos-hr-finance.md` |
| Cross-cutting overview | `docs/IMPLEMENTATION-AND-DESIGN-GUIDE.md` |
| Management ops | `apps/android-management/README.md` |
| Delivery ops | `apps/android-delivery/README.md` |
| Skills | `accounting-ledger`, `qr-inventory-workflow`, `token-discipline` |
| Typed RPCs | `packages/supabase-client/src/database.types.ts`; Android `RpcNames.kt` |

### 6.7 Scenario cheat sheet

| Goal | First files |
|------|-------------|
| Add staff nav item | `staff-auth.ts` → new page under `app/(staff)/staff/…` → gate roles → RPC already exists? |
| New staff-only table | Migration with RLS using `has_staff_role` in **same** file |
| Emp# login bug | `resolve_staff_login_email`, `staff_login_*` tables, web `signInWithStaffIdentifier` |
| Force password reset | `set_must_change_password` / onboarding Edge; UI `/staff/change-password` |
| Quarantine return | `post_return_to_quarantine` + `post_return_credit_note`; ensure warehouse `is_quarantine` |
| ZiG / multi-currency | Explicit `currency_code` on money rows; finance exchange-rate tab; never assume USD |
| Driver GPS | Only delivery app + location-tracker bridge |

---

## 7. Status snapshot (honest)

| Area | Backend | Web staff | Android management | Notes |
|------|---------|-----------|--------------------|-------|
| Auth / roles / emp# | Strong | Strong | Strong | OAuth = customers |
| POS | Strong | Strong (no QR) | Strong + offline + bridges | |
| Warehouse / bins / consignment | Strong | Strong | Strong | |
| Finance desk | Strong | Strong | **Absent** | Use web |
| HR clock / onboarding / organogram | Strong | Strong | Clock + onboarding | Payroll UI thinner than schema |
| Logistics / dispatch | Strong | Strong (view) | Strong (no GPS out) | |
| Delivery driver | Strong | N/A | N/A (other app) | |
| Chat / CRM credit / reviews | Strong | Strong | Chat + credit | |
| Analytics | Strong | Strong | Absent | |
| Catalog price admin UI | Data model | Gap | Gap | Pipeline / SQL |
| Biometric clock / auth | Deferred | N/A | Photo only | |

When in doubt: **prefer extending existing RPCs and role matrices** over inventing parallel carts, second auth systems, or browser hardware shortcuts.
