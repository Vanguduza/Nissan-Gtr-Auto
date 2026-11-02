import { expect, test } from "@playwright/test";

const itemId = "00000000-0000-4000-8000-000000000101";

test.beforeEach(async ({ page }) => {
  await page.route("https://bicyjghgdnzlnjqxzoud.supabase.co/**", async (route) => {
    const url = new URL(route.request().url());
    const headers = { "access-control-allow-origin": "*", "access-control-allow-headers": "apikey,authorization,content-type,x-client-info", "access-control-allow-methods": "GET,POST,OPTIONS" };
    if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers });
    if (url.pathname === "/auth/v1/authorize") return route.fulfill({ contentType: "text/html", body: "<p>Isolated OAuth redirect fixture</p>", headers });
    const data = url.pathname.endsWith("/list_storefront_home_rails") ? [{
      rail: "featured", stock_item_id: itemId, oem_part_number: "PRIVATE-OEM-101",
      catalog_title: "Nissan service filter", qty_saleable: 5, unit_price: 12.5,
      currency: "USD", created_at: "2026-11-01T00:00:00Z", reorder_point: 1,
      discount_kind: "none", discount_value: 0, discount_description: null,
    }] : [];
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(data), headers });
  });
});

test("curated home shows real response fields without exposing technical part numbers", async ({ page }) => {
  const pageErrors: string[] = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.goto("/");
  const product = page.getByRole("region", { name: "Featured products", exact: true }).getByRole("link").filter({ has: page.getByText("Nissan service filter", { exact: true }) });
  await expect(product).toBeVisible();
  await expect(product).toHaveAttribute("href", `/parts/${itemId}`);
  await expect(page.getByText("PRIVATE-OEM-101", { exact: true })).toHaveCount(0);
  await expect(product).toContainText("12.50");
  expect(pageErrors).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
});

test("unauthenticated users cannot reach staff, procurement or technical catalog pages", async ({ page }) => {
  for (const path of ["/staff", "/staff/hr", "/procurement", "/catalog", "/staff/verify/example"]) {
    await page.goto(path);
    await expect(page).toHaveURL((url) => url.pathname === "/login" && url.searchParams.get("next") === path);
    await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  }
});

test("guest checkout cannot submit a financial mutation", async ({ page }) => {
  const mutations: string[] = [];
  page.on("request", (request) => {
    if (/prepare_customer_checkout|mark_.*settled|post_payment_entry/.test(request.url())) mutations.push(request.url());
  });
  await page.goto("/cart");
  await expect(page.getByRole("heading", { name: "Cart", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: /sign in/i }).first()).toBeVisible();
  expect(mutations).toEqual([]);
});

test("Google sign-in keeps the callback on this app and rejects an external continuation", async ({ page }) => {
  await page.goto("/login?next=" + encodeURIComponent("/\\attacker.invalid"));
  await page.getByRole("button", { name: "Google", exact: true }).click();
  await expect(page).toHaveURL((url) => url.hostname === "bicyjghgdnzlnjqxzoud.supabase.co" && url.pathname === "/auth/v1/authorize");
  const oauth = new URL(page.url());
  const callback = new URL(oauth.searchParams.get("redirect_to")!);
  expect(callback.origin).toBe("http://127.0.0.1:3000");
  expect(callback.pathname).toBe("/auth/callback");
  expect(callback.searchParams.has("next")).toBe(false);
});
