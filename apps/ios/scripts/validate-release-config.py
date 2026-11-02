#!/usr/bin/env python3
"""Fail a Release build before packaging a fake or wrong-project customer app.

Only public client credentials belong in these build settings. This validates the
configuration class; real Auth/provider acceptance remains a separate live check.
"""
import base64
import json
import math
import os
import re
import time
from urllib.parse import urlsplit

PRODUCTION_HOST = "bicyjghgdnzlnjqxzoud.supabase.co"


def validate(settings: dict[str, str]) -> None:
    if settings.get("STOREFRONT_FORCE_FAKE", "").strip().lower() in {"1", "true", "yes", "on"}:
        raise ValueError("Release builds cannot enable fake storefront data")
    url = urlsplit(settings.get("SUPABASE_URL", "").strip())
    if (url.scheme != "https" or url.hostname != PRODUCTION_HOST or url.port is not None
            or url.username is not None or url.password is not None or url.query or url.fragment
            or url.path not in {"", "/"}):
        raise ValueError("Release requires the approved HTTPS Supabase project")
    key = settings.get("SUPABASE_ANON_KEY", "").strip()
    if key.startswith("sb_publishable_"):
        if re.fullmatch(r"sb_publishable_[A-Za-z0-9_-]{20,}", key):
            return
        raise ValueError("Release requires a valid public publishable key shape")
    try:
        parts = key.split(".")
        if len(parts) != 3:
            raise ValueError("not a JWT")
        payload = json.loads(base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)))
        if (isinstance(payload, dict) and payload.get("role") == "anon"
                and payload.get("ref") == PRODUCTION_HOST.split(".")[0]
                and type(payload.get("exp")) in (int, float)
                and math.isfinite(payload["exp"])
                and payload["exp"] > time.time()):
            return
    except (ValueError, TypeError, UnicodeError):
        pass
    raise ValueError("Release requires a public publishable/anon key; server credentials are forbidden")


if __name__ == "__main__":
    try:
        validate(dict(os.environ))
    except (ValueError, TypeError) as exc:
        raise SystemExit(f"BLOCKED: {exc}")
    print("iOS release configuration verified (public client configuration only)")
