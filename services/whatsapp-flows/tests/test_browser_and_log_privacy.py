"""The server callback surface grants no browser origin or payment-log access."""

import asyncio
import logging

from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import app
from app.services.ecocash_client import EcoCashClient


def test_health_response_does_not_grant_cross_origin_browser_access():
    with TestClient(app) as client:
        response = client.get("/health", headers={"origin": "https://untrusted.example"})
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
    assert "access-control-allow-origin" not in response.headers
    assert "access-control-allow-credentials" not in response.headers


def test_payment_callback_does_not_authorize_browser_preflight():
    with TestClient(app) as client:
        response = client.options(
            "/api/v1/payments/callback",
            headers={
                "origin": "https://untrusted.example",
                "access-control-request-method": "POST",
                "access-control-request-headers": "x-payments-webhook-secret",
            },
        )
    assert response.status_code == 405
    assert "access-control-allow-origin" not in response.headers


def test_local_payment_stub_does_not_log_payer_or_payment_details(caplog):
    settings = Settings(
        _env_file=None,
        environment="development",
        supabase_url="http://127.0.0.1:54321",
        ecocash_api_key="",
        ecocash_allow_stub=True,
    )
    with caplog.at_level(logging.WARNING, logger="app.services.ecocash_client"):
        result = asyncio.run(
            EcoCashClient(settings).initiate_c2b(
                customer_phone="0771234567",
                amount=12.34,
                currency="USD",
                reason="Synthetic privacy regression",
                source_reference="synthetic-private-payment-reference",
            )
        )
    assert result.stub is True
    assert result.raw["request"]["customerEcocashPhoneNumber"] == "263771234567"
    assert "EcoCash local stub requested" in caplog.text
    for sensitive in ("0771234567", "263771234567", "synthetic-private-payment-reference", "12.34"):
        assert sensitive not in caplog.text
