"""A trading day at the Harare branch: till, counter sales, on account, a return, pay-on-delivery
refusals that suspend a customer, a manager lifting it, and closing the till.

Every step prints OK or FAIL; the run continues so one report shows every finding.
"""
import json, uuid
from sim import sql, as_user, as_service, val, auth_user, RpcError

ids = json.load(open('ids.json'))
U, C, W = ids['users'], ids['customers'], ids['warehouses']
MAIN = W['MAIN']
failures = []


def step(name, fn):
    try:
        r = fn()
        print('OK  ', name, '->', json.dumps(r, default=str)[:220])
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


def stock_item(min_qty=3):
    return sql("""select si.id, si.base_uom_id uom from stock_levels sl join stock_items si on si.id=sl.stock_item_id
                  where sl.warehouse_id=%s and sl.quantity>=%s and not si.requires_serial order by si.id limit 1""",
               (MAIN, min_qty), one=True)


cashier, manager, dispatch, d1 = U['cashier'], U['manager'], U['dispatch'], U['driver1']
drawer = {'cash': 100.0}  # what the cashier expects to count at close
item = stock_item()

# --- Open the till ---------------------------------------------------------------------------
sql("update pos_till_sessions set status='closed' where status<>'closed' and opened_by=%s", (cashier,))
till = step('cashier opens till with USD 100 float',
            lambda: val(cashier, "select open_pos_till_session(%s,'sim-tablet-1',100,'USD')", (MAIN,)))


def counter_sale(customer_id, qty, tenders_for):
    cart = val(cashier, "select create_pos_cart(%s,%s,'USD','immediate')", (MAIN, customer_id))
    as_user(cashier, "select add_cart_line(%s,%s,%s,%s)", (cart, item['id'], item['uom'], qty))
    if till:
        as_user(cashier, "select attach_pos_cart_till_session(%s,%s)", (cart, till))
    order = val(cashier, "select prepare_pos_commerce_checkout_v2(%s,%s,'20 minutes',null,null,null)", (cart, str(uuid.uuid4())))
    status = val(cashier, "select get_pos_payment_status(%s)", (order,))
    due = float(status.get('amount_due') or status.get('total') or 0)
    tenders = tenders_for(due)
    r = val(cashier, "select settle_pos_commerce_tenders(%s,%s,%s::jsonb)", (order, str(uuid.uuid4()), json.dumps(tenders)))
    drawer['cash'] += sum(t['amount'] for t in tenders if t['tender'] == 'cash')
    return {'invoice': r['invoice_id'], 'due': due, 'state': r.get('state')}


# --- Counter sales ---------------------------------------------------------------------------
cash_sale = step('walk-in pays cash', lambda: counter_sale(None, 1, lambda due: [{'tender': 'cash', 'amount': due}]))
split_sale = step('walk-in splits cash + bank transfer',
                  lambda: counter_sale(None, 1, lambda due: [{'tender': 'cash', 'amount': round(due / 2, 2)},
                                                              {'tender': 'bank', 'amount': round(due - round(due / 2, 2), 2)}]))
refused('payment short of the total', lambda: counter_sale(None, 1, lambda due: [{'tender': 'cash', 'amount': round(due - 1, 2)}]))


def on_account(customer, qty):
    cart = val(cashier, "select create_pos_cart(%s,%s,'USD','immediate')", (MAIN, customer))
    as_user(cashier, "select add_cart_line(%s,%s,%s,%s)", (cart, item['id'], item['uom'], qty))
    return val(cashier, "select checkout_pos_cart_on_account(%s,null,null,null)", (cart,))


acct_inv = step('trade customer buys on account', lambda: on_account(C['Harare Motor Spares'], 2))
refused('on account beyond the credit limit', lambda: on_account(C['Bulawayo Fleet Services'], 30))
refused('retail customer with no credit buys on account', lambda: on_account(C['Chipo Ndlovu'], 1))

# --- Return: cashier raises, manager posts ---------------------------------------------------
if cash_sale:
    line = sql("select id from sales_invoice_lines where invoice_id=%s limit 1", (cash_sale['invoice'],), one=True)
    case = step('cashier raises a cash refund for the walk-in', lambda: val(
        cashier, "select create_pos_return_case(%s,'cash_refund','customer_changed_mind',%s::jsonb,'wrong part',null,%s)",
        (cash_sale['invoice'], json.dumps([{'invoice_line_id': str(line['id']), 'qty': 1, 'condition': 'unopened'}]), till)))
    if case:
        refused('cashier posts the return alone', lambda: as_user(cashier, "select post_pos_return_case(%s)", (case,)))
        if step('manager posts the return', lambda: as_user(manager, "select post_pos_return_case(%s)", (case,))) is not None:
            drawer['cash'] -= float(sql("select unit_price from sales_invoice_lines where id=%s", (line['id'],), one=True)['unit_price'])



def return_case(invoice, resolution, reason):
    line = sql("select id from sales_invoice_lines where invoice_id=%s limit 1", (invoice,), one=True)
    case = val(cashier, "select create_pos_return_case(%s,%s,%s,%s::jsonb,null,null,%s)",
               (invoice, resolution, reason, json.dumps([{'invoice_line_id': str(line['id']), 'qty': 1, 'condition': 'unopened'}]), till))
    as_user(manager, "select post_pos_return_case(%s)", (case,))
    return sql("select status, credit_note_id is not null as credit_note, store_credit_ledger_id is not null as store_credit from pos_return_cases where id=%s", (case,), one=True)


paid_named = step('named customer pays cash', lambda: counter_sale(C['Chipo Ndlovu'], 1, lambda due: [{'tender': 'cash', 'amount': due}]))
if paid_named:
    step('manager gives store credit on a return', lambda: return_case(paid_named['invoice'], 'store_credit', 'wrong_part'))
if acct_inv:
    step('manager credits an on-account sale (credit note)', lambda: return_case(acct_inv, 'credit_note', 'defective'))

# --- Pay on delivery refused twice -> suspension -> manager lifts --------------------------------
# A new online customer each run, so earlier runs' refusals don't count against them.
run = uuid.uuid4().hex[:6]
tapiwa_user = auth_user(f'buyer-{run}@sim.gtr', f'Online buyer {run}')
tapiwa = str(val(cashier, "select public.create_pos_customer('individual',%s,null,%s,%s,%s)",
                 (f'Online buyer {run}', f'buyer-{run}@sim.gtr', '+26377' + str(int(run, 16) % 10**7).zfill(7), None)))
as_service("update customers set profile_id=%s where id=%s and profile_id is null", (tapiwa_user, tapiwa))


def cod_order_refused():
    cart = val(tapiwa_user, "select create_customer_cart(%s,'USD','dispatch',1)", (MAIN,))
    as_user(tapiwa_user, "select add_customer_cart_line(%s,%s,%s,1)", (cart, item['id'], item['uom']))
    inv = val(tapiwa_user, "select checkout_customer_cart_v2(%s,'cash_on_delivery')", (cart,))
    pick = sql("select id from pick_lists where sales_invoice_id=%s", (inv,), one=True)['id']
    lines = [{'pick_list_line_id': str(r['id']), 'qty_picked': float(r['qty_requested'])}
             for r in sql("select id, qty_requested from pick_list_lines where pick_list_id=%s", (pick,))]
    as_user(U['warehouse'], "select confirm_pick_lines(%s,%s::jsonb)", (pick, json.dumps(lines)))
    job = sql("select dj.id from delivery_jobs dj join delivery_notes dn on dn.id=dj.delivery_note_id where dn.sales_invoice_id=%s",
              (inv,), one=True)['id']
    as_user(dispatch, "select assign_delivery_job(%s,%s,true)", (job, d1))
    as_user(dispatch, "select update_delivery_job_status(%s,'dispatched')", (job,))
    as_user(d1, "select fail_delivery_job(%s,'refused','customer refused to pay',false)", (job,))
    return job


step('1st pay-on-delivery order refused at the door', cod_order_refused)
step('not suspended after one refusal', lambda: (lambda s: s if not (s and s.get('active')) else (_ for _ in ()).throw(AssertionError('suspended too early')))(
    val(manager, "select get_customer_suspension(%s)", (tapiwa,))))
step('2nd pay-on-delivery order refused at the door', cod_order_refused)
susp = step('customer is now suspended', lambda: val(manager, "select get_customer_suspension(%s)", (tapiwa,)))
refused('suspended customer orders pay-on-delivery', cod_order_refused, expect='suspend')
refused('cashier lifts the suspension', lambda: val(cashier, "select lift_customer_suspension(%s,'paid up')", ((susp or {}).get('id'),)))
if susp and susp.get('id'):
    step('manager lifts the suspension', lambda: val(manager, "select lift_customer_suspension(%s,'spoke to customer, will pay')", (susp['id'],)))
    def cod_checkout():
        cart = val(tapiwa_user, "select create_customer_cart(%s,'USD','dispatch',1)", (MAIN,))
        as_user(tapiwa_user, "select add_customer_cart_line(%s,%s,%s,1)", (cart, item['id'], item['uom']))
        return val(tapiwa_user, "select checkout_customer_cart_v2(%s,'cash_on_delivery')", (cart,))
    step('after the lift the customer can order pay-on-delivery again', cod_checkout)

# --- Close the till ----------------------------------------------------------------------------
if till:
    exp = round(drawer['cash'], 2)
    print('     drawer should hold USD %.2f' % exp)
    whole = int(exp // 10)
    rest = round(exp - whole * 10, 2)
    denoms = [{'denomination': 10, 'quantity': whole}] + ([{'denomination': rest, 'quantity': 1}] if rest else [])
    closed = step('cashier closes the till with an exact count', lambda: val(
        cashier, "select submit_pos_till_denominated_close(%s,%s::jsonb,null,null)", (till, json.dumps(denoms))))
    if closed is not None:
        step('no variance at close', lambda: (lambda v: v if abs(float(v.get('variance') or 0)) < 0.01 else (_ for _ in ()).throw(AssertionError(v)))(closed))

print()
print('FINDINGS' if failures else 'ALL STEPS PASSED')
for n, e in failures:
    print(' -', n, ':', e)
