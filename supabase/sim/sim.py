"""Helpers for the local replica: Auth users, and calling database functions as a given user."""
import json, os, urllib.request, psycopg2, psycopg2.extras

# Local stack only (see README). CI points these at its own stack.
DB = os.environ.get('SIM_DB_URL', 'postgresql://postgres:postgres@127.0.0.1:55422/postgres')
API = os.environ.get('SIM_API_URL', 'http://127.0.0.1:55421')
# Public local-development key printed by `supabase start` (not a secret).
SERVICE = os.environ.get('SIM_SERVICE_KEY', 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU')
if '127.0.0.1' not in DB and 'localhost' not in DB:
    raise SystemExit('simulation refuses a non-local database: the ledger is append-only')
PASSWORD = 'Sim-Passw0rd!'

conn = psycopg2.connect(DB)
conn.autocommit = False

class RpcError(Exception):
    pass

def sql(query, params=None, one=False):
    """As postgres (setup only)."""
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(query, params)
        rows = cur.fetchall() if cur.description else []
    conn.commit()
    return (rows[0] if rows else None) if one else rows

def as_service(query, params=None):
    """As the service role (what Edge functions and admin tooling use)."""
    with conn.cursor() as cur:
        cur.execute("select set_config('request.jwt.claims', '{\"role\":\"service_role\"}', true)")
        cur.execute(query, params)
    conn.commit()

def as_user(uid, query, params=None, one=True):
    """Runs [query] as an authenticated user (RLS and auth.uid() apply), committing on success."""
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        try:
            cur.execute("select set_config('request.jwt.claims', %s, true), set_config('role','authenticated',true)",
                        (json.dumps({'sub': uid, 'role': 'authenticated'}),))
            cur.execute("set local role authenticated")
            cur.execute(query, params)
            rows = cur.fetchall() if cur.description else []
            conn.commit()
        except Exception as e:
            conn.rollback()
            raise RpcError(str(e).split('\n')[0]) from None
    return (rows[0] if rows else None) if one else rows

def val(uid, query, params=None):
    r = as_user(uid, query, params)
    return list(r.values())[0] if r else None

def auth_user(email, full_name, phone=None):
    """Creates (or finds) an Auth user through the local Auth admin API."""
    existing = sql('select id from auth.users where email=%s', (email,), one=True)
    if existing:
        return str(existing['id'])
    body = {'email': email, 'password': PASSWORD, 'email_confirm': True, 'user_metadata': {'full_name': full_name}}
    if phone:
        body['phone'] = phone
        body['phone_confirm'] = True
    req = urllib.request.Request(API + '/auth/v1/admin/users', data=json.dumps(body).encode(), method='POST',
        headers={'Authorization': 'Bearer ' + SERVICE, 'apikey': SERVICE, 'Content-Type': 'application/json'})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(req, timeout=30) as r:
        return json.loads(r.read())['id']


def free_item(warehouse_id, need=3):
    """The non-serial item with the most stock not held by open reservations at [warehouse_id]."""
    return sql("""select si.id, si.base_uom_id uom,
                         sl.quantity - coalesce((select sum(r.reserved_qty - coalesce(r.consumed_qty,0)) from inventory_reservations r
                                                 where r.stock_item_id=si.id and r.warehouse_id=sl.warehouse_id
                                                   and r.state in ('active','allocated')),0) free
                  from stock_levels sl join stock_items si on si.id=sl.stock_item_id
                  where sl.warehouse_id=%s and not si.requires_serial
                  order by free desc limit 1""", (warehouse_id,), one=True)
