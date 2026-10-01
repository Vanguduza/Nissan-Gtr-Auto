import type { PosGateway } from "@/lib/pos/gateway";
import { roundMoney } from "@/lib/pos/money";
import type {
  CartLine,
  EpcDiagram,
  GarageVehicle,
  ParkedCart,
  PopularPin,
  PosCart,
  PosCustomer,
  PosPart,
  PosResult,
  Quotation,
  RecentInvoice,
  ReceiptDocument,
  SelectedVehicle,
  VehicleModel,
  VehicleVariant,
} from "@/lib/pos/types";

/**
 * DEVELOPMENT-ONLY preview data so the benchmark POS can be built and screenshot without a
 * Supabase backend. Enabled only by `NEXT_PUBLIC_POS_PREVIEW=1` outside production; `next.config.ts`
 * refuses a production build with the flag set, and the UI labels the screen "Preview data".
 * Equivalent of the tablet's `FakeRpcClient`. Never a source of business truth.
 *
 * Preview manager credentials: identifier "manager", password "preview".
 */
const MODELS: VehicleModel[] = [
  { slug: "gt-r", name: "GT-R", yearStart: 2007, yearEnd: null },
  { slug: "navara", name: "Navara", yearStart: 2005, yearEnd: null },
  { slug: "x-trail", name: "X-Trail", yearStart: 2001, yearEnd: null },
];
const VARIANTS: Record<string, VehicleVariant[]> = {
  "gt-r": [{ slug: "r35-vr38", chassisCode: "R35", engineCode: "VR38DETT", yearLabel: "2007–" }],
  navara: [
    { slug: "d40-yd25", chassisCode: "D40", engineCode: "YD25DDTi", yearLabel: "2005–2015" },
    { slug: "d40-qr25", chassisCode: "D40", engineCode: "QR25DE", yearLabel: "2005–2015" },
    { slug: "d23-yd25", chassisCode: "D23", engineCode: "YD25DDTi", yearLabel: "2015–" },
  ],
  "x-trail": [
    { slug: "t31-mr20", chassisCode: "T31", engineCode: "MR20DE", yearLabel: "2007–2013" },
    { slug: "t32-qr25", chassisCode: "T32", engineCode: "QR25DE", yearLabel: "2014–" },
  ],
};
const PARTS: PosPart[] = [
  { stockItemId: "p-1", oemPartNumber: "15208-65F0A", name: "Oil Filter", price: { amount: 12.5, currency: "USD" }, saleableQty: 42, imageUrl: null, categoryName: "Engine & Drivetrain" },
  { stockItemId: "p-2", oemPartNumber: "D1060-JF00A", name: "Front Brake Pad Set", price: { amount: 96, currency: "USD" }, saleableQty: 8, imageUrl: null, categoryName: "Brakes" },
  { stockItemId: "p-3", oemPartNumber: "16546-JF00A", name: "Air Filter", price: { amount: 24, currency: "USD" }, saleableQty: 15, imageUrl: null, categoryName: "Engine & Drivetrain" },
  { stockItemId: "p-4", oemPartNumber: "22401-JF01B", name: "Spark Plug (Iridium)", price: { amount: 19.5, currency: "USD" }, saleableQty: 60, imageUrl: null, categoryName: "Electrical" },
  { stockItemId: "p-5", oemPartNumber: "54618-JF00B", name: "Front Stabiliser Link", price: { amount: 38, currency: "USD" }, saleableQty: 4, imageUrl: null, categoryName: "Suspension" },
  { stockItemId: "p-6", oemPartNumber: "21430-JF00A", name: "Radiator Assembly", price: { amount: 410, currency: "USD" }, saleableQty: 0, imageUrl: null, categoryName: "Engine & Drivetrain" },
];
const DIAGRAM: EpcDiagram = {
  title: "Front brake",
  // Pixel callouts on a 480×320 source with no stored size: the loaded image decides (as seeded data does).
  imageUrl: "/pos/epc-front-brake.svg",
  width: null,
  height: null,
  hotspots: [
    { oem: "D1060-JF00A", pnc: "41060", x: 214, y: 96, w: 70, h: 120 },
    { oem: "40206-JF00A", pnc: "40206", x: 40, y: 40, w: 150, h: 240 },
    { oem: "41001-JF00A", pnc: "41001", x: 300, y: 70, w: 140, h: 170 },
  ],
  parts: [
    { oemPartNumber: "D1060-JF00A", pnc: "41060", name: "Front Brake Pad Set", categoryName: "Brakes", subcategoryName: "Front brake" },
    { oemPartNumber: "40206-JF00A", pnc: "40206", name: "Front Rotor", categoryName: "Brakes", subcategoryName: "Front brake" },
    { oemPartNumber: "41001-JF00A", pnc: "41001", name: "Front Caliper (L)", categoryName: "Brakes", subcategoryName: "Front brake" },
  ],
};

const ok = <T,>(data: T): Promise<PosResult<T>> => Promise.resolve({ ok: true, data });
const no = <T,>(error: string): Promise<PosResult<T>> => Promise.resolve({ ok: false, error });
const managerOk = (m: { identifier: string; password: string }) => m.identifier.trim() === "manager" && m.password === "preview";

export function createPreviewPosGateway(): PosGateway {
  let pins: PopularPin[] = [];
  const hidden = new Set<string>();
  let seq = 1;
  const nextDoc = (prefix: string) => `${prefix}-PREVIEW-${String(seq++).padStart(4, "0")}`;
  const emptyCart = (): PosCart => ({
    id: `cart-${seq++}`,
    documentNumber: null,
    status: "open",
    currency: "USD",
    warehouseId: "wh-main",
    fulfillmentMode: "immediate",
    lines: [],
    customerId: null,
    customerName: null,
    vehicle: null,
    vehicles: [],
  });
  const carts = new Map<string, PosCart>();
  const customers: PosCustomer[] = [
    { id: "c-1", kind: "individual", displayName: "Tendai Moyo", businessName: null, email: "tendai@example.com", phoneE164: "+263771000001", whatsappE164: "+263771000001" },
    { id: "c-2", kind: "business", displayName: "Rumbi Chari", businessName: "Harare Fleet Services", email: "fleet@example.com", phoneE164: "+263242000002", whatsappE164: null },
  ];
  const garage = new Map<string, GarageVehicle[]>([
    ["c-1", [{ id: "g-1", modelSlug: "navara", model: "Navara", generation: "D40 · 2005–2015", chassisCode: "D40", engine: "YD25DDTi", vin: null, isPrimary: true }]],
  ]);
  const quotations: Quotation[] = [];
  const quoteLines = new Map<string, CartLine[]>();
  const invoices = new Map<string, ReceiptDocument>();
  const recent: RecentInvoice[] = [];

  const save = (c: PosCart) => {
    carts.set(c.id, c);
    return c;
  };
  const get = (id: string) => carts.get(id) ?? save({ ...emptyCart(), id });
  const total = (c: PosCart) => roundMoney(c.lines.reduce((s, l) => s + l.lineTotal, 0));

  const gateway: PosGateway = {
    isPreview: true,
    listModels: () => ok(MODELS),
    listVariants: (slug) => ok(VARIANTS[slug] ?? []),
    searchParts: (query) => {
      const q = query.trim().toLowerCase();
      return ok(PARTS.filter((p) => !q || `${p.name} ${p.oemPartNumber} ${p.categoryName}`.toLowerCase().includes(q)));
    },
    listBestSellers: () => ok(PARTS.slice(0, 5)),
    listPins: () => ok(pins),
    pin: (pin) => {
      pins = [pin, ...pins.filter((p) => !(p.kind === pin.kind && p.key === pin.key))];
      return ok(true as const);
    },
    unpin: (pin) => {
      pins = pins.filter((p) => !(p.kind === pin.kind && p.key === pin.key));
      return ok(true as const);
    },
    listHiddenBestSellers: () => ok([...hidden]),
    hideBestSeller: (id) => {
      hidden.add(id);
      return ok(true as const);
    },
    unhideBestSeller: (id) => {
      hidden.delete(id);
      return ok(true as const);
    },

    listWarehouses: () => ok([{ id: "wh-main", code: "WH2", name: "Main counter" }, { id: "wh-yard", code: "WH3", name: "Yard store" }]),
    openCart: (setup) => ok(save({ ...emptyCart(), currency: setup.currency, warehouseId: setup.warehouseId ?? "wh-main", fulfillmentMode: setup.fulfillmentMode })),
    createScanSession: (cartId) => {
      get(cartId);
      return ok({ sessionId: `preview-${cartId}`, pairingCode: "482 913", expiresAt: new Date(Date.now() + 10 * 60_000).toISOString() });
    },
    revokeScanSession: () => ok(true as const),
    // Preview has no phone and no realtime; nothing ever changes remotely.
    watchCompanion: () => () => {},
    loadCart: (id) => ok(get(id)),
    addPart: (cartId, part, qty) => {
      const c = get(cartId);
      const price = part.price?.amount ?? 0;
      const existing = c.lines.find((l) => l.oemPartNumber === part.oemPartNumber);
      const lines = existing
        ? c.lines.map((l) => (l === existing ? { ...l, qty: l.qty + qty, lineTotal: roundMoney(l.unitPrice * (l.qty + qty)) } : l))
        : [
            ...c.lines,
            {
              id: `line-${seq++}`,
              stockItemId: part.stockItemId ?? part.oemPartNumber,
              oemPartNumber: part.oemPartNumber,
              name: part.name,
              qty,
              unitPrice: price,
              lineTotal: roundMoney(price * qty),
              imageUrl: part.imageUrl,
            },
          ];
      return ok(save({ ...c, lines }));
    },
    setLineQty: (cartId, lineId, qty) => {
      const c = get(cartId);
      const lines =
        qty > 0
          ? c.lines.map((l) => (l.id === lineId ? { ...l, qty, lineTotal: roundMoney(l.unitPrice * qty) } : l))
          : c.lines.filter((l) => l.id !== lineId);
      return ok(save({ ...c, lines }));
    },
    removeLine: (cartId, lineId) => {
      const c = get(cartId);
      return ok(save({ ...c, lines: c.lines.filter((l) => l.id !== lineId) }));
    },
    setCartVehicle: (cartId, vehicle: SelectedVehicle | null) => {
      const c = get(cartId);
      const vehicles = vehicle && !c.vehicles.some((v) => v.chassisCode === vehicle.chassisCode && v.engineCode === vehicle.engineCode)
        ? [...c.vehicles, vehicle]
        : c.vehicles;
      return ok(save({ ...c, vehicle, vehicles }));
    },
    voidCart: (cartId, manager) => {
      if (!managerOk(manager)) return no("Manager sign-in failed.");
      carts.delete(cartId);
      return ok(true as const);
    },
    applyDiscount: (cartId, percent, manager) => {
      if (!managerOk(manager)) return no("Manager sign-in failed.");
      const c = get(cartId);
      const lines = c.lines.map((l) => {
        const unit = roundMoney(l.unitPrice * (1 - percent / 100));
        return { ...l, unitPrice: unit, lineTotal: roundMoney(unit * l.qty) };
      });
      return ok(save({ ...c, lines }));
    },
    overrideLinePrice: (cartId, lineId, unitPrice, manager) => {
      if (!managerOk(manager)) return no("Manager sign-in failed.");
      const c = get(cartId);
      const lines = c.lines.map((l) => (l.id === lineId ? { ...l, unitPrice, lineTotal: roundMoney(unitPrice * l.qty) } : l));
      return ok(save({ ...c, lines }));
    },

    searchCustomers: (query) => {
      const q = query.trim().toLowerCase();
      return ok(customers.filter((c) => !q || `${c.displayName} ${c.businessName ?? ""} ${c.email ?? ""} ${c.phoneE164 ?? ""}`.toLowerCase().includes(q)));
    },
    createCustomer: (input) => {
      const c = { id: `c-${seq++}`, ...input };
      customers.push(c);
      return ok(c);
    },
    updateCustomer: (id, input) => {
      const i = customers.findIndex((c) => c.id === id);
      if (i < 0) return no("Customer not found.");
      customers[i] = { id, ...input };
      return ok(customers[i]);
    },
    setCartCustomer: (cartId, customerId) => {
      const c = get(cartId);
      const cust = customers.find((x) => x.id === customerId) ?? null;
      return ok(save({ ...c, customerId: cust?.id ?? null, customerName: cust ? cust.businessName || cust.displayName : null }));
    },
    listGarage: (customerId) => ok(garage.get(customerId) ?? []),
    saveGarageVehicle: (customerId, v, isPrimary) => {
      const list = (garage.get(customerId) ?? []).map((g) => (isPrimary ? { ...g, isPrimary: false } : g));
      list.push({ id: `g-${seq++}`, modelSlug: v.modelSlug, model: v.modelName, generation: v.generation, chassisCode: v.chassisCode, engine: v.engineCode, vin: null, isPrimary });
      garage.set(customerId, list);
      return ok(true as const);
    },

    checkout: (cartId, tenders) => {
      const c = get(cartId);
      const due = total(c);
      const paid = roundMoney(tenders.reduce((s, t) => s + t.amount, 0));
      if (c.lines.length === 0) return no("The sale is empty.");
      if (Math.abs(paid - due) > 0.004) return no(`Tenders ${paid.toFixed(2)} must equal the balance ${due.toFixed(2)}.`);
      const id = `inv-${seq++}`;
      const vehicleLabel = c.vehicle ? `${c.vehicle.modelName} ${c.vehicle.chassisCode} ${c.vehicle.engineCode}` : null;
      invoices.set(id, {
        invoiceId: id,
        documentNumber: nextDoc("INV"),
        postedAt: new Date().toISOString(),
        currency: c.currency,
        customerName: c.customerName,
        lines: c.lines.map((l) => ({ name: l.name, oemPartNumber: l.oemPartNumber, qty: l.qty, unitPrice: l.unitPrice, lineTotal: l.lineTotal })),
        subtotal: due,
        total: due,
        amountPaid: paid,
        tenders,
        vehicleLabel,
        operator: "",
      });
      recent.unshift({ id, documentNumber: invoices.get(id)?.documentNumber ?? null, customerName: c.customerName, total: due, currency: c.currency, postedAt: new Date().toISOString(), vehicleLabel });
      carts.delete(cartId);
      return ok(id);
    },
    loadReceipt: (invoiceId) => {
      const r = invoices.get(invoiceId);
      return r ? ok(r) : no("Invoice not found.");
    },
    requestEcocash: (invoiceId, msisdn) => (msisdn.trim() ? ok(`ecocash-${invoiceId}`) : no("Enter the customer's EcoCash number.")),

    parkCart: (cartId) => {
      const c = get(cartId);
      save({ ...c, status: "parked", documentNumber: c.documentNumber ?? nextDoc("CART") });
      return ok(true as const);
    },
    listParked: () =>
      ok(
        [...carts.values()]
          .filter((c) => c.status === "parked")
          .map((c): ParkedCart => ({ id: c.id, documentNumber: c.documentNumber, updatedAt: new Date().toISOString(), lineCount: c.lines.length, total: total(c), currency: c.currency })),
      ),
    resumeCart: (cartId) => ok(save({ ...get(cartId), status: "open" })),
    listQuotations: () => ok(quotations),
    createQuotation: (cartId, validUntil) => {
      const c = get(cartId);
      if (c.lines.length === 0) return no("Add parts before quoting.");
      const id = `q-${seq++}`;
      quotations.unshift({ id, documentNumber: nextDoc("QUO"), status: "issued", validUntil, sentChannel: null, createdAt: new Date().toISOString(), lineCount: c.lines.length, total: total(c), currency: c.currency });
      quoteLines.set(id, c.lines);
      return ok(id);
    },
    sendQuotation: (id, channel) => {
      const q = quotations.find((x) => x.id === id);
      if (!q) return no("Quotation not found.");
      q.status = "sent";
      q.sentChannel = channel;
      return ok(true as const);
    },
    convertQuotation: (id) => {
      const q = quotations.find((x) => x.id === id);
      if (!q) return no("Quotation not found.");
      q.status = "converted";
      return ok(save({ ...emptyCart(), lines: (quoteLines.get(id) ?? []).map((l) => ({ ...l, id: `line-${seq++}` })) }));
    },

    listRecentInvoices: (query) => {
      const q = query.trim().toLowerCase();
      return ok(recent.filter((r) => !q || `${r.documentNumber} ${r.customerName ?? ""}`.toLowerCase().includes(q)));
    },
    refundInvoice: (invoiceId, manager) => {
      if (!managerOk(manager)) return no("Manager sign-in failed.");
      const i = recent.findIndex((r) => r.id === invoiceId);
      if (i < 0) return no("Invoice not found.");
      recent.splice(i, 1);
      return ok(nextDoc("RET"));
    },

    listEpcVariants: (slug) => ok(VARIANTS[slug] ?? []),
    listEpcSections: () => ok([{ slug: "brakes", name: "Brakes", thumbnailUrl: null }, { slug: "suspension", name: "Front suspension", thumbnailUrl: null }]),
    listEpcDiagrams: () => ok([{ id: "preview-front-brake", slug: "front-brake", title: "Front brake", imageUrl: null }]),
    getEpcDiagram: () => ok(DIAGRAM),

    operatorLabel: () => Promise.resolve("Preview operator"),
    reauthenticate: (password) => (password ? ok(true as const) : no("Password required.")),
  };
  return gateway;
}

/** Preview is never available in production builds, whatever the env says. */
export function isPosPreviewEnabled(): boolean {
  return process.env.NODE_ENV !== "production" && process.env.NEXT_PUBLIC_POS_PREVIEW === "1";
}
