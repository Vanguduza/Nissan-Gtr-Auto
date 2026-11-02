import assert from "node:assert/strict";
import { test } from "node:test";
import { safeAuthNextPath } from "../lib/auth-next.ts";
import { publicSiteUrl } from "../lib/site-url.ts";
import { canAccessPath, postLoginPath } from "../lib/staff-auth.ts";
import { evaluateStaffIdleLock, parseStaffIdleLockPersisted } from "../lib/staff-idle-lock-state.ts";
import { checkoutRequestIdForCart, clearCheckoutRequestId } from "../lib/customer-storefront.ts";

test("login continuation rejects off-origin URL parser tricks", () => {
  for (const raw of ["https://attacker.invalid", "//attacker.invalid", "/\\attacker.invalid", "/%5cattacker.invalid", "/%2fattacker.invalid", "/\n/attacker.invalid", "/%0d%0aLocation:evil"])
    assert.equal(safeAuthNextPath(raw), null, raw);
  assert.equal(safeAuthNextPath("/staff?tab=stock"), "/staff?tab=stock");
});

test("staff access requires staff identity as well as role labels", () => {
  assert.equal(canAccessPath({ isStaff: false, roles: ["admin"] }, "/staff/finance"), false);
  assert.equal(canAccessPath({ isStaff: true, roles: ["sales"], moduleAccess: ["warehouse"] }, "/staff/finance"), false);
  assert.equal(canAccessPath({ isStaff: true, roles: ["admin"], moduleAccess: [] }, "/staff/finance"), true);
});

test("cashier cannot acquire an approver-only manager screen", () => {
  assert.equal(canAccessPath({ isStaff: true, roles: ["sales"], isApprover: false }, "/staff/dashboard"), false);
  assert.equal(canAccessPath({ isStaff: true, roles: ["finance"], isApprover: false }, "/staff/dashboard"), true);
});

test("password change gate takes precedence over a supplied staff destination", () => {
  assert.equal(postLoginPath(true, "/staff/finance", ["finance"], true), "/staff/change-password");
  assert.equal(postLoginPath(false, "//attacker.invalid", [], false), "/account");
});

test("staff idle lock survives reload and locks at the timeout boundary", () => {
  assert.equal(evaluateStaffIdleLock({ lastActiveAt: 100, locked: true }, 101, 1000).locked, true);
  assert.equal(evaluateStaffIdleLock({ lastActiveAt: 100, locked: false }, 1100, 1000).locked, true);
  assert.equal(parseStaffIdleLockPersisted('{"lastActiveAt":"now","locked":false}'), null);
});

test("checkout retry retains one idempotency identity per cart", () => {
  const data = new Map<string, string>();
  const original = Object.getOwnPropertyDescriptor(globalThis, "window");
  Object.defineProperty(globalThis, "window", { configurable: true, value: {
    localStorage: { getItem: (k: string) => data.get(k) ?? null, setItem: (k: string, v: string) => data.set(k, v), removeItem: (k: string) => data.delete(k) },
  } });
  try {
    const first = checkoutRequestIdForCart("cart-a");
    assert.equal(checkoutRequestIdForCart("cart-a"), first);
    assert.notEqual(checkoutRequestIdForCart("cart-b"), first);
    clearCheckoutRequestId("cart-a");
    assert.notEqual(checkoutRequestIdForCart("cart-a"), first);
  } finally {
    if (original) Object.defineProperty(globalThis, "window", original);
    else Reflect.deleteProperty(globalThis, "window");
  }
});

test("nonlocal callback origins use HTTPS", () => {
  assert.equal(publicSiteUrl("http://nissangtrauto.co.zw"), "https://nissangtrauto.co.zw");
  assert.equal(publicSiteUrl("http://127.0.0.1:3000"), "http://127.0.0.1:3000");
});
