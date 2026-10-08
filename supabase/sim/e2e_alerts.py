"""Alerts away from the screen: staff choose phone alerts for themselves; an urgent item that has
waited 10 minutes is texted (once) to the people it waits on; the 07:00 summary goes to managers
who asked for it, with the figures they are allowed to see."""
import json, uuid
from sim import sql, as_user, val, RpcError

ids = json.load(open('ids.json'))
U, C, W = ids['users'], ids['customers'], ids['warehouses']
MAIN, BYO = W['MAIN'], W['WH2']
cashier, manager, warehouse = U['cashier'], U['manager'], U['warehouse']
failures = []


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


def outbox(user, code):
    return sql("select channel, phone_e164, body from sms_outbox where recipient_user_id=%s and event_code=%s order by created_at", (user, code))


def settings(user, phone, channel, urgent, summary):
    return val(user, "select set_my_alert_settings(%s,%s,%s,%s)", (phone, channel, urgent, summary))


refused('alerts without a phone number', lambda: settings(warehouse, None, 'sms', True, False), expect='phone')
refused('a phone number not in international form', lambda: settings(warehouse, '0771 234 567', 'sms', True, False), expect='international')
step('warehouse turns on urgent alerts by WhatsApp', lambda: expect(settings(warehouse, '+263 77 100 0005', 'whatsapp', True, False)['phone_e164'] == '+263771000005', 'phone not cleaned'))
step('manager turns on urgent alerts and the morning summary by SMS', lambda: settings(manager, '+263771000002', 'sms', True, True))
step('cashier asks for the morning summary too', lambda: expect(settings(cashier, '+263771000003', 'sms', False, True)['can_get_summary'] is False, 'cashier can get summary'))
step('nobody reads another person\'s settings', lambda: expect(val(cashier, "select count(*) from staff_alert_settings") == 1, 'sees others'))

# An urgent item: a branch transfer requested for a waiting customer (the warehouse must send it).
part = sql("""select si.id, si.base_uom_id from stock_levels sl join stock_items si on si.id=sl.stock_item_id
              where sl.warehouse_id=%s and sl.quantity>2 and not si.requires_serial limit 1""", (BYO,), one=True)
tr = step('cashier asks Bulawayo for a part for a waiting customer', lambda: val(
    cashier, "select create_pos_fulfillment_request('branch_transfer',%s,%s,1,%s,%s,%s,null,null,'customer at the counter',null)",
    (part['id'], part['base_uom_id'], BYO, MAIN, C['Harare Motor Spares'])))
before = len(outbox(warehouse, 'approval_urgent'))
sql("select private.sweep_approval_alerts()")
step('not texted before it has waited 10 minutes', lambda: expect(len(outbox(warehouse, 'approval_urgent')) == before, 'texted too early'))
sql("update pos_fulfillment_requests set created_at = now() - interval '15 minutes' where id=%s", (tr,))  # simulation only
sql("select private.sweep_approval_alerts()")
msgs = outbox(warehouse, 'approval_urgent')
step('warehouse gets one WhatsApp for the urgent transfer', lambda: expect(
    len(msgs) == before + 1 and msgs[-1]['channel'] == 'whatsapp' and 'URGENT' in msgs[-1]['body'], msgs[-1:]))
sql("select private.sweep_approval_alerts()")
step('the next sweep does not text it again', lambda: expect(len(outbox(warehouse, 'approval_urgent')) == before + 1, 'texted twice'))
step('cashier (no urgent alerts) is not texted', lambda: expect(len(outbox(cashier, 'approval_urgent')) == 0, 'cashier texted'))
val(cashier, "select cancel_pos_fulfillment_request(%s,'alerts test done')", (tr,))

# 07:00 summary for yesterday (the simulated day is today, so send today's here).
day = sql("select (now() at time zone 'Africa/Harare')::date d", one=True)['d']
n = step('morning summaries sent', lambda: sql("select private.send_morning_summaries(%s) n", (day,), one=True)['n'])
m = outbox(manager, 'morning_summary')
step('manager gets yesterday\'s figures by SMS', lambda: expect(m and m[-1]['channel'] == 'sms' and 'Sales USD' in m[-1]['body'], m[-1:]))
print('     ' + (m[-1]['body'].replace('\n', '\n     ') if m else ''))
step('cashier gets no summary (not allowed to see the dashboard)', lambda: expect(len(outbox(cashier, 'morning_summary')) == 0, 'cashier got one'))
step('manager also sees it in the app', lambda: expect(sql(
    "select count(*) n from staff_ops_notifications where recipient_user_id=%s and kind='morning_summary'", (manager,), one=True)['n'] >= 1, 'no notice'))
sql("select private.send_morning_summaries(%s)", (day,))
step('sending again the same day does nothing', lambda: expect(len(outbox(manager, 'morning_summary')) == len(m), 'sent twice'))
step('summary is scheduled for 07:00 Harare', lambda: expect(sql("select schedule from cron.job where jobname='morning-summary-v1'", one=True)['schedule'] == '0 5 * * *', 'not scheduled'))

print()
print('FINDINGS' if failures else 'ALL STEPS PASSED')
for n_, e in failures:
    print(' -', n_, ':', e)
