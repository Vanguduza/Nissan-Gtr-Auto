/**
 * Simulate HR onboarding completion for a sample driver and render the
 * real CR80 ID card HTML from @gtr/documents (same layout tokens as Edge PDF).
 *
 * Usage (repo root):
 *   node --experimental-strip-types scripts/simulate-hr-onboarding-id-card.mjs
 *
 * Output:
 *   docs/previews/hr-onboarding-id-card-driver.html
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, "..");
const docsPkg = join(root, "packages", "documents", "src", "index.ts");

const {
  buildIdCardPayload,
  formatStaffRoleLabel,
  renderIdCardHtml,
} = await import(pathToFileURL(docsPkg).href);

/** Mirrors complete_hr_onboarding + link_employee_auth_user outcome for a driver. */
const simulated = {
  full_name: "Tariro Moyo",
  role_title: "Delivery Driver",
  staff_role: "driver",
  employee_code: "GTRD1001",
  employee_id: "00000000-0000-4000-8000-sim000000001",
  email: "tariro.driver@gtr.local",
  message:
    "Simulated: payload.staff_role=driver → apply_hr_onboarding_staff_role on auth link",
};

const payload = buildIdCardPayload({
  storeName: "Nissan GTR Auto",
  fullName: simulated.full_name,
  roleTitle: simulated.role_title,
  staffRole: simulated.staff_role,
  employeeCode: simulated.employee_code,
  verifyUrl: `https://nissangtrauto.co.zw/staff/verify/${simulated.employee_id}`,
});

const html = renderIdCardHtml(payload);
const outDir = join(root, "docs", "previews");
mkdirSync(outDir, { recursive: true });
const outPath = join(outDir, "hr-onboarding-id-card-driver.html");
writeFileSync(outPath, html, "utf8");

const metaPath = join(outDir, "hr-onboarding-id-card-driver.json");
writeFileSync(
  metaPath,
  JSON.stringify(
    {
      ...simulated,
      staff_role_label: formatStaffRoleLabel(simulated.staff_role),
      preview: "docs/previews/hr-onboarding-id-card-driver.html",
      card_mm: { width: 85.6, height: 54 },
      fiscal_qr: false,
    },
    null,
    2,
  ),
  "utf8",
);

console.log("Wrote", outPath);
console.log("Staff role on card:", formatStaffRoleLabel(simulated.staff_role));
console.log("Open file:///" + outPath.replace(/\\/g, "/"));
