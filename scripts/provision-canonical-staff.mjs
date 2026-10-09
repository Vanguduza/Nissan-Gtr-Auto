#!/usr/bin/env node
/** Provision/verify the three authoritative Nissan GTR Auto hosted staff identities. */
import { createClient } from "@supabase/supabase-js";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

function loadDotEnv(path) {
  if (!existsSync(path)) return {};
  const out = {};
  for (const raw of readFileSync(path, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const i = line.indexOf("=");
    if (i < 1) continue;
    const key = line.slice(0, i).trim();
    let value = line.slice(i + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    out[key] = value;
  }
  return out;
}

const manifest = JSON.parse(readFileSync(resolve("config/canonical-staff.json"), "utf8"));
const env = {
  ...loadDotEnv(resolve(".env")),
  ...loadDotEnv(resolve(".canonical-staff.env")),
  ...process.env,
};
const expectedUrl = `https://${manifest.project_ref}.supabase.co`;
const url = env.SUPABASE_URL || env.NEXT_PUBLIC_SUPABASE_URL;
if (url !== expectedUrl) {
  console.error(`Refusing target ${url || "<unset>"}; expected ${expectedUrl}`);
  process.exit(1);
}

const secretKey = env.SUPABASE_SECRET_KEY || env.SUPABASE_SERVICE_ROLE_KEY || env.SUPABASE_SERVICE_KEY;
if (!secretKey) {
  console.error("Missing server-only SUPABASE_SECRET_KEY (preferred) or legacy service-role key.");
  process.exit(1);
}

const verifyOnly = process.argv.includes("--verify-only");
const rotatePasswords = process.argv.includes("--rotate-passwords");
const admin = createClient(url, secretKey, {
  auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
});

function passwordFor(account) {
  const value = env[account.password_env];
  if (!value) throw new Error(`Missing ${account.password_env}`);
  if (value.length < 20 || /^local-dev-/i.test(value)) {
    throw new Error(`${account.password_env} must be a unique production secret of at least 20 characters`);
  }
  return value;
}

async function listAllUsers() {
  const users = [];
  for (let page = 1; page <= 100; page++) {
    const { data, error } = await admin.auth.admin.listUsers({ page, perPage: 200 });
    if (error) throw error;
    users.push(...data.users);
    if (data.users.length < 200) break;
  }
  return users;
}

async function ensureAuthAccount(account, users) {
  const byId = users.find((u) => u.id === account.id);
  const byEmail = users.find((u) => (u.email || "").toLowerCase() === account.email.toLowerCase());
  if (byId && (byId.email || "").toLowerCase() !== account.email.toLowerCase()) {
    throw new Error(`Canonical UUID collision for ${account.id}`);
  }
  if (byEmail && byEmail.id !== account.id) {
    throw new Error(`Canonical email collision for ${account.email}; refusing account takeover`);
  }
  if (verifyOnly) return { user: byId || null, created: false };

  const attrs = {
    email: account.email,
    email_confirm: true,
    user_metadata: { full_name: account.full_name },
    app_metadata: { provider: "email", providers: ["email"], gtr_provisioned_via: "hr_onboarding" },
  };
  if (!byId) {
    const { data, error } = await admin.auth.admin.createUser({
      id: account.id,
      ...attrs,
      password: passwordFor(account),
    });
    if (error || !data.user) throw error || new Error(`createUser failed for ${account.email}`);
    return { user: data.user, created: true };
  }

  const update = { ...attrs };
  if (rotatePasswords) update.password = passwordFor(account);
  const { data, error } = await admin.auth.admin.updateUserById(account.id, update);
  if (error || !data.user) throw error || new Error(`updateUserById failed for ${account.email}`);
  return { user: data.user, created: false };
}

async function ensureDatabaseAuthority(account, forcePasswordChange) {
  const profile = { id: account.id, full_name: account.full_name };
  if (forcePasswordChange) profile.must_change_password = true;
  const { error: profileError } = await admin.from("profiles").upsert(
    profile,
    { onConflict: "id" },
  );
  if (profileError) throw profileError;

  const { error: deleteRoleError } = await admin.from("staff_roles")
    .delete().eq("user_id", account.id).neq("role", account.role);
  if (deleteRoleError) throw deleteRoleError;
  const { error: roleError } = await admin.from("staff_roles").upsert(
    { user_id: account.id, role: account.role }, { onConflict: "user_id,role" },
  );
  if (roleError) throw roleError;
}
async function verifyAuthority() {
  const { data, error } = await admin.rpc("canonical_staff_drift");
  if (error) throw error;
  const rows = Array.isArray(data) ? data : [];
  const failed = rows.filter((row) => row.in_sync !== true);
  if (rows.length !== manifest.accounts.length || failed.length) {
    for (const row of rows) {
      console.error(`${row.email}: ${row.in_sync ? "OK" : "DRIFT"}`);
    }
    throw new Error(`Canonical staff verification failed (${failed.length || "missing"} drift rows)`);
  }
  for (const row of rows) console.log(`- ${row.email}: authoritative (${row.role})`);
}

async function main() {
  const users = await listAllUsers();
  for (const account of manifest.accounts) {
    const { user, created } = await ensureAuthAccount(account, users);
    if (verifyOnly && !user) console.error(`- ${account.email}: missing from Auth`);
    if (!verifyOnly) {
      await ensureDatabaseAuthority(account, created || rotatePasswords);
      console.log(`- ${account.email}: provisioned/normalized`);
    }
  }
  await verifyAuthority();
  console.log("Canonical staff authority gate: PASS");
}

main().catch((error) => {
  console.error("Canonical staff authority gate: FAIL", error?.message || error);
  process.exit(1);
});
