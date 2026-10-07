# Simulated trading data and end-to-end runs (local only)

Never point these scripts at the hosted project: the ledger is append-only and simulated sales
would stay in the books. They target a **local** Supabase stack (`supabase start`, API on port
55421, database on 55422) that has the same schema as hosted.

| Script | What it does |
|--------|--------------|
| `sim.py` | Helpers: run SQL as a signed-in user (RLS and `auth.uid()` apply), as the service role, create Auth users. |
| `seed.py` | Staff (owner, manager/approver, two cashiers, warehouse, dispatch, finance, two drivers), trade and retail customers, opening stock at two branches, card machines. Writes `ids.json`. Password for every simulated user: `Sim-Passw0rd!`. |
| `e2e_cod.py` | Customer checks out paying on delivery → warehouse picks → dispatcher assigns and dispatches → proof of delivery is refused while money is due → driver collects in two parts → proof of delivery with the customer's code. |
| `e2e_handin.py` | Driver hands in the cash → second hand-in refused → cashier counts it short (reason required) → cashier and driver cannot sign it off → manager signs off. |

```bash
cd supabase/sim
python3 seed.py
python3 e2e_cod.py && python3 e2e_handin.py
```

Each line prints `OK` or `FAIL` with the server's message. `e2e_cod.py` writes proof-of-delivery
storage rows directly in place of the app's photo upload.
