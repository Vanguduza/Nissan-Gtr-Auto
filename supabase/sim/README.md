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
| `e2e_card.py` | Card payments: admin pairs the till tablet; counter charge approved (signed answer, replay changes nothing, same machine transaction refused on another sale), declined, forged/tampered answers refused, no answer → recovery → approved; driver pairs phone, card-only order refuses cash, card at the door settles the invoice. `card.py` signs like the device and verifies like the `card-terminal-result` Edge Function (needs `pip install cryptography`). |
| `e2e_backorder.py` | A part Harare does not have: back-order → cannot be marked ready before it arrives → supplier delivers → marked ready holds it for the customer (another customer cannot buy it) → customer pays → handed over. Transfer from Bulawayo for a customer: two-person transfer, held on arrival, sold, then handed over (not before paying). |
| `e2e_handin.py` | Driver hands in the cash → second hand-in refused → cashier counts it short (reason required) → cashier and driver cannot sign it off → manager signs off. |

```bash
cd supabase/sim
python3 seed.py
python3 e2e_day.py
python3 e2e_card.py
python3 e2e_backorder.py
python3 e2e_cod.py && python3 e2e_handin.py
```

Each line prints `OK` or `FAIL` with the server's message. `e2e_cod.py` writes proof-of-delivery
storage rows directly in place of the app's photo upload.
