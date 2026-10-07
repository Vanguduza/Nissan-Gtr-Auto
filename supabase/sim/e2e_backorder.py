"""Back-orders and branch transfers: a part Harare does not have.

A. Back-order: the customer pays now, the supplier delivers, the counter hands it over.
B. Branch transfer: Bulawayo has it; Harare requests it, the warehouse approves and sends it.
"""
import json, uuid
from sim import sql, as_user, val, RpcError

ids = json.load(open('ids.json'))
U, C, W = ids['users'], ids['customers'], ids['warehouses']
MAIN, BYO = W['MAIN'], W['WH2']
cashier, manager, warehouse = U['cashier'], U['manager'], U['warehouse']
cust = C['Harare Motor Spares']
failures = []
RUN = uuid.uuid4().hex[:6].upper()


def step(name, fn):
    try:
        r = fn()
        print('OK  ', name, '->', json.dumps(r, default=str)[:200])
        return r
    except (RpcError, AssertionError, KeyError, TypeError) as e:
        print('FAIL', name, '->', e)
        failures.append((name, str(e)))
        return None


def refused(name, fn, expect=None):
    try:
        r = fn()
        print('FAIL', name, 'was allowed ->', json.dumps(r, default=str)[:160])
        failures.append((name, 'allowed'))
    except RpcError as e:
        ok = expect is None or expect in str(e)
        print('OK  ' if ok else 'FAIL', name, 'refused ->', e)
        if not ok:
            failures.append((name, str(e)))


def expect(cond, msg):
    if not cond:
        raise AssertionError(msg)
    return True


def on_hand(item, wh):
    r = sql("select quantity from stock_levels where stock_item_id=%s and warehouse_id=%s", (item, wh), one=True)
    return float(r['quantity']) if r else 0.0


def request(rid):
    return sql("select status, invoice_id, cart_id from pos_fulfillment_requests where id=%s", (rid,), one=True)


# Setup: a part stocked only in Bulawayo (simulation only).
uom = sql("select base_uom_id from stock_items where base_uom_id is not null limit 1", one=True)['base_uom_id']
part = str(sql("""insert into stock_items(oem_part_number, description, base_uom_id) values (%s, 'Simulated slow-moving part', %s)
                  returning id""", (f'SIM-BO-{RUN}', uom), one=True)['id'])
sql("insert into price_list_items(price_list_id, stock_item_id, unit_price) select id, %s, 120 from price_lists where code='RETAIL'", (part,))
step('Bulawayo receives 3', lambda: val(warehouse, "select post_stock_receipt(%s,'Simulated supplier delivery',%s::jsonb)",
     (BYO, json.dumps([{'stock_item_id': part, 'uom_id': str(uom), 'qty': 3, 'unit_cost': 66, 'currency': 'USD'}]))))
print('     Harare on hand:', on_hand(part, MAIN), ' Bulawayo on hand:', on_hand(part, BYO))

sql("update pos_till_sessions set status='closed' where status<>'closed' and opened_by=%s", (cashier,))
till = val(cashier, "select open_pos_till_session(%s,'sim-tablet-1',50,'USD')", (MAIN,))

# --- A. Back-order -------------------------------------------------------------------------
def sale(customer, lines, attach=None):
    """Rings up [lines] for [customer] (attaching back-orders/transfers), takes cash, returns the invoice."""
    cart = val(cashier, "select create_pos_cart(%s,%s,'USD','immediate')", (MAIN, customer))
    as_user(cashier, "select attach_pos_cart_till_session(%s,%s)", (cart, till))
    for item_id, qty in lines:
        as_user(cashier, "select add_cart_line(%s,%s,%s,%s)", (cart, item_id, uom, qty))
    for rid in attach or []:
        as_user(cashier, "select attach_pos_fulfillment_to_cart(%s,%s)", (rid, cart))
    order = val(cashier, "select prepare_pos_commerce_checkout_v2(%s,%s,'20 minutes',null,null,null)", (cart, str(uuid.uuid4())))
    due = float(val(cashier, "select get_pos_payment_status(%s)", (order,))['total'])
    return val(cashier, "select settle_pos_commerce_tenders(%s,%s,%s::jsonb)",
               (order, str(uuid.uuid4()), json.dumps([{'tender': 'cash', 'amount': due}])))['invoice_id']


refused('selling it from Harare stock (none there)', lambda: sale(cust, [(part, 1)]), expect='insufficient')
bo = step('cashier raises a back-order for the customer', lambda: val(
    cashier, "select create_pos_fulfillment_request('backorder',%s,%s,1,null,%s,%s,null,null,'call when in',null)", (part, uom, MAIN, cust)))
refused('handing over before the part arrives', lambda: val(cashier, "select collect_pos_fulfillment_request(%s,'x')", (bo,)))
refused('marking it ready before it arrives', lambda: val(cashier, "select mark_pos_fulfillment_ready(%s,'x')", (bo,)))
step('supplier delivers 1 to Harare', lambda: val(warehouse, "select post_stock_receipt(%s,'Simulated supplier delivery',%s::jsonb)",
     (MAIN, json.dumps([{'stock_item_id': part, 'uom_id': str(uom), 'qty': 1, 'unit_cost': 66, 'currency': 'USD'}]))))
step('counter marks it ready (holds the part for the customer)', lambda: val(cashier, "select mark_pos_fulfillment_ready(%s,'arrived')", (bo,)))
refused('another customer buys the held part', lambda: sale(C['Tendai Garage'], [(part, 1)]), expect='insufficient')
refused('customer collects before paying', lambda: val(cashier, "select collect_pos_fulfillment_request(%s,'x')", (bo,)))
inv = step('customer pays for the part at the counter', lambda: sale(cust, [(part, 1)], attach=[bo]))
step('back-order is linked to the paid invoice', lambda: (lambda r: expect(str(r['invoice_id']) == str(inv), r) and r['status'])(request(bo)))
step('customer collects', lambda: val(cashier, "select collect_pos_fulfillment_request(%s,'collected by customer')", (bo,)))
step('Harare stock is back to 0', lambda: (lambda q: expect(abs(q) < 0.001, q) and q)(on_hand(part, MAIN)))
step('no hold left behind', lambda: (lambda n: expect(n == 0, n) and n)(sql(
    "select count(*) n from inventory_reservations where stock_item_id=%s and state in ('active','allocated')", (part,), one=True)['n']))

# --- B. Branch transfer --------------------------------------------------------------------
refused('transfer more than Bulawayo has', lambda: val(
    cashier, "select create_pos_fulfillment_request('branch_transfer',%s,%s,9,%s,%s,%s,null,null,null,null)", (part, uom, BYO, MAIN, cust)))
tr = step('cashier requests 1 from Bulawayo for the customer', lambda: val(
    cashier, "select create_pos_fulfillment_request('branch_transfer',%s,%s,1,%s,%s,%s,null,null,'customer waiting',null)", (part, uom, BYO, MAIN, cust)))
refused('cashier approves the transfer', lambda: val(cashier, "select approve_pos_fulfillment_request(%s,null)", (tr,)))
entry = step('Bulawayo warehouse approves and sends it', lambda: val(warehouse, "select approve_pos_fulfillment_request(%s,'14:00 truck')", (tr,)))
refused('the same person also receives it', lambda: val(warehouse, "select approve_stock_transfer(%s)", (entry,)), expect='different')
step('a second person (owner) receives it at Harare', lambda: val(U['owner'], "select approve_stock_transfer(%s)", (entry,)))
step('request is ready', lambda: (lambda r: expect(r['status'] == 'ready', r) and r['status'])(request(tr)))
print('     Harare on hand:', on_hand(part, MAIN), ' Bulawayo on hand:', on_hand(part, BYO))
refused('another customer buys the transferred part', lambda: sale(C['Tendai Garage'], [(part, 1)]), expect='insufficient')
refused('customer collects the transferred part without paying', lambda: val(cashier, "select collect_pos_fulfillment_request(%s,'x')", (tr,)))
inv2 = step('customer pays for the transferred part', lambda: sale(cust, [(part, 1)], attach=[tr]))
step('customer collects', lambda: val(cashier, "select collect_pos_fulfillment_request(%s,'collected')", (tr,)))
step('Harare stock is back to 0', lambda: (lambda q: expect(abs(q) < 0.001, q) and q)(on_hand(part, MAIN)))

print()
print('FINDINGS' if failures else 'ALL STEPS PASSED')
for n, e in failures:
    print(' -', n, ':', e)
