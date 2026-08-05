"""Resolve EcoCash payer MSISDN — WhatsApp / saved profile / other entered number."""

from __future__ import annotations

from typing import Literal

from app.core.supabase_client import get_supabase
from app.services.ecocash_client import EcoCashError, normalize_ecocash_msisdn

EcoCashPayerMode = Literal["whatsapp", "saved", "other"]


def lookup_saved_ecocash_msisdn(wa_id: str | None) -> str | None:
    """Best-effort: match customers.phone_e164 / whatsapp_e164 to the WhatsApp MSISDN."""
    if not wa_id:
        return None
    try:
        digits = normalize_ecocash_msisdn(str(wa_id))
    except EcoCashError:
        return None

    sb = get_supabase()
    # Match common storage shapes: 263…, +263…, 07…
    local = "0" + digits[3:]
    plus = "+" + digits
    for col in ("phone_e164", "whatsapp_e164"):
        for candidate in (digits, plus, local):
            rows = (
                sb.table("customers")
                .select(f"id,{col}")
                .eq(col, candidate)
                .limit(1)
                .execute()
            )
            if rows.data:
                raw = rows.data[0].get(col)
                if raw:
                    try:
                        return normalize_ecocash_msisdn(str(raw))
                    except EcoCashError:
                        continue
    return None


def resolve_ecocash_payer(
    *,
    mode: str | None,
    wa_id: str | None,
    entered_msisdn: str | None,
) -> tuple[EcoCashPayerMode, str]:
    """Return (mode, normalized 263… payer). Raises EcoCashError / ValueError on bad input."""
    raw_mode = (mode or "").strip().lower()
    if raw_mode not in ("whatsapp", "saved", "other"):
        raise ValueError(
            "Choose EcoCash number: use WhatsApp number, saved number, or enter another.",
        )

    mode_t: EcoCashPayerMode = raw_mode  # type: ignore[assignment]

    if mode_t == "other":
        if not entered_msisdn or not str(entered_msisdn).strip():
            raise ValueError("Enter the EcoCash number to charge.")
        return mode_t, normalize_ecocash_msisdn(str(entered_msisdn))

    if mode_t == "saved":
        saved = lookup_saved_ecocash_msisdn(wa_id)
        if saved:
            return mode_t, saved
        # Fall through messaging: no saved → require explicit other or whatsapp
        if wa_id:
            # Soft fallback only if profile missing — still explicit whatsapp wallet
            return "whatsapp", normalize_ecocash_msisdn(str(wa_id))
        raise ValueError(
            "No saved EcoCash/profile number found. Enter a different EcoCash number.",
        )

    # whatsapp
    if not wa_id:
        raise ValueError(
            "WhatsApp number unknown. Enter your EcoCash number to continue.",
        )
    return mode_t, normalize_ecocash_msisdn(str(wa_id))


def payer_choice_summary(mode: EcoCashPayerMode, msisdn: str) -> str:
    labels = {
        "whatsapp": "WhatsApp number",
        "saved": "saved / profile number",
        "other": "different EcoCash number",
    }
    masked = msisdn[:5] + "****" + msisdn[-2:] if len(msisdn) >= 9 else msisdn
    return f"Charging {labels.get(mode, mode)} ({masked})"
