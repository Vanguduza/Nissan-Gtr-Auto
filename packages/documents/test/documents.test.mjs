import test from "node:test";
import assert from "node:assert/strict";
import { buildEmployeeQrPayload, renderEmployeeQrSvg, renderIdCardHtml, buildPayslipExportPayload } from "../src/index.ts";

test("employee references contain no authentication credential and encode punctuation", () => {
  assert.equal(buildEmployeeQrPayload({employeeCode: "GTR A/12"}), "gtr://employee/GTR%20A%2F12");
  assert.equal(buildEmployeeQrPayload({employeeCode: ""}), "");
});
test("only the approved authenticated verification route is emitted as a web QR", () => {
  const good = "https://nissangtrauto.co.zw/staff/verify/a1234567-1234-4234-8234-123456789012";
  assert.equal(buildEmployeeQrPayload({employeeCode: "E1", verifyUrl: good}), good);
  for (const verifyUrl of ["javascript:alert(1)", "https://evil.example/staff/verify/a1234567-1234-4234-8234-123456789012", good + "?token=secret", good.replace("https:","http:")])
    assert.equal(buildEmployeeQrPayload({employeeCode: "E1", verifyUrl}), "gtr://employee/E1");
});
test("ID-card front/back contains a rendered QR and escapes staff text", () => {
  const svg = renderEmployeeQrSvg("gtr://employee/E1");
  assert.match(svg, /<svg/);
  assert.match(svg, /(?:<path|<rect)/);
  const html = renderIdCardHtml({storeName:"Nissan GTR Auto",fullName:'<script>alert("x")</script>',roleTitle:"Sales",employeeCode:"E1"});
  assert.match(html, /data:image\/svg\+xml/);
  assert.match(html, /&lt;script&gt;/);
  assert.equal(html.includes('<script>alert("x")</script>'), false);
});
test("payslip fallback uses gross minus explicit manual deductions", () => {
  const result = buildPayslipExportPayload({storeName:"Nissan GTR Auto",employeeName:"Staff",employeeCode:"E1",periodStart:"2026-01-01",periodEnd:"2026-01-31",currency:"USD",grossPay:100,manualDeductions:[{label:"Advance",amount:20}],netPay:undefined});
  assert.equal(result.netPay,80);
  assert.deepEqual(result.manualDeductions,[{label:"Advance",amount:20}]);
});
