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
  SplitSession,
  SplitLeg,
  SplitRefund,
  PosCurrency,
  TenderLine,
  ApprovalTrailRow,
  IssuedBadge,
  ManagerCandidate,
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

/** The preview manager's badge (what a USB scanner types when it reads the printed preview card). */
export const PREVIEW_BADGE = "GTRMGR1:preview-manager:preview-badge-secret";

export function createPreviewPosGateway(): PosGateway {
  const emp = (id: string, fullName: string, code: string, grade: string | null, roleTitle: string | null, department: string | null, hasLogin: boolean, source: string | null): ManagerCandidate => ({
    holderType: "employee", employeeId: id, userId: hasLogin ? id : null, fullName, employeeCode: code, grade, roleTitle, department, hasLogin,
    isApprover: source !== null, source, assigned: false, activeBadges: 0,
  });
  const candidates: ManagerCandidate[] = [
    emp("preview-manager", "Preview manager", "EMP-0100", "Shop & warehouse manager", "Shop manager", "Sales", true, "senior_grade"),
    emp("emp-workshop", "Tafadzwa Dube", "EMP-0201", "Shop attendant", "Workshop head", "Workshop", false, "department_manager"),
    emp("preview-operator", "Preview operator", "EMP-0001", "Shop attendant", "Counter sales", "Sales", true, null),
    emp("op-2", "Farai Ncube", "EMP-0002", "Shop attendant", "Counter sales", "Sales", true, null),
  ];
  type PreviewBadge = IssuedBadge & { userId: string; label: string | null; issuedAt: string; revokedAt: string | null; revokeReason: string | null; lastUsedAt: string | null; useCount: number };
  const badges: PreviewBadge[] = [
    { badgeId: "preview-manager", payload: PREVIEW_BADGE, expiresAt: new Date(Date.now() + 365 * 86_400_000).toISOString(), fullName: "Preview manager", employeeCode: "EMP-0100", title: "Shop manager", userId: "preview-manager", label: "Preview card", issuedAt: new Date().toISOString(), revokedAt: null, revokeReason: null, lastUsedAt: null, useCount: 0 },
  ];
  const trail: ApprovalTrailRow[] = [];
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

  // Part payments (staged split): one session per reserved order; cash / bank capture at once,
  // store credit is held; the sale posts when captured + held covers the total.
  type PreviewSplit = { session: SplitSession; legKeys: Map<string, string> };
  const splits = new Map<string, PreviewSplit>();
  const refreshSplit = (sp: PreviewSplit) => {
    const s = sp.session;
    const sum = (st: string[]) => roundMoney(s.legs.filter((l) => st.includes(l.status)).reduce((a, l) => a + l.amount, 0));
    s.captured = sum(["captured", "allocated", "refund_review", "refund_pending"]);
    s.held = sum(["held"]);
    s.pending = sum(["pending", "unknown"]);
    s.locked = roundMoney(s.captured + s.held);
    s.balanceDue = Math.max(0, roundMoney(s.total - s.locked));
    s.availableToAllocate = Math.max(0, roundMoney(s.total - s.locked - s.pending));
    if (["settled", "cancelled", "refunded", "refund_review", "refund_pending"].includes(s.status)) return;
    s.status = s.pending > 0 ? "leg_pending" : s.locked + 0.01 >= s.total ? "fully_committed" : s.locked > 0 ? "partially_captured" : "open";
  };
  const finalizeSplit = (sp: PreviewSplit) => {
    refreshSplit(sp);
    const s = sp.session;
    if (s.status !== "fully_committed") return;
    const o = orders.get(s.orderId)!;
    let left = s.total;
    const tenders: TenderLine[] = [];
    for (const l of s.legs) {
      if (l.status !== "captured" && l.status !== "held") continue;
      const apply = Math.min(l.amount, Math.max(0, left));
      left = roundMoney(left - apply);
      if (apply > 0) tenders.push({ tender: l.tender as TenderLine["tender"], amount: apply });
      l.appliedAmount = apply;
      if (l.status === "held") l.status = "allocated";
    }
    s.finalInvoiceId = postInvoice(o.cartId, tenders);
    Object.assign(o, { state: "paid", invoiceId: s.finalInvoiceId, settledProvider: tenders.length === 1 ? tenders[0]!.tender : "split_payment" });
    s.status = s.refunds.some((r) => r.status !== "settled" && r.status !== "cancelled") ? "refund_review" : "settled";
  };
  const splitCopy = (sp: PreviewSplit): SplitSession => structuredClone(sp.session);
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
      if (!manager || !managerOk(manager)) return no("manager or finance approval required");
      const o = orders.get(orderId);
      if (!o || o.state !== "allocation_pending" || o.invoiceId) return no("paid-but-unfinalized commerce order required");
      o.invoiceId = postInvoice(o.cartId, [{ tender: "ecocash", amount: o.total }]);
      Object.assign(o, { state: "paid", exception: null });
      return ok(o.invoiceId);
    },
    findSplit: (orderId) => {
      const sp = [...splits.values()].find((x) => x.session.orderId === orderId);
      return ok(sp ? splitCopy(sp) : null);
    },
    startSplit: (orderId) => {
      const o = orders.get(orderId);
      if (!o || o.invoiceId || !["awaiting_payment", "payment_processing", "payment_failed"].includes(o.state)) return no("unsettled reserve-first commerce order required");
      let sp = [...splits.values()].find((x) => x.session.orderId === orderId);
      if (!sp) {
        sp = {
          session: {
            sessionId: `split-${seq++}`, orderId, status: "open", total: o.total, currency: o.currency, captured: 0, held: 0, pending: 0, locked: 0,
            balanceDue: o.total, availableToAllocate: o.total, finalInvoiceId: null, finalizationError: null, reducedBasketAcceptedAt: null, legs: [], refunds: [],
          },
          legKeys: new Map(),
        };
        splits.set(sp.session.sessionId, sp);
      }
      o.state = "payment_processing";
      o.expiresAt = Math.max(o.expiresAt, Date.now() + 60 * 60_000);
      refreshSplit(sp);
      return ok(splitCopy(sp));
    },
    getSplit: (sessionId) => {
      const sp = splits.get(sessionId);
      return sp ? ok(splitCopy(sp)) : no("split payment session not found");
    },
    addSplitLeg: (sessionId, tender, amount, requestId, reference) => {
      const sp = splits.get(sessionId);
      if (!sp) return no("split payment session not found");
      if (sp.legKeys.has(requestId)) return ok(splitCopy(sp));
      const s = sp.session;
      if (["settled", "refund_review", "refund_pending", "refunded", "cancelled", "finalizing"].includes(s.status)) return no(`split session does not accept new payments in status ${s.status}`);
      refreshSplit(sp);
      if (!(amount > 0) || amount > s.availableToAllocate + 0.01) return no(`split leg amount must be > 0 and <= available balance ${s.availableToAllocate.toFixed(2)}`);
      if (tender === "bank" && !reference?.trim()) return no("bank split payment requires a transfer/reference number");
      if (tender === "store_credit") {
        const c = get(orders.get(s.orderId)!.cartId);
        if (c.customerId !== "c-2") return no("insufficient available store credit after active POS holds");
      }
      const leg: SplitLeg = {
        id: `leg-${seq++}`, sequenceNo: s.legs.length + 1, tender, amount: roundMoney(amount), status: tender === "store_credit" ? "held" : "captured",
        reference: reference?.trim() || null, providerRef: reference?.trim() || null, statusDetail: null, appliedAmount: null, refundRequired: null,
      };
      s.legs.push(leg);
      sp.legKeys.set(requestId, leg.id);
      finalizeSplit(sp);
      return ok(splitCopy(sp));
    },
    acceptReducedBasket: (sessionId, items, notes) => {
      const sp = splits.get(sessionId);
      if (!sp) return no("split payment session not found");
      const s = sp.session;
      if (!items.length) return no("at least one accepted line is required");
      if (s.locked <= 0) return no("no locked payment is available for reduced-basket settlement");
      const o = orders.get(s.orderId)!;
      const c = get(o.cartId);
      const kept: CartLine[] = [];
      for (const it of items) {
        const line = c.lines.find((l) => l.id === it.cartLineId);
        if (!line || !(it.qty > 0) || it.qty > line.qty) return no(`invalid accepted quantity for cart line ${it.cartLineId}`);
        kept.push({ ...line, qty: it.qty, lineTotal: roundMoney((line.lineTotal / line.qty) * it.qty) });
      }
      const newTotal = roundMoney(kept.reduce((a, l) => a + l.lineTotal, 0));
      if (newTotal > s.locked + 0.01) return no(`accepted basket total ${newTotal.toFixed(2)} must be > 0 and <= locked payment ${s.locked.toFixed(2)}`);
      save({ ...c, lines: kept });
      o.total = newTotal;
      s.total = newTotal;
      s.reducedBasketAcceptedAt = new Date().toISOString();
      let left = newTotal;
      for (const l of s.legs.filter((x) => x.status === "captured")) {
        const apply = Math.min(l.amount, Math.max(0, left));
        left = roundMoney(left - apply);
        l.appliedAmount = apply;
        l.refundRequired = roundMoney(l.amount - apply);
        if (l.refundRequired > 0.009)
          s.refunds.push({ id: `refund-${seq++}`, legId: l.id, status: "review", grossAmount: l.refundRequired, feePolicy: "manual_review", netCustomerRefund: null, providerRef: null, failureReason: null, notes: notes ?? "Captured surplus after customer accepted reduced basket" });
      }
      finalizeSplit(sp);
      return ok(splitCopy(sp));
    },
    cancelSplit: (sessionId, reason, feePolicy) => {
      const sp = splits.get(sessionId);
      if (!sp) return no("split payment session not found");
      const s = sp.session;
      if (s.status === "settled") return no("settled sale must use the posted invoice return/refund workflow");
      for (const l of s.legs) {
        if (["planned", "failed", "held"].includes(l.status)) l.status = "cancelled";
        if (l.status === "captured") {
          s.refunds.push({ id: `refund-${seq++}`, legId: l.id, status: "review", grossAmount: l.amount, feePolicy, netCustomerRefund: null, providerRef: null, failureReason: null, notes: reason });
          l.status = "refund_review";
        }
      }
      orders.get(s.orderId)!.state = "cancelled";
      s.status = s.legs.some((l) => l.status === "refund_review") ? "refund_review" : "cancelled";
      refreshSplit(sp);
      return ok(splitCopy(sp));
    },
    retrySplitFinalization: (sessionId) => {
      const sp = splits.get(sessionId);
      if (!sp) return no("split payment session not found");
      if (sp.session.status !== "finalization_failed" && sp.session.status !== "fully_committed") return no(`only a fully paid split sale that did not post can be retried (status ${sp.session.status})`);
      sp.session.status = "fully_committed";
      finalizeSplit(sp);
      return ok(splitCopy(sp));
    },
    listSplitRecovery: () =>
      ok(
        [...splits.values()]
          .filter((sp) => ["partially_captured", "leg_pending", "fully_committed", "finalization_failed", "refund_review", "refund_pending"].includes(sp.session.status))
          .map((sp) => {
            const o = orders.get(sp.session.orderId)!;
            const c = carts.get(o.cartId);
            return {
              sessionId: sp.session.sessionId, orderId: o.orderId, status: sp.session.status, documentNumber: null, customerName: c?.customerName ?? null,
              total: sp.session.total, currency: sp.session.currency, updatedAt: new Date().toISOString(), session: splitCopy(sp),
            };
          }),
      ),
    splitRefundStep: (refundId, step, manager) => {
      if (manager && !managerOk(manager)) return no("Manager sign-in failed.");
      const sp = [...splits.values()].find((x) => x.session.refunds.some((r) => r.id === refundId));
      if (!sp) return no("split refund request not found");
      const s = sp.session;
      const r: SplitRefund = s.refunds.find((x) => x.id === refundId)!;
      const leg = s.legs.find((l) => l.id === r.legId);
      if (step.kind === "approve") {
        if (r.status !== "review" && r.status !== "failed") return no("review/failed refund request required");
        if (step.customerFee > 0 && step.feePolicy !== "customer_bears") return no("customer fee deduction requires customer_bears policy");
        Object.assign(r, { status: "pending", feePolicy: step.feePolicy, netCustomerRefund: roundMoney(r.grossAmount - step.customerFee) });
        if (leg) leg.status = "refund_pending";
        s.status = "refund_pending";
      } else if (step.kind === "complete") {
        if (!["pending", "review", "failed"].includes(r.status)) return no(`refund cannot settle in status ${r.status}`);
        Object.assign(r, { status: "settled", providerRef: step.providerRef || null });
        if (leg) leg.status = (leg.appliedAmount ?? 0) > 0 ? "allocated" : "refunded";
        const open = s.refunds.some((x) => x.status !== "settled" && x.status !== "cancelled");
        s.status = open ? "refund_pending" : s.finalInvoiceId ? "settled" : "refunded";
      } else {
        Object.assign(r, { status: "failed", failureReason: step.reason });
        if (leg) Object.assign(leg, { status: "refund_review", statusDetail: step.reason });
        s.status = "refund_review";
      }
      return ok(splitCopy(sp));
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
      if (!manager || !managerOk(manager)) return no("variance approval requires a POS manager");
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
      if (!manager || !managerOk(manager)) return no("POS manager approval required");
      const t = tills.find((x) => x.id === sessionId);
      if (!t || t.status !== "open") return no("open till session required");
      if (newOperatorUserId === t.operatorUserId) return no("different operator required");
      t.operatorUserId = newOperatorUserId;
      return ok(true as const);
    },
    listTillSessions: (status) => ok(tills.filter((t) => !status || t.status === status)),

    // Badges: the preview manager carries PREVIEW_BADGE; new ones are kept in memory. The preview
    // operator is not a manager, so every governed action asks for approval.
    approverStatus: () => ok({ isApprover: false, source: null }),
    badgeApprove: async (payload, action, args) => {
      const now = Date.now();
      const badge = badges.find((b) => b.payload === payload.trim());
      const reject = (error: string) => {
        trail.unshift({ at: new Date().toISOString(), method: "badge", outcome: "rejected", action, managerName: null, requestedByName: "Preview operator", reasonCode: null, detail: error, deviceId: null });
        return no<{ managerName: string | null }>(error);
      };
      if (trail.filter((t) => t.outcome === "rejected" && now - Date.parse(t.at) < 15 * 60_000).length >= 5)
        return no("too many rejected badge scans; try again in 15 minutes or ask the manager to sign in");
      if (!badge) return reject("badge not recognised");
      if (badge.revokedAt) return reject("badge revoked");
      if (Date.parse(badge.expiresAt) <= now) return reject("badge expired");
      const m = { identifier: "manager", password: "preview" };
      const a = args as Record<string, never>;
      const g = { reasonCode: String(a.reason_code ?? ""), notes: (a.notes as string | null) ?? null, manager: m };
      const res = await (async () => {
        switch (action) {
          case "discount": return gateway.applyDiscount(a.cart_id, Number(a.percent), g);
          case "price_override": return gateway.overrideLinePrice(String(a.cart_id ?? ""), a.line_id, Number(a.unit_price), g);
          case "void_sale": return gateway.voidCart(a.cart_id, g);
          case "refund": return gateway.refundInvoice(a.invoice_id, g);
          case "cash_out": return gateway.recordCashMovement(a.session_id, a.kind, Number(a.amount), String(a.reason_code), g.notes, m);
          case "till_variance": return gateway.approveTillVariance(a.session_id, String(a.reason_code), g.notes, m);
          case "till_handover": return gateway.handoverTill(a.session_id, a.new_operator_user_id, g.notes, m);
          case "repair_paid_order": return gateway.repairPaidOrder(a.order_id, g.notes, m);
          case "split_refund_approve":
            return gateway.splitRefundStep(a.refund_id, { kind: "approve", feePolicy: (a.fee_policy as never) ?? "business_absorbs", customerFee: Number(a.customer_fee ?? 0), notes: g.notes }, m);
          case "split_refund_complete":
            return gateway.splitRefundStep(a.refund_id, { kind: "complete", providerRef: String(a.provider_ref ?? ""), notes: g.notes }, m);
          case "split_refund_fail":
            return gateway.splitRefundStep(a.refund_id, { kind: "fail", reason: String(a.reason ?? "") }, m);
        }
      })();
      trail.unshift({
        at: new Date().toISOString(), method: "badge", outcome: res.ok ? "approved" : "failed", action, managerName: badge.fullName,
        requestedByName: "Preview operator", reasonCode: (a.reason_code as string | null) ?? null, detail: res.ok ? null : res.error, deviceId: "preview",
      });
      if (!res.ok) return no(res.error);
      badge.useCount += 1;
      badge.lastUsedAt = new Date().toISOString();
      return ok({ managerName: badge.fullName });
    },
    listManagerCandidates: () => ok(candidates.map((c) => ({ ...c, activeBadges: badges.filter((b) => b.userId === c.employeeId && !b.revokedAt).length }))),
    setManagerAssignment: (employeeId, assigned) => {
      const c = candidates.find((x) => x.employeeId === employeeId);
      if (!c) return no("active employee required");
      const own = c.source && c.source !== "assigned" ? c.source : null;
      Object.assign(c, { assigned, source: own ?? (assigned ? "assigned" : null) });
      c.isApprover = c.source !== null;
      trail.unshift({ at: new Date().toISOString(), method: "admin", outcome: assigned ? "assigned" : "unassigned", action: assigned ? "assigned" : "unassigned", managerName: "Preview admin", requestedByName: c.fullName, reasonCode: null, detail: null, deviceId: null });
      return ok(true as const);
    },
    issueBadge: (holder, label, validDays) => {
      const key = "employeeId" in holder ? holder.employeeId : holder.userId;
      const c = candidates.find((x) => x.employeeId === key || x.userId === key);
      if (!c?.isApprover) return no("only a manager or an assigned approver can hold an approval badge");
      const id = `badge-${seq++}`;
      const issued = { badgeId: id, payload: `GTRMGR1:${id}:${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`, expiresAt: new Date(Date.now() + validDays * 86_400_000).toISOString(), fullName: c.fullName, employeeCode: c.employeeCode, title: c.roleTitle ?? c.grade };
      badges.unshift({ ...issued, userId: key, label, issuedAt: new Date().toISOString(), revokedAt: null, revokeReason: null, lastUsedAt: null, useCount: 0 });
      trail.unshift({ at: new Date().toISOString(), method: "admin", outcome: "badge_issued", action: "badge_issued", managerName: "Preview admin", requestedByName: c.fullName, reasonCode: null, detail: label, deviceId: null });
      return ok(issued);
    },
    revokeBadge: (badgeId, reason) => {
      const b = badges.find((x) => x.badgeId === badgeId && !x.revokedAt);
      if (!b) return no("active badge not found");
      if (!reason.trim()) return no("revocation reason required");
      Object.assign(b, { revokedAt: new Date().toISOString(), revokeReason: reason });
      trail.unshift({ at: new Date().toISOString(), method: "admin", outcome: "badge_revoked", action: "badge_revoked", managerName: "Preview admin", requestedByName: b.fullName, reasonCode: null, detail: reason, deviceId: null });
      return ok(true as const);
    },
    listBadges: () =>
      ok(
        badges
          .map((b) => ({
            badgeId: b.badgeId, employeeId: b.userId, userId: null, fullName: b.fullName, label: b.label, issuedAt: b.issuedAt, expiresAt: b.expiresAt,
            revokedAt: b.revokedAt, revokeReason: b.revokeReason, lastUsedAt: b.lastUsedAt, useCount: b.useCount,
            status: b.revokedAt ? ("revoked" as const) : Date.parse(b.expiresAt) <= Date.now() ? ("expired" as const) : ("active" as const),
          })),
      ),
    approvalTrail: (limit) => ok(trail.slice(0, limit)),
    operatorLabel: () => Promise.resolve("Preview operator"),
    reauthenticate: (password) => (password ? ok(true as const) : no("Password required.")),
  };
  return gateway;
}

/** Preview is never available in production builds, whatever the env says. */
export function isPosPreviewEnabled(): boolean {
  return process.env.NODE_ENV !== "production" && process.env.NEXT_PUBLIC_POS_PREVIEW === "1";
}
