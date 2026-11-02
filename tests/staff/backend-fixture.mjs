/**
 * Loopback-only browser fixture. This file is never imported by product code.
 * It emulates response shapes, not Supabase authorization. Native PostgreSQL
 * smoke tests prove RLS/RPC authority; these tests exercise real browser sign-in,
 * cookies, middleware and recovered screen behavior against synthetic data.
 */
import { createServer } from "node:http";

const host = "127.0.0.1";
const port = 54329;
const origin = `http://${host}:${port}`;
const password = "staff-browser-fixture-only";
const employeeId = "20000000-0000-4000-8000-000000000001";
const runId = "30000000-0000-4000-8000-000000000001";
const lineId = "40000000-0000-4000-8000-000000000001";
const itemId = "50000000-0000-4000-8000-000000000001";
const journalId = "60000000-0000-4000-8000-000000000001";
const scheduleId = "70000000-0000-4000-8000-000000000001";
const users = ["admin", "finance", "hr", "warehouse", "customer", "empty"].map((name, i) => ({
  id: `10000000-0000-4000-8000-${String(i + 1).padStart(12, "0")}`,
  email: `${name}@staff-fixture.test`,
  name,
  roles: name === "customer" ? [] : [name === "empty" ? "admin" : name],
  modules: name === "finance" ? ["finance", "hr"] : name === "hr" ? ["hr"] : name === "warehouse" ? ["warehouse"] : [],
}));
const state = new Map();
function fresh(user) {
  return { calls: [], phone: "+263771000001", address: "1 Synthetic Road", photo: null,
    funded: false, carousel: [], product: { stock_item_id: itemId, oem_part_number: "FIXTURE-OEM-1", catalog_title: "Nissan service filter", unit_price: 12.5, currency: "USD", qty_saleable: 5, discount_kind: "none", discount_value: 0, discount_description: null, primary_image_path: null, image_count: 0, pin_featured: false, pin_movers: false, pin_newest: false, pin_sort: 0 },
    schedules: user.name === "empty" ? [] : [{ id: scheduleId, pay_frequency: "monthly", cron_expr: "0 6 25 * *", last_run_at: null, next_run_at: "2026-12-25T06:00:00Z", is_active: true, cash_account_code: "1100", currency: "USD", default_exchange_rate: 1, auto_fund: true }],
  };
}
users.forEach((u) => state.set(u.email, fresh(u)));
function authUser(user) {
  return { id: user.id, aud: "authenticated", role: "authenticated", email: user.email,
    email_confirmed_at: "2026-01-01T00:00:00Z", confirmed_at: "2026-01-01T00:00:00Z",
    phone: "", app_metadata: { provider: "email", providers: ["email"] },
    user_metadata: { full_name: `Fixture ${user.name}` }, identities: [],
    created_at: "2026-01-01T00:00:00Z", updated_at: "2026-01-01T00:00:00Z", is_anonymous: false };
}
function session(user) {
  const now = Math.floor(Date.now() / 1000);
  const encoded = (data) => Buffer.from(JSON.stringify(data)).toString("base64url");
  // A deliberately synthetic token accepted ONLY by this loopback fixture.
  const access_token = `${encoded({ alg: "HS256", typ: "JWT" })}.${encoded({ iss: `${origin}/auth/v1`, sub: user.id, aud: "authenticated", role: "authenticated", email: user.email, iat: now, exp: now + 3600 })}.fixture-only-signature`;
  return { access_token, token_type: "bearer", expires_in: 3600, expires_at: now + 3600, refresh_token: `fixture-refresh-${user.name}`, user: authUser(user) };
}
function bearerUser(req) {
  try {
    const jwt = String(req.headers.authorization || "").replace(/^Bearer /i, "");
    if (!jwt.endsWith(".fixture-only-signature")) return null;
    const claims = JSON.parse(Buffer.from(jwt.split(".")[1], "base64url").toString());
    return users.find((u) => u.id === claims.sub) || null;
  } catch { return null; }
}
function profile(user) {
  const s = state.get(user.email);
  return { has_employee: user.name !== "empty", employee_id: user.name === "empty" ? null : employeeId,
    employee_code: "GTR-FIXTURE-1", full_name: `Fixture ${user.name}`, email: user.email,
    phone_e164: s.phone, address: s.address, photo_storage_path: s.photo, status: "active",
    hire_date: "2026-01-01", grade_id: null, grade_code: "C1", grade_title: "Staff",
    hr_role_id: null, role_title: user.name === "hr" ? "HR officer" : "Staff officer",
    module_access: user.modules, staff_roles: user.roles, user_id: user.id };
}
function dashboard(user, args) {
  const empty = user.name === "empty";
  const commercial = user.roles.some((r) => ["admin", "finance"].includes(r));
  const finance = commercial;
  const inventory = user.roles.some((r) => ["admin", "warehouse"].includes(r));
  const customers = user.roles.includes("admin");
  const logistics = user.roles.includes("admin");
  return { generated_at: new Date().toISOString(), timezone: "Africa/Harare",
    period: { from: args.p_from, to: args.p_to, previous_from: "2026-09-01T00:00:00Z", previous_to: "2026-10-01T00:00:00Z" },
    staff: { name: `Fixture ${user.name}`, roles: user.roles }, permissions: { commercial, finance, customers, inventory, logistics },
    summary: { sales: commercial ? { order_count: empty ? 0 : 2, by_currency: [{ currency: "USD", invoice_count: empty ? 0 : 2, revenue: empty ? 0 : 125, subtotal: empty ? 0 : 125, average_order_value: empty ? 0 : 62.5 }] } : null,
      previous_sales: commercial ? { order_count: 1, by_currency: [{ currency: "USD", invoice_count: 1, revenue: 100 }] } : null,
      customers: customers ? (empty ? 0 : 3) : null, previous_customers: customers ? 2 : null, new_customers: customers ? (empty ? 0 : 1) : null,
      net_margin_pct: finance && !empty ? 20 : null, previous_net_margin_pct: finance && !empty ? 15 : null },
    sales_trend: commercial && !empty ? [{ date: "2026-11-01", revenue_usd: 125, orders: 2 }] : [],
    payment_mix: finance && !empty ? [{ tender: "cash", payments: 2, amount_usd: 125 }] : [],
    top_products: commercial && !empty ? [{ stock_item_id: itemId, oem: "FIXTURE-OEM-1", description: "Nissan service filter", qty: 5, revenue_usd: 125, image_path: null }] : [],
    customer_segments: customers && !empty ? [{ segment: "cash", customer_count: 3 }] : [],
    recent_orders: finance && !empty ? [{ id: runId, journal_entry_id: journalId, document_number: "INV-FIXTURE-1", customer: "Synthetic customer", total: 125, currency: "USD", amount_paid: 125, fulfillment_mode: "collection", delivery_payment_method: null, state: "paid", posted_at: "2026-11-01T10:00:00Z" }] : [],
    inventory: inventory ? { total_skus: empty ? 0 : 5, out_of_stock: 0, low_stock: 1, in_stock: 4, quarantine: 0, on_hand_qty: empty ? 0 : 15, quarantine_qty: 0 } : {},
    delivery: logistics ? { open_jobs: 0, pending: 0, dispatched: 0, completed: 0, failed: 0 } : {} };
}
const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jEnkAAAAASUVORK5CYII=", "base64");

const server = createServer(async (req, res) => {
  const send = (data, status = 200, type = "application/json") => {
    res.writeHead(status, { "content-type": type, "access-control-allow-origin": "http://127.0.0.1:3000", "access-control-allow-headers": "*", "access-control-allow-methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS", "cache-control": "no-store" });
    res.end(type === "application/json" ? JSON.stringify(data) : data);
  };
  if (req.method === "OPTIONS") return send(null, 204);
  const url = new URL(req.url || "/", origin);
  let body = {};
  try {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const raw = Buffer.concat(chunks).toString();
    if (raw && req.headers["content-type"]?.includes("application/json")) body = JSON.parse(raw);
  } catch { return send({ message: "invalid fixture request" }, 400); }
  const user = bearerUser(req);
  if (url.pathname === "/__fixture/health") return send({ scope: "loopback synthetic staff backend" });
  if (url.pathname === "/__fixture/state") {
    const email = url.searchParams.get("email");
    return send(state.get(email) || {});
  }
  if (url.pathname === "/__fixture/reset" && req.method === "POST") {
    const fixtureUser = users.find((u) => u.email === body.email);
    if (!fixtureUser) return send({ message: "unknown fixture user" }, 400);
    state.set(fixtureUser.email, fresh(fixtureUser));
    return send({ ok: true });
  }
  if (url.pathname === "/auth/v1/token") {
    const fixtureUser = users.find((u) => (body.email === u.email && body.password === password) || body.refresh_token === `fixture-refresh-${u.name}`);
    return fixtureUser ? send(session(fixtureUser)) : send({ error_code: "invalid_credentials", message: "Invalid login credentials" }, 400);
  }
  if (url.pathname === "/auth/v1/user") return user ? send(authUser(user)) : send({ message: "invalid fixture session" }, 401);
  if (url.pathname === "/auth/v1/logout") return send(null, 204);
  if (url.pathname.startsWith("/storage/v1/")) {
    if (!user) return send({ message: "authentication required" }, 401);
    if (url.pathname.includes("/object/sign/")) return send({ signedURL: `/object/public/employee-photos/fixture.png?token=fixture` });
    if (req.method === "POST") return send({ Key: "employee-photos/fixture", Id: employeeId });
    return send(png, 200, "image/png");
  }
  if (url.pathname === "/functions/v1/render-branded-doc") {
    if (!user) return send({ message: "authentication required" }, 401);
    state.get(user.email).calls.push({ name: "render-branded-doc", args: body });
    return send(Buffer.from("%PDF-1.4\n% Synthetic browser download fixture; real renderer has Deno tests.\n%%EOF"), 200, "application/pdf");
  }
  if (url.pathname.startsWith("/rest/v1/rpc/")) {
    const fn = url.pathname.split("/").pop();
    if (fn === "resolve_staff_login_email") return send(users.find((u) => u.email === body.p_identifier)?.email || null);
    if (!user) return send({ message: "authentication required" }, 401);
    const s = state.get(user.email);
    s.calls.push({ name: fn, args: body });
    if (fn === "my_module_access") return send(user.modules);
    if (fn === "is_pos_approver") return send(user.roles.includes("admin"));
    if (fn === "get_my_staff_profile") return send(profile(user));
    if (fn === "update_my_staff_profile") {
      if (body.p_phone_e164 !== undefined) s.phone = body.p_phone_e164;
      if (body.p_address !== undefined) s.address = body.p_address;
      if (body.p_photo_storage_path !== undefined) s.photo = body.p_photo_storage_path;
      return send(profile(user));
    }
    if (fn === "list_my_payslip_history") return send(user.name === "empty" ? [] : [{ payroll_line_id: lineId, payroll_run_id: runId, payslip_id: null, period_start: "2026-10-01", period_end: "2026-10-31", document_number: "PAY-FIXTURE-1", run_status: "submitted", currency: "USD", gross_amount: 100, deductions_amount: 20, net_amount: 80, funded: s.funded, storage_bucket: null, storage_path: null, generated_at: null }]);
    if (fn === "get_staff_overview_dashboard") return send(dashboard(user, body));
    if (fn === "search_staff_overview") return send([{ kind: "part", id: itemId, label: "Nissan service filter", subtitle: "FIXTURE-OEM-1", destination: "/staff/warehouse/master-stock", filter: "FIXTURE-OEM-1" }]);
    if (fn === "verify_staff_identity") return send({ employee_id: employeeId, full_name: `Fixture ${user.name}`, employee_code: "GTR-FIXTURE-1", role_title: "Staff officer", status: "active" });
    if (fn === "list_payroll_funding_employees") return send(user.name === "empty" ? [] : [{ id: employeeId, employee_code: "GTR-FIXTURE-1", full_name: `Fixture ${user.name}`, status: "active" }]);
    if (fn === "upsert_hr_payslip_schedule") {
      const existing = s.schedules.find((r) => r.pay_frequency === body.p_pay_frequency);
      const row = existing || { id: body.p_pay_frequency === "weekly" ? "70000000-0000-4000-8000-000000000002" : "70000000-0000-4000-8000-000000000003", pay_frequency: body.p_pay_frequency, cash_account_code: "1100", currency: "USD", default_exchange_rate: 1, auto_fund: false };
      for (const [key, value] of Object.entries(body)) if (value !== undefined && value !== null) row[key.replace(/^p_/, "")] = value;
      if (!existing) s.schedules.push(row);
      return send(row.id);
    }
    if (fn === "fund_payroll_lines" || fn === "fund_payroll_run_selected") {
      s.funded = true;
      return send({ payroll_run_id: runId, funded_count: 1, skipped_count: 0, accrual_journal_id: null, payment_journal_id: journalId, total_net: 80, currency: "USD", cash_account_code: "1100", payslip_ids: [] });
    }
    if (fn === "list_staff_product_pages") return send(user.name === "empty" ? [] : [s.product]);
    if (fn === "upsert_staff_product_page") {
      for (const key of ["unit_price", "discount_kind", "discount_value", "discount_description", "pin_featured", "pin_movers", "pin_newest", "pin_sort"]) s.product[key] = body[`p_${key}`];
      return send(itemId);
    }
    if (["list_staff_home_carousel", "list_storefront_home_carousel", "list_storefront_home_rails"].includes(fn)) return send([]);
    if (fn === "set_storefront_home_carousel") {
      s.carousel = body.p_rank == null ? [] : [{ stock_item_id: body.p_stock_item_id, home_carousel_rank: body.p_rank, home_carousel_caption: body.p_caption, stock_items: { description: s.product.catalog_title } }];
      return send(null);
    }
    if (fn === "list_my_approvals") return send([]);
    return send({ message: `unimplemented browser fixture RPC: ${fn}` }, 400);
  }
  if (url.pathname.startsWith("/rest/v1/")) {
    const table = url.pathname.split("/").pop();
    if (!user) return send([], 200);
    const s = state.get(user.email);
    if (table === "profiles") return send({ id: user.id, is_staff: user.name !== "customer", must_change_password: false });
    if (table === "staff_roles") return send(user.roles.map((role) => ({ role })));
    if (table === "payroll_runs") return send(user.name === "empty" ? [] : [{ id: runId, document_number: "PAY-FIXTURE-1", period_start: "2026-10-01", period_end: "2026-10-31", currency: "USD", total_net: 80, status: "submitted" }]);
    if (table === "payroll_lines") return send(user.name === "empty" ? [] : [{ id: lineId, employee_id: employeeId, gross_amount: 100, deductions_amount: 20, net_amount: 80, currency: "USD", payroll_run_id: runId, funded_at: s.funded ? new Date().toISOString() : null, payment_journal_id: s.funded ? journalId : null }]);
    if (table === "payroll_deduction_lines") return send([{ id: itemId, payroll_line_id: lineId, label: "Advance", amount: 20 }]);
    if (table === "hr_payslip_schedules") return send(s.schedules);
    if (table === "stock_item_shop_merch") return send(s.carousel);
    return send([]);
  }
  send({ message: "fixture route not found" }, 404);
});
server.listen(port, host, () => console.log(`Synthetic staff browser backend: ${origin}`));
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => server.close(() => process.exit(0)));
