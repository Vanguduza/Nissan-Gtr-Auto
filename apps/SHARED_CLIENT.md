# Mobile shared client consumption

Phase 11–12 scaffolds (`apps/ios`, `apps/android-customer`, `apps/android-management`) must **not** duplicate business rules.

| Package | Path | Use from mobile |
|---------|------|-----------------|
| `@gtr/supabase-client` | `packages/supabase-client` | Generated `Database` types, query helpers — bind after customer/staff APIs exist |
| `@gtr/shared` | `packages/shared` | Money (`USD`/`ZIG`), cart/core-charge math, QR payload helpers (`inventory/qr`) |

Native apps will consume these via TS in a shared layer, codegen, or thin hand ports — **pricing and core-charge logic stays in `@gtr/shared`**, never reimplemented per app.

Hardware interfaces: `bridges/contracts/` only (see `bridges/README.md`).
