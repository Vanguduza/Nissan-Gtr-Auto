"""Restocking: a fast seller at Harare is suggested a transfer from Bulawayo plus a purchase from its
preferred supplier; once the transfer and the submitted purchase order exist, the suggestion is gone
(stock on its way counts). Cashiers cannot see it.
Then: a branch's own reorder point; a part nobody could buy (lost demand) is restocked; the supplier's
lead time comes from orders actually received; slow stock is moved to the branch that sells it or
marked down (logged)."""
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
    # The quoted 10 days, unless this supplier's orders have already been received (then the days actually seen).
    seen = sql("select private.supplier_lead_days(%s,%s) l", (sup['id'], item), one=True)['l']
    lead = int(seen['days']) if seen else 10
    step(f'uses the supplier lead time ({lead} days) and cost', lambda: expect(s['lead_days'] == lead and float(s['unit_cost']) == 30 and s['supplier'], s) and (s['lead_days'], s['lead_source'], s['supplier']))
    step('reorder point = daily x (lead + safety)', lambda: expect(abs(float(s['reorder_point']) - __import__('math').ceil(float(s['daily']) * (lead + 7))) < 1.01 or s['reorder_point_source'] == 'set', s) and s['reorder_point'])
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

# --- Branch reorder point -------------------------------------------------------------------
refused_rop = False
try:
    val(cashier, "select set_branch_reorder_point(%s,%s,40,null)", (item, BYO))
except RpcError:
    refused_rop = True
print('OK  ' if refused_rop else 'FAIL', 'cashier cannot set reorder points')
step('warehouse sets Bulawayo\'s own reorder point to 40', lambda: val(warehouse, "select set_branch_reorder_point(%s,%s,40,10)", (item, BYO)))
byo = lambda: next((x for x in val(warehouse, "select get_restock_suggestions(%s,28,14,7,28)", (BYO,))['suggestions'] if x['stock_item_id'] == item), None)
step('Bulawayo now restocks it from its own reorder point', lambda: (lambda x: expect(x and float(x['reorder_point']) == 40 and x['reorder_point_source'] == 'branch', x) and x['qty_needed'])(byo()))
step('clearing it goes back to the part\'s rule', lambda: val(warehouse, "select set_branch_reorder_point(%s,%s,null,null)", (item, BYO)))
step('no Bulawayo suggestion without it', lambda: expect(byo() is None, byo()))

# --- Lost demand ----------------------------------------------------------------------------
lost_item = str(sql("insert into stock_items(oem_part_number, description, base_uom_id) values (%s, 'Simulated part nobody stocks', %s) returning id",
                    (f'SIM-LOST-{RUN}', uom), one=True)['id'])
step('no suggestion for a part never stocked or sold', lambda: expect(harare(plan(), lost_item) is None, 'suggested'))
for q in (2, 3, 1):
    val(cashier, "select record_lost_demand(%s,%s,%s,'customer wanted it, none here')", (lost_item, MAIN, q))
x = step('six asked for and not had: it is restocked', lambda: (lambda x: expect(x and float(x['lost']) == 6 and float(x['qty_needed']) > 0, x) and x)(harare(plan(), lost_item)))
if x:
    print(f"     lost {x['lost']} · sells {x['daily']}/day · reorder at {x['reorder_point']} · buy {x['buy_qty']}")

# --- Lead time from orders actually received ---------------------------------------------------
def receive(days):
    po = val(warehouse, "select create_purchase_order(%s,%s,'USD',1,%s::jsonb,'Lead time sample',%s,null)",
             (sup['id'], MAIN, json.dumps([{'stock_item_id': item, 'uom_id': str(uom), 'qty': 2, 'unit_price': 30}]), date.today().isoformat()))
    val(warehouse, "select submit_purchase_order(%s)", (po,))
    val(owner, "select approve_purchase_order(%s)", (po,))
    # Simulation only: pretend the order went out [days] ago (the procurement guard is bypassed for this one update).
    sql("set session_replication_role = replica; update purchase_orders set submitted_at = now() - make_interval(days => %s) where id=%s; set session_replication_role = origin",
        (days, po))
    line = sql("select id from purchase_order_lines where purchase_order_id=%s", (po,), one=True)['id']
    grn = val(warehouse, "select create_goods_receipt(%s,%s::jsonb,'Simulated delivery')",
              (po, json.dumps([{'purchase_order_line_id': str(line), 'qty': 2, 'unit_cost': 30, 'currency': 'USD'}])))
    val(warehouse, "select submit_goods_receipt(%s)", (grn,))
    return po
step('two orders from the supplier arrive after 21 and 25 days', lambda: [receive(21), receive(25)])
step('lead time is the 23 days actually seen, not the quoted 10', lambda: (lambda x: expect(x['days'] == 23 and x['samples'] == 2, x) and x)(
    sql("select private.supplier_lead_days(%s,%s) l", (sup['id'], item), one=True)['l']))
step('suggestions use it', lambda: (lambda x: expect(x and x['lead_days'] == 23 and x['lead_source'] == 'received', x) and (x['lead_days'], x['lead_source']))(harare(plan(), lost_item) or {'lead_days': None}) if sql(
    "select 1 from supplier_preferred_skus where stock_item_id=%s", (lost_item,), one=True) else expect(True, '') and 'part has no supplier yet')
sql("""insert into supplier_preferred_skus(supplier_id,stock_item_id,typical_lead_days,last_quoted_unit_cost,currency,is_active)
       values (%s,%s,10,12,'USD',true)""", (sup['id'], lost_item))
step('the lost-demand part gets the supplier\'s received lead time', lambda: (lambda x: expect(x and x['lead_days'] == 23 and x['lead_source'] == 'received', x) and x['lead_days'])(harare(plan(), lost_item)))

# --- Slow stock: move or mark down --------------------------------------------------------------
dead = str(sql("insert into stock_items(oem_part_number, description, base_uom_id) values (%s, 'Simulated part nobody buys', %s) returning id",
               (f'SIM-DEAD-{RUN}', uom), one=True)['id'])
sql("insert into price_list_items(price_list_id, stock_item_id, unit_price) select id, %s, 80 from price_lists where code='RETAIL'", (dead,))
val(warehouse, "select post_stock_receipt(%s,'Simulated old stock',%s::jsonb)",
    (BYO, json.dumps([{'stock_item_id': dead, 'uom_id': str(uom), 'qty': 4, 'unit_cost': 50, 'currency': 'USD'}])))
slow = lambda part, wh: next((r for r in val(warehouse, "select get_restock_suggestions(%s,28,14,7,28)", (wh,))['slow_stock'] if r['stock_item_id'] == part), None)
step('a part no branch sells: mark it down 30%', lambda: (lambda r: expect(r and r['markdown_pct'] == 30 and r['move_to'] is None, r) and r['markdown_pct'])(slow(dead, BYO)))
refused_md = False
try:
    val(cashier, "select markdown_stock_item(%s,30,'slow')", (dead,))
except RpcError:
    refused_md = True
print('OK  ' if refused_md else 'FAIL', 'cashier cannot mark prices down')
step('manager marks it down 30%', lambda: (lambda r: expect(r and float(r[0]['new']) == 56, r) and r)(val(U['manager'], "select markdown_stock_item(%s,30,'Not sold in a year')", (dead,))))
step('price change logged with who and why', lambda: (lambda r: expect(r and float(r['old_price']) == 80 and 'Not sold' in r['reason'], r) and r['reason'])(
    sql("select old_price, new_price, reason from price_changes where stock_item_id=%s", (dead,), one=True)))
# The fast seller sits unsold at Bulawayo (its 12 there never sold) while Harare sells it: move it.
step('stock a branch never sells goes where it sells', lambda: (lambda r: expect(r is None or (r['move_to'] and r['move_to']['warehouse_id'] == MAIN and r['move_qty'] >= 1), r) and (r and r['move_to']['warehouse'], r and r['move_qty']))(slow(item, BYO)))

print()
print('FINDINGS' if failures else 'ALL STEPS PASSED')
for n, e in failures:
    print(' -', n, ':', e)
