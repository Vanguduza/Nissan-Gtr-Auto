export { epcBox, type EpcBox } from "@/lib/epc-box";

export const sameOem = (a: string | null | undefined, b: string | null | undefined) =>
  !!a && !!b && a.trim().toUpperCase() === b.trim().toUpperCase();
