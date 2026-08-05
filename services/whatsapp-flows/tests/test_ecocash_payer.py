"""EcoCash payer mode resolution (WhatsApp vs other)."""

from __future__ import annotations

import pytest

from app.services.ecocash_client import EcoCashError
from app.services.ecocash_payer import resolve_ecocash_payer


def test_whatsapp_mode_uses_wa_id() -> None:
    mode, msisdn = resolve_ecocash_payer(
        mode="whatsapp",
        wa_id="263771234567",
        entered_msisdn=None,
    )
    assert mode == "whatsapp"
    assert msisdn == "263771234567"


def test_other_mode_requires_entered() -> None:
    with pytest.raises(ValueError, match="Enter the EcoCash"):
        resolve_ecocash_payer(mode="other", wa_id="263771234567", entered_msisdn=None)


def test_other_mode_normalizes_local() -> None:
    mode, msisdn = resolve_ecocash_payer(
        mode="other",
        wa_id="263771111111",
        entered_msisdn="0779998877",
    )
    assert mode == "other"
    assert msisdn == "263779998877"


def test_missing_mode_rejected() -> None:
    with pytest.raises(ValueError, match="Choose EcoCash"):
        resolve_ecocash_payer(mode="", wa_id="263771234567", entered_msisdn=None)


def test_invalid_other_msisdn() -> None:
    with pytest.raises(EcoCashError):
        resolve_ecocash_payer(mode="other", wa_id="263771234567", entered_msisdn="12")
