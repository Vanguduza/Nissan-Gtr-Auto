"""Real HTTP handlers with atomic row-filter semantics and deterministic racing updates."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from threading import Barrier, Lock
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.api.v1 import ecocash_webhook, payment_webhook
from app.core.config import Settings
from app.main import app

PATHS = ["/api/v1/payments/callback", "/api/v1/payments/ecocash/callback"]
PROTECTED = ["PAID", "FULFILLING", "COMPLETED", "CANCELLED", "REFUNDED", "PARTIALLY_REFUNDED"]


class AtomicOrders:
    def __init__(self):
        self.row = {
            "id": "test-order",
            "status": "PENDING",
            "payment_reference": None,
            "payment_source_reference": "source-test",
            "payment_provider": "ecocash",
            "total": 100.0,
            "currency": "USD",
            "settle_currency": "ZIG",
            "settle_total": 2500.0,
            "settle_amount_minor": 250000,
            "ecocash_payer_msisdn": "263771234567",
        }
        self.lock = Lock()
        self.update_barrier = None
        self.before_update = None
        self.updates = []
        self.receipts = []

    def table(self, name):
        assert name == "whatsapp_flow_orders"
        return AtomicOrderQuery(self)


class AtomicOrderQuery:
    def __init__(self, db):
        self.db = db
        self.filters = {}
        self.patch = None

    def select(self, *_args):
        return self

    def limit(self, *_args):
        return self

    def eq(self, key, value):
        self.filters[key] = (value,)
        return self

    def in_(self, key, values):
        self.filters[key] = tuple(values)
        return self

    def is_(self, key, value):
        assert value == "null"
        self.filters[key] = (None,)
        return self

    def update(self, values):
        self.patch = values
        return self

    def execute(self):
        if self.patch is not None:
            # A missing status predicate would reproduce the original race even
            # if an application-side read had checked an old snapshot first.
            assert "status" in self.filters, (
                "payment transition must be conditional in the database"
            )
            if self.db.update_barrier is not None:
                self.db.update_barrier.wait(timeout=10)
        with self.db.lock:
            if self.patch is not None and self.db.before_update is not None:
                hook, self.db.before_update = self.db.before_update, None
                hook(self.db.row)
            matches = all(self.db.row.get(k) in values for k, values in self.filters.items())
            if self.patch is not None:
                self.db.updates.append(
                    {
                        "filters": deepcopy(self.filters),
                        "patch": deepcopy(self.patch),
                        "matched": matches,
                    }
                )
                if matches:
                    self.db.row.update(self.patch)
            return SimpleNamespace(data=[deepcopy(self.db.row)] if matches else [])


@pytest.fixture
def db(monkeypatch):
    db = AtomicOrders()
    settings = Settings(
        _env_file=None,
        environment="production",
        supabase_url="https://project.supabase.co",
        payments_webhook_secret="test-secret",
    )
    for module in (payment_webhook, ecocash_webhook):
        monkeypatch.setattr(module, "get_settings", lambda: settings)
        monkeypatch.setattr(module, "get_supabase", lambda: db)

        async def record_receipt(order):
            with db.lock:
                db.receipts.append(deepcopy(order))

        monkeypatch.setattr(module, "_deliver_receipt", record_receipt)
    db.settings = settings
    return db


def post(path, status, reference="provider-success", **overrides):
    # Separate clients also provide separate request execution threads for races.
    with TestClient(app) as client:
        return client.post(
            path,
            json={
                "order_id": "test-order",
                "status": status,
                "reference": reference,
                "provider": "ecocash",
                "amount": "2500.00",
                "currency": "ZIG",
                "merchant_reference": "source-test",
                "sourceReference": "source-test",
                **overrides,
            },
            headers={"x-payments-webhook-secret": "test-secret"},
        )


@pytest.mark.parametrize("path", PATHS)
@pytest.mark.parametrize("initial", PROTECTED)
@pytest.mark.parametrize("callback_status", ["FAILED", "CANCELLED", "PAID"])
def test_late_or_replayed_callbacks_preserve_terminal_state_and_reference(
    db, path, initial, callback_status
):
    db.row.update(status=initial, payment_reference="settled-ref")
    response = post(
        path, callback_status, "settled-ref" if callback_status == "PAID" else "late-ref"
    )
    assert response.status_code == 200
    assert response.json()["status"] == initial
    assert response.json()["receipt"] is False
    assert db.row["status"] == initial
    assert db.row["payment_reference"] == "settled-ref"
    assert not db.receipts
    assert all(not update["matched"] for update in db.updates)


@pytest.mark.parametrize("path", PATHS)
def test_success_replay_schedules_exactly_one_initial_receipt(db, path):
    first = post(path, "PAID", "first-proof")
    replay = post(path, "PAID", "first-proof")
    assert first.status_code == replay.status_code == 200
    assert first.json()["receipt"] is True
    assert replay.json()["receipt"] is False
    assert db.row["payment_reference"] == "first-proof"
    assert len(db.receipts) == 1
    assert sum(update["matched"] for update in db.updates) == 1


@pytest.mark.parametrize("path", PATHS)
def test_failure_does_not_erase_reference_and_later_success_can_settle(db, path):
    db.row["payment_reference"] = "provider-created"
    failed = post(path, "FAILED", None)
    assert failed.json()["status"] == "FAILED"
    assert db.row["payment_reference"] == "provider-created"
    settled = post(path, "PAID", "provider-created")
    assert settled.json()["receipt"] is True
    assert db.row["status"] == "PAID"
    assert db.row["payment_reference"] == "provider-created"
    assert len(db.receipts) == 1


@pytest.mark.parametrize("path", PATHS)
@pytest.mark.parametrize("status", ["PENDING", "FULFILLING", "REFUNDED", "unknown-provider-state"])
def test_nonpayment_notifications_do_not_author_fulfilment_or_refund_states(db, path, status):
    response = post(path, status)
    assert response.status_code == 200
    assert response.json()["status"] == "PENDING"
    assert not db.updates
    assert not db.receipts


@pytest.mark.parametrize(
    "paths", [(PATHS[0], PATHS[0]), (PATHS[1], PATHS[1]), (PATHS[0], PATHS[1])]
)
def test_concurrent_successes_have_one_atomic_winner_and_one_receipt(db, paths):
    db.update_barrier = Barrier(2)
    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(post, path, "PAID", "same-proof") for path in paths]
        responses = [f.result(timeout=15) for f in futures]
    assert [r.status_code for r in responses] == [200, 200]
    assert sum(r.json()["receipt"] for r in responses) == 1
    assert db.row["status"] == "PAID"
    assert len(db.receipts) == 1
    assert db.row["payment_reference"] == db.receipts[0]["payment_reference"]
    assert sum(update["matched"] for update in db.updates) == 1


@pytest.mark.parametrize(
    "paths", [(PATHS[0], PATHS[0]), (PATHS[1], PATHS[1]), (PATHS[0], PATHS[1])]
)
def test_racing_failure_cannot_overwrite_verified_success(db, paths):
    db.update_barrier = Barrier(2)
    with ThreadPoolExecutor(max_workers=2) as pool:
        success = pool.submit(post, paths[0], "PAID", "settled-ref")
        failure = pool.submit(post, paths[1], "FAILED", None)
        assert (
            success.result(timeout=15).status_code == failure.result(timeout=15).status_code == 200
        )
    assert db.row["status"] == "PAID"
    assert db.row["payment_reference"] == "settled-ref"
    assert len(db.receipts) == 1


def test_ecocash_completed_payment_does_not_mark_order_fulfilled(db):
    response = post(PATHS[1], "COMPLETED")
    assert response.status_code == 200
    assert response.json()["status"] == "PAID"
    assert db.row["status"] == "PAID"
    assert len(db.receipts) == 1


@pytest.mark.parametrize("path", PATHS)
def test_missing_success_reference_preserves_existing_provider_reference(db, path):
    db.row["payment_reference"] = "existing-provider-reference"
    response = post(path, "PAID", None)
    assert response.status_code == 422
    assert db.row["status"] == "PENDING"
    assert db.row["payment_reference"] == "existing-provider-reference"
    assert not db.receipts


@pytest.mark.parametrize("path", PATHS)
@pytest.mark.parametrize("amount", ["0.01", "100.00", "2499.99", "2500.01", "9999.00"])
def test_authenticated_wrong_amount_never_settles_authoritative_d57_amount(db, path, amount):
    response = post(path, "PAID", amount=amount)
    assert response.status_code == 409
    assert db.row["status"] == "PENDING"
    assert db.row["payment_reference"] is None
    assert not db.receipts
    assert not db.updates


@pytest.mark.parametrize("path", PATHS)
@pytest.mark.parametrize(
    "amount", ["NaN", "Infinity", "-Infinity", "0", "-1", "2500.001", "1e999999", True]
)
def test_nonfinite_negative_or_subminor_proofs_are_rejected(db, path, amount):
    response = post(path, "PAID", amount=amount)
    assert response.status_code == 422
    assert db.row["status"] == "PENDING"
    assert not db.receipts


@pytest.mark.parametrize("path", PATHS)
def test_wrong_currency_source_or_registered_provider_reference_is_rejected(db, path):
    for overrides in [
        {"currency": "USD"},
        {"merchant_reference": "peer-source", "sourceReference": "peer-source"},
    ]:
        response = post(path, "PAID", **overrides)
        assert response.status_code == 409
        assert db.row["status"] == "PENDING"
    db.row["payment_reference"] = "registered-provider-proof"
    assert post(path, "PAID", "different-provider-proof").status_code == 409
    assert db.row["payment_reference"] == "registered-provider-proof"
    assert not db.receipts


@pytest.mark.parametrize("path", PATHS)
def test_wrong_rail_cannot_settle_a_different_registered_provider(db, path):
    db.row["payment_provider"] = "paynow"
    assert post(path, "PAID").status_code == 409
    assert db.row["status"] == "PENDING"
    assert not db.receipts


@pytest.mark.parametrize("path", PATHS)
def test_ecocash_usd_snapshot_is_not_a_qualified_d57_rail(db, path):
    db.row.update(settle_currency="USD", settle_total=100, settle_amount_minor=10000)
    assert post(path, "PAID", amount="100.00", currency="USD").status_code == 503
    assert db.row["status"] == "PENDING"
    assert not db.receipts


@pytest.mark.parametrize("path", PATHS)
def test_inconsistent_stored_settlement_fields_fail_closed(db, path):
    db.row["settle_amount_minor"] = 249999
    assert post(path, "PAID").status_code == 503
    assert db.row["status"] == "PENDING"
    assert not db.receipts


def signed_paynow(db, amount="100.00", currency="USD", include_currency=True):
    from urllib.parse import urlencode

    from app.core.webhook_auth import paynow_hash_from_values

    db.settings.paynow_integration_key = "test-key"
    fields = {
        "reference": "test-order",
        "paynowreference": "paynow-registered-proof",
        "status": "Paid",
        "amount": amount,
    }
    if include_currency:
        fields["currency"] = currency
    fields["hash"] = paynow_hash_from_values(fields.values(), "test-key")
    with TestClient(app) as client:
        return client.post(
            PATHS[0],
            content=urlencode(fields),
            headers={"content-type": "application/x-www-form-urlencoded"},
        )


def legacy_paynow_order(db):
    db.row.update(
        payment_provider="paynow",
        payment_source_reference=None,
        settle_currency=None,
        settle_amount_minor=None,
        settle_total=None,
    )


def test_validly_signed_paynow_underpayment_does_not_entitle_order_settlement(db):
    legacy_paynow_order(db)
    response = signed_paynow(db, amount="0.01")
    assert response.status_code == 409
    assert db.row["status"] == "PENDING"
    assert not db.receipts
    assert not db.updates


def test_signed_paynow_exact_legacy_amount_and_currency_can_settle(db):
    legacy_paynow_order(db)
    response = signed_paynow(db)
    assert response.status_code == 200
    assert db.row["status"] == "PAID"
    assert db.row["payment_reference"] == "paynow-registered-proof"
    assert len(db.receipts) == 1


def test_paynow_without_signed_currency_requires_explicit_merchant_binding(db):
    legacy_paynow_order(db)
    assert signed_paynow(db, include_currency=False).status_code == 503
    assert db.row["status"] == "PENDING"
    db.settings.paynow_settlement_currency = "USD"
    assert signed_paynow(db, include_currency=False).status_code == 200
    assert db.row["status"] == "PAID"


def test_signed_paynow_wrong_currency_or_provider_does_not_settle(db):
    legacy_paynow_order(db)
    assert signed_paynow(db, currency="ZIG").status_code == 409
    db.row["payment_provider"] = "contipay"
    assert signed_paynow(db).status_code == 409
    assert db.row["status"] == "PENDING"
    assert not db.receipts


def test_stored_financial_snapshot_change_cannot_race_valid_proof(db):
    # An independent worker reprices the still-pending order after the validation
    # read but before this callback reaches its conditional database update.
    db.before_update = lambda row: row.update(settle_total=2600, settle_amount_minor=260000)
    response = post(PATHS[0], "PAID")
    assert response.status_code == 409
    assert db.row["status"] == "PENDING"
    assert not db.receipts
    assert all(not update["matched"] for update in db.updates)
