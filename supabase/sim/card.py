"""Simulated card machine and the card-terminal-result Edge Function, for a local stack.

The phone/tablet signs the machine's answer with its paired key (RSASSA-PKCS1-v1_5, SHA-256) over
the canonical JSON; the Edge Function checks the signature against the registered key and records
the result as the service role. This module does both halves the same way
(supabase/functions/card-terminal-result/index.ts), since the Edge runtime is off locally.
"""
import base64, hashlib, json
from datetime import datetime, timezone
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.exceptions import InvalidSignature
from sim import sql, as_user, val, RpcError


class Device:
    """A paired till tablet or driver phone."""

    def __init__(self, device_id):
        self.device_id = device_id
        self.key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        der = self.key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        self.spki_b64 = base64.b64encode(der).decode()
        self.sha = hashlib.sha256(der).hexdigest()

    def pair(self, uid, terminal_id, rpc='register_pos_card_terminal_device_key'):
        return val(uid, f"select {rpc}(%s,%s,%s,%s)", (terminal_id, self.device_id, self.spki_b64, self.sha))

    def answer(self, attempt_id, outcome, txn=None, rrn=None, auth=None, last4=None, message=None):
        """The machine's answer as the app signs it (key order matters: it is the canonical JSON)."""
        payload = {
            'version': 'gtr-card-terminal-evidence-v1',
            'attempt_id': attempt_id,
            'device_id': self.device_id,
            'observed_at': datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
            'outcome': outcome,
            'terminal_transaction_id': txn,
            'rrn': rrn,
            'authorization_code': auth,
            'card_last4': last4,
            'card_scheme': 'VISA' if last4 else None,
            'response_code': '00' if outcome == 'approved' else ('05' if outcome == 'declined' else None),
            'response_message': message,
        }
        canonical = json.dumps(payload, separators=(',', ':'), ensure_ascii=False)
        sig = self.key.sign(canonical.encode(), padding.PKCS1v15(), hashes.SHA256())
        return payload, base64.b64encode(sig).decode()


def edge_card_terminal_result(actor_uid, payload, signature_b64):
    """What the deployed card-terminal-result function does after reading the staff session."""
    if payload['outcome'] == 'approved' and (not payload['terminal_transaction_id'] or not (payload['rrn'] or payload['authorization_code'])):
        raise RpcError('approved result requires transaction id plus RRN or authorization code')
    attempt = sql("select id, terminal_id, created_by from pos_card_terminal_attempts where id=%s", (payload['attempt_id'],), one=True)
    if not attempt:
        raise RpcError('card terminal attempt not found')
    if str(attempt['created_by']) != actor_uid:
        roles = {r['role'] for r in sql("select role::text from staff_roles where user_id=%s", (actor_uid,))}
        if not roles & {'admin', 'finance'}:
            raise RpcError('card terminal attempt access denied')
    key = sql("""select public_key_spki_base64 from pos_card_terminal_device_keys where terminal_id=%s and device_id=%s
                 and is_active and revoked_at is null""", (attempt['terminal_id'], payload['device_id']), one=True)
    if not key:
        raise RpcError('terminal device is not securely paired')
    pub = serialization.load_der_public_key(base64.b64decode(key['public_key_spki_base64']))
    canonical = json.dumps(payload, separators=(',', ':'), ensure_ascii=False)
    try:
        pub.verify(base64.b64decode(signature_b64), canonical.encode(), padding.PKCS1v15(), hashes.SHA256())
    except InvalidSignature:
        raise RpcError('terminal evidence signature rejected') from None
    from sim import conn
    import psycopg2
    with conn.cursor() as cur:
        try:
            cur.execute("select set_config('request.jwt.claims', '{\"role\":\"service_role\"}', true), set_config('role','service_role',true)")
            cur.execute("set local role service_role")
            cur.execute("select record_pos_card_terminal_result(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                        (actor_uid, payload['attempt_id'], payload['outcome'], payload['terminal_transaction_id'], payload['rrn'],
                         payload['authorization_code'], payload['card_last4'], payload['card_scheme'], payload['response_code'],
                         payload['response_message']))
            r = cur.fetchone()[0]
            conn.commit()
            return r
        except psycopg2.Error as e:
            conn.rollback()
            raise RpcError(str(e).split('\n')[0]) from None
