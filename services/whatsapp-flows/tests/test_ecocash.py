"""EcoCash MSISDN + stub C2B (no network)."""

from __future__ import annotations

import asyncio

import pytest

from app.services.ecocash_client import (
    EcoCashClient,
    EcoCashError,
    ecocash_status_is_paid,
    normalize_ecocash_msisdn,
)


def test_normalize_msisdn_local_and_e164() -> None:
    assert normalize_ecocash_msisdn("0771234567") == "263771234567"
    assert normalize_ecocash_msisdn("+263771234567") == "263771234567"
    assert normalize_ecocash_msisdn("263771234567") == "263771234567"


def test_normalize_rejects_bad() -> None:
    with pytest.raises(EcoCashError):
        normalize_ecocash_msisdn("123")


def test_stub_c2b_when_no_api_key() -> None:
    from app.core.config import Settings

    settings = Settings(
        ecocash_api_key="",
        ecocash_allow_stub=True,
        allow_payment_stub=True,
    )
    client = EcoCashClient(settings)
    result = asyncio.run(
        client.initiate_c2b(
            customer_phone="0771234567",
            amount=12.5,
            currency="USD",
            reason="test",
            source_reference="src-1",
        ),
    )
    assert result.ok and result.stub
    assert result.source_reference == "src-1"


def test_paid_status_mapping() -> None:
    assert ecocash_status_is_paid("SUCCESS")
    assert ecocash_status_is_paid("successful")
    assert ecocash_status_is_paid("00")
    assert ecocash_status_is_paid(None, {"transactionStatus": "Completed"})
    assert not ecocash_status_is_paid("FAILED")
    # Substring traps that previously false-settled as paid
    assert not ecocash_status_is_paid("unsuccessful")
    assert not ecocash_status_is_paid("unpaid")
    assert not ecocash_status_is_paid("not_ok")
    assert not ecocash_status_is_paid("not_paid")
    assert not ecocash_status_is_paid("not_approved")
