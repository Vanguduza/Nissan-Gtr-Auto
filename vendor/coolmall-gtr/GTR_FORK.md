# CoolMall → GTR management fork

Upstream: [Joker-x-dev/CoolMallKotlin](https://github.com/Joker-x-dev/CoolMallKotlin) (MIT).  
Fashion demo images under `docs/images` were stripped to keep the monorepo lean.

## Intent

This tree is the **product UX shell** for Nissan GTR Auto staff Android.

| Concern | Source of truth |
|---------|-----------------|
| Behavior / modules / RPCs | `apps/web` staff (`lib/staff-auth.ts`, `lib/staff-*.ts`) |
| Look & feel | This CoolMall fork |
| Colours | GTR only (`packages/ui/brand-tokens.json`) — applied in `core/designsystem` |
| Backend | Supabase adapters (see `gtradapter/`) — **not** CoolMall’s API |

Do **not** port UI from `apps/android-management-legacy/`.

Plan: `docs/plans/2026-08-14-management-oss-shell-rebuild.md`

## Phase A done here

- Vendored CoolMall source
- GTR primary `#C8102E`, chalk/steel/accent overrides in `Color.kt` + `ThemeColorOption`
- Stub auth/hub adapter contracts in `gtradapter/`

## Phase A+ (this pass) — DI + non-POS desks

- `:gtradapter` on Gradle + Hilt (`GtrAdapterModule` → Fake by default)
- Staff login: emp#/email/phone → `signInWithStaffIdentifier` (web parity)
- Hub tiles from `StaffNavTree` / `filterModules` (**POS excluded**)
- Bottom nav: Hub · Warehouse · Account (no cart/POS)
- Warehouse **master stock** list + search (`list_master_stock` Fake seed)
- Account **change password** + sign-out
- Unit tests: `:gtradapter:testDevDebugUnitTest`

Build: `./gradlew :app:assembleDevDebug` (or Windows `gradlew.bat`)

## Next

Phase B: Live supabase-kt GoTrue + RPCs; then POS when requested. More warehouse desks (transfers, receive) after Live inject.
