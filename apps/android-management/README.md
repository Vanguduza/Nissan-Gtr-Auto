# GTR Management — Android (OSS shell)

Phase 1 rebuild: **CoolMallKotlin-inspired** POS dual-pane chrome + **inventree-app-inspired**
warehouse receive IA, painted with **GTR brand tokens only** (`packages/android-ui` /
`packages/ui/brand-tokens.json`). Backend structures come from **Supabase via `RpcClient`**
(Fake when URL/key unset) — not CoolMall or InvenTree servers.

> Honesty: this is **not** a full CoolMall clone. Phase 1 = auth gate + role hub + POS skeleton
> + warehouse receive skeleton. Prior modules live under [`../android-management-legacy/`](../android-management-legacy/).

Plan: [`docs/plans/2026-08-14-management-oss-shell-rebuild.md`](../../docs/plans/2026-08-14-management-oss-shell-rebuild.md)

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Copy `local.properties.example` → `local.properties` (gitignored). Never commit secrets.

## Module layout

| Module | Role |
|--------|------|
| `:app` | Launcher, AuthGate, role hub, route shell; **phone** / **tablet** flavors |
| `:core:rpc` | `RpcClient` + `FakeRpcClient` + `SupabaseRpcClient` (SoR adapters) |
| `:feature:auth` | Emp#/email/phone + password (Live) / Fake skip |
| `:feature:pos` | Dual-pane catalog \| cart skeleton (≥700dp) |
| `:feature:warehouse` | Receive IA: OEM → location → `post_stock_receipt` |
| `:android-ui` | GTR colors / typography only |

## Fake smoke (no Supabase)

1. Leave `SUPABASE_URL` / `SUPABASE_ANON_KEY` empty (or `rpc.forceFake=true`).
2. Launch → continue without signing in → hub (Fake roles include admin).
3. **POS** → Open cart → search OEM → Add → Checkout.
4. **Warehouse receive** → OEM → pick WH → qty/cost → Post receipt.

## Build / test

```bash
cd apps/android-management
./gradlew :app:assemblePhoneDebug :app:assembleTabletDebug
./gradlew :core:rpc:test :feature:pos:test
```

## Constraints

- Supabase remains system of record — no ERPNext/Odoo/InvenTree/CoolMall API SoR.
- Bridge-First for QR/printer (Phase 2 wiring). No Expo. No ZIMRA. No payroll-tax UI.
- Do not edit `apps/catalog-apk/**` from this lane.

## Phase 2+

See plan: full POS (offline SqlCipher, companion, quotations, Bridge QR/print, Lock Task),
warehouse bins/consignment/transfers, and port of legacy hub modules.
