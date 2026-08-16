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
| Backend | Supabase via `gtradapter/` Fake → Live (supabase-kt 3.1.1) |

Do **not** port UI from `apps/android-management-legacy/`.

Plans: `docs/plans/2026-08-14-management-oss-shell-rebuild.md`,  
`docs/plans/2026-08-16-coolmall-web-feature-injection.md` (Phase B).

## Phase A done

- Vendored CoolMall + GTR colours
- Fake auth / hub / change-password / master-stock
- Bottom nav Hub · Warehouse · Account (POS excluded)

## Phase B (this pass) — Live Supabase inject

| Slice | Status |
|-------|--------|
| **B1 Auth Live** | `LiveGtrStaffAuthAdapter` — resolve + GoTrue + staff context; 3 min idle lock overlay |
| **B2 My Account Live (MVP)** | `GtrMyAccountAdapter` profile edit + payslip list (photo/PDF later) |
| **B3 Master-stock Live** | `LiveGtrWarehouseAdapter` OEM query (+ optional chassis) |

**Live vs Fake:** Live when `SUPABASE_URL` + `SUPABASE_ANON_KEY` are set in `local.properties` and `rpc.forceFake` is not `true`. Otherwise Fake (CI/smoke).

```
# vendor/coolmall-gtr/local.properties  (or monorepo root)
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=your-anon-key
# rpc.forceFake=true
```

See `local.properties.example`. Never commit real keys.

Build / test:

```
./gradlew :gtradapter:testDevDebugUnitTest :app:assembleDevDebug
```

## Phase D1 (this pass) — Warehouse deepen (partial)

| Slice | Status |
|-------|--------|
| Receive | Typed OEM → `post_stock_receipt` (USD/ZIG + unit cost). Bridge QR later |
| Transfers | WH1→WH2 create + pending approve/reject |
| Cycle / bins / consignment / insights | Next D1 pass |

## Next

Cycle count, bins, consignment, insights. Then D2 finance. Open POS = deep-link to `android-pos` only when asked.
