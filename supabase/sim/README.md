# Simulated trading data and end-to-end runs (local only)

Never point these scripts at the hosted project: the ledger is append-only and simulated sales
would stay in the books. They target a **local** Supabase stack (`supabase start`, API on port
55421, database on 55422) that has the same schema as hosted.

| Script | What it does |
|--------|--------------|
| `sim.py` | Helpers: run SQL as a signed-in user (RLS and `auth.uid()` apply), as the service role, create Auth users. |
| `seed.py` | Staff (owner, manager/approver, two cashiers, warehouse, dispatch, finance, two drivers), trade and retail customers, opening stock at two branches, card machines. Writes `ids.json`. Password for every simulated user: `Sim-Passw0rd!`. |
| `e2e_cod.py` | Customer checks out paying on delivery → warehouse picks → dispatcher assigns and dispatches → proof of delivery is refused while money is due → driver collects in two parts → proof of delivery with the customer's code. |
| `e2e_day.py` | A trading day: till opens with a float → cash and split sales → short payment refused → sale on account (and refusals over the limit / with no credit) → cash refund, store credit and credit note returns posted by a manager → two refused pay-on-delivery orders suspend a new online customer → cashier cannot lift it, manager can → the customer can order again → till closes with no variance. |
| `e2e_card.py` | Card payments: admin pairs the till tablet; counter charge approved (signed answer, replay changes nothing, same machine transaction refused on another sale), declined, forged/tampered answers refused, no answer → recovery → approved; driver pairs phone, card-only order refuses cash, card at the door settles the invoice; an approved charge voided on the machine before the sale is finished; a manager refunds a sale to the card (credit note, Dr 4110 / Cr 1170). `card.py` signs like the device and verifies like the `card-terminal-result` Edge Function (needs `pip install cryptography`). |
| `e2e_backorder.py` | A part Harare does not have: back-order → cannot be marked ready before it arrives → supplier delivers → marked ready holds it for the customer (another customer cannot buy it) → customer pays → handed over. Transfer from Bulawayo for a customer: two-person transfer, held on arrival, sold, then handed over (not before paying). Deposits: cash deposit into the till and store credit (Dr 1120 / Cr 2200), used at the sale; refunded by a manager after cancellation; refused once spent. |
| `e2e_exceptions.py` | Manager voids a sale, cashier removes a rung-up part, manager gives a discount and cuts a price, warehouse posts a short stock count; the exception report must show each against the cashier with the approving manager. |
| `e2e_restock.py` | The fastest seller at Harare gets a supplier (simulation only): the suggestion uses its 10-day lead time and cost, suggests moving spare stock from Bulawayo and buying the rest; a draft order is shown but not counted; once the order is submitted and the transfer made, the suggestion is gone. Cashiers are refused. |
| `e2e_alerts.py` | Phone alerts: staff choose their own (bad numbers refused); an urgent transfer for a waiting customer is texted to the warehouse by WhatsApp only after 10 minutes and only once; the 07:00 summary reaches the manager (not the cashier, who may not see the dashboard), once per day, also in the app. |
| `e2e_handin.py` | Driver hands in the cash → second hand-in refused → cashier counts it short (reason required) → cashier and driver cannot sign it off → manager signs off and it posts (Dr 1250 / Cr 1120) → the driver owes it → repays part at the counter → another manager writes off the rest. |

```bash
cd supabase/sim
python3 seed.py
python3 e2e_day.py
python3 e2e_card.py
python3 e2e_backorder.py
python3 e2e_exceptions.py
python3 e2e_restock.py
python3 e2e_alerts.py
python3 e2e_cod.py && python3 e2e_handin.py
```

Each line prints `OK` or `FAIL` with the server's message. `e2e_cod.py` writes proof-of-delivery
storage rows directly in place of the app's photo upload.

## Continuous integration (`.github/workflows/simulation.yml`)

Every pull request that touches `supabase/` runs `ci/run.sh` on a throwaway stack (project
`gtr-sim-ci`, API 56421, database 56422, so it never collides with a developer's replica):

1. Load `snapshot/schema.sql.gz` (schema-only dump of `public`, `private`, `rebuild_internal`),
   `snapshot/managed_schemas.sql` (storage policies, the new-user trigger) and
   `snapshot/reference.sql` (chart of accounts, number series, approval policies, branches,
   price lists, stock items and other set-up tables; no customer or ledger data).
2. Apply every migration newer than `snapshot/VERSION`, so a new migration is tested before
   it reaches hosted.
3. Seed, run every `e2e_*.py` scenario (any `FAIL`, finding or traceback fails the build), then
   `ci/rules.sh`, which runs `supabase/tests/sim_role_rules_smoke.sql`: one person per role
   (a cashier can sell but not void, a driver never sees the customer's delivery code, nobody
   approves their own count, signed-out visitors cannot call money functions, every table has
   row level security).

Run it locally with `bash supabase/sim/ci/run.sh`. The scripts read `SIM_DB_URL`, `SIM_API_URL`
and `SIM_SERVICE_KEY` and refuse a database that is not on localhost.

Refresh the snapshot when the migration list after `VERSION` gets long: dump the replica with
`pg_dump --schema-only --no-owner -n public -n private -n rebuild_internal`, change
`CREATE SCHEMA` to `CREATE SCHEMA IF NOT EXISTS`, drop `ALTER DEFAULT PRIVILEGES` lines, gzip
with `gzip -n`, and set `VERSION` to the newest migration it contains.

## Edge Functions on the local stack

`bash supabase/sim/ci/functions.sh` serves every function in `supabase/functions` (linked into the
CI project as `ci/supabase/functions`) against the simulation stack, e.g.
`POST http://127.0.0.1:56421/functions/v1/process-sms-outbox` with the stack's service key drains the
outbox in stub mode (nothing is really sent). The web app's staff sign-in uses the `auth-otp` function,
so with functions served the real login page works against simulated users. Needs Docker and network
access to jsr.io and npm (the functions import from there).

