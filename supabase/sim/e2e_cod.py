"""COD delivery: customer checks out pay-on-delivery, dispatch ships it, driver collects cash."""
import json, uuid, sys
from sim import sql, as_user, val, RpcError
ids = json.load(open('ids.json'))
U, C, W = ids['users'], ids['customers'], ids['warehouses']

def step(name, fn):
    try:
        r = fn(); print('OK  ', name, '->', json.dumps(r, default=str)[:300]); return r
    except RpcError as e:
        print('FAIL', name, '->', e); raise SystemExit(1)

item = sql("select sl.stock_item_id id, si.base_uom_id uom from stock_levels sl join stock_items si on si.id=sl.stock_item_id where sl.warehouse_id=%s and sl.quantity>=5 and not si.requires_serial limit 1", (W['MAIN'],), one=True)
cust = U['customer:Chipo Ndlovu']
cart = step('customer cart', lambda: val(cust, "select create_customer_cart(%s,'USD','dispatch',1)", (W['MAIN'],)))
step('add line', lambda: as_user(cust, "select add_customer_cart_line(%s,%s,%s,2)", (cart, item['id'], item['uom'])))
co = step('checkout cash on delivery', lambda: val(cust, "select checkout_customer_cart_v2(%s,'cash_on_delivery')", (cart,)))
inv = co['invoice_id'] if isinstance(co, dict) and 'invoice_id' in co else co
print('invoice', inv)
print(sql("select id,status,total,amount_paid,currency from sales_invoices where id=%s", (inv,), one=False))
print(sql("select id,status from pick_lists where sales_invoice_id::text=%s", (str(inv),)))

pick = sql("select id from pick_lists where sales_invoice_id=%s", (inv,), one=True)['id']
lines = [{'pick_list_line_id': str(r['id']), 'qty_picked': float(r["qty_requested"])} for r in sql("select id, qty_requested from pick_list_lines where pick_list_id=%s", (pick,))]
step('warehouse confirms pick', lambda: as_user(U['warehouse'], "select confirm_pick_lines(%s,%s::jsonb)", (pick, json.dumps(lines))))
print(sql("select dn.id dn, dn.status, dj.id job, dj.status jstatus, dj.assignee_user_id from delivery_notes dn left join delivery_jobs dj on dj.delivery_note_id=dn.id where dn.sales_invoice_id=%s", (inv,)))

job = sql("select dj.id from delivery_jobs dj join delivery_notes dn on dn.id=dj.delivery_note_id where dn.sales_invoice_id=%s", (inv,), one=True)['id']
d1 = U['driver1']
step('dispatch assigns driver1', lambda: as_user(U['dispatch'], "select assign_delivery_job(%s,%s,true)", (job, d1)))
for st in ('dispatched',):
    step('dispatch marks ' + st, lambda: as_user(U['dispatch'], "select update_delivery_job_status(%s,%s::delivery_job_status)", (job, st)))
step('payment context', lambda: val(d1, "select get_delivery_job_payment_context(%s)", (job,)))
shown = val(d1, "select generate_delivery_pod_otp(%s,null)", (job,))
print('OK  ' if shown is None else 'FAIL', 'driver sends the code but cannot see it ->', shown)
mine = step('customer reads the code in their order', lambda: val(cust, "select get_my_delivery_codes()"))
code = next(c['code'] for c in mine if c['delivery_job_id'] == job)
refused_wrong = None
try:
    as_user(d1, "select verify_delivery_pod_otp(%s,'000000')", (job,)); print('FAIL wrong code accepted')
except RpcError as e:
    print('OK   wrong code refused ->', e)
step('driver checks the code the customer gives', lambda: val(d1, "select verify_delivery_pod_otp(%s,%s)", (job, code)))
try:
    as_user(d1, "select submit_delivery_pod(%s,%s||'/photo.jpg',%s||'/signature.png',%s,'x')", (job, job, job, code)); print('FAIL POD accepted while unpaid')
except RpcError as e:
    print('OK   POD refused while unpaid ->', e)
step('driver collects 30 cash', lambda: val(d1, "select collect_delivery_cash(%s,30,%s,null)", (job, str(uuid.uuid4()))))
step('driver collects 20 cash', lambda: val(d1, "select collect_delivery_cash(%s,20,%s,'rest')", (job, str(uuid.uuid4()))))
for f in ('photo.jpg', 'signature.png'):  # stands in for the app's upload
    sql("insert into storage.objects(bucket_id,name,owner,metadata) values ('delivery-pods',%s,%s,'{}'::jsonb)", (job + '/' + f, d1))
step('POD accepted when paid', lambda: val(d1, "select submit_delivery_pod(%s,%s||'/photo.jpg',%s||'/signature.png',%s,'delivered')", (job, job, job, code)))
print(sql("select status, amount_paid from sales_invoices where id=%s", (inv,)))
print('job', job)
