"""Restocking: a fast seller at Harare is suggested a transfer from Bulawayo plus a purchase from its
preferred supplier; once the transfer and the submitted purchase order exist, the suggestion is gone
(stock on its way counts). Cashiers cannot see it."""
import json, uuid
from datetime import date, timedelta
from sim import sql, as_user, val, RpcError

ids = json.load(open('ids.json'))
U, W = ids['users'], ids['warehouses']
MAIN, BYO = W['MAIN'], W['WH2']
warehouse, owner, cashier = U['warehouse'], U['owner'], U['cashier']
failures = []


def step(name, fn):
    try:
        r = fn(); print('OK  ', name, '->', json.dumps(r, default=str)[:180]); return r
    except (RpcError, AssertionError, KeyError, TypeError, StopIteration) as e:
        print('FAIL', name, '->', e); failures.append((name, str(e))); return None


def expect(c, m):
    if not c:
        raise AssertionError(m)
    return True


def plan():
    return val(warehouse, "select get_restock_suggestions(%s,28,14,7,28)", (MAIN,))


def harare(p, item):
    return next((s for s in p['suggestions'] if s['stock_item_id'] == item and s['warehouse_id'] == MAIN), None)


# The fastest seller at Harare over the last 28 days.
top = sql("""select l.stock_item_id id, sum(l.qty_base) sold from sales_invoice_lines l join sales_invoices i on i.id=l.invoice_id
             where i.warehouse_id=%s and i.status='posted' and i.doc_type='invoice' and i.posted_at > now()-interval '28 days'
             group by 1 order by 2 desc limit 1""", (MAIN,), one=True)
item = str(top['id'])
print('     fastest seller sold', top['sold'], 'in 28 days')

# A supplier for it (simulation setup).
sup = sql("select id from suppliers where code='SIM-SUP'", one=True)
if not sup:
    sup = sql("insert into suppliers(code,name,default_currency,is_active,is_preferred) values ('SIM-SUP','Simulated Parts Supplier','USD',true,true) returning id", one=True)
sql("""insert into supplier_preferred_skus(supplier_id,stock_item_id,typical_lead_days,last_quoted_unit_cost,currency,is_active)
       select %s,%s,10,30,'USD',true where not exists(select 1 from supplier_preferred_skus where supplier_id=%s and stock_item_id=%s)""",
    (sup['id'], item, sup['id'], item))

refused = False
try:
    val(cashier, "select get_restock_suggestions(null,28,14,7,28)")
except RpcError:
    refused = True
print('OK  ' if refused else 'FAIL', 'cashier cannot see restock suggestions')

s = step('suggestion for the fast seller at Harare', lambda: (lambda x: expect(x, 'no suggestion') and x)(harare(plan(), item)))
if s:
    step('uses the supplier lead time (10 days) and cost', lambda: expect(s['lead_days'] == 10 and float(s['unit_cost']) == 30 and s['supplier'], s) and (s['lead_days'], s['supplier']))
    step('reorder point = daily x (lead + safety)', lambda: expect(abs(float(s['reorder_point']) - __import__('math').ceil(float(s['daily']) * 17)) < 1.01 or s['reorder_point_source'] == 'set', s) and s['reorder_point'])
    print(f"     free {s['available']} · sells {s['daily']}/day · reorder at {s['reorder_point']} · move {s['transfer_qty']} from {(s['transfer_from'] or {}).get('warehouse')} · buy {s['buy_qty']}")
    if float(s['buy_qty']) > 0:
        po = step('draft the purchase order', lambda: val(warehouse, "select create_purchase_order(%s,%s,'USD',1,%s::jsonb,'Draft from restock suggestions',%s,null)",
             (s['supplier_id'], MAIN, json.dumps([{'stock_item_id': item, 'uom_id': s['uom_id'], 'qty': float(s['buy_qty']), 'unit_price': 30}]),
              (date.today() + timedelta(days=10)).isoformat())))
        step('a draft is not on its way yet, but is shown', lambda: (lambda x: expect(x and float(x['on_order']) == 0 and float(x['in_draft']) == float(s['buy_qty']), x) and x['in_draft'])(harare(plan(), item)))
        if po:
            step('purchase order submitted', lambda: val(warehouse, "select submit_purchase_order(%s)", (po,)))
    if float(s['transfer_qty']) > 0:
        step('create the suggested transfer', lambda: val(warehouse, "select create_stock_transfer(%s,%s,'Restock suggestion',%s::jsonb)",
             (s['transfer_from']['warehouse_id'], MAIN, json.dumps([{'stock_item_id': item, 'uom_id': s['uom_id'], 'qty': float(s['transfer_qty']), 'valuation_method': 'FIFO'}]))))
    after = harare(plan(), item)
    step('suggestion gone once stock is on its way', lambda: expect(after is None, after) and 'gone')

print()
print('FINDINGS' if failures else 'ALL STEPS PASSED')
for n, e in failures:
    print(' -', n, ':', e)
