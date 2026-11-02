import assert from "node:assert/strict";
import { PDFDict, PDFDocument, PDFName } from "npm:pdf-lib@1.17.1";
import { buildIdCardPdf, mmToPt } from "./branded_docs_pdf.ts";
import { brandedLogoBytes } from "./branded_logo.ts";

const card = {
  storeName: "Nissan GTR Auto",
  fullName: "Staff Member",
  roleTitle: "Sales",
  employeeCode: "GTRC1999",
  verifyUrl: "https://nissangtrauto.co.zw/staff/verify/a1234567-1234-4234-8234-123456789012",
};

Deno.test("employee card PDF has CR80 front and back with a real QR", async () => {
  const bytes = await buildIdCardPdf(card);
  const pdf = await PDFDocument.load(bytes);
  assert.equal(pdf.getPageCount(), 2);
  for (const page of pdf.getPages()) {
    assert.ok(Math.abs(page.getWidth() - mmToPt(85.6)) < 0.01);
    assert.ok(Math.abs(page.getHeight() - mmToPt(54)) < 0.01);
  }
  assert.ok(bytes.length > 10_000);
});

Deno.test("employee card embeds authorized image bytes without a remote fetch", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = () => Promise.reject(new Error("PDF rendering must not fetch remote images"));
  try {
    const pdf = await PDFDocument.load(await buildIdCardPdf({ ...card, photoBytes: brandedLogoBytes() }));
    assert.equal(pdf.getPageCount(), 2);
    const xObjects = pdf.getPages()[0].node.Resources()?.lookup(PDFName.of("XObject"), PDFDict);
    assert.ok(xObjects && xObjects.keys().length >= 2, "official logo and staff photo are embedded");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
