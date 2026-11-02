import assert from "node:assert/strict";
import { test } from "node:test";
import { APPROVED_SUPABASE_HOST, validateWebEnvironment } from "./web-environment.mjs";

// Synthetic structural fixtures; these do not authenticate to a hosted service.
const jwt = (claims) => ["test-only", Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600, ...claims })).toString("base64url"), "not-a-signature"].join(".");
const good = { NEXT_PUBLIC_SUPABASE_URL: `https://${APPROVED_SUPABASE_HOST}`, NEXT_PUBLIC_SUPABASE_ANON_KEY: "sb_publishable_ci_fixture_only_not_for_deployment" };

test("missing production configuration fails closed", () => assert.equal(validateWebEnvironment({}).length, 2));
test("retired project is rejected", () => assert.ok(validateWebEnvironment({ ...good, NEXT_PUBLIC_SUPABASE_URL: "https://gylrgwqyuiwkyykardwc.supabase.co" }).length));
test("service-role and secret keys cannot enter a browser build", () => {
  for (const key of ["sb_secret_test_only", jwt({ role: "service_role", ref: APPROVED_SUPABASE_HOST.split(".")[0] })])
    assert.ok(validateWebEnvironment({ ...good, NEXT_PUBLIC_SUPABASE_ANON_KEY: key }).length);
});
test("legacy anon key must match the URL project", () => {
  assert.ok(validateWebEnvironment({ ...good, NEXT_PUBLIC_SUPABASE_ANON_KEY: jwt({ role: "anon", ref: "retired-project" }) }).length);
  assert.deepEqual(validateWebEnvironment({ ...good, NEXT_PUBLIC_SUPABASE_ANON_KEY: jwt({ role: "anon", ref: APPROVED_SUPABASE_HOST.split(".")[0] }) }), []);
});
test("only approved HTTPS origin is accepted", () => {
  for (const url of [`http://${APPROVED_SUPABASE_HOST}`, `https://${APPROVED_SUPABASE_HOST}.attacker.invalid`, `https://user@${APPROVED_SUPABASE_HOST}`, `https://${APPROVED_SUPABASE_HOST}/extra`])
    assert.ok(validateWebEnvironment({ ...good, NEXT_PUBLIC_SUPABASE_URL: url }).length, url);
  assert.deepEqual(validateWebEnvironment(good), []);
});
test("empty or malformed opaque keys and expired legacy keys fail closed", () => {
  for (const key of ["sb_publishable_", "sb_publishable_short", "sb_publishable_" + "!".repeat(30), jwt({ role: "anon", ref: APPROVED_SUPABASE_HOST.split(".")[0], exp: 1 }), jwt({ role: "anon", ref: APPROVED_SUPABASE_HOST.split(".")[0], exp: null })])
    assert.ok(validateWebEnvironment({ ...good, NEXT_PUBLIC_SUPABASE_ANON_KEY: key }).length);
});
