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
  documentNumber: string | null;
  status: string;
  currency: PosCurrency;
  warehouseId: string | null;
  fulfillmentMode: FulfillmentMode;
  lines: CartLine[];
  customerId: string | null;
  customerName: string | null;
  vehicle: SelectedVehicle | null;
  /** Every vehicle shopped for in this sale (multi-vehicle context). */
  vehicles: SelectedVehicle[];
};

export type PosResult<T> = { ok: true; data: T } | { ok: false; error: string };

// ───────── Sale setup (Quick Sale) ─────────
export type FulfillmentMode = "immediate" | "dispatch";
export type Warehouse = { id: string; code: string; name: string };
export type SaleSetup = { warehouseId: string | null; currency: PosCurrency; fulfillmentMode: FulfillmentMode };

// ───────── Customers + garage ─────────
export type CustomerKind = "individual" | "business";
export type PosCustomer = {
  id: string;
  kind: CustomerKind;
  displayName: string;
  businessName: string | null;
  email: string | null;
  phoneE164: string | null;
  whatsappE164: string | null;
};
export type CustomerInput = Omit<PosCustomer, "id">;
export type GarageVehicle = {
  id: string;
  modelSlug: string | null;
  model: string | null;
  generation: string | null;
  chassisCode: string | null;
  engine: string | null;
  vin: string | null;
  isPrimary: boolean;
};

// ───────── Payment + receipt ─────────
/** `public.payment_tender` values usable at the counter (ContiPay/Paynow are online PSP flows). */
export type Tender = "cash" | "bank" | "ecocash" | "store_credit";
export type TenderLine = { tender: Tender; amount: number };
export type ReceiptContacts = { email: string | null; whatsappE164: string | null; phoneE164: string | null };
export type ReceiptLine = { name: string; oemPartNumber: string; qty: number; unitPrice: number; lineTotal: number };
export type ReceiptDocument = {
  invoiceId: string;
  documentNumber: string | null;
  postedAt: string | null;
  currency: PosCurrency;
  customerName: string | null;
  lines: ReceiptLine[];
  subtotal: number;
  total: number;
  amountPaid: number;
  tenders: TenderLine[];
  vehicleLabel: string | null;
  operator: string;
};

// ───────── Manager approval ─────────
export type ManagerCredentials = { identifier: string; password: string };

// ───────── Orders + returns ─────────
export type QuotationStatus = "draft" | "issued" | "sent" | "converted" | "cancelled" | "expired";
export type Quotation = {
  id: string;
  documentNumber: string | null;
  status: QuotationStatus;
  validUntil: string | null;
  sentChannel: string | null;
  createdAt: string;
  lineCount: number;
  total: number;
  currency: PosCurrency;
};
export type ParkedCart = { id: string; documentNumber: string | null; updatedAt: string; lineCount: number; total: number; currency: PosCurrency };
export type RecentInvoice = {
  id: string;
  documentNumber: string | null;
  customerName: string | null;
  total: number;
  currency: PosCurrency;
  postedAt: string | null;
  vehicleLabel: string | null;
};

// ───────── EPC ─────────
export type EpcSection = { slug: string; name: string; thumbnailUrl: string | null };
/** [id] is the full-catalogue diagram id that keys its R2 part shard and image. */
export type EpcDiagramRef = { id: string | null; slug: string; title: string; imageUrl: string | null };
export type EpcHotspot = { oem: string; pnc: string | null; x: number; y: number; w: number; h: number };
export type EpcDiagramPart = {
  oemPartNumber: string;
  pnc: string | null;
  /** Reference number printed on the diagram artwork. */
  ref?: string | null;
  name: string;
  categoryName: string | null;
  subcategoryName: string | null;
};
export type EpcDiagram = {
  title: string;
  imageUrl: string | null;
  width: number | null;
  height: number | null;
  hotspots: EpcHotspot[];
  parts: EpcDiagramPart[];
  /** Why parts or the image are missing (e.g. not yet published to the live catalogue). */
  notice?: string | null;
};
