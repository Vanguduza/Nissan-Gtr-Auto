"""Driver hands in COD cash; cashier counts it short; a manager signs off the difference."""
import json
from sim import sql, as_user, val, RpcError
ids = json.load(open('ids.json'))
U = ids['users']
d1, cashier, manager, owner = U['driver1'], U['cashier'], U['manager'], U['owner']

def step(name, fn):
    try:
        r = fn(); print('OK  ', name, '->', json.dumps(r, default=str)[:400]); return r
    except RpcError as e:
        print('FAIL', name, '->', e); raise SystemExit(1)

def refused(name, fn):
    try:
        r = fn(); print('FAIL', name, 'was allowed ->', json.dumps(r, default=str)[:200])
    except RpcError as e:
        print('OK  ', name, 'refused ->', e)

mine = step('driver sees cash held', lambda: val(d1, "select get_my_driver_cash()"))
held = next((h for h in mine['holding'] if h['currency'] == 'USD'), None)
assert held, 'driver holds no USD'
amount = float(held['amount'])
h = step('driver submits hand-in', lambda: val(d1, "select submit_driver_cash_handin('USD',%s,'end of run')", (amount,)))
hid = h['id'] if 'id' in h else h['handin_id']
refused('second open hand-in', lambda: val(d1, "select submit_driver_cash_handin('USD',1,null)"))
refused('driver receives own hand-in', lambda: val(d1, "select receive_driver_cash_handin(%s,%s,null,null)", (hid, amount)))
step('staff list shows it', lambda: [x for x in val(cashier, "select list_driver_cash(null,50)")['handins'] if x['id'] == hid])
refused('short count without reason', lambda: val(cashier, "select receive_driver_cash_handin(%s,%s,null,null)", (hid, amount - 5)))
r = step('cashier counts 5 short', lambda: val(cashier, "select receive_driver_cash_handin(%s,%s,'driver_short','one note missing')", (hid, amount - 5)))
refused('receiver approves own count', lambda: val(cashier, "select approve_driver_cash_variance(%s,'driver_short',null)", (hid,)))
refused('driver approves', lambda: val(d1, "select approve_driver_cash_variance(%s,'driver_short',null)", (hid,)))
owed_before = sum(float(x['amount']) for x in val(d1, "select get_my_driver_cash()")['owed'] if x['currency'] == 'USD')
a = step('manager approves difference', lambda: val(manager, "select approve_driver_cash_variance(%s,'driver_short','deduct from driver')", (hid,)))
assert a['journal_number'] and float(a['outstanding_amount']) == 5, a
lines = sql("""select l.account_code, l.debit, l.credit from driver_cash_handins h join journal_entry_lines l on l.journal_entry_id = h.journal_entry_id
               where h.id = %s order by l.account_code""", (hid,))
assert [(r['account_code'], float(r['debit']), float(r['credit'])) for r in lines] == [('1120', 0, 5), ('1250', 5, 0)], lines
print('OK   shortage posted: Dr 1250 staff receivables / Cr 1120 cash', a['journal_number'])
mine = step('driver holds nothing now and owes 5 more', lambda: val(d1, "select get_my_driver_cash()"))
assert not [x for x in mine['holding'] if x['currency'] == 'USD'] and [float(x['amount']) for x in mine['owed'] if x['currency'] == 'USD'] == [owed_before + 5], mine
refused('driver records own repayment', lambda: val(d1, "select record_driver_cash_recovery(%s,3,null)", (hid,)))
refused('repayment above what is owed', lambda: val(cashier, "select record_driver_cash_recovery(%s,6,null)", (hid,)))
r = step('cashier takes 3 back from the driver', lambda: val(cashier, "select record_driver_cash_recovery(%s,3,'paid at the till')", (hid,)))
assert float(r['outstanding_amount']) == 2, r
refused('cashier writes off the rest', lambda: val(cashier, "select write_off_driver_cash_shortage(%s,2,'small_amount',null)", (hid,)))
refused('approving manager writes off', lambda: val(manager, "select write_off_driver_cash_shortage(%s,2,'small_amount',null)", (hid,)))
refused('write-off without notes where required', lambda: val(owner, "select write_off_driver_cash_shortage(%s,2,'not_recoverable',null)", (hid,)))
r = step('owner writes off the last 2', lambda: val(owner, "select write_off_driver_cash_shortage(%s,2,'small_amount',null)", (hid,)))
assert float(r['outstanding_amount']) == 0 and len(r['recoveries']) == 2, r
refused('nothing left to recover', lambda: val(cashier, "select record_driver_cash_recovery(%s,1,null)", (hid,)))
bal = sql("""select l.account_code, sum(l.debit - l.credit) from driver_cash_recoveries r join journal_entry_lines l on l.journal_entry_id = r.journal_entry_id
             where r.handin_id = %s group by 1 order by 1""", (hid,))
assert [(r['account_code'], float(r['sum'])) for r in bal] == [('1120', 3), ('1250', -5), ('5310', 2)], bal
print('OK   repayment Dr 1120 / Cr 1250, write-off Dr 5310 / Cr 1250; staff receivable cleared')
print(sql("select document_number,status,expected_amount,declared_amount,received_amount,variance from driver_cash_handins where id=%s", (hid,)))
