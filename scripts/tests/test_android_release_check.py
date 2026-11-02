"""Release safety behavior, including leaked service tokens and unsigned artifacts."""

import base64
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("android_release_check", Path(__file__).parents[1] / "android_release_check.py")
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class AndroidReleaseCheckTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.keystore = Path(self.directory.name) / "fixture-keystore"
        self.keystore.touch()  # Validation fixture only; never used for a build/signature.
        self.config = {
            "SUPABASE_URL": "https://bicyjghgdnzlnjqxzoud.supabase.co",
            "SUPABASE_ANON_KEY": "sb_publishable_" + "a" * 32,
            "GTR_ANDROID_TARGET_SDK": "36",
            "GOOGLE_WEB_CLIENT_ID": "123-fixture.apps.googleusercontent.com",
            "ROUTING_BASE_URL": "https://routing.nissangtrauto.co.zw/route/v1/driving",
            "GTR_ANDROID_KEYSTORE_PATH": str(self.keystore),
            "GTR_ANDROID_STORE_PASSWORD": "test-only-value",
            "GTR_ANDROID_KEY_ALIAS": "test-fixture",
            "GTR_ANDROID_KEY_PASSWORD": "test-only-value",
        }

    def errors(self, app="customer-android", **overrides):
        return CHECK.configuration_errors(app, self.config | overrides)

    def test_valid_public_release_configuration(self):
        self.assertEqual([], self.errors())
        self.assertEqual([], self.errors(app="management-android"))
        self.assertEqual([], self.errors(app="delivery-android"))

    def test_missing_or_wrong_project_never_silently_uses_fake(self):
        self.assertTrue(self.errors(SUPABASE_URL=""))
        self.assertTrue(self.errors(SUPABASE_URL="https://other.supabase.co"))
        self.assertTrue(self.errors(RPC_FORCE_FAKE="true"))

    def test_private_service_keys_are_rejected_without_echoing_values(self):
        payload = base64.urlsafe_b64encode(json.dumps({"role": "service_role"}).encode()).decode().rstrip("=")
        for value in ("sb_secret_do-not-embed", "header." + payload + ".signature"):
            errors = self.errors(SUPABASE_ANON_KEY=value)
            self.assertTrue(errors)
            self.assertNotIn(value, " ".join(errors))

    def test_expired_anon_key_is_rejected(self):
        payload = base64.urlsafe_b64encode(json.dumps({"role": "anon", "exp": 1}).encode()).decode().rstrip("=")
        self.assertTrue(self.errors(SUPABASE_ANON_KEY="header." + payload + ".signature"))

    def test_legacy_anon_token_must_belong_to_canonical_project(self):
        for ref, expected in [("bicyjghgdnzlnjqxzoud", False), ("retired-project", True)]:
            claims = {"role": "anon", "ref": ref, "exp": 4102444800}
            payload = base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip("=")
            self.assertEqual(expected, bool(self.errors(SUPABASE_ANON_KEY="header." + payload + ".signature")))

    def test_legacy_token_expiry_must_be_numeric_finite_and_present(self):
        invalid = [None, True, "4102444800", "1e999", float("inf"), float("nan")]
        for expiry in invalid:
            claims = {"role": "anon", "ref": "bicyjghgdnzlnjqxzoud", "exp": expiry}
            payload = base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip("=")
            self.assertTrue(self.errors(SUPABASE_ANON_KEY="header." + payload + ".signature"))
        claims = {"role": "anon", "ref": "bicyjghgdnzlnjqxzoud"}
        payload = base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip("=")
        self.assertTrue(self.errors(SUPABASE_ANON_KEY="header." + payload + ".signature"))

    def test_api_34_cannot_be_packaged_as_current_store_release(self):
        self.assertTrue(self.errors(GTR_ANDROID_TARGET_SDK="34"))

    def test_missing_signing_identity_and_debug_alias_block_release(self):
        self.assertTrue(self.errors(GTR_ANDROID_KEYSTORE_PATH=""))
        self.assertTrue(self.errors(GTR_ANDROID_KEYSTORE_PATH="/missing/keystore"))
        self.assertTrue(self.errors(GTR_ANDROID_KEY_ALIAS="androiddebugkey"))

    def test_customer_oauth_and_delivery_demo_router_fail_closed(self):
        self.assertTrue(self.errors(GOOGLE_WEB_CLIENT_ID=""))
        self.assertTrue(self.errors(app="delivery-android", ROUTING_BASE_URL="https://router.project-osrm.org/route/v1/driving"))
        self.assertTrue(self.errors(app="delivery-android", ROUTING_BASE_URL="http://localhost:5000"))

    @patch.object(CHECK.subprocess, "run")
    def test_certificate_cannot_hide_debug_identity_behind_another_alias(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, "PrivateKeyEntry CN=Android Debug", "")
        self.assertTrue(CHECK.signing_errors(self.config))

    @patch.object(CHECK.subprocess, "run")
    def test_private_key_password_is_verified_and_never_passed_in_arguments(self, run):
        run.side_effect = [subprocess.CompletedProcess([], 0, "PrivateKeyEntry CN=Fixture", ""), subprocess.CompletedProcess([], 1, "", "private error")]
        self.assertTrue(CHECK.signing_errors(self.config))
        args = run.call_args[0][0]
        self.assertNotIn(self.config["GTR_ANDROID_KEY_PASSWORD"], args)
        self.assertIn("-keypass:env", args)


if __name__ == "__main__":
    unittest.main()
