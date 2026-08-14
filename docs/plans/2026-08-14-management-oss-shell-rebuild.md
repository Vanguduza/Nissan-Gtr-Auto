# Management Android — OSS shell rebuild (CoolMall + InvenTree IA)

- **Date:** 2026-08-14
- **Lane:** `@management_app_agent`
- **Status:** Phase 1 in progress

## Intent

1. From GTR, **adopt colours only** (steel / chalk / red + existing `@gtr/ui` / `packages/android-ui` fonts).
2. **Retire** the current management UI chrome (thin scaffolds grown into a kitchen-sink hub).
3. Rebuild UX using **CoolMallKotlin** (MIT) POS/commerce density patterns and **inventree-app** warehouse IA (scan → location/item → actions) — **Compose reimplementation**, not Flutter InvenTree runtime, not CoolMall/InvenTree backends.
4. **Inject GTR structures** via existing Supabase RPCs (`RpcClient` / Fake / Live) — adapters only. Supabase remains SoR.

## Hard constraints

- No ERPNext / Odoo / Moqui / InvenTree server / CoolMall API as SoR.
- Bridge-First for QR / printer / biometric / GPS. No Expo. No ZIMRA. No payroll-tax UI.
- Do **not** touch `apps/catalog-apk/**`.
- Multi-currency USD/ZIG, core charges, quarantine, Lock Task tablet dual-pane (catalog left / cart right) remain product rules even when OSS donors lack them.

## What is archived vs deleted

| Path | Action |
|------|--------|
| `apps/android-management/` (pre-rebuild) | **Archived** → `apps/android-management-legacy/` (full prior feature tree + tests; history preserved) |
| New `apps/android-management/` | **Scaffold** CoolMall-inspired Compose shell + retained `:core:rpc` |
| Legacy feature UIs (POS offline SqlCipher, kiosk DO, HR, dispatch, …) | Stay in **legacy** until Phase 2+ port; not deleted from git |
| `apps/catalog-apk/**` | Untouched |

## Color-only token adoption

| Source | Use |
|--------|-----|
| `packages/ui/brand-tokens.json` | Canonical hex |
| `packages/android-ui` (`GtrColors`, `GtrTheme`, typography) | Compose Material3 mapping |
| CoolMall fashion theme | **Strip** — do not vendor pastel/fashion palettes |
| inventree-app Flutter theme | **IA only** — re-skin with GTR steel/chalk/red |

## Adapter map (RPC → screens)

| Screen (new shell) | GTR structures | RPC / Fake |
|--------------------|----------------|------------|
| Sign-in | session | GoTrue via `SupabaseRpcClient` / Fake bypass |
| Role hub | `staff_roles`, `module_access` | `listMyStaffRoles`, `my_module_access` + `ManagementHomeRoles` |
| POS dual-pane | warehouses, catalog hits, cart lines, currency | `listWarehouses` / `search_catalog` / `create_pos_cart` / `add_cart_line` / `listPosCartLines` / `checkout_pos_cart` |
| Warehouse receive | warehouses, stock by OEM, receipt lines | `listWarehouses` / `lookupStockItemByOem` / `post_stock_receipt` |
| (Phase 2+) bins, consignment, pick/DN, HR, … | existing `RpcNames` | port from legacy modules |

## Warehouse IA (InvenTree-inspired)

Compose flow (not Flutter embed):

1. **Scan / OEM entry** → resolve stock item  
2. **Choose location / warehouse** (WH1 receive vs quarantine awareness)  
3. **Action** (receive qty → `post_stock_receipt`)

## Phased milestones

### Phase 1 (this pass) — green shell

- [x] Plan under `docs/plans/`
- [x] Archive prior tree → `android-management-legacy/`
- [x] New app: Fake sign-in, role hub, POS dual-pane skeleton, warehouse receive skeleton
- [x] `:core:rpc` retained (Fake + Live + unit tests)
- [x] Docs: README, CHANGELOG, ENHANCEMENTS
- [x] `assembleDebug` + focused unit tests; commit + push

### Phase 2 — full POS port

- CoolMall-density catalog grid, core-charge lines, USD/ZIG settle, offline SqlCipher, companion scan, quotations, Bridge QR/print, Lock Task tablet flavor polish

### Phase 3 — full warehouse + ops port

- Bins / pick-path / consignment / transfers / recon from legacy + inventree-like navigation depth
- Dispatch, HR (gross-only), procurement, credit, CRM, fleet, chat — port or re-skin from legacy

## Risks

| Risk | Mitigation |
|------|------------|
| Accidental SoR rebase onto CoolMall/InvenTree APIs | Adapters call only `RpcClient`; no donor HTTP clients |
| Losing working Fake/Live RPC surface | Keep `:core:rpc` in new tree; legacy as reference |
| Catalog-apk / other dirty trees | Do not stage those paths |
| Claiming “full CoolMall clone” | Phase 1 is scaffold + skeletons only — document honesty |
| Dual trees confuse agents | README + plan point to legacy; `rufler` stays on `apps/android-management/**` |

## OSS attribution (patterns only)

- [CoolMallKotlin](https://github.com/Jiu-xiao/CoolMallKotlin) — MIT — POS/commerce chrome density  
- [inventree-app](https://github.com/inventree/inventree-app) — MIT — warehouse scan→item→action IA  

No full source vendor in Phase 1 (too large); Compose shells inspired by those patterns.

## Out of scope (Phase 1)

- Full CoolMall module graph / fashion theme assets  
- Flutter InvenTree runtime  
- Port of every legacy feature module into the new shell  
- Any change under `apps/catalog-apk/**`
