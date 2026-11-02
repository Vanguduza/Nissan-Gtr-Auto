"""Meta raw-body authentication precedes RSA decryption and all checkout effects."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json

import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from fastapi.testclient import TestClient

from app.api.v1 import flow_endpoint
from app.core.config import Settings
from app.core.flow_crypto import FlowCrypto
from app.main import app


@pytest.fixture(scope="module")
def encrypted_flow():
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = private.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    )
    aes_key = AESGCM.generate_key(bit_length=128)
    iv = b"\x07" * 16
    # This protocol can be constructed using the public key only; HMAC authenticates the sender.
    sealed = AESGCM(aes_key).encrypt(iv, json.dumps({"action": "ping"}).encode(), None)
    wrapped = private.public_key().encrypt(
        aes_key,
        padding.OAEP(mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None),
    )
    body = json.dumps(
        {
            "encrypted_aes_key": base64.b64encode(wrapped).decode(),
            "encrypted_flow_data": base64.b64encode(sealed).decode(),
            "initial_vector": base64.b64encode(iv).decode(),
        }
    ).encode()
    return FlowCrypto(pem), body, aes_key, iv


def setup(monkeypatch, encrypted_flow, **overrides):
    settings = Settings(
        _env_file=None,
        environment="production",
        supabase_url="https://project.supabase.co",
        whatsapp_app_secret="test-meta-app-secret",
    )
    for name, value in overrides.items():
        setattr(settings, name, value)
    monkeypatch.setattr(flow_endpoint, "get_settings", lambda: settings)
    monkeypatch.setattr(flow_endpoint, "_crypto", lambda: encrypted_flow[0])
    return settings


def test_valid_meta_signature_and_real_encryption_round_trip(monkeypatch, encrypted_flow):
    settings = setup(monkeypatch, encrypted_flow)
    _, body, key, iv = encrypted_flow
    signature = (
        "sha256="
        + hmac.new(settings.whatsapp_app_secret.encode(), body, hashlib.sha256).hexdigest()
    )
    response = TestClient(app).post(
        "/api/v1/whatsapp/flow",
        content=body,
        headers={"content-type": "application/json", "x-hub-signature-256": signature},
    )
    assert response.status_code == 200
    plaintext = AESGCM(key).decrypt(
        bytes(b ^ 0xFF for b in iv), base64.b64decode(response.text), None
    )
    assert json.loads(plaintext) == {"data": {"status": "active"}}


@pytest.mark.parametrize(
    "header,alter_body",
    [(None, False), ("sha256=" + "0" * 64, False), ("valid-before-tamper", True)],
)
def test_unsigned_forged_or_tampered_requests_rejected_before_decrypt(
    monkeypatch, encrypted_flow, header, alter_body
):
    settings = setup(monkeypatch, encrypted_flow)
    body = encrypted_flow[1]
    if header == "valid-before-tamper":
        header = (
            "sha256="
            + hmac.new(settings.whatsapp_app_secret.encode(), body, hashlib.sha256).hexdigest()
        )
    if alter_body:
        body += b" "

    def forbidden_crypto():
        raise AssertionError("unauthenticated Flow request reached RSA/business processing")

    monkeypatch.setattr(flow_endpoint, "_crypto", forbidden_crypto)
    headers = {"content-type": "application/json"}
    if header is not None:
        headers["x-hub-signature-256"] = header
    response = TestClient(app).post("/api/v1/whatsapp/flow", content=body, headers=headers)
    assert response.status_code == 432


@pytest.mark.parametrize(
    "environment,url",
    [("production", "http://127.0.0.1:54321"), ("development", "https://project.supabase.co")],
)
def test_flow_missing_app_secret_fails_closed_even_with_local_flag(
    monkeypatch, encrypted_flow, environment, url
):
    setup(
        monkeypatch,
        encrypted_flow,
        environment=environment,
        supabase_url=url,
        whatsapp_app_secret="",
        whatsapp_allow_unverified_local=True,
    )
    monkeypatch.setattr(flow_endpoint, "_crypto", lambda: pytest.fail("must reject before decrypt"))
    response = TestClient(app).post(
        "/api/v1/whatsapp/flow",
        content=encrypted_flow[1],
        headers={"content-type": "application/json"},
    )
    assert response.status_code == 503


def test_explicit_local_flow_stub_can_decrypt_without_meta_secret(monkeypatch, encrypted_flow):
    setup(
        monkeypatch,
        encrypted_flow,
        environment="development",
        supabase_url="http://127.0.0.1:54321",
        whatsapp_app_secret="",
        whatsapp_allow_unverified_local=True,
    )
    response = TestClient(app).post(
        "/api/v1/whatsapp/flow",
        content=encrypted_flow[1],
        headers={"content-type": "application/json"},
    )
    assert response.status_code == 200
