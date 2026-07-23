import type { ValuationMethod } from "./types.js";

/** Canonical QR payload — Bridge-First scan/print only. */
export function buildInventoryQrPayload(params: {
  oemPartNumber: string;
  batchCode: string;
  valuation: ValuationMethod;
}): string {
  const oem = params.oemPartNumber.trim();
  const batch = params.batchCode.trim();
  if (!oem || !batch) {
    throw new Error("oemPartNumber and batchCode are required");
  }
  return `gtr://part/${oem}?batch=${encodeURIComponent(batch)}&valuation=${params.valuation}`;
}

export function parseInventoryQrPayload(payload: string): {
  oemPartNumber: string;
  batchCode: string;
  valuation: ValuationMethod;
} {
  const m = /^gtr:\/\/part\/([^?]+)\?batch=([^&]+)&valuation=(FIFO|AVG)$/.exec(
    payload.trim(),
  );
  if (!m) {
    throw new Error(`Invalid inventory QR payload: ${payload}`);
  }
  return {
    oemPartNumber: decodeURIComponent(m[1]),
    batchCode: decodeURIComponent(m[2]),
    valuation: m[3] as ValuationMethod,
  };
}

/** Convert qty in `from` UOM to base using factor (from→base). */
export function convertQtyToBase(qty: number, factorToBase: number): number {
  if (qty <= 0) throw new Error("qty must be positive");
  if (factorToBase <= 0) throw new Error("factor must be positive");
  return Math.round(qty * factorToBase * 1000) / 1000;
}
