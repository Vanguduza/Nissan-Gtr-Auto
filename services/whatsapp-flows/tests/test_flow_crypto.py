"""Unit tests for Meta Flow AES-GCM / RSA crypto (no network)."""

from __future__ import annotations

import base64
import json

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from app.core.flow_crypto import FlowCrypto


def _keypair_pem() -> bytes:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )


def test_flow_crypto_roundtrip() -> None:
    pem = _keypair_pem()
    crypto = FlowCrypto(pem)
    private_key = serialization.load_pem_private_key(pem, password=None)
    public_key = private_key.public_key()

    aes_key = AESGCM.generate_key(bit_length=128)
    iv = b"\x01" * 16
    clear = {"action": "ping", "version": "3.0", "data": {}}
    sealed = AESGCM(aes_key).encrypt(iv, json.dumps(clear).encode(), None)

    enc_aes = public_key.encrypt(
        aes_key,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )

    payload, out_key, out_iv = crypto.decrypt_request(
        encrypted_aes_key_b64=base64.b64encode(enc_aes).decode(),
        encrypted_flow_data_b64=base64.b64encode(sealed).decode(),
        initial_vector_b64=base64.b64encode(iv).decode(),
    )
    assert payload["action"] == "ping"
    assert out_key == aes_key
    assert out_iv == iv

    response = {"data": {"status": "active"}}
    b64 = crypto.encrypt_response(response, aes_key=aes_key, request_iv=iv)
    raw = base64.b64decode(b64)
    inverted = bytes(b ^ 0xFF for b in iv)
    opened = AESGCM(aes_key).decrypt(inverted, raw, None)
    assert json.loads(opened.decode()) == response


def test_cart_summary_text_empty() -> None:
    from app.services.cart_service import CartQuote

    q = CartQuote()
    assert "empty" in q.summary_text().lower()
