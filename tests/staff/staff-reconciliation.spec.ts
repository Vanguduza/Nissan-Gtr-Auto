import { expect, test, type Page, type TestInfo } from "@playwright/test";

// Synthetic loopback backend only. Native SQL smokes validate authorization.
const backend = "http://127.0.0.1:54329";
const employeeId = "20000000-0000-4000-8000-000000000001";
const lineId = "40000000-0000-4000-8000-000000000001";
const browserErrors = new WeakMap<Page, string[]>();

test.beforeEach(async ({ page }) => {
  const errors: string[] = [];
  browserErrors.set(page, errors);
  page.on("pageerror", (error) => errors.push(error.message));
});
test.afterEach(async ({ page }) => {
  expect(browserErrors.get(page) || [], "screen must not raise an unhandled browser error").toEqual([]);
});

async function login(page: Page, role = "admin", path = "/staff") {
  await page.request.post(`${backend}/__fixture/reset`, { data: { email: `${role}@staff-fixture.test` } });
  await page.goto(`/login?next=${encodeURIComponent(path)}`);
  await page.getByLabel("Emp # / email / phone", { exact: true }).fill(`${role}@staff-fixture.test`);
  await page.getByLabel("Password", { exact: true }).fill("staff-browser-fixture-only");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL((url) => url.pathname === (role === "customer" ? "/account" : path), { timeout: 45_000 });
  await expect.poll(async () => (await page.context().cookies()).some((c) => c.name.includes("auth-token"))).toBe(true);
}

async function fixtureState(page: Page, role = "admin") {
  return (await page.request.get(`${backend}/__fixture/state?email=${encodeURIComponent(`${role}@staff-fixture.test`)}`)).json();
}

async function screenshot(page: Page, info: TestInfo, name: string) {
  await expect.poll(() => page.evaluate(() => document.fonts.status)).toBe("loaded");
  const path = info.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage: true, animations: "disabled" });
  await info.attach(name, { path, contentType: "image/png" });
}

test("admin overview renders live response metrics, bounded search and date validation", async ({ page }, info) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await login(page);
  await expect(page.getByRole("region", { name: "Overview KPIs" })).toContainText("$125.00");
  await expect(page.getByRole("heading", { name: "Recent Orders", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "INV-FIXTURE-1", exact: true })).toHaveAttribute("href", "/staff/finance/transactions/60000000-0000-4000-8000-000000000001");
  await expect(page.getByRole("img", { name: "Sales revenue trend in USD equivalent" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Staff overview navigation" })).toBeVisible();
  await screenshot(page, info, "staff-overview-desktop");
  await page.getByLabel("Search Nissan GTR Auto staff records").fill("filter");
  const result = page.getByRole("listbox", { name: "Staff search results" }).getByRole("link", { name: /Nissan service filter/ });
  await expect(result).toHaveAttribute("href", /\/staff\/warehouse\/master-stock\?overview_q=FIXTURE-OEM-1/);
  await page.getByLabel("From", { exact: true }).fill("2026-12-31");
  await page.getByLabel("To", { exact: true }).fill("2026-01-01");
  await expect(page.getByRole("alert").filter({ hasText: "End date must be on or after start date" })).toBeVisible();
  expect(errors).toEqual([]);
});

test("empty overview communicates missing activity without invented metrics", async ({ page }, info) => {
  await login(page, "empty");
  await expect(page.getByText("No sales in this period", { exact: true })).toBeVisible();
  await expect(page.getByText("No posted orders in this period.", { exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Overview KPIs" })).toContainText("$0.00");
  await screenshot(page, info, "staff-overview-empty");
});

test("My Account edits own contacts, uploads a photo and requests an ID download", async ({ page }, info) => {
  await login(page, "admin", "/staff/account");
  await expect(page.getByRole("heading", { name: "Identity", exact: true })).toBeVisible();
  await expect(page.getByText("Unfunded · submitted", { exact: true })).toBeVisible();
  await page.getByLabel("Phone (E.164)", { exact: true }).fill("+263771000099");
  await page.getByRole("textbox", { name: "Address", exact: true }).fill("99 Synthetic Avenue");
  await page.getByRole("button", { name: "Save details", exact: true }).click();
  await expect(page.getByText("Profile saved.", { exact: true })).toBeVisible();
  const stored = await fixtureState(page);
  expect(stored.phone).toBe("+263771000099");
  expect(stored.address).toBe("99 Synthetic Avenue");
  const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jEnkAAAAASUVORK5CYII=", "base64");
  await page.locator('input[type="file"]').setInputFiles({ name: "portrait.png", mimeType: "image/png", buffer: png });
  await expect(page.getByText("Photo uploaded.", { exact: true })).toBeVisible();
  await expect(page.getByRole("img", { name: "Your staff photo" })).toBeVisible();
  expect((await fixtureState(page)).photo).toMatch(new RegExp(`^${employeeId}/portrait-.*\\.png$`));
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download ID card", exact: true }).click();
  expect((await download).suggestedFilename()).toContain(".pdf");
  await expect(page.getByText("ID card PDF downloaded (CR80).", { exact: true })).toBeVisible();
  await screenshot(page, info, "staff-account-desktop");
});

test("unlinked staff account shows onboarding and payslip empty states", async ({ page }, info) => {
  await login(page, "empty", "/staff/account");
  await expect(page.getByText(/No employee record is linked to this login/)).toBeVisible();
  await expect(page.getByText("No payslips yet.", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Download ID card", exact: true })).toHaveCount(0);
  await screenshot(page, info, "staff-account-empty");
});

test("finance records selected-line funding and saves explicit schedule terms", async ({ page }, info) => {
  await login(page, "finance", "/staff/hr/payroll");
  await expect(page.getByRole("heading", { name: "Payroll & payslips", exact: true })).toBeVisible();
  await page.getByLabel("Select GTR-FIXTURE-1", { exact: true }).check();
  await page.getByRole("button", { name: "Fund selected (1)", exact: true }).click();
  await expect(page.getByText("No unfunded payroll lines visible.", { exact: true })).toBeVisible();
  const funded = (await fixtureState(page, "finance")).calls.find((c: { name: string }) => c.name === "fund_payroll_lines");
  expect(funded.args.p_payroll_line_ids).toEqual([lineId]);
  expect(funded.args.p_cash_account_code).toBe("1100");
  await page.getByRole("combobox", { name: "Currency", exact: true }).selectOption("ZIG");
  await page.getByLabel("Exchange rate", { exact: true }).fill("2");
  await page.getByLabel("Next run date (06:00 UTC)", { exact: true }).fill("2026-12-25");
  await page.getByRole("button", { name: "Save schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Schedule saved");
  expect((await page.getByRole("checkbox", { name: "Active", exact: true }).boundingBox())?.width).toBeLessThan(25);
  const saved = (await fixtureState(page, "finance")).calls.findLast((c: { name: string }) => c.name === "upsert_hr_payslip_schedule");
  expect(saved.args.p_currency).toBe("ZIG");
  expect(saved.args.p_default_exchange_rate).toBe(2);
  expect(saved.args.p_next_run_at).toBe("2026-12-25T06:00:00.000Z");
  await screenshot(page, info, "staff-payroll-finance");
  await page.goto("/staff/hr");
  await expect(page.getByRole("heading", { name: "Access denied", exact: true })).toBeVisible();
});

test("HR cannot fund or edit an automatic schedule and can prepare an unfunded schedule", async ({ page }, info) => {
  await login(page, "hr", "/staff/hr/payroll");
  await expect(page.getByRole("button", { name: "Fund entire run", exact: true })).toBeDisabled();
  await expect(page.getByLabel("Cash CoA", { exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Save schedule", exact: true })).toBeDisabled();
  await expect(page.getByText(/Finance approval is required to change this automatically funded schedule/)).toBeVisible();
  await page.getByRole("combobox", { name: "Frequency", exact: true }).selectOption("weekly");
  await expect(page.getByRole("button", { name: "Save schedule", exact: true })).toBeEnabled();
  await page.getByLabel("Next run date (06:00 UTC)", { exact: true }).fill("2026-12-07");
  await page.getByRole("button", { name: "Save schedule", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Schedule saved");
  const calls = (await fixtureState(page, "hr")).calls;
  expect(calls.some((c: { name: string }) => c.name.startsWith("fund_payroll"))).toBe(false);
  const saved = calls.findLast((c: { name: string }) => c.name === "upsert_hr_payslip_schedule");
  expect(saved.args.p_pay_frequency).toBe("weekly");
  for (const key of ["p_cash_account_code", "p_currency", "p_default_exchange_rate", "p_auto_fund"]) expect(saved.args).not.toHaveProperty(key);
  await screenshot(page, info, "staff-payroll-hr");
});

test("independent merchandising pins preserve the existing home carousel controls", async ({ page }, info) => {
  await login(page, "admin", "/staff/crm/product-pages");
  await expect(page.getByText("Nissan service filter", { exact: true }).first()).toBeVisible();
  await page.getByRole("button", { name: "Edit", exact: true }).click();
  await page.getByLabel("Pin to Featured products", { exact: true }).check();
  expect((await page.getByRole("checkbox", { name: "Pin to Featured products", exact: true }).boundingBox())?.width).toBeLessThan(25);
  await page.getByLabel("Pin to Newest additions", { exact: true }).check();
  await expect(page.getByLabel("Pin to Fast movers", { exact: true })).not.toBeChecked();
  await page.getByLabel("Pin sort (lower = earlier among pins)", { exact: true }).fill("8");
  await page.getByRole("button", { name: "Save merchandising", exact: true }).click();
  await expect(page.getByText(/Saved price, discount, and home-rail pins/)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Home page carousel", exact: true })).toBeVisible();
  const stored = await fixtureState(page);
  expect(stored.product).toMatchObject({ pin_featured: true, pin_movers: false, pin_newest: true, pin_sort: 8 });
  expect(stored.calls.some((c: { name: string }) => c.name === "set_storefront_home_carousel")).toBe(false);
  await page.getByRole("combobox", { name: "Position", exact: true }).selectOption("3");
  await page.getByRole("button", { name: "Save carousel", exact: true }).click();
  await expect(page.getByText(/Home carousel updated \(position 3\)/)).toBeVisible();
  const afterCarousel = await fixtureState(page);
  expect(afterCarousel.carousel[0].home_carousel_rank).toBe(3);
  expect(afterCarousel.product).toMatchObject({ pin_featured: true, pin_movers: false, pin_newest: true, pin_sort: 8 });
  await screenshot(page, info, "staff-product-pages-pins-and-carousel");
});

test("warehouse overview suppresses finance metrics and direct payroll access is denied", async ({ page }) => {
  await login(page, "warehouse");
  await expect(page.getByText("On Hand", { exact: true })).toBeVisible();
  await expect(page.getByText("Total Revenue", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("navigation", { name: "Staff overview navigation" }).getByRole("link", { name: "Finance", exact: true })).toHaveCount(0);
  await page.goto("/staff/hr/payroll");
  await expect(page.getByRole("heading", { name: "Access denied", exact: true })).toBeVisible();
});

test("identity verification uses protected normal login and presents only minimal identity", async ({ page }, info) => {
  const path = `/staff/verify/${employeeId}`;
  await page.goto(path);
  await expect(page).toHaveURL((url) => url.pathname === "/login" && url.searchParams.get("next") === path);
  await login(page, "admin", path);
  await expect(page.getByRole("heading", { name: "Staff identity", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Fixture admin", exact: true })).toBeVisible();
  await expect(page.getByText(/Access still requires the employee’s own sign-in/)).toBeVisible();
  await expect(page.getByText("+263771000001", { exact: true })).toHaveCount(0);
  await expect(page.getByText("1 Synthetic Road", { exact: true })).toHaveCount(0);
  await screenshot(page, info, "staff-identity-verification");
});

test("customer session is rejected by middleware before staff content appears", async ({ page }) => {
  await login(page, "customer");
  await page.goto("/staff/account");
  await expect(page).toHaveURL((url) => url.pathname === "/account" && url.searchParams.get("notice") === "staff-only");
  await expect(page.getByRole("heading", { name: "Identity", exact: true })).toHaveCount(0);
});

test("overview and My Account fit phone viewport", async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page);
  await expect(page.getByRole("region", { name: "Overview KPIs" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  for (const locator of [page.getByRole("heading", { level: 1 }), page.getByLabel("To", { exact: true }), page.getByRole("img", { name: "Sales revenue trend in USD equivalent" })]) {
    const box = await locator.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(390);
  }
  await expect(page.getByRole("link", { name: "My Account", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Sign out", exact: true })).toBeVisible();
  await screenshot(page, info, "staff-overview-phone");
  await page.goto("/staff/account");
  await expect(page.getByRole("heading", { name: "Identity", exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  expect((await page.getByRole("textbox", { name: "Address", exact: true }).boundingBox())?.width).toBeGreaterThan(200);
  await screenshot(page, info, "staff-account-phone");
});
