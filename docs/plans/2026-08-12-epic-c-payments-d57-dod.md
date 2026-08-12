# Epic C — E3 payments + D-57 checkout DoD

- Status: **closed** (web + `@gtr/payments` evidence)
- Lanes: `@web_agent` (`apps/web/**`), `@gtr/payments` (`packages/payments/**`)
- DoD source: `docs/DIAL_SPARE_ADOPTION_PLAN.md` E3 + §8 Epic Finance; prime brief Epic C
- No ZIMRA. No Epic D+.

## DoD lines

| Line | Item | Evidence |
|------|------|----------|
| **C1** | PspAdapter registry + stub webhook/idempotency habits | `packages/payments` — `PspRegistry` / `createStubPspAdapter`; initiate deterministic on `idempotencyKey`; webhook replay `duplicate: true` (`psp.test.ts`) |
| **C2** | Cart wires `fxRateId` for EcoCash / ZiG | `cart-checkout.tsx` → `buildCheckoutDisplay({ fxRateId })`; rate via `get_zig_exchange_rate`; id via `fetchZigExchangeRateId` → `daily_exchange_rates.id` (same ordering as RPC). Non-EcoCash / USD-only → `fxRateId: null` |
| **C5** | D-57 USD browse + ZiG at pay | `buildCheckoutDisplay` keeps browse USD; EcoCash/ZiG pay path carries rate + `fxRateId` |
| **C6** | AI cannot write payable / `amount_minor` / PO money | Grep-clean: `supabase/functions/process-ai-reports/index.ts`, `process-crm-promos/index.ts` (delivery/KPI RPCs only). Semgrep-style unit: `packages/payments/src/psp.test.ts` + `AI_NEVER_WRITES_MONEY.greppedWorkerPaths` |
| **C7** | WA / satellite D-57 surface | **Gap (documented, non-blocking):** `services/whatsapp-flows` has **no** `buildCheckoutDisplay` import/usage. Primary D-57 surface is web `/cart`. WA Flow parity = follow-up, not Epic C blocker. |

## Files

- `apps/web/lib/customer-storefront.ts` — `fetchZigExchangeRateId`
- `apps/web/components/cart-checkout.tsx` — pass `fxRateId` on ZiG/EcoCash display path
- `packages/payments/src/psp.ts` — `AI_NEVER_WRITES_MONEY` + grepped paths
- `packages/payments/src/psp.test.ts` — C6 filesystem grep
- `promptfoo/README.md` — pointer to C6 worker grep

## Out of scope

Epic D (Meili), WhatsApp Flow checkout display parity, live Edge PSP extraction from stubs.
