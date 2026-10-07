"""Driver hands in COD cash; cashier counts it short; a manager signs off the difference."""
import json
from sim import sql, as_user, val, RpcError
ids = json.load(open('ids.json'))
U = ids['users']
d1, cashier, manager = U['driver1'], U['cashier'], U['manager']

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
step('manager approves difference', lambda: val(manager, "select approve_driver_cash_variance(%s,'driver_short','deduct from driver')", (hid,)))
step('driver holds nothing now', lambda: val(d1, "select get_my_driver_cash()"))
print(sql("select document_number,status,expected_amount,declared_amount,received_amount,variance from driver_cash_handins where id=%s", (hid,)))
