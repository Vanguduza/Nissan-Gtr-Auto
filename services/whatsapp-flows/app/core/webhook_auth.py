"""Fail-closed auth for WhatsApp Flows payment settle endpoints.

Mirrors Edge ContiPay/Paynow/EcoCash/worker stubs:
- Secret required in production
- Local unverified only behind an explicit allow flag when secret is unset
"""

from __future__ import annotations

import hashlib
import hmac
import logging
from typing import Iterable
from urllib.parse import unquote_plus

from fastapi import HTTPException, Request

logger = logging.getLogger(__name__)


def timing_safe_equal(a: str, b: str) -> bool:
    """Constant-time string compare (length mismatch → False)."""
    try:
        return hmac.compare_digest(a.encode("utf-8"), b.encode("utf-8"))
    except Exception:  # noqa: BLE001
        return False


def assert_shared_secret(
    *,
    provided: str | None,
    expected: str,
    allow_unverified_local: bool,
    unset_message: str,
) -> None:
    """Refuse when secret unset (unless local flag) or header missing/wrong."""
    secret = (expected or "").strip()
    if not secret:
        if allow_unverified_local:
            logger.warning("payments webhook: unverified local stub (allow flag set)")
            return
        raise HTTPException(status_code=503, detail=unset_message)
    got = (provided or "").strip()
    if not got or not timing_safe_equal(got, secret):
        raise HTTPException(status_code=401, detail="invalid or missing webhook signature")


def header_secret(request: Request, *names: str) -> str | None:
    for name in names:
        val = request.headers.get(name)
        if val and val.strip():
            return val.strip()
    return None


def paynow_hash_from_values(values: Iterable[str], integration_key: str) -> str:
    """SHA512 uppercase hex over concatenated field values + integration key."""
    concat = "".join(values) + integration_key
    return hashlib.sha512(concat.encode("utf-8")).hexdigest().upper()


def parse_paynow_form(raw: str) -> tuple[dict[str, str], list[str]]:
    """Parse form-urlencoded Paynow body → lowercased fields + ordered values (excl. hash)."""
    fields: dict[str, str] = {}
    ordered: list[str] = []
    trimmed = (raw or "").strip()
    if not trimmed:
        return fields, ordered
    for part in trimmed.split("&"):
        if not part:
            continue
        eq = part.find("=")
        raw_key = part if eq == -1 else part[:eq]
        raw_val = "" if eq == -1 else part[eq + 1 :]
        key = unquote_plus(raw_key.replace("+", " "))
        val = unquote_plus(raw_val.replace("+", " "))
        fields[key.lower()] = val
        if key.lower() != "hash":
            ordered.append(val)
    return fields, ordered


def verify_paynow_form_hash(raw: str, integration_key: str) -> tuple[bool, dict[str, str]]:
    fields, ordered = parse_paynow_form(raw)
    provided = (fields.get("hash") or "").strip()
    if not provided or not integration_key.strip():
        return False, fields
    expected = paynow_hash_from_values(ordered, integration_key.strip())
    return timing_safe_equal(expected.upper(), provided.upper()), fields
