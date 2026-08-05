# POS Kiosk + Role Routing (shopfloor tablet)

- Status: draft
- Date: 2026-08-03
- Source (verbose, leave untouched): [`Nissan_GTR_Auto_POS_Kiosk_Role_Based_Routing_Specification.md`](../../Nissan_GTR_Auto_POS_Kiosk_Role_Based_Routing_Specification.md) (repo root)
- Parent / prior: Batch 1 [`2026-08-03-erp-batch1-commerce-pos-hr-finance.md`](./2026-08-03-erp-batch1-commerce-pos-hr-finance.md); ADRs [`pos-scan-session-pairing`](../decisions/2026-07-25-pos-scan-session-pairing.md), [`web-management-parity-rbac`](../decisions/2026-07-25-web-management-parity-rbac.md)
- Lane(s): **`@management_app_agent`** (primary) → `@backend_agent` (landing/session schema if needed) → `@hardware_mobile_agent` (Bridge-only auth sensors later) → `@web_agent` (parity of landing/session UX only, not Android kiosk)
- Skills: `/token-discipline`; `/qr-inventory-workflow` only if scan path changes; **no** auto `/ui-ux-pro-max`

## Goal

Turn the **android-management** shop-counter tablet into a dedicated Nissan GTR Auto POS/business terminal: branded app startup → staff auth → **role-based auto-routing** (sales → POS; warehouse/admin → hub modules) → Lock Task / Device Owner kiosk — **reusing** Batch 1 POS, organogram `module_access`, and RPC authority. Do not rebuild POS or invent a second RBAC stack.

## What already exists (adopt-first)

| Area | Status | Pointers |
|------|--------|----------|
| Standalone POS | **Done** | Web `/staff/pos`; Android `feature/pos`; auto `create_pos_cart`; search/catalog/cart |
| Role home (coarse) | **Done** | `prefersPosHome` / `ManagementHomeRoles` — sales-only → POS; admin\|warehouse → hub |
| Module gate | **Done** | `hr_roles.module_access` + `my_module_access`; web `filterNavTreeForModuleAccess`; Android hub `moduleAllowed` |
| Companion scan | **Done** | `pos_scan_sessions` + Realtime; Bridge QR only; pairing QR **display** on web |
| Split-bill / park | **Done** | `checkout_pos_cart_with_tenders`; `park_pos_cart` / `resume_pos_cart` |
| Auth | **Partial** | Supabase email/phone + password (`SignInScreen`, web `/login`); **not** Employee ID+PIN |
| staff_roles | **Coarse** | `admin` \| `sales` \| `warehouse` — authority via RLS/`has_staff_role` RPCs |
| Kiosk / Lock Task / boot launch | **Missing** | No Device Owner / Lock Task / BOOT_COMPLETED / maintenance console |
| Session idle lock | **Missing** | No PIN re-lock / inactivity lock on tablet |
| Configurable default landing | **Partial** | Hard-coded `prefersPosHome`; no `default_landing` on `hr_roles` yet |
| Elaborate GT-R splash + engine audio | **Missing** | Licensing-sensitive; not required for ops |

## Spec → priority (Must / Should / Later)

### Must (operational + security)

1. **Role-based auto-routing after auth** — sales → POS ready for a new sale; warehouse → inventory/hub; admin → hub. Extend today’s `prefersPosHome`; prefer DB-driven `default_landing` on `hr_roles` (or role→route map) over hard-coding every role in UI.
2. **Kiosk / dedicated device** — Lock Task Mode + Device Owner / Android Enterprise allow-list; auto-launch after reboot/crash; block Home/Recents/Settings/Play for ordinary staff. Immersive UI alone is insufficient.
3. **Permission enforcement = RPC + RLS** — nav hide is UX only. Deepen action gates (discount, void, stock adjust, refund, price override) in SECURITY DEFINER RPCs; clients stay thin. Align with existing `staff_roles` + `module_access`.
4. **Session lifecycle** — inactivity lock → branded lock/reauth → resume; logout → staff login **inside** app (never Android launcher); fail closed if roles/permissions cannot load.
5. **Maintenance bypass** — elevated reauth → limited device admin console (Wi‑Fi/BT/printer/scanner diagnostics, logs, restart, exit Lock Task) + audit; not unrestricted Settings for every manager.
6. **Preserve POS ops already shipped** — search (OEM/SKU/VIN modes), Bridge scan-to-cart, park/resume, split-bill tenders, receipt contacts, companion pairing optional.

### Should (next epic after Must)

1. App-level branded splash (short, local assets, **licensed**; skip firmware boot logo / rooting — vendor spec already forbids unsafe bootloader work).
2. Configurable landing + richer organogram role → route mapping without app rebuild.
3. Counter/terminal identity on login UI + audit fields (branch/terminal on session).
4. Web parity for landing/session messaging only (web is **management fallback**, not the kiosk SoR — ADR web-management-parity-rbac).

### Later (defer)

1. Employee ID + PIN as primary auth (conflicts with current Supabase password flow — optional second factor or map emp# → auth user via Edge; do not fork SoR).
2. NFC / badge QR / fingerprint login (Bridge-First biometric only; no WebView shortcuts).
3. Full GT-R motion + synchronized engine audio (licensing + YAGNI; replaceable asset pack if business supplies rights).
4. Fine UI roles from vendor table (Cashier, Parts Specialist, Workshop, Service Advisor…) as **first-class app roles** — express via `hr_roles` + `module_access` + landing, not a parallel enum dump.
5. Offline permission cache / offline sale sync (needs explicit ADR; high risk of privilege escalation).
6. Casbin / full action-matrix framework — open decision from Batch 1; only if `module_access` + RPC checks prove insufficient.
7. iOS companion / multi-scanner / firmware OEM boot animation.

## Conflicts with standing laws

| Spec item | Conflict / resolution |
|-----------|------------------------|
| Browser QR / HTML5 badge scan | **Bridge-First** — native Bridge only |
| Fiscal / tax QR on receipts | **No ZIMRA** — standard receipts only |
| Client-only permission hide | **RPC + RLS authority** — reject unauthorized actions server-side |
| New in-page tab strips for dashboards | **Staff sidebar IA** — web: `STAFF_NAV_TREE` children only; Android: hub/module menu pattern |
| Root / custom firmware boot logo | Out — app-level splash only |
| Invent Employee ID+PIN store | Prefer extend Supabase auth; no plaintext PIN; Keystore only for local session secrets |
| Duplicate POS architecture (`feature/startup` rewrite) | Integrate into existing `android-management` auth + MainActivity routing |

## Acceptance checklist

### Routing & POS

- [ ] Sales-only staff land on POS (Android + web) without hub detour; cart auto-created / ready
- [ ] Admin/warehouse land on hub; POS available only if `module_access` allows
- [ ] Missing/inactive role → deny, no unrestricted dashboard
- [ ] Park/resume, split-bill, Bridge scan, companion optional still work

### Kiosk & session (Android tablet)

- [ ] App auto-starts after reboot; Lock Task holds focus; Home/Recents blocked under Device Owner policy
- [ ] Idle lock + reauth; logout returns to in-app login
- [ ] Maintenance mode: permission-gated, audited, expires back to kiosk
- [ ] No HTML5/browser camera; no ZIMRA; new tables (if any) ship with RLS

### Permissions

- [ ] Unauthorized modules hidden **and** RPCs reject
- [ ] `module_access` empty → staff_roles fallback (current behavior); admin bypass documented
- [ ] Landing configurable without rebuild (Should)

## Paths in scope (likely)

- `apps/android-management/` — auth, `MainActivity` routing, new kiosk/Lock Task/maintenance (net-new)
- `apps/web/lib/staff-auth.ts`, staff gate — landing parity only
- `supabase/migrations/` — optional `default_landing` / terminal session / maintenance audit (+ RLS same file)
- `bridges/` — only if badge/biometric login or printer/scanner config surfaces (Should/Later → `@hardware_mobile_agent`)
- Docs: this plan; optional ADR if Device Owner provisioning is durable

## Out of scope

- Rewriting web/Android POS cart/checkout
- ZIMRA / payroll tax / browser QR
- Firmware/bootloader branding, device rooting
- Replacing `staff_roles` with vendor’s full role taxonomy overnight
- Offline-first POS SoR
- Customer apps (`apps/ios`, `android-customer`, delivery)
- Mutating the root vendor MD beyond optional one-line pointer (prefer leave unchanged)

## Suggested implementation slices

| Slice | Owner | Notes |
|-------|-------|-------|
| A — Configurable landing + route matrix | `@backend_agent` + `@management_app_agent` | Extend `hr_roles` / map; wire Android+web home |
| B — Lock Task + boot recover + Device Owner runbook | `@management_app_agent` | Docs for provisioning; no unsafe OEM mods |
| C — Idle lock + maintenance console + audit | `@management_app_agent` (+ backend if audit table) | |
| D — Action-level RPC harden (discount/void/adjust) | `@backend_agent` | Triggered by real POS gaps, not speculative matrix |
| E — Branded splash (licensed assets) | `@management_app_agent` | Should; audio Later |

## Handoff

1. Implement slices A→C in **`@management_app_agent`** (backend where schema needed); do not dual-run lanes on one dirty tree
2. `/security-reviewer` (auth, Device Owner, session, maintenance)
3. Migrations → `/supabase-rls-auditor`
4. `/verifier` (exclusions: ZIMRA, payroll tax, HTML5 QR; POS smokes still green)
5. `/manager` for done gate

**Invoke:** `/manager` to sequence, or `@management_app_agent` for slice A/B once approved.
