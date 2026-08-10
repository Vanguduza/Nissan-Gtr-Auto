"""EcoCash Instant Payments (C2B) — direct merchant API (no Paynow/ContiPay).

Official portal: https://developers.ecocash.co.zw/
After merchant + online-merchant approval, set ECOCASH_API_KEY (+ env) and go live.

Customer authorises on-handset (PIN / USSD prompt). This client only calls HTTP APIs.
Paths are overridable via env so portal URL changes do not require code edits.
"""

from __future__ import annotations

import logging
import re
import uuid
from dataclasses import dataclass
from typing import Any

import httpx

from app.core.config import Settings, get_settings

logger = logging.getLogger(__name__)


class EcoCashError(Exception):
    def __init__(self, message: str, *, status: int | None = None, body: str | None = None):
        super().__init__(message)
        self.status = status
        self.body = body


@dataclass
class EcoCashChargeResult:
    ok: bool
    source_reference: str
    provider_reference: str | None
    status: str
    raw: dict[str, Any]
    stub: bool = False


def normalize_ecocash_msisdn(phone: str) -> str:
    """Normalize to 263XXXXXXXXX (EcoCash expects Zimbabwe MSISDN without +)."""
    digits = re.sub(r"\D", "", phone or "")
    if digits.startswith("0") and len(digits) == 10:
        digits = "263" + digits[1:]
    elif digits.startswith("263"):
        pass
    elif len(digits) == 9 and digits[0] in "77":  # 77/78 EcoCash-ish
        digits = "263" + digits
    if not re.fullmatch(r"263\d{9}", digits):
        raise EcoCashError(f"invalid EcoCash MSISDN: {phone!r} → {digits!r}")
    return digits


class EcoCashClient:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()

    @property
    def configured(self) -> bool:
        return bool(self.settings.ecocash_api_key.strip())

    def _headers(self) -> dict[str, str]:
        key = self.settings.ecocash_api_key.strip()
        # Portal SDKs use X-API-KEY; some merchants receive Bearer — both supported via env.
        mode = (self.settings.ecocash_auth_header or "x-api-key").strip().lower()
        if mode in ("bearer", "authorization"):
            return {
                "Authorization": f"Bearer {key}",
                "Content-Type": "application/json",
                "Accept": "application/json",
            }
        return {
            "X-API-KEY": key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    def _c2b_url(self) -> str:
        base = self.settings.ecocash_api_base_url.rstrip("/")
        path = (
            self.settings.ecocash_c2b_path_live
            if self.settings.ecocash_environment == "live"
            else self.settings.ecocash_c2b_path_sandbox
        )
        if not path.startswith("/"):
            path = "/" + path
        return f"{base}{path}"

    def _lookup_url(self) -> str:
        base = self.settings.ecocash_api_base_url.rstrip("/")
        path = self.settings.ecocash_lookup_path
        if not path.startswith("/"):
            path = "/" + path
        return f"{base}{path}"

    async def initiate_c2b(
        self,
        *,
        customer_phone: str,
        amount: float,
        currency: str,
        reason: str,
        source_reference: str | None = None,
    ) -> EcoCashChargeResult:
        """Push a C2B charge — customer gets EcoCash PIN / USSD prompt on handset."""
        msisdn = normalize_ecocash_msisdn(customer_phone)
        source_reference = source_reference or str(uuid.uuid4())
        payload = {
            "customerEcocashPhoneNumber": msisdn,
            "amount": round(float(amount), 2),
            "reason": reason[:50],
            "currency": currency,
            "sourceReference": source_reference,
        }

        if not self.configured:
            if self.settings.ecocash_allow_stub or self.settings.allow_payment_stub:
                logger.warning(
                    "EcoCash stub C2B (no ECOCASH_API_KEY): ref=%s msisdn=%s amount=%s %s",
                    source_reference,
                    msisdn,
                    amount,
                    currency,
                )
                return EcoCashChargeResult(
                    ok=True,
                    source_reference=source_reference,
                    provider_reference=f"stub-{source_reference[:8]}",
                    status="PENDING_CUSTOMER",
                    raw={"stub": True, "request": payload},
                    stub=True,
                )
            raise EcoCashError("ECOCASH_API_KEY unset — register at developers.ecocash.co.zw")

        async with httpx.AsyncClient(timeout=45.0) as client:
            res = await client.post(
                self._c2b_url(),
                headers=self._headers(),
                json=payload,
            )
        text = res.text
        try:
            body: dict[str, Any] = res.json() if text else {}
        except Exception:  # noqa: BLE001
            body = {"raw": text}

        if res.status_code >= 400:
            raise EcoCashError(
                f"EcoCash C2B HTTP {res.status_code}: {text[:400]}",
                status=res.status_code,
                body=text,
            )

        provider_ref = (
            body.get("ecocashReference")
            or body.get("transactionReference")
            or body.get("reference")
            or body.get("id")
        )
        status = str(
            body.get("transactionStatus")
            or body.get("status")
            or body.get("message")
            or "PENDING_CUSTOMER",
        )
        return EcoCashChargeResult(
            ok=True,
            source_reference=source_reference,
            provider_reference=str(provider_ref) if provider_ref else None,
            status=status,
            raw=body,
            stub=False,
        )

    async def lookup_transaction(
        self,
        *,
        source_mobile_number: str,
        source_reference: str,
    ) -> dict[str, Any]:
        """Poll EcoCash for final status after customer PIN entry."""
        msisdn = normalize_ecocash_msisdn(source_mobile_number)
        if not self.configured:
            if self.settings.ecocash_allow_stub or self.settings.allow_payment_stub:
                return {
                    "stub": True,
                    "sourceReference": source_reference,
                    "status": "PENDING",
                }
            raise EcoCashError("ECOCASH_API_KEY unset")

        payload = {
            "sourceMobileNumber": msisdn,
            "sourceReference": source_reference,
        }
        async with httpx.AsyncClient(timeout=45.0) as client:
            res = await client.post(
                self._lookup_url(),
                headers=self._headers(),
                json=payload,
            )
        text = res.text
        try:
            body: dict[str, Any] = res.json() if text else {}
        except Exception:  # noqa: BLE001
            body = {"raw": text}
        if res.status_code >= 400:
            raise EcoCashError(
                f"EcoCash lookup HTTP {res.status_code}: {text[:400]}",
                status=res.status_code,
                body=text,
            )
        return body


def ecocash_status_is_paid(status: str | None, body: dict[str, Any] | None = None) -> bool:
    """Map EcoCash status strings / webhook payloads to PAID."""
    candidates: list[str] = []
    if status:
        candidates.append(status)
    if body:
        for key in (
            "transactionStatus",
            "status",
            "paymentStatus",
            "result",
            "message",
        ):
            val = body.get(key)
            if isinstance(val, str):
                candidates.append(val)
    paid_tokens = {
        "paid",
        "success",
        "successful",
        "completed",
        "complete",
        "approved",
        "ok",
        "00",
    }
    for c in candidates:
        norm = c.strip().lower().replace(" ", "_")
        if norm in paid_tokens or "success" in norm or norm.endswith("_ok"):
            return True
    return False
