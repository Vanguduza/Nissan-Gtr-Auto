"""Card payments: at the counter (approved, declined, no answer then recovered, forged answer refused),
voided on the machine before the sale is finished, refunded to the card by a manager, and at the door
(driver's paired phone and machine)."""
import json, uuid
from sim import sql, as_user, val, free_item, RpcError
from card import Device, edge_card_terminal_result

ids = json.load(open('ids.json'))
U, C, W = ids['users'], ids['customers'], ids['warehouses']
MAIN = W['MAIN']
cashier, manager, dispatch, d1 = U['cashier'], U['manager'], U['dispatch'], U['driver1']
failures = []
RUN = uuid.uuid4().hex[:6].upper()


def step(name, fn):
    try:
        r = fn()
        print('OK  ', name, '->', json.dumps(r, default=str)[:200])
        return r
    except (RpcError, AssertionError, KeyError, TypeError, StopIteration) as e:
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


term = {r['code']: str(r['id']) for r in sql("select id, code from pos_card_terminals where code like 'SIM-%'")}
item = free_item(MAIN)

tablet = Device('sim-tablet-1')
refused('cashier pairs the till tablet with the machine', lambda: tablet.pair(cashier, term['SIM-CT-01']))
step('admin pairs the till tablet with the machine (setup)', lambda: tablet.pair(U['owner'], term['SIM-CT-01']))

sql("update pos_till_sessions set status='closed' where status<>'closed' and opened_by=%s", (cashier,))
till = step('cashier opens till', lambda: val(cashier, "select open_pos_till_session(%s,'sim-tablet-1',50,'USD')", (MAIN,)))


def order():
    cart = val(cashier, "select create_pos_cart(%s,null,'USD','immediate')", (MAIN,))
    as_user(cashier, "select add_cart_line(%s,%s,%s,1)", (cart, item['id'], item['uom']))
    as_user(cashier, "select attach_pos_cart_till_session(%s,%s)", (cart, till))
    return val(cashier, "select prepare_pos_commerce_checkout_v2(%s,%s,'20 minutes',null,null,null)", (cart, str(uuid.uuid4())))


def begin(o):
    a = val(cashier, "select begin_pos_card_terminal_purchase(%s,%s,%s)", (o, term['SIM-CT-01'], str(uuid.uuid4())))
    return a['attempt_id'] if isinstance(a, dict) else a


# Approved
o1 = order()
a1 = step('card charge starts', lambda: begin(o1))
p, sig = tablet.answer(a1, 'approved', txn='TX-1001-'+RUN, rrn='RRN1001', auth='A1', last4='4242')
step('machine approves; signed answer recorded', lambda: edge_card_terminal_result(cashier, p, sig))
inv = step('sale finalised from the approved card payment',
           lambda: val(cashier, "select finalize_pos_card_terminal_purchase(%s)", (a1,)))
step('order paid', lambda: (lambda s: expect(s['state'] == 'paid' or s.get('sales_invoice_id'), s) and s['state'])(
    val(cashier, "select get_pos_payment_status(%s)", (o1,))))
step('replaying the same answer changes nothing (no second payment)', lambda: (lambda r: expect(r['status'] == 'settled', r) and
     expect(sql("select count(*) n from payment_entries pe join payment_allocations pa on pa.payment_entry_id=pe.id where pa.sales_invoice_id=%s",
                (sql("select sales_invoice_id from commerce_orders where id=%s", (o1,), one=True)["sales_invoice_id"],), one=True)['n'] == 1, 'paid twice') and 'settled once')(
    edge_card_terminal_result(cashier, p, sig)))

# Declined: no money, sale still waiting for payment
o2 = order()
a_dup = begin(o2)
p_dup, sig_dup = tablet.answer(a_dup, 'approved', txn='TX-1001-' + RUN, rrn='RRN1001', last4='4242')
refused('the same machine transaction used for a second sale', lambda: edge_card_terminal_result(cashier, p_dup, sig_dup), expect='already recorded')
p_dup, sig_dup = tablet.answer(a_dup, 'cancelled')
edge_card_terminal_result(cashier, p_dup, sig_dup)
a2 = step('second card charge starts', lambda: begin(o2))
p, sig = tablet.answer(a2, 'declined', message='Insufficient funds')
step('machine declines', lambda: edge_card_terminal_result(cashier, p, sig))
refused('declined charge cannot be finalised', lambda: val(cashier, "select finalize_pos_card_terminal_purchase(%s)", (a2,)))
step('order still awaiting payment', lambda: (lambda s: expect(s['state'] != 'paid', s) and s['state'])(
    val(cashier, "select get_pos_payment_status(%s)", (o2,))))

# Forged answer from an unpaired device / tampered payload
a3 = step('third card charge starts', lambda: begin(o2))
other = Device('sim-tablet-1')  # same device id, different key: not the paired key
p, sig = other.answer(a3, 'approved', txn='TX-9999-'+RUN, rrn='RRN9999', last4='1111')
refused('answer signed with the wrong key', lambda: edge_card_terminal_result(cashier, p, sig), expect='signature rejected')
p, sig = tablet.answer(a3, 'declined')
p['outcome'] = 'approved'; p['terminal_transaction_id'] = 'TX-X-'+RUN; p['rrn'] = 'R'
refused('answer changed after signing', lambda: edge_card_terminal_result(cashier, p, sig), expect='signature rejected')

# No answer, then the machine is asked again
p, sig = tablet.answer(a3, 'unknown', message='Timeout')
step('machine gives no answer', lambda: edge_card_terminal_result(cashier, p, sig))
step('shows in card recovery', lambda: (lambda rows: expect(any(a3 in json.dumps(r, default=str) for r in rows), 'not listed') and len(rows))(
    as_user(manager, "select * from list_pos_card_terminal_recovery(50)", one=False)))
refused('a new charge while one has no answer', lambda: begin(o2))
p, sig = tablet.answer(a3, 'approved', txn='TX-1003-'+RUN, rrn='RRN1003', last4='4242')
step('asking the machine again: it was approved', lambda: edge_card_terminal_result(cashier, p, sig))
step('sale finalised after recovery', lambda: val(cashier, "select finalize_pos_card_terminal_purchase(%s)", (a3,)))

# Void: an approved charge the cashier cancels before the sale is finalised (machine reversal)
o4 = order()
a4 = step('fourth card charge starts', lambda: begin(o4))
p, sig = tablet.answer(a4, 'approved', txn='TX-1004-'+RUN, rrn='RRN1004', last4='4242')
step('machine approves', lambda: edge_card_terminal_result(cashier, p, sig))
rv = step('cashier voids it on the machine before finishing the sale', lambda: val(
    cashier, "select begin_pos_card_terminal_reversal(%s,%s)", (a4, str(uuid.uuid4())))['attempt_id'])
p, sig = tablet.answer(rv, 'approved', txn='TX-1004R-'+RUN, rrn='RRN1004R')
step('machine confirms the void', lambda: edge_card_terminal_result(cashier, p, sig))
refused('voided charge cannot be finalised into a sale', lambda: val(cashier, "select finalize_pos_card_terminal_purchase(%s)", (a4,)))
step('no payment recorded for the voided charge', lambda: expect(sql(
    "select payment_entry_id from pos_card_terminal_attempts where id=%s", (a4,), one=True)['payment_entry_id'] is None, 'payment recorded'))
refused('a settled sale cannot be voided (refund instead)', lambda: val(cashier, "select begin_pos_card_terminal_reversal(%s,%s)", (a1, str(uuid.uuid4()))))

# Refund: the first sale is refunded to the card by a manager
inv1 = str(sql("select sales_invoice_id from commerce_orders where id=%s", (o1,), one=True)['sales_invoice_id'])
refused('cashier refunds to a card', lambda: val(cashier, "select begin_pos_card_terminal_refund(%s,%s,%s)", (inv1, term['SIM-CT-01'], str(uuid.uuid4()))), expect='manager')
req = str(uuid.uuid4())
rf = step('manager starts a card refund for the whole sale', lambda: val(
    manager, "select begin_pos_card_terminal_refund(%s,%s,%s)", (inv1, term['SIM-CT-01'], req))['attempt_id'])
step('asking again with the same request gives the same refund', lambda: expect(val(
    manager, "select begin_pos_card_terminal_refund(%s,%s,%s)", (inv1, term['SIM-CT-01'], req))['attempt_id'] == rf, 'new refund'))
refused('a second refund while one is open', lambda: val(manager, "select begin_pos_card_terminal_refund(%s,%s,%s)", (inv1, term['SIM-CT-01'], str(uuid.uuid4()))))
refused('finalised before the machine answers', lambda: val(manager, "select finalize_pos_card_terminal_refund(%s,null)", (rf,)), expect='approval')
p, sig = tablet.answer(rf, 'approved', txn='TX-1001RF-'+RUN, rrn='RRN1001RF', last4='4242')
step('machine approves the refund', lambda: edge_card_terminal_result(manager, p, sig))
refused('cashier finalises the refund', lambda: val(cashier, "select finalize_pos_card_terminal_refund(%s,null)", (rf,)), expect='manager')
step('manager finalises the refund', lambda: expect(val(manager, "select finalize_pos_card_terminal_refund(%s,'customer returned part')", (rf,))['status'] == 'settled', 'not settled'))
step('credit note for the whole sale, money back through card clearing (Dr 4110 / Cr 1170)', lambda: (lambda rows: expect(
    {r['account_code']: (float(r['debit']), float(r['credit'])) for r in rows if r['account_code'] in ('4110', '1170')} ==
    {'4110': (float(rows[0]['total']), 0.0), '1170': (0.0, float(rows[0]['total']))}, rows) and rows[0]['document_number'])(sql(
    """select cn.document_number, cn.total, l.account_code, l.debit, l.credit from pos_card_terminal_attempts a
       join sales_invoices cn on cn.id=a.credit_note_id join journal_entry_lines l on l.journal_entry_id=cn.journal_entry_id where a.id=%s""", (rf,))))
step('the part went back into stock (quarantine)', lambda: expect(sql(
    "select count(*) n from sales_invoice_lines l join pos_card_terminal_attempts a on a.credit_note_id=l.invoice_id where a.id=%s and l.issues_stock", (rf,), one=True)['n'] >= 1, 'no stock line'))
refused('refunding the same sale again', lambda: val(manager, "select begin_pos_card_terminal_refund(%s,%s,%s)", (inv1, term['SIM-CT-01'], str(uuid.uuid4()))), expect='already')

# Card at the door
phone = Device('sim-driver1-phone')
step('driver pairs phone with the delivery machine',
     lambda: phone.pair(d1, term['SIM-DRV-01'], 'register_delivery_card_terminal_device_key'))
cust = U['customer:Chipo Ndlovu']
cart = val(cust, "select create_customer_cart(%s,'USD','dispatch',1)", (MAIN,))
as_user(cust, "select add_customer_cart_line(%s,%s,%s,1)", (cart, item['id'], item['uom']))
dinv = val(cust, "select checkout_customer_cart_v2(%s,'card_on_delivery')", (cart,))
pick = sql("select id from pick_lists where sales_invoice_id=%s", (dinv,), one=True)['id']
lines = [{'pick_list_line_id': str(r['id']), 'qty_picked': float(r['qty_requested'])}
         for r in sql("select id, qty_requested from pick_list_lines where pick_list_id=%s", (pick,))]
as_user(U['warehouse'], "select confirm_pick_lines(%s,%s::jsonb)", (pick, json.dumps(lines)))
job = sql("select dj.id from delivery_jobs dj join delivery_notes dn on dn.id=dj.delivery_note_id where dn.sales_invoice_id=%s", (dinv,), one=True)['id']
as_user(dispatch, "select assign_delivery_job(%s,%s,true)", (job, d1))
as_user(dispatch, "select update_delivery_job_status(%s,'dispatched')", (job,))
due = float(val(d1, "select get_delivery_job_payment_context(%s)", (job,))['amount_due'])
refused('driver collects cash on a card-only order', lambda: val(d1, "select collect_delivery_cash(%s,%s,%s,null)", (job, due, str(uuid.uuid4()))))
da = step('driver starts card charge at the door', lambda: val(
    d1, "select begin_delivery_card_terminal_payment(%s,%s,%s,%s,%s)", (job, term['SIM-DRV-01'], phone.device_id, due, str(uuid.uuid4()))))
da_id = (da or {}).get('attempt_id')
p, sig = phone.answer(da_id, 'approved', txn='TX-D1-'+RUN, rrn='RRND1', last4='5555')
step('machine approves at the door', lambda: edge_card_terminal_result(d1, p, sig))
step('payment posted against the invoice', lambda: val(d1, "select finalize_delivery_card_terminal_payment(%s)", (da_id,)))
step('nothing left to collect', lambda: (lambda c: expect(float(c['amount_due']) < 0.01, c) and c['amount_due'])(
    val(d1, "select get_delivery_job_payment_context(%s)", (job,))))

print()
print('FINDINGS' if failures else 'ALL STEPS PASSED')
for n, e in failures:
    print(' -', n, ':', e)
