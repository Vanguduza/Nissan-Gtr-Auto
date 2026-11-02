"""Callback authentication and production refusal, with no provider/database calls."""

from __future__ import annotations

import asyncio
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.api.v1 import ecocash_webhook, payment_webhook
from app.core.config import Settings
from app.core.webhook_auth import paynow_hash_from_values
from app.main import app
from app.services.ecocash_client import EcoCashClient, EcoCashError
from app.services.meta_client import MetaClient, MetaClientError


@pytest.fixture
def setup(monkeypatch):
    settings = Settings(
        _env_file=None, environment="production", supabase_url="https://project.supabase.co"
    )
    marked = []
    monkeypatch.setattr(payment_webhook, "get_settings", lambda: settings)
    monkeypatch.setattr(ecocash_webhook, "get_settings", lambda: settings)

    def mark_paid(order_id, reference, proof):
        marked.append((order_id, reference))
        return {"id": order_id, "status": "PAID"}, True

    async def no_receipt(_order):
        return None

    def refuse_database():
        raise AssertionError("unauthorized request reached database")

    monkeypatch.setattr(payment_webhook, "_mark_paid", mark_paid)
    monkeypatch.setattr(payment_webhook, "_deliver_receipt", no_receipt)
    monkeypatch.setattr(ecocash_webhook, "_mark_paid", mark_paid)
    monkeypatch.setattr(ecocash_webhook, "_deliver_receipt", no_receipt)
    monkeypatch.setattr(payment_webhook, "get_supabase", refuse_database)
    monkeypatch.setattr(ecocash_webhook, "get_supabase", refuse_database)
    return SimpleNamespace(settings=settings, marked=marked, client=TestClient(app))


@pytest.mark.parametrize(
    "path",
    [
        "/api/v1/payments/callback",
        "/api/v1/payments/ecocash/callback",
        "/api/v1/payments/ecocash/push",
        "/api/v1/payments/ecocash/lookup",
    ],
)
def test_unsigned_requests_fail_before_database(setup, path):
    response = setup.client.post(path, json={"order_id": "test-order", "status": "PAID"})
    assert response.status_code == 503
    assert not setup.marked


@pytest.mark.parametrize(
    "path",
    [
        "/api/v1/payments/callback",
        "/api/v1/payments/ecocash/callback",
        "/api/v1/payments/ecocash/push",
        "/api/v1/payments/ecocash/lookup",
    ],
)
def test_wrong_configured_secret_fails_before_database(setup, path):
    setup.settings.payments_webhook_secret = "test-server-secret"
    response = setup.client.post(
        path,
        json={"order_id": "test-order", "status": "PAID"},
        headers={"x-payments-webhook-secret": "incorrect"},
    )
    assert response.status_code == 401
    assert not setup.marked


def test_authenticated_generic_callback_marks_paid(setup):
    setup.settings.payments_webhook_secret = "test-server-secret"
    response = setup.client.post(
        "/api/v1/payments/callback",
        json={
            "order_id": "test-order",
            "status": "PAID",
            "reference": "provider-test",
            "provider": "paynow",
            "amount": "12.00",
            "currency": "USD",
            "merchant_reference": "test-order",
        },
        headers={"x-payments-webhook-secret": "test-server-secret"},
    )
    assert response.status_code == 200
    assert setup.marked == [("test-order", "provider-test")]


def test_verified_paynow_form_callback_and_tamper_rejection(setup):
    setup.settings.paynow_integration_key = "test-paynow-key"
    digest = paynow_hash_from_values(
        ["test-order", "Paid", "12.00", "USD", "provider-test"], "test-paynow-key"
    )
    raw = f"reference=test-order&status=Paid&amount=12.00&currency=USD&paynowreference=provider-test&hash={digest}"
    response = setup.client.post(
        "/api/v1/payments/callback",
        content=raw,
        headers={"content-type": "application/x-www-form-urlencoded"},
    )
    assert response.status_code == 200
    assert setup.marked == [("test-order", "provider-test")]
    setup.marked.clear()
    response = setup.client.post(
        "/api/v1/payments/callback",
        content=raw.replace("test-order", "other-order"),
        headers={"content-type": "application/x-www-form-urlencoded"},
    )
    assert response.status_code == 401
    assert not setup.marked


@pytest.mark.parametrize(
    "environment,url",
    [
        ("production", "http://127.0.0.1:54321"),
        ("staging", "http://127.0.0.1:54321"),
        ("development", "https://project.supabase.co"),
        ("development", "https://localhost.example"),
        ("development", "https://example.test:54321"),
    ],
)
def test_local_flags_do_not_bypass_auth_outside_local_development(setup, environment, url):
    setup.settings.environment = environment
    setup.settings.supabase_url = url
    setup.settings.payments_allow_unverified_local = True
    setup.settings.ecocash_allow_unverified_local = True
    for path in ["/api/v1/payments/callback", "/api/v1/payments/ecocash/callback"]:
        assert (
            setup.client.post(path, json={"order_id": "test-order", "status": "PAID"}).status_code
            == 503
        )
    assert not setup.marked


def test_explicit_local_callback_stub_remains_available(setup):
    setup.settings.environment = "development"
    setup.settings.supabase_url = "http://127.0.0.1:54321"
    setup.settings.payments_allow_unverified_local = True
    assert (
        setup.client.post(
            "/api/v1/payments/callback",
            json={
                "order_id": "test-order",
                "status": "PAID",
                "provider": "paynow",
                "amount": "12.00",
                "currency": "USD",
                "merchant_reference": "test-order",
                "reference": "provider-test",
            },
        ).status_code
        == 200
    )
    assert setup.marked


def test_ecocash_configured_callback_reaches_payment_only_after_auth(setup, monkeypatch):
    setup.settings.ecocash_webhook_secret = "test-ecocash-secret"

    class OrderQuery:
        def table(self, *_args):
            return self

        def select(self, *_args):
            return self

        def eq(self, *_args):
            return self

        def limit(self, *_args):
            return self

        def execute(self):
            return SimpleNamespace(data=[{"id": "test-order", "status": "PENDING"}])

    monkeypatch.setattr(ecocash_webhook, "get_supabase", lambda: OrderQuery())
    response = setup.client.post(
        "/api/v1/payments/ecocash/callback",
        json={
            "order_id": "test-order",
            "status": "success",
            "reference": "provider-test",
            "sourceReference": "source-test",
            "amount": "12.00",
            "currency": "ZIG",
        },
        headers={"x-ecocash-signature": "test-ecocash-secret"},
    )
    assert response.status_code == 200
    assert setup.marked == [("test-order", "provider-test")]


@pytest.mark.parametrize(
    "environment,url",
    [
        ("production", "http://127.0.0.1:54321"),
        ("development", "https://project.supabase.co"),
    ],
)
def test_provider_and_meta_stubs_are_refused_outside_local(environment, url):
    settings = Settings(
        _env_file=None,
        environment=environment,
        supabase_url=url,
        ecocash_api_key="",
        ecocash_allow_stub=True,
        allow_payment_stub=True,
        whatsapp_access_token="",
        whatsapp_phone_number_id="",
        whatsapp_allow_unverified_local=True,
    )
    with pytest.raises(EcoCashError, match="ECOCASH_API_KEY"):
        asyncio.run(
            EcoCashClient(settings).initiate_c2b(
                customer_phone="0771234567", amount=10, currency="USD", reason="test"
            )
        )
    with pytest.raises(EcoCashError, match="ECOCASH_API_KEY"):
        asyncio.run(
            EcoCashClient(settings).lookup_transaction(
                source_mobile_number="0771234567", source_reference="test"
            )
        )
    with pytest.raises(MetaClientError):
        asyncio.run(MetaClient(settings).send_text("0771234567", "test"))
    with pytest.raises(MetaClientError):
        asyncio.run(MetaClient(settings).upload_pdf(b"synthetic-pdf"))


def test_missing_environment_configuration_never_enables_local_stubs(monkeypatch):
    monkeypatch.delenv("ENVIRONMENT", raising=False)
    settings = Settings(
        _env_file=None,
        supabase_url="http://127.0.0.1:54321",
        payments_allow_unverified_local=True,
        ecocash_allow_stub=True,
    )
    assert not settings.is_local_development
