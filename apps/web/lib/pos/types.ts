/**
 * Web POS domain types. These mirror the tablet POS (`PosViewModel` / `RpcClient`) so both
 * runtimes describe the same product (owner decision D7, `docs/design/pos/WEB_POS_PARITY.md`).
 */

export type PosCurrency = "USD" | "ZIG";

export type Money = { amount: number; currency: PosCurrency };

export type PosPart = {
  stockItemId: string | null;
  oemPartNumber: string;
  name: string;
  price: Money | null;
  saleableQty: number | null;
  imageUrl: string | null;
  categoryName?: string | null;
};

/** Model → Generation → Engine. Make is never part of the cascade (owner decision D2). */
export type VehicleModel = { slug: string; name: string; yearStart: number | null; yearEnd: number | null };
export type VehicleVariant = {
  slug: string;
  chassisCode: string;
  engineCode: string | null;
  yearLabel: string | null;
};
export type VehicleGeneration = { chassisCode: string; label: string };
export type SelectedVehicle = {
  modelSlug: string;
  modelName: string;
  generation: string;
  chassisCode: string;
  engineCode: string;
};

export type PopularPinKind = "part" | "model" | "category" | "subcategory";

export type PopularPin = {
  kind: PopularPinKind;
  key: string;
  label: string;
  subtitle: string | null;
  searchQuery: string;
  oemPartNumber: string | null;
  imageUrl: string | null;
};

/** One card in the Popular Items row: an operator pin or a server best seller (D1). */
export type PopularRowItem =
  | { source: "pin"; key: string; pin: PopularPin }
  | { source: "bestseller"; key: string; part: PosPart };

export type CartLine = {
  id: string;
  stockItemId: string;
  oemPartNumber: string;
  name: string;
  qty: number;
  unitPrice: number;
  lineTotal: number;
  imageUrl: string | null;
};

export type PosCart = {
  id: string;
  currency: PosCurrency;
  lines: CartLine[];
  customerName: string | null;
  vehicle: SelectedVehicle | null;
};

export type PosResult<T> = { ok: true; data: T } | { ok: false; error: string };
