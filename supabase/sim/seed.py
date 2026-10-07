"""Simulated business on the local replica: staff, manager, drivers, customers, opening stock,
card machines. Every business object is created through the system's own functions as the right
person, so the rules production uses also apply here. Safe to re-run (it finds what exists)."""
import json
from sim import sql, as_user, as_service, val, auth_user, RpcError

WH = {r['code']: str(r['id']) for r in sql("select id, code from warehouses")}
MAIN, BYO = WH['MAIN'], WH['WH2']
sql("update warehouses set name='Harare main' where code='MAIN'; update warehouses set name='Bulawayo branch' where code='WH2'")

STAFF = [
    # email, name, roles, phone
    ('owner@sim.gtr', 'Tatenda Owner', ['admin', 'finance'], '+263771000001'),
    ('manager@sim.gtr', 'Rudo Manager', ['sales', 'dispatcher'], '+263771000002'),
    ('cashier@sim.gtr', 'Farai Cashier', ['sales'], '+263771000003'),
    ('cashier2@sim.gtr', 'Nyasha Cashier', ['sales'], '+263771000004'),
    ('warehouse@sim.gtr', 'Tinashe Stores', ['warehouse'], '+263771000005'),
    ('dispatch@sim.gtr', 'Kuda Dispatch', ['dispatcher'], '+263771000006'),
    ('finance@sim.gtr', 'Chiedza Finance', ['finance'], '+263771000007'),
    ('driver1@sim.gtr', 'Blessing Driver', ['driver'], '+263771000008'),
    ('driver2@sim.gtr', 'Simba Driver', ['driver'], '+263771000009'),
]
U = {}
for email, name, roles, phone in STAFF:
    uid = auth_user(email, name)
    U[email.split('@')[0]] = uid
    sql("update profiles set is_staff=true, full_name=%s, phone_e164=%s where id=%s", (name, phone, uid))
    for r in roles:
        sql("insert into staff_roles(user_id, role) values (%s, %s) on conflict do nothing", (uid, r))
owner = U['owner']

# Employees (the manager is an approver for discounts, refunds, lifting suspensions...).
for i, (email, name, roles, phone) in enumerate(STAFF):
    key = email.split('@')[0]
    if not sql("select 1 from employees where user_id=%s", (U[key],)):
        val(owner, "select public.create_employee(%s,%s,%s,%s,%s,current_date - 400)", (f'SIM-{i+1:03d}', name, U[key], email, phone))
mgr_emp = sql("select id from employees where user_id=%s", (U['manager'],), one=True)['id']
try:
    val(owner, "select public.set_approver_assignment(%s, true, 'Branch manager')", (mgr_emp,))
except RpcError as e:
    print('approver:', e)

# Customers.
CUSTOMERS = [
    # name, kind, business, email, phone, credit limit, note
    ('Harare Motor Spares', 'business', 'Harare Motor Spares (Pvt) Ltd', 'accounts@hms.sim', '+263772100001', 3000),
    ('Bulawayo Fleet Services', 'business', 'Bulawayo Fleet Services', 'fleet@bfs.sim', '+263772100002', 500),
    ('Tendai Garage', 'business', 'Tendai Garage', 'tendai@garage.sim', '+263772100003', 1500),
    ('Chipo Ndlovu', 'individual', None, 'chipo@sim.gtr', '+263772100004', 0),
    ('Tapiwa Moyo', 'individual', None, 'tapiwa@sim.gtr', '+263772100005', 0),
]
C = {}
for name, kind, biz, email, phone, limit in CUSTOMERS:
    row = sql("select id from customers where email=%s", (email,), one=True)
    if row:
        C[name] = str(row['id'])
        continue
    cid = val(U['cashier'], "select public.create_pos_customer(%s,%s,%s,%s,%s,%s)", (kind, name, biz, email, phone, phone))
    C[name] = str(cid)
    if limit:
        as_user(U['finance'], "select * from public.set_customer_credit(%s, %s, false)", (cid, limit))
# Two retail customers also shop online and in the customer app.
for name, email in (('Chipo Ndlovu', 'chipo@sim.gtr'), ('Tapiwa Moyo', 'tapiwa@sim.gtr')):
    uid = auth_user(email, name)
    U['customer:' + name] = uid
    as_service("update customers set profile_id=%s where id=%s and profile_id is null", (uid, C[name]))

# Opening stock at both branches (FIFO batches with a cost), unless already received.
if not sql("select 1 from stock_entries where notes='Simulated opening stock' limit 1"):
    items = sql("select si.id, si.base_uom_id, si.requires_serial, coalesce(pli.unit_price,50) price from stock_items si left join price_list_items pli on pli.stock_item_id=si.id and pli.price_list_id=(select id from price_lists where code='RETAIL')")
    for wh, factor in ((MAIN, 1.0), (BYO, 0.4)):
        lines = []
        for it in items:
            qty = max(2, round(30 * factor)) if float(it['price']) < 100 else max(1, round(8 * factor))
            line = {'stock_item_id': str(it['id']), 'uom_id': str(it['base_uom_id']), 'qty': qty,
                    'unit_cost': round(float(it['price']) * 0.55, 2), 'currency': 'USD'}
            if it['requires_serial']:
                line['serials'] = [f'SIM-{str(it["id"])[:6]}-{wh[:4]}-{n}' for n in range(qty)]
            lines.append(line)
        val(U['warehouse'], "select public.post_stock_receipt(%s, 'Simulated opening stock', %s::jsonb)", (wh, json.dumps(lines)))

# Card machines: one counter machine, one for drivers (assigned to driver 1's phone).
for code, label, device, delivery in (('SIM-CT-01', 'Counter card machine', 'sim-tablet-1', False), ('SIM-DRV-01', 'Driver card machine', 'sim-driver1-phone', True)):
    tid = val(owner, """select public.upsert_pos_card_terminal(null,%s,%s,'Demo Bank','T-'||%s,'android_intent_v1',
        '{"package_name":"zw.demo.pos","purchase_action":"zw.demo.pos.PURCHASE","status_action":"zw.demo.pos.STATUS"}'::jsonb,%s,%s,true)""",
        (code, label, code, MAIN, device))
    val(owner, "select public.set_pos_card_terminal_delivery_enabled(%s,%s)", (tid, delivery))

json.dump({'users': U, 'customers': C, 'warehouses': WH}, open('ids.json', 'w'), indent=1)
print('staff', len(STAFF), 'customers', len(C))
print(sql("select w.code, count(*) items, sum(sl.quantity) units from stock_levels sl join warehouses w on w.id=sl.warehouse_id group by 1"))
print(sql("select count(*) batches from stock_batches"))
