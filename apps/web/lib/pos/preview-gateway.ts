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
  ReasonCode,
  ReceiptDocument,
  RecentInvoice,
  SelectedVehicle,
  TillSession,
  VehicleModel,
  VehicleVariant,
  ApprovalPolicy,
  Governed,
  DigitalProvider,
  PaymentStatus,
  PosCurrency,
  TenderLine,
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
  // Preview policies: discount up to 5% needs no manager, everything else does (same table as the server).
  const policies: ApprovalPolicy[] = [
    "cash_out", "core_return", "discount_percent", "price_override_delta_percent", "refund_full_invoice",
    "return_post", "till_variance", "void_cart", "warranty_decision",
  ].map((action) => ({
    action,
    thresholdValue: action === "discount_percent" ? 5 : 0,
    alwaysRequireManager: action !== "discount_percent",
    reasonRequired: true,
    updatedAt: null,
  }));
  /** Same order as the governed RPCs: reason first, then the manager when policy asks for one. */
  const governedRefusal = (action: string, g: Governed, value = 0): string | null => {
    if (!g.reasonCode.trim()) return `valid active reason code required for ${action}`;
    if (g.manager && !managerOk(g.manager)) return "Manager sign-in failed.";
    const p = policies.find((x) => x.action === action);
    if (!g.manager && (!p || p.alwaysRequireManager || value > p.thresholdValue)) return "POS manager approval required";
    return null;
  };
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
    tillSessionId: null,
  });
  const carts = new Map<string, PosCart>();
  // Till: one preview drawer; cash sales and movements feed its expected cash.
  const tills: TillSession[] = [];
  let tillCash = 0;
  const openTillOf = () => tills.find((t) => t.status !== "closed") ?? null;
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

  // Reserve-first checkout: one order per cart attempt, settled by manual tenders or a preview provider.
  type PreviewOrder = {
    orderId: string;
    cartId: string;
    requestId: string;
    state: string;
    total: number;
    currency: PosCurrency;
    expiresAt: number;
    provider: DigitalProvider | null;
    intentId: string | null;
    providerStatus: string | null;
    providerFailure: string | null;
    resolveAt: number;
    outcome?: "settled" | "failed";
    invoiceId: string | null;
    settledProvider: string | null;
    exception: string | null;
  };
  const orders = new Map<string, PreviewOrder>();
  const settlements = new Map<string, { invoiceId: string; state: string }>();
  const isLive = (o: PreviewOrder) =>
    (o.state === "awaiting_payment" || o.state === "payment_processing" || o.state === "payment_failed") && o.expiresAt > Date.now();
  /** The provider's webhook, simulated: settles or fails the order once its time comes. */
  const tickProvider = (o: PreviewOrder) => {
    if (o.state !== "payment_processing" || Date.now() < o.resolveAt) return;
    if (o.outcome === "failed") {
      Object.assign(o, { state: "payment_failed", providerStatus: "failed", providerFailure: "Insufficient funds in the wallet." });
      return;
    }
    o.invoiceId = postInvoice(o.cartId, [{ tender: (o.provider ?? "ecocash") === "ecocash" ? "ecocash" : "bank", amount: o.total }]);
    Object.assign(o, { state: "paid", providerStatus: "settled", settledProvider: o.provider });
  };
  const statusOf = (o: PreviewOrder): PaymentStatus => ({
    orderId: o.orderId,
    cartId: o.cartId,
    state: o.state,
    total: o.total,
    currency: o.currency,
    reservationExpiresAt: new Date(o.expiresAt).toISOString(),
    activeProvider: o.provider,
    activeIntentId: o.intentId,
    providerStatus: o.providerStatus,
    providerFailure: o.providerFailure,
    settledProvider: o.settledProvider,
    settledProviderRef: o.invoiceId ? `PREVIEW-${o.orderId}` : null,
    salesInvoiceId: o.invoiceId,
    paymentException: o.exception,
    exceptions: [],
  });
  /** Post the cart as an invoice (same shape the server returns); cash feeds the open till. */
  const postInvoice = (cartId: string, tenders: TenderLine[]): string => {
    const c = get(cartId);
    const due = total(c);
    const id = `inv-${seq++}`;
    if (c.tillSessionId) tillCash += tenders.filter((t) => t.tender === "cash").reduce((sum, t) => sum + t.amount, 0);
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
      amountPaid: roundMoney(tenders.reduce((sum, t) => sum + t.amount, 0)),
      tenders,
      vehicleLabel,
      operator: "",
    });
    recent.unshift({ id, documentNumber: invoices.get(id)?.documentNumber ?? null, customerName: c.customerName, total: due, currency: c.currency, postedAt: new Date().toISOString(), vehicleLabel });
    carts.delete(cartId);
    return id;
  };

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
    voidCart: (cartId, g) => {
      const refused = governedRefusal("void_cart", g);
      if (refused) return no(refused);
      carts.delete(cartId);
      return ok(true as const);
    },
    applyDiscount: (cartId, percent, g) => {
      const refused = governedRefusal("discount_percent", g, percent);
      if (refused) return no(refused);
      const c = get(cartId);
      const lines = c.lines.map((l) => {
        const unit = roundMoney(l.unitPrice * (1 - percent / 100));
        return { ...l, unitPrice: unit, lineTotal: roundMoney(unit * l.qty) };
      });
      return ok(save({ ...c, lines }));
    },
    overrideLinePrice: (cartId, lineId, unitPrice, g) => {
      const refused = governedRefusal("price_override_delta_percent", g);
      if (refused) return no(refused);
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

    prepareCheckout: (cartId, requestId) => {
      const c = get(cartId);
      if (c.lines.length === 0) return no("cart has no lines");
      if (!c.tillSessionId) return no("open till session required before payment");
      const existing = [...orders.values()].find((o) => o.cartId === cartId && (o.requestId === requestId || isLive(o)));
      if (existing && isLive(existing)) return ok(existing.orderId);
      const o: PreviewOrder = {
        orderId: `order-${seq++}`,
        cartId,
        requestId,
        state: "awaiting_payment",
        total: total(c),
        currency: c.currency,
        expiresAt: Date.now() + 20 * 60_000,
        provider: null,
        intentId: null,
        providerStatus: null,
        providerFailure: null,
        resolveAt: 0,
        invoiceId: null,
        settledProvider: null,
        exception: null,
      };
      orders.set(o.orderId, o);
      return ok(o.orderId);
    },
    paymentStatus: (orderId) => {
      const o = orders.get(orderId);
      if (!o) return no("commerce order not found");
      tickProvider(o);
      return ok(statusOf(o));
    },
    settleTenders: (orderId, paymentRequestId, tenders) => {
      const o = orders.get(orderId);
      if (!o) return no("commerce order not found");
      const done = settlements.get(paymentRequestId);
      if (done) return ok(done);
      if (o.expiresAt <= Date.now()) return no("commerce reservation expired");
      if (o.state !== "awaiting_payment" && o.state !== "payment_failed") return no(`manual tenders cannot settle order in state ${o.state}`);
      const paid = roundMoney(tenders.reduce((sum, t) => sum + t.amount, 0));
      if (Math.abs(paid - o.total) > 0.01) return no(`manual tenders sum ${paid.toFixed(2)} must equal order total ${o.total.toFixed(2)}`);
      const invoiceId = postInvoice(o.cartId, tenders);
      Object.assign(o, { state: "paid", invoiceId, settledProvider: new Set(tenders.map((t) => t.tender)).size === 1 ? tenders[0]!.tender : "manual_split" });
      const result = { invoiceId, state: "paid" };
      settlements.set(paymentRequestId, result);
      return ok(result);
    },
    // Preview providers: EcoCash approves after a few seconds (a number ending 0 declines, 9 never
    // answers, to exercise recovery); Paynow gives a hosted page; ContiPay is not set up.
    providerAvailability: () => ok({ ecocash: null, paynow: null, contipay: "Not set up for this shop yet." }),
    startProvider: (orderId, provider, params) => {
      const o = orders.get(orderId);
      if (!o) return no("commerce order not found");
      if (o.expiresAt <= Date.now()) return no("commerce reservation expired");
      if (o.state === "payment_processing" && o.provider !== provider) return no("another payment provider is already processing");
      if (provider === "contipay") return no("CONTIPAY_API_KEY / CONTIPAY_MERCHANT_ID required");
      const digits = (params.msisdn ?? "").replace(/\D/g, "");
      if (provider === "ecocash" && !/^(263|0)7\d{8}$/.test(digits)) return no("payer_msisdn must normalize to 263XXXXXXXXX");
      Object.assign(o, {
        state: "payment_processing",
        provider,
        intentId: `intent-${seq++}`,
        providerStatus: "pending",
        providerFailure: null,
        resolveAt: digits.endsWith("9") ? Number.POSITIVE_INFINITY : Date.now() + 5_000,
        outcome: digits.endsWith("0") ? "failed" : "settled",
      });
      return ok({
        intentId: o.intentId!,
        checkoutUrl: provider === "paynow" ? `https://www.paynow.co.zw/payment/preview/${o.intentId}` : null,
        message: provider === "ecocash" ? `PIN request sent to ${params.msisdn}.` : null,
      });
    },
    cancelCheckout: (orderId) => {
      const o = orders.get(orderId);
      if (!o) return no("commerce order not found");
      if (o.state === "payment_processing") return no("payment is in flight; resolve it from recovery");
      if (o.invoiceId) return no("order already settled");
      o.state = "cancelled";
      return ok(true as const);
    },
    checkoutOnAccount: (cartId) => {
      const c = get(cartId);
      if (!c.customerId) return no("registered customer required for on-account checkout");
      if (c.customerId !== "c-2") return no("customer has no approved credit limit");
      return ok(postInvoice(cartId, []));
    },
    listPaymentRecovery: () =>
      ok(
        [...orders.values()]
          .filter((o) => o.state === "payment_processing" || o.state === "payment_failed" || o.state === "allocation_pending" || o.exception)
          .map((o) => {
            tickProvider(o);
            return {
              orderId: o.orderId,
              state: o.state,
              total: o.total,
              currency: o.currency,
              activeProvider: o.provider,
              settledProvider: o.settledProvider,
              salesInvoiceId: o.invoiceId,
              paymentException: o.exception,
              reservationExpiresAt: new Date(o.expiresAt).toISOString(),
              updatedAt: new Date().toISOString(),
              openExceptions: o.exception ? 1 : 0,
            };
          }),
      ),
    repairPaidOrder: (orderId, _notes, manager) => {
      if (!managerOk(manager)) return no("Manager sign-in failed.");
      const o = orders.get(orderId);
      if (!o || o.state !== "allocation_pending" || o.invoiceId) return no("paid-but-unfinalized commerce order required");
      o.invoiceId = postInvoice(o.cartId, [{ tender: "ecocash", amount: o.total }]);
      Object.assign(o, { state: "paid", exception: null });
      return ok(o.invoiceId);
    },
    listPickupOrders: (query) =>
      ok(
        [...orders.values()]
          .filter((o) => (o.state === "paid" || o.state === "account_invoiced") && o.invoiceId)
          .map((o) => {
            const inv = invoices.get(o.invoiceId!);
            return {
              orderId: o.orderId,
              documentNumber: inv?.documentNumber ?? null,
              customerName: inv?.customerName ?? null,
              state: o.state,
              total: o.total,
              currency: o.currency,
              salesInvoiceId: o.invoiceId,
              settledProvider: o.settledProvider,
              updatedAt: new Date().toISOString(),
            };
          })
          .filter((p) => !query.trim() || `${p.documentNumber} ${p.customerName}`.toLowerCase().includes(query.trim().toLowerCase())),
      ),
    collectOrder: (orderId) => {
      const o = orders.get(orderId);
      if (!o || (o.state !== "paid" && o.state !== "account_invoiced")) return no("eligible pickup order required");
      o.state = "delivered";
      return ok(true as const);
    },
    loadReceipt: (invoiceId) => {
      const r = invoices.get(invoiceId);
      return r ? ok(r) : no("Invoice not found.");
    },

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
    refundInvoice: (invoiceId, g) => {
      const refused = governedRefusal("refund_full_invoice", g);
      if (refused) return no(refused);
      const i = recent.findIndex((r) => r.id === invoiceId);
      if (i < 0) return no("Invoice not found.");
      recent.splice(i, 1);
      return ok(nextDoc("RET"));
    },

    listEpcVariants: (slug) => ok(VARIANTS[slug] ?? []),
    listEpcSections: () => ok([{ slug: "brakes", name: "Brakes", thumbnailUrl: null }, { slug: "suspension", name: "Front suspension", thumbnailUrl: null }]),
    listEpcDiagrams: () => ok([{ id: "preview-front-brake", slug: "front-brake", title: "Front brake", imageUrl: null }]),
    getEpcDiagram: () => ok(DIAGRAM),

    getMyTill: () => ok(openTillOf()),
    openTill: (warehouseId, deviceId, openingFloat, currency) => {
      if (openTillOf()) return no("device or operator already has an open till session");
      if (!(openingFloat >= 0)) return no("opening_float must be >= 0");
      const t: TillSession = {
        id: `till-${seq++}`,
        deviceId,
        warehouseId,
        currency,
        operatorUserId: "preview-operator",
        openingFloat: roundMoney(openingFloat),
        status: "open",
        expectedCash: null,
        countedCash: null,
        variance: null,
        varianceReasonCode: null,
        openedAt: new Date().toISOString(),
        closedAt: null,
      };
      tills.unshift(t);
      tillCash = t.openingFloat;
      return ok(t);
    },
    attachCartToTill: (cartId, sessionId) => {
      const c = get(cartId);
      save({ ...c, tillSessionId: sessionId });
      return ok(true as const);
    },
    requiresManager: (action, value) => {
      const p = policies.find((x) => x.action === action);
      return ok(!p || p.alwaysRequireManager || value > p.thresholdValue);
    },
    listApprovalPolicies: () => ok(policies.map((p) => ({ ...p }))),
    setApprovalPolicy: (p) => {
      if (p.thresholdValue < 0) return no("threshold must be >= 0");
      const i = policies.findIndex((x) => x.action === p.action);
      const row = { ...p, updatedAt: new Date().toISOString() };
      if (i >= 0) policies[i] = row;
      else policies.push(row);
      return ok(true as const);
    },
    listReasons: (action) =>
      ok(
        (
          {
            till_variance: [
              { code: "count_error", label: "Count error", requiresNotes: false },
              { code: "cash_movement_missing", label: "Cash movement not recorded", requiresNotes: false },
              { code: "investigation", label: "Needs investigation", requiresNotes: false },
            ],
            discount_percent: [
              { code: "customer_retention", label: "Customer retention", requiresNotes: false },
              { code: "price_match", label: "Price match", requiresNotes: false },
              { code: "damaged_packaging", label: "Damaged packaging", requiresNotes: false },
            ],
            price_override_delta_percent: [
              { code: "supplier_price", label: "Supplier price change", requiresNotes: false },
              { code: "advertised_price", label: "Advertised price", requiresNotes: false },
              { code: "data_correction", label: "Price data correction", requiresNotes: false },
            ],
            void_cart: [
              { code: "customer_cancelled", label: "Customer cancelled", requiresNotes: false },
              { code: "duplicate_cart", label: "Duplicate sale", requiresNotes: false },
              { code: "pricing_error", label: "Pricing error", requiresNotes: true },
              { code: "operator_error", label: "Operator error", requiresNotes: true },
            ],
            refund_full_invoice: [
              { code: "wrong_part", label: "Wrong part", requiresNotes: false },
              { code: "customer_changed_mind", label: "Customer changed mind", requiresNotes: false },
              { code: "defective", label: "Defective", requiresNotes: false },
              { code: "manager_exception", label: "Manager exception", requiresNotes: true },
            ],
            cash_out: [
              { code: "petty_cash", label: "Petty cash", requiresNotes: false },
              { code: "bank_drop", label: "Bank drop", requiresNotes: false },
              { code: "customer_refund", label: "Customer refund", requiresNotes: false },
            ],
          } as Record<string, ReasonCode[]>
        )[action] ?? [],
      ),
    recordCashMovement: (sessionId, kind, amount, reasonCode, _notes, manager) => {
      const t = tills.find((x) => x.id === sessionId);
      if (!t || t.status !== "open") return no("open till session required");
      if (kind !== "cash_in" && (!manager || !managerOk(manager))) return no("manager approval required for cash-out movement");
      if (!(amount > 0)) return no("amount must be > 0");
      if (!reasonCode.trim()) return no("reason_code required");
      tillCash += kind === "cash_in" ? amount : -amount;
      return ok(true as const);
    },
    closeTill: (sessionId, counts, reason) => {
      const t = tills.find((x) => x.id === sessionId);
      if (!t || t.status !== "open") return no("open operator till session required");
      if (counts.length === 0) return no("denomination count required");
      const counted = roundMoney(counts.reduce((s, c) => s + c.denomination * c.quantity, 0));
      const expected = roundMoney(tillCash);
      const variance = roundMoney(counted - expected);
      if (Math.abs(variance) > 0.009 && !reason) return no("variance reason required");
      const pending = Math.abs(variance) > 0.009;
      Object.assign(t, {
        expectedCash: expected,
        countedCash: counted,
        variance,
        varianceReasonCode: reason,
        status: pending ? "variance_pending" : "closed",
        closedAt: pending ? null : new Date().toISOString(),
      });
      return ok({ sessionId, expectedCash: expected, countedCash: counted, variance, status: pending ? ("variance_pending" as const) : ("closed" as const) });
    },
    approveTillVariance: (sessionId, reasonCode, _notes, manager) => {
      if (!managerOk(manager)) return no("Manager sign-in failed.");
      const t = tills.find((x) => x.id === sessionId);
      if (!t || t.status !== "variance_pending") return no("variance-pending till session required");
      Object.assign(t, { status: "closed", varianceReasonCode: reasonCode, closedAt: new Date().toISOString() });
      return ok(true as const);
    },
    listHandoverOperators: () =>
      ok([
        { userId: "preview-operator", employeeCode: "EMP-0001", fullName: "Preview operator", roles: ["sales"] },
        { userId: "op-2", employeeCode: "EMP-0002", fullName: "Farai Ncube", roles: ["sales"] },
      ]),
    handoverTill: (sessionId, newOperatorUserId, _notes, manager) => {
      if (!managerOk(manager)) return no("Manager sign-in failed.");
      const t = tills.find((x) => x.id === sessionId);
      if (!t || t.status !== "open") return no("open till session required");
      if (newOperatorUserId === t.operatorUserId) return no("different operator required");
      t.operatorUserId = newOperatorUserId;
      return ok(true as const);
    },
    listTillSessions: (status) => ok(tills.filter((t) => !status || t.status === status)),
    operatorLabel: () => Promise.resolve("Preview operator"),
    reauthenticate: (password) => (password ? ok(true as const) : no("Password required.")),
  };
  return gateway;
}

/** Preview is never available in production builds, whatever the env says. */
export function isPosPreviewEnabled(): boolean {
  return process.env.NODE_ENV !== "production" && process.env.NEXT_PUBLIC_POS_PREVIEW === "1";
}
