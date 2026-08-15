/**
 * Assert master-stock report helpers (no vitest in apps/web yet).
 * Run: node apps/web/scripts/assert-master-stock-report.mjs
 */

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../../..");

function masterStockRpcArgs(filters, limit) {
  const q = filters.query?.trim() || undefined;
  const chassis = filters.chassisCode?.trim() || undefined;
  const cat = filters.categorySlug?.trim() || undefined;
  const sub = filters.subcategorySlug?.trim() || undefined;
  const args = { p_limit: limit };
  if (q) args.p_query = q;
  if (chassis) args.p_chassis_code = chassis;
  if (cat && !sub) args.p_category_needles = [`needle:${cat}`];
  if (sub) args.p_subcategory_needles = [`needle:${sub}`];
  return args;
}

function masterStockToCsvRows(rows) {
  return rows.map((r) => [
    r.oem_part_number,
    r.description ?? "",
    r.chassis_codes ?? "",
    r.category_name ?? "",
    r.subcategory_name ?? "",
    Number(r.qty_total) || 0,
    Number(r.qty_wh1) || 0,
    Number(r.qty_wh2) || 0,
  ]);
}

function sumMasterStockQty(rows) {
  return rows.reduce(
    (acc, r) => ({
      total: acc.total + (Number(r.qty_total) || 0),
      wh1: acc.wh1 + (Number(r.qty_wh1) || 0),
      wh2: acc.wh2 + (Number(r.qty_wh2) || 0),
    }),
    { total: 0, wh1: 0, wh2: 0 },
  );
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

// Empty filters → limit only
{
  const a = masterStockRpcArgs({}, 500);
  assert(a.p_limit === 500 && !a.p_query && !a.p_chassis_code, "empty filters");
}

// Parent category only
{
  const a = masterStockRpcArgs({ categorySlug: "brakes" }, 200);
  assert(
    Array.isArray(a.p_category_needles) && !a.p_subcategory_needles,
    "parent category needles",
  );
}

// Subcategory wins for needles channel
{
  const a = masterStockRpcArgs(
    { categorySlug: "brakes", subcategorySlug: "brake-pads" },
    200,
  );
  assert(
    !a.p_category_needles && Array.isArray(a.p_subcategory_needles),
    "subcategory needles only",
  );
}

// CSV shape + sums
{
  const rows = [
    {
      oem_part_number: "16546-XXXX",
      description: "Oil filter",
      chassis_codes: "D40",
      category_name: "Filters",
      subcategory_name: "Oil filters",
      qty_total: 10,
      qty_wh1: 4,
      qty_wh2: 6,
    },
    {
      oem_part_number: "=CMD",
      description: null,
      chassis_codes: null,
      category_name: null,
      subcategory_name: null,
      qty_total: 1,
      qty_wh1: 1,
      qty_wh2: 0,
    },
  ];
  const csv = masterStockToCsvRows(rows);
  assert(csv.length === 2 && csv[0][0] === "16546-XXXX", "csv oem");
  assert(csv[1][0] === "=CMD", "csv preserves oem for esc layer");
  const sums = sumMasterStockQty(rows);
  assert(sums.total === 11 && sums.wh1 === 5 && sums.wh2 === 6, "qty sums");
}

// pathAccessFor must match list_master_stock roles (not warehouse-only parent gate)
{
  const auth = readFileSync(join(root, "apps/web/lib/staff-auth.ts"), "utf8");
  assert(
    auth.includes('path === "/staff/warehouse/master-stock"') ||
      auth.includes('path.startsWith("/staff/warehouse/master-stock")'),
    "pathAccessFor must special-case master-stock",
  );
  const gateIdx = auth.indexOf('path === "/staff/warehouse/master-stock"');
  const fallbackIdx = auth.indexOf('path.startsWith("/staff/warehouse")');
  assert(gateIdx > 0 && gateIdx < fallbackIdx, "master-stock gate before warehouse fallback");
  const slice = auth.slice(gateIdx, gateIdx + 280);
  assert(slice.includes('"sales"') && slice.includes('"finance"'), "master-stock roles include sales|finance");
}

console.log("assert-master-stock-report: OK");
