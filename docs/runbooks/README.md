# Operator runbooks index

Thin index for Phase 15. Prefer smoke SQL + Edge README over duplicating procedures.

| Topic | Smoke / SQL | Edge / hardening |
|-------|-------------|------------------|
| Finance period close | `supabase/tests/phase3_finance_smoke.sql` (`lock_accounting_period`, opening balances, journals) | — |
| Stock reconciliation / cycle count | `supabase/tests/phase4b_reconciliation_smoke.sql` | — |
| POS checkout / sales invoice | `supabase/tests/phase5_sales_smoke.sql` | — |
| Pick / pack / Delivery Note | `supabase/tests/phase10_logistics_smoke.sql` | — |
| Payment allocation + store credit | `supabase/tests/phase13_payments_receipts_smoke.sql` | ContiPay/Paynow: `supabase/functions/contipay-initiate`, `contipay-webhook`, `paynow-initiate`, `paynow-webhook` |
| Manager SMS + customer receipt drain | same Phase 13 smoke; RPCs `drain_sms_outbox_batch` / receipt drain in `…93000_receipt_delivery_artifacts.sql` | `supabase/functions/process-sms-outbox`, `process-customer-receipts`; worker auth: [`supabase/functions/README.md`](../../supabase/functions/README.md) |

## How to run smokes locally

See [`docs/HARDENING.md`](../HARDENING.md) §4 (Docker `psql` exec pattern) and CI `db-smoke` in `.github/workflows/ci.yml`.

```powershell
# After supabase start / db reset — container name from `docker ps`
docker exec -i <supabase_db_*> psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/phase3_finance_smoke.sql
```

## Secrets / worker AuthZ

Do not put credentials in git. Env names and header rules: [`docs/HARDENING.md`](../HARDENING.md) §2 and [`supabase/functions/README.md`](../../supabase/functions/README.md).
