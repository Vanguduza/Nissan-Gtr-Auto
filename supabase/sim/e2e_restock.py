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


# A part of its own (simulation setup): 12 spare in Bulawayo, 23 at Harare of which 20 sell today.
RUN = uuid.uuid4().hex[:6].upper()
uom = sql("select base_uom_id from stock_items where base_uom_id is not null limit 1", one=True)['base_uom_id']
item = str(sql("insert into stock_items(oem_part_number, description, base_uom_id) values (%s, 'Simulated fast seller', %s) returning id",
               (f'SIM-FAST-{RUN}', uom), one=True)['id'])
sql("insert into price_list_items(price_list_id, stock_item_id, unit_price) select id, %s, 45 from price_lists where code='RETAIL'", (item,))
for wh, qty in ((BYO, 12), (MAIN, 23)):
    val(warehouse, "select post_stock_receipt(%s,'Simulated supplier delivery',%s::jsonb)",
        (wh, json.dumps([{'stock_item_id': item, 'uom_id': str(uom), 'qty': qty, 'unit_cost': 30, 'currency': 'USD'}])))
sql("update pos_till_sessions set status='closed' where status<>'closed' and opened_by=%s", (cashier,))
till = val(cashier, "select open_pos_till_session(%s,'sim-restock-tablet',20,'USD')", (MAIN,))
cart = val(cashier, "select create_pos_cart(%s,null,'USD','immediate')", (MAIN,))
as_user(cashier, "select add_cart_line(%s,%s,%s,20)", (cart, item, uom))
as_user(cashier, "select attach_pos_cart_till_session(%s,%s)", (cart, till))
order = val(cashier, "select prepare_pos_commerce_checkout_v2(%s,%s,'20 minutes',null,null,null)", (cart, str(uuid.uuid4())))
total = float(val(cashier, "select get_pos_payment_status(%s)", (order,))['total'])
val(cashier, "select settle_pos_commerce_tenders(%s,%s,%s::jsonb)", (order, str(uuid.uuid4()), json.dumps([{'tender': 'cash', 'amount': total}])))
print('     sold 20 at Harare; 3 left, 12 in Bulawayo')

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
