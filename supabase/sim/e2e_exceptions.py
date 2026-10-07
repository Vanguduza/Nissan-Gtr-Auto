"""Unusual activity for the exception report: a voided sale, a part removed after ringing, a
discount, a price cut, a stock count that does not match. Then the report must show each of them
against the cashier, with the approving manager."""
import json, uuid
from sim import sql, as_user, val, free_item, RpcError

ids = json.load(open('ids.json'))
U, W = ids['users'], ids['warehouses']
MAIN = W['MAIN']
cashier, manager, warehouse, owner = U['cashier'], U['manager'], U['warehouse'], U['owner']
item = free_item(MAIN)
failures = []


def step(name, fn):
    try:
        r = fn(); print('OK  ', name, '->', json.dumps(r, default=str)[:160]); return r
    except (RpcError, AssertionError, KeyError, TypeError) as e:
        print('FAIL', name, '->', e); failures.append((name, str(e))); return None


def cart(qty=2):
    c = val(cashier, "select create_pos_cart(%s,null,'USD','immediate')", (MAIN,))
    line = val(cashier, "select add_cart_line(%s,%s,%s,%s)", (c, item['id'], item['uom'], qty))
    return c, line


c1, _ = cart()
step('manager voids a sale (customer cancelled)', lambda: val(manager, "select void_pos_cart_governed(%s,'customer_cancelled','changed mind')", (c1,)))
c2, l2 = cart()
step('cashier removes a rung-up part', lambda: as_user(cashier, "select set_pos_cart_line_qty(%s,0)", (l2,)))
c3, l3 = cart()
step('manager gives 15% discount', lambda: val(manager, "select apply_pos_cart_discount_governed(%s,15,'price_match','matched competitor')", (c3,)))
c4, l4 = cart(1)
price = float(sql("select unit_price from pos_cart_lines where id=%s", (l4,), one=True)['unit_price'])
step('manager cuts a price by 30%', lambda: val(manager, "select apply_pos_line_price_override_governed(%s,%s,'advertised_price',null)", (l4, round(price * 0.7, 2))))

rec = step('warehouse starts a stock count', lambda: val(warehouse, "select create_stock_reconciliation_draft(%s,'partial',%s::uuid[],'simulated count','USD',1)", (MAIN, [item['id']])))
if rec:
    books = float(sql("select quantity from stock_levels where stock_item_id=%s and warehouse_id=%s", (item['id'], MAIN), one=True)['quantity'])
    step('counts 2 fewer than the books', lambda: val(warehouse, "select upsert_stock_reconciliation_lines(%s,%s::jsonb)",
         (rec, json.dumps([{'stock_item_id': item['id'], 'counted_qty': books - 2}]))))
    step('submitted', lambda: val(warehouse, "select submit_stock_reconciliation(%s)", (rec,)))
    # Over the two-person limit it waits for a second person; under it, it posts at once.
    if sql("select status from stock_reconciliations where id=%s", (rec,), one=True)['status'] == 'pending_approval':
        step('approved by a second person', lambda: val(owner, "select approve_stock_reconciliation(%s)", (rec,)))

r = step('report for today', lambda: val(manager, "select get_exception_report(null,null,null)"))
if r:
    kinds = {k['kind'] for k in r['by_kind']}
    for k in ('cart_voided', 'cart_line_removed', 'discount_applied', 'price_override', 'stock_count_difference'):
        print('OK  ' if k in kinds else 'FAIL', 'report shows', k)
        if k not in kinds:
            failures.append(('report shows ' + k, 'missing'))
    for it in r['items']:
        if it['kind'] in ('cart_voided', 'discount_applied', 'price_override', 'cart_line_removed', 'stock_count_difference') and it['at'] >= r['from']:
            print('     ', it['kind'], '|', it['person'], '| approved by', it['approved_by'], '|', it['amount'], it['currency'], '|', it['detail'][:70])
    cash = next((p for p in r['by_person'] if p['person'] == 'Farai Cashier'), None)
    print('OK  ' if cash and 'cart_voided' in cash['kinds'] else 'FAIL', 'void counted against the cashier, not the manager')

print()
print('FINDINGS' if failures else 'ALL STEPS PASSED')
for n, e in failures:
    print(' -', n, ':', e)
