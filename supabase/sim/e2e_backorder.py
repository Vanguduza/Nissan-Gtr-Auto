"""Back-orders and branch transfers: a part Harare does not have.

A. Back-order: the customer leaves a deposit, the supplier delivers, the customer pays the rest
   (deposit taken as store credit) and the counter hands it over.
B. Branch transfer: Bulawayo has it; Harare requests it, the warehouse approves and sends it.
C. Deposits given back: refunded by a manager once the order is cancelled, and refused once the
   customer has already spent it.
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
def sale(customer, lines, attach=None, store_credit=0):
    """Rings up [lines] for [customer] (attaching back-orders/transfers), takes [store_credit] then cash, returns the invoice."""
    cart = val(cashier, "select create_pos_cart(%s,%s,'USD','immediate')", (MAIN, customer))
    as_user(cashier, "select attach_pos_cart_till_session(%s,%s)", (cart, till))
    for item_id, qty in lines:
        as_user(cashier, "select add_cart_line(%s,%s,%s,%s)", (cart, item_id, uom, qty))
    for rid in attach or []:
        as_user(cashier, "select attach_pos_fulfillment_to_cart(%s,%s)", (rid, cart))
    order = val(cashier, "select prepare_pos_commerce_checkout_v2(%s,%s,'20 minutes',null,null,null)", (cart, str(uuid.uuid4())))
    due = float(val(cashier, "select get_pos_payment_status(%s)", (order,))['total'])
    tenders = ([{'tender': 'store_credit', 'amount': store_credit}] if store_credit else []) + [{'tender': 'cash', 'amount': round(due - store_credit, 2)}]
    return val(cashier, "select settle_pos_commerce_tenders(%s,%s,%s::jsonb)",
               (order, str(uuid.uuid4()), json.dumps(tenders)))['invoice_id']


def credit(customer):
    r = sql("select balance from store_credit_accounts where customer_id=%s and currency='USD'", (customer,), one=True)
    return float(r['balance']) if r else 0.0


def expected_cash(user, session):
    return float(val(user, "select pos_till_expected_cash(%s)", (session,)))


def journal(doc_like):
    return sorted((r['account_code'], float(r['debit']), float(r['credit'])) for r in sql(
        """select l.account_code, l.debit, l.credit from journal_entries j join journal_entry_lines l on l.journal_entry_id=j.id
           where j.description like %s""", (doc_like,)))


refused('selling it from Harare stock (none there)', lambda: sale(cust, [(part, 1)]), expect='insufficient')
bo = step('cashier raises a back-order for the customer', lambda: val(
    cashier, "select create_pos_fulfillment_request('backorder',%s,%s,1,null,%s,%s,null,null,'call when in',null)", (part, uom, MAIN, cust)))
credit0, cash0 = credit(cust), expected_cash(cashier, till)
refused('deposit with no till for cash', lambda: val(cashier, "select take_pos_fulfillment_deposit(%s,50,'USD','cash',null,null)", (bo,)), expect='till')
refused('bank deposit with no reference', lambda: val(cashier, "select take_pos_fulfillment_deposit(%s,50,'USD','bank',null,null)", (bo,)), expect='reference')
dep = step('customer leaves a USD 50 cash deposit', lambda: val(cashier, "select take_pos_fulfillment_deposit(%s,50,'USD','cash',%s,null)", (bo, till)))
step('deposit is in the till and held as store credit', lambda: expect(
    expected_cash(cashier, till) == cash0 + 50 and credit(cust) == credit0 + 50, (expected_cash(cashier, till), credit(cust))))
step('deposit posted Dr 1120 cash / Cr 2200 customer deposits', lambda: expect(
    journal(f"Deposit {dep['document_number']} on %") == [('1120', 50, 0), ('2200', 0, 50)], journal(f"Deposit {dep['document_number']} on %")))
step('request list shows the deposit', lambda: expect([r for r in val(cashier, "select coalesce(jsonb_agg(x),'[]') from list_pos_fulfillment_requests(%s,null,5) x", (dep['request_number'],))
                                                       if r['deposits_held'] == [{'currency': 'USD', 'amount': 50}]], 'not listed'))
step('a customer sees no back-orders', lambda: expect(val(U['customer:Tapiwa Moyo'], "select count(*) from list_pos_fulfillment_requests(null,null,5)") == 0, 'customer sees requests'))
refused('handing over before the part arrives', lambda: val(cashier, "select collect_pos_fulfillment_request(%s,'x')", (bo,)))
refused('marking it ready before it arrives', lambda: val(cashier, "select mark_pos_fulfillment_ready(%s,'x')", (bo,)))
step('supplier delivers 1 to Harare', lambda: val(warehouse, "select post_stock_receipt(%s,'Simulated supplier delivery',%s::jsonb)",
     (MAIN, json.dumps([{'stock_item_id': part, 'uom_id': str(uom), 'qty': 1, 'unit_cost': 66, 'currency': 'USD'}]))))
step('counter marks it ready (holds the part for the customer)', lambda: val(cashier, "select mark_pos_fulfillment_ready(%s,'arrived')", (bo,)))
refused('another customer buys the held part', lambda: sale(C['Tendai Garage'], [(part, 1)]), expect='insufficient')
refused('customer collects before paying', lambda: val(cashier, "select collect_pos_fulfillment_request(%s,'x')", (bo,)))
inv = step('customer pays the rest; the deposit is taken as store credit', lambda: sale(cust, [(part, 1)], attach=[bo], store_credit=50))
step('store credit used up', lambda: expect(credit(cust) == credit0, credit(cust)))
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

# --- C. Deposits given back -----------------------------------------------------------------
bo2 = step('another back-order with a USD 30 deposit', lambda: val(
    cashier, "select create_pos_fulfillment_request('backorder',%s,%s,1,null,%s,%s,null,null,'may cancel',null)", (part, uom, MAIN, cust)))
dep2 = step('deposit taken', lambda: val(cashier, "select take_pos_fulfillment_deposit(%s,30,'USD','cash',%s,null)", (bo2, till)))
sql("update pos_till_sessions set status='closed' where status<>'closed' and opened_by=%s", (manager,))
mtill = val(manager, "select open_pos_till_session(%s,'sim-tablet-mgr',100,'USD')", (MAIN,))
refused('refund before the order is cancelled', lambda: val(manager, "select refund_pos_fulfillment_deposit(%s,%s,'changed mind')", (dep2['id'], mtill)), expect='cancel')
step('customer cancels the order', lambda: val(cashier, "select cancel_pos_fulfillment_request(%s,'customer changed mind')", (bo2,)))
refused('cashier refunds the deposit', lambda: val(cashier, "select refund_pos_fulfillment_deposit(%s,%s,'changed mind')", (dep2['id'], till)), expect='manager')
r2 = step('manager refunds it in cash from their till', lambda: val(manager, "select refund_pos_fulfillment_deposit(%s,%s,'customer changed mind')", (dep2['id'], mtill)))
step('cash left the manager till; store credit back to before', lambda: expect(
    expected_cash(manager, mtill) == 70 and credit(cust) == credit0, (expected_cash(manager, mtill), credit(cust))))
step('refund posted Dr 2200 / Cr 1120', lambda: expect(journal(f"Refund of deposit {dep2['document_number']} on %") == [('1120', 0, 30), ('2200', 30, 0)], 'journal'))
refused('refund twice', lambda: val(manager, "select refund_pos_fulfillment_deposit(%s,%s,'again')", (dep2['id'], mtill)), expect='held')
bo3 = step('third back-order with a USD 20 deposit', lambda: val(
    cashier, "select create_pos_fulfillment_request('backorder',%s,%s,1,null,%s,%s,null,null,null,null)", (part, uom, MAIN, cust)))
dep3 = step('deposit taken', lambda: val(cashier, "select take_pos_fulfillment_deposit(%s,20,'USD','cash',%s,null)", (bo3, till)))
step('customer spends the credit on something else', lambda: sale(cust, [(sql(
    "select si.id from stock_items si join stock_levels sl on sl.stock_item_id=si.id where sl.warehouse_id=%s and sl.quantity>2 and not si.requires_serial and exists(select 1 from price_list_items p where p.stock_item_id=si.id) limit 1",
    (MAIN,), one=True)['id'], 1)], store_credit=20))
step('order cancelled', lambda: val(cashier, "select cancel_pos_fulfillment_request(%s,'not needed')", (bo3,)))
refused('refund of a spent deposit', lambda: val(manager, "select refund_pos_fulfillment_deposit(%s,%s,'not needed')", (dep3['id'], mtill)), expect='already used')
sql("update pos_till_sessions set status='closed' where id=%s", (mtill,))

print()
print('FINDINGS' if failures else 'ALL STEPS PASSED')
for n, e in failures:
    print(' -', n, ':', e)
