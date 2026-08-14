# GTR Supabase adapter stubs (CoolMall injection)

Contracts mirror **web staff** auth/hub (`apps/web/lib/staff-auth.ts`).  
CoolMall `core:network` / `AuthRepository` must call these instead of CoolMall HTTP.

Not yet wired into Gradle — Phase B includes this module and swaps repositories.

## Auth

| Method | Web equivalent | Supabase |
|--------|----------------|----------|
| `resolveLoginEmail(identifier)` | `signInWithStaffIdentifier` | RPC `resolve_staff_login_email` |
| `signInWithPassword(email, password)` | GoTrue | `auth.signInWithPassword` |
| `loadStaffContext()` | `loadStaffContext` | `profiles` + `staff_roles` + RPC `my_module_access` |
| Fake bypass | local smoke | no secrets |

## Module hub

Nav tree and role gates must match `STAFF_NAV_TREE` / `pathAccessFor` / `filterNavTreeForModuleAccess`.  
Sales-only home → POS (web `prefersPosHome`).

## Forbidden

- CoolMall fashion API as SoR  
- Porting screens from `android-management-legacy`  
- Browser/HTML5 QR (Bridge-First only)
