# GTR Supabase adapter (CoolMall injection)

Contracts mirror **web staff** auth/hub (`apps/web/lib/staff-auth.ts`).  
CoolMall DI registers Fake implementations by default (`GtrAdapterModule`).

## Auth

| Method | Web equivalent | Supabase |
|--------|----------------|----------|
| `resolveLoginEmail(identifier)` | `signInWithStaffIdentifier` | RPC `resolve_staff_login_email` |
| `signInWithPassword(email, password)` | GoTrue | `auth.signInWithPassword` |
| `signInWithStaffIdentifier` | same | resolve → password |
| `loadStaffContext()` | `loadStaffContext` | `profiles` + `staff_roles` + RPC `my_module_access` |
| `changePassword` | `/staff/change-password` | GoTrue `updateUser` |
| Fake bypass | local smoke | no secrets |

## Module hub

`StaffNavTree` mirrors `STAFF_NAV_TREE` ids/roles.  
`filterModules` = web `filterNavTreeForModuleAccess` with **POS excluded** this phase.

## Warehouse (first real desk)

`GtrWarehouseAdapter.listMasterStock` → web RPC `list_master_stock` (Fake seed for smoke).

## Forbidden

- CoolMall fashion API as SoR  
- Porting screens from `android-management-legacy`  
- Browser/HTML5 QR (Bridge-First only)  
- POS cart / `staff-pos` bindings this phase
