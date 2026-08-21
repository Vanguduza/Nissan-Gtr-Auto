"""Fail-closed payment webhook auth (no network)."""

from __future__ import annotations

import pytest
from fastapi import HTTPException

from app.core.webhook_auth import (
    assert_shared_secret,
    paynow_hash_from_values,
    verify_paynow_form_hash,
)


def test_assert_shared_secret_refuses_when_unset() -> None:
    with pytest.raises(HTTPException) as exc:
        assert_shared_secret(
            provided=None,
            expected="",
            allow_unverified_local=False,
            unset_message="secret required",
        )
    assert exc.value.status_code == 503


def test_assert_shared_secret_local_stub_when_flag() -> None:
    assert_shared_secret(
        provided=None,
        expected="",
        allow_unverified_local=True,
        unset_message="secret required",
    )


def test_assert_shared_secret_rejects_wrong_header() -> None:
    with pytest.raises(HTTPException) as exc:
        assert_shared_secret(
            provided="wrong",
            expected="correct-secret",
            allow_unverified_local=False,
            unset_message="secret required",
        )
    assert exc.value.status_code == 401


def test_assert_shared_secret_accepts_matching_header() -> None:
    assert_shared_secret(
        provided="correct-secret",
        expected="correct-secret",
        allow_unverified_local=False,
        unset_message="secret required",
    )


def test_paynow_form_hash_roundtrip() -> None:
    key = "secret-key"
    values = ["ref-1", "Paid", "12.00"]
    digest = paynow_hash_from_values(values, key)
    raw = f"reference=ref-1&status=Paid&amount=12.00&hash={digest}"
    ok, fields = verify_paynow_form_hash(raw, key)
    assert ok
    assert fields["status"] == "Paid"
    assert not verify_paynow_form_hash(raw, "other-key")[0]
