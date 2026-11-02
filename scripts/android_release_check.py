#!/usr/bin/env python3
"""Fail closed before Android release packaging; never print configuration values."""

from __future__ import annotations

import argparse
import base64
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.parse import urlparse

CANONICAL_SUPABASE_HOST = "bicyjghgdnzlnjqxzoud.supabase.co"
# https://developer.android.com/google/play/requirements/target-sdk (2026-10-28)
MINIMUM_STORE_TARGET = 36
APPLICATIONS = {"customer-android", "management-android", "delivery-android"}
SIGNING_VARIABLES = (
    "GTR_ANDROID_KEYSTORE_PATH",
    "GTR_ANDROID_STORE_PASSWORD",
    "GTR_ANDROID_KEY_ALIAS",
    "GTR_ANDROID_KEY_PASSWORD",
)


def public_key_valid(key: str) -> bool:
    """Accept publishable keys or legacy anon JWTs, never a secret/service token."""
    if re.fullmatch(r"sb_publishable_[A-Za-z0-9_-]{20,}", key):
        return not any(part in key.lower() for part in ("example", "placeholder", "your_key"))
    try:
        header, payload, signature = key.split(".")
        if not header or not signature:
            return False
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        if not isinstance(claims, dict):
            return False
        expiry = claims.get("exp")
        return (
            claims.get("role") == "anon"
            and claims.get("ref") == CANONICAL_SUPABASE_HOST.split(".")[0]
            and isinstance(expiry, (int, float))
            and not isinstance(expiry, bool)
            and math.isfinite(expiry)
            and expiry > time.time()
        )
    except (ValueError, TypeError, OverflowError, json.JSONDecodeError):
        return False


def https_endpoint(value: str) -> bool:
    try:
        url = urlparse(value)
        return (
            url.scheme == "https"
            and bool(url.hostname)
            and url.hostname not in {"localhost", "127.0.0.1", "0.0.0.0", "::1"}
            and not url.hostname.endswith((".example", ".invalid", ".test"))
            and not url.username
            and not url.password
            and not url.fragment
        )
    except ValueError:
        return False


def configuration_errors(app: str, config: dict[str, str]) -> list[str]:
    errors: list[str] = []
    url = config.get("SUPABASE_URL", "").strip()
    if not https_endpoint(url) or urlparse(url).hostname != CANONICAL_SUPABASE_HOST:
        errors.append("SUPABASE_URL must select the canonical HTTPS project.")
    if not public_key_valid(config.get("SUPABASE_ANON_KEY", "").strip()):
        errors.append("SUPABASE_ANON_KEY must be a public publishable key or an unexpired anon JWT.")
    if config.get("RPC_FORCE_FAKE", "").strip().lower() not in {"", "false", "0"}:
        errors.append("RPC_FORCE_FAKE must be disabled for Release.")
    try:
        target = int(config.get("GTR_ANDROID_TARGET_SDK", "0"))
    except ValueError:
        target = 0
    if target < MINIMUM_STORE_TARGET:
        errors.append(f"Release must target API {MINIMUM_STORE_TARGET} or newer.")
    if app == "customer-android":
        client_id = config.get("GOOGLE_WEB_CLIENT_ID", "").strip()
        if not re.fullmatch(r"[0-9]+-[a-zA-Z0-9_-]+\.apps\.googleusercontent\.com", client_id):
            errors.append("GOOGLE_WEB_CLIENT_ID must configure the customer Google sign-in client.")
    if app == "delivery-android":
        route = config.get("ROUTING_BASE_URL", "").strip()
        if not https_endpoint(route) or urlparse(route).hostname == "router.project-osrm.org":
            errors.append("ROUTING_BASE_URL/OSRM_URL must select the production HTTPS router, not the public demo.")
        style = config.get("MAPLIBRE_STYLE_URL", "").strip()
        if style and not https_endpoint(style):
            errors.append("MAPLIBRE_STYLE_URL must use HTTPS when configured.")
    for name in SIGNING_VARIABLES:
        if not config.get(name, "").strip():
            errors.append(f"{name} is required for Release signing.")
    keystore = config.get("GTR_ANDROID_KEYSTORE_PATH", "").strip()
    if keystore and not Path(keystore).is_file():
        errors.append("GTR_ANDROID_KEYSTORE_PATH must identify an existing keystore file.")
    if config.get("GTR_ANDROID_KEY_ALIAS", "").strip().lower() == "androiddebugkey":
        errors.append("The Android debug signing identity cannot sign a production Release.")
    return errors


def signing_errors(config: dict[str, str]) -> list[str]:
    """Check certificate identity and private-key access without exposing passwords/keys."""
    child_env = dict(os.environ)
    child_env.update({name: config[name] for name in SIGNING_VARIABLES})
    common = [
        "keytool", "-J-Duser.language=en", "-J-Duser.country=US",
        "-keystore", config["GTR_ANDROID_KEYSTORE_PATH"],
        "-storepass:env", "GTR_ANDROID_STORE_PASSWORD",
        "-alias", config["GTR_ANDROID_KEY_ALIAS"],
    ]
    try:
        certificate = subprocess.run(
            common + ["-list", "-v"], env=child_env, capture_output=True, text=True, timeout=30,
        )
        if certificate.returncode:
            return ["Release keystore password/alias could not be verified."]
        if "CN=Android Debug" in certificate.stdout or "PrivateKeyEntry" not in certificate.stdout:
            return ["Release requires a non-debug private-key signing entry."]
        # A CSR is public data and proves that the configured key password unlocks the key.
        # No file is written and no keystore state is changed.
        key_access = subprocess.run(
            common + ["-certreq", "-keypass:env", "GTR_ANDROID_KEY_PASSWORD"],
            env=child_env, capture_output=True, text=True, timeout=30,
        )
        if key_access.returncode:
            return ["Release private-key password could not be verified."]
    except (OSError, subprocess.TimeoutExpired):
        return ["The JDK keytool must be available to verify Release signing."]
    return []


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", required=True, choices=sorted(APPLICATIONS))
    args = parser.parse_args()
    config = dict(os.environ)
    errors = configuration_errors(args.app, config)
    if not errors:
        errors.extend(signing_errors(config))
    if errors:
        print("BLOCKED: Android Release prerequisites are incomplete:", file=sys.stderr)
        for message in errors:
            print(f" - {message}", file=sys.stderr)
        return 1
    print(f"android-release: {args.app} Live configuration and non-debug signing verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
