# GTR Supabase adapter (CoolMall injection)

Contracts mirror **web staff** (`apps/web/lib/staff-auth.ts`, `staff-account.ts`, `staff-warehouse.ts`).  
DI: `GtrAdapterModule` picks **Live** when `GtrSupabaseConfig.useLive()` (URL + anon set, `rpc.forceFake` false); otherwise **Fake**.

App wires config from BuildConfig ← `local.properties` (`vendor/coolmall-gtr/local.properties.example`).

## Auth (B1)

| Method | Web equivalent | Live | Fake |
|--------|----------------|------|------|
| `resolveLoginEmail` | `resolve_staff_login_email` | RPC | always `fake@local.test` |
| `signInWithPassword` | GoTrue | auth-kt Email | accepts non-blank |
| `signInWithStaffIdentifier` | same | resolve → password → context | same |
| `loadStaffContext` | `loadStaffContext` | profiles + staff_roles + `my_module_access` | admin seed |
| `changePassword` | `/staff/change-password` | `updateUser` + `clear_must_change_password` | clears flag |
| `reauthWithPassword` | idle unlock | re-sign with session email | length ≥ 6 |
| Idle lock | 3 min | `GtrIdleLockController` + MainActivity overlay | same |

## My Account (B2 MVP)

`GtrMyAccountAdapter` → `get_my_staff_profile` / `update_my_staff_profile` / `list_my_payslip_history`.  
Photo Storage + branded PDF deferred.

## Warehouse

`listMasterStock` → `list_master_stock`.  
D1: `listWarehouses` / `searchStockItems` / `post_stock_receipt` / `create_stock_transfer` / approve+reject. Typed OEM (Bridge QR later). Cycle/bins/consignment/insights next.

## Forbidden

- CoolMall fashion API as SoR  
- Porting screens from `android-management-legacy`  
- Browser/HTML5 QR (Bridge-First only)  
- POS cart / `staff-pos` bindings in CoolMall
