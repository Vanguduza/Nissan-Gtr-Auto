"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { PosGateway, ScanSession } from "@/lib/pos/gateway";
import { haptic } from "@/lib/pos/haptics";
import { formatMoney, roundMoney } from "@/lib/pos/money";
import { buildPopularRow, pinForPart } from "@/lib/pos/popular";
import type {
  CashMovementKind,
  CustomerInput,
  DenominationCount,
  HandoverOperator,
  TillCloseResult,
  TillSession,
  GarageVehicle,
  ManagerCredentials,
  PosCustomer,
  ReceiptContacts,
  ReceiptDocument,
  SaleSetup,
  TenderLine,
  Warehouse,
  PopularPin,
  PopularRowItem,
  PosCart,
  PosCurrency,
  PosPart,
  SelectedVehicle,
  VehicleGeneration,
  VehicleModel,
  VehicleVariant,
  Governed,
  ContipayMethod,
  DigitalProvider,
  ManualTenderLine,
  PaymentStatus,
  PaynowMethod,
  PickupOrder,
  ProviderAvailability,
  RecoveryItem,
  TenderOutcome,
  BadgeAction,
  ManagerProof,
  RefundFeePolicy,
  SplitRecoveryItem,
  SplitRefundStep,
  SplitSession,
  SplitTender,
} from "@/lib/pos/types";

const RECENT_KEY = "gtr.pos.recentSearches";
const RECENT_MAX = 8;

/** Actions that need an approver (`is_pos_approver`). [reasonAction] asks the manager for a configured reason. */
export type ManagerPrompt =
  | { kind: "void" }
  | { kind: "discount"; percent: number }
  | { kind: "override"; lineId: string; lineName: string; unitPrice: number }
  | { kind: "refund"; invoiceId: string; documentNumber: string | null }
  | { kind: "cashOut"; movement: CashMovementKind; amount: number; reasonCode: string; label: string }
  | { kind: "tillVariance"; sessionId: string; variance: number | null }
  | { kind: "handover"; sessionId: string; userId: string; name: string };

/** The configured reason list (`pos_approval_reason_codes.action`) a prompt chooses from, if any. */
export function reasonActionFor(prompt: ManagerPrompt): string | null {
  switch (prompt.kind) {
    case "tillVariance":
      return "till_variance";
    case "void":
      return "void_cart";
    case "discount":
      return "discount_percent";
    case "override":
      return "price_override_delta_percent";
    case "refund":
      return "refund_full_invoice";
    default:
      return null;
  }
}

/** A reserved checkout: the server's order and the digital attempt in flight, if any. */
export type CheckoutSession = {
  orderId: string;
  cartId: string;
  status: PaymentStatus;
  attempt: { provider: DigitalProvider; intentId: string; checkoutUrl: string | null; startedAt: number } | null;
  outcome: TenderOutcome | null;
  message: string | null;
};

/** The `pos_badge_approve` action and arguments for a prompt (same fields the governed RPCs take). */
function badgeRequestFor(
  prompt: ManagerPrompt,
  cart: PosCart | null,
  till: TillSession | null,
  reasonCode: string | null,
  notes: string | null,
): { action: BadgeAction; args: Record<string, unknown> } | null {
  switch (prompt.kind) {
    case "discount":
      return cart ? { action: "discount", args: { cart_id: cart.id, percent: prompt.percent, reason_code: reasonCode, notes } } : null;
    case "override":
      return cart ? { action: "price_override", args: { cart_id: cart.id, line_id: prompt.lineId, unit_price: prompt.unitPrice, reason_code: reasonCode, notes } } : null;
    case "void":
      return cart ? { action: "void_sale", args: { cart_id: cart.id, reason_code: reasonCode, notes } } : null;
    case "refund":
      return { action: "refund", args: { invoice_id: prompt.invoiceId, reason_code: reasonCode, notes } };
    case "cashOut":
      return till ? { action: "cash_out", args: { session_id: till.id, kind: prompt.movement, amount: prompt.amount, reason_code: prompt.reasonCode, notes } } : null;
    case "tillVariance":
      return { action: "till_variance", args: { session_id: prompt.sessionId, reason_code: reasonCode, notes } };
    case "handover":
      return { action: "till_handover", args: { session_id: prompt.sessionId, new_operator_user_id: prompt.userId, notes } };
  }
}

/** How long a digital attempt may stay unanswered before it is treated as Unknown (§10.7). */
const PROVIDER_WAIT_MS = 3 * 60_000;

const DEVICE_KEY = "gtr.pos.deviceId";

/** Stable id for this browser as a till device (one open till per device). */
function deviceId(): string {
  try {
    const existing = window.localStorage.getItem(DEVICE_KEY);
    if (existing) return existing;
    const id = `web-${crypto.randomUUID()}`;
    window.localStorage.setItem(DEVICE_KEY, id);
    return id;
  } catch {
    return "web-unpersisted";
  }
}

export type PosDestination =
  | "home"
  | "search"
  | "quickSale"
  | "customer"
  | "orders"
  | "returns"
  | "epc"
  | "till"
  | "recovery"
  | "managers"
  | "settings";

function readRecent(): string[] {
  try {
    const raw = window.localStorage.getItem(RECENT_KEY);
    const parsed = raw ? (JSON.parse(raw) as unknown) : [];
    return Array.isArray(parsed) ? parsed.filter((s): s is string => typeof s === "string").slice(0, RECENT_MAX) : [];
  } catch {
    return [];
  }
}

function writeRecent(list: string[]) {
  try {
    window.localStorage.setItem(RECENT_KEY, JSON.stringify(list));
  } catch {
    // Device-local convenience only; the POS works without it.
  }
}

/** Single POS store for the web runtime — same responsibilities as the tablet `PosViewModel`. */
export function usePos(gateway: PosGateway) {
  const [destination, setDestination] = useState<PosDestination>("home");
  const [operator, setOperator] = useState("Operator");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [online, setOnline] = useState(true);

  const [models, setModels] = useState<VehicleModel[]>([]);
  const [variants, setVariants] = useState<VehicleVariant[]>([]);
  const [modelSlug, setModelSlug] = useState("");
  const [chassisCode, setChassisCode] = useState("");
  const [engineCode, setEngineCode] = useState("");

  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PosPart[] | null>(null);
  const [recent, setRecent] = useState<string[]>([]);

  const [pins, setPins] = useState<PopularPin[]>([]);
  const [bestSellers, setBestSellers] = useState<PosPart[]>([]);
  const [hidden, setHidden] = useState<Set<string>>(new Set());

  const [cart, setCart] = useState<PosCart | null>(null);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [setup, setSetup] = useState<SaleSetup>({ warehouseId: null, currency: "USD", fulfillmentMode: "immediate" });
  const [managerPrompt, setManagerPrompt] = useState<ManagerPrompt | null>(null);
  /** Whether the open prompt needs a manager (policy `pos_action_requires_manager`); true until known. */
  const [promptNeedsManager, setPromptNeedsManager] = useState(true);
  /** The signed-in user is a POS manager: approvals are theirs, with no prompt for a badge or password. */
  const [selfApprover, setSelfApprover] = useState(false);
  const [discount, setDiscount] = useState<{ cartId: string; percent: number; amount: number } | null>(null);
  const [garageChoices, setGarageChoices] = useState<GarageVehicle[] | null>(null);
  const [lastReceipt, setLastReceipt] = useState<ReceiptDocument | null>(null);
  /** The reserved order behind the last receipt, for "handed over" (counter pickup). */
  const [receiptOrderId, setReceiptOrderId] = useState<string | null>(null);
  const [checkout, setCheckout] = useState<CheckoutSession | null>(null);
  const checkoutRef = useRef<CheckoutSession | null>(null);
  checkoutRef.current = checkout;
  /** Idempotency keys for the current cart's checkout attempt (Blueprint §10.2). */
  const checkoutKeys = useRef<{ cartId: string; requestId: string; paymentRequestId: string } | null>(null);
  const [providers, setProviders] = useState<ProviderAvailability | null>(null);
  const [recoveryOrderId, setRecoveryOrderId] = useState<string | null>(null);
  const [recoveryItems, setRecoveryItems] = useState<RecoveryItem[] | null>(null);
  const [pickups, setPickups] = useState<PickupOrder[] | null>(null);
  /** Part payments on the reserved order (staged split); amounts are always the server's. */
  const [split, setSplit] = useState<SplitSession | null>(null);
  /** Idempotency key of the part being taken; kept after a dropped answer so the retry cannot charge twice. */
  const splitLegKey = useRef<string | null>(null);
  const [splitRecovery, setSplitRecovery] = useState<SplitRecoveryItem[] | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [till, setTill] = useState<TillSession | null>(null);
  const [tillLoaded, setTillLoaded] = useState(false);
  const [tillHistory, setTillHistory] = useState<TillSession[]>([]);
  const tillRef = useRef<TillSession | null>(null);
  tillRef.current = till;
  const currency: PosCurrency = cart?.currency ?? setup.currency;
  const cartRef = useRef<PosCart | null>(null);
  cartRef.current = cart;

  const report = useCallback(<T,>(res: { ok: true; data: T } | { ok: false; error: string }): T | null => {
    if (res.ok) return res.data;
    setError(res.error);
    return null;
  }, []);

  // Haptics: every surfaced error buzzes as an error; every confirmation as a success.
  useEffect(() => {
    if (error) haptic("error");
  }, [error]);
  useEffect(() => {
    if (notice) haptic("success");
  }, [notice]);

  // Initial load
  useEffect(() => {
    setRecent(readRecent());
    setOnline(typeof navigator === "undefined" ? true : navigator.onLine);
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    void (async () => {
      setOperator(await gateway.operatorLabel());
      void gateway.approverStatus().then((r) => setSelfApprover(r.ok && r.data.isApprover));
      const mine = await gateway.getMyTill(deviceId());
      setTill(report(mine) ?? null);
      setTillLoaded(true);
      const [m, p, b, h] = await Promise.all([
        gateway.listModels(),
        gateway.listPins(),
        gateway.listBestSellers(),
        gateway.listHiddenBestSellers(),
      ]);
      setModels(report(m) ?? []);
      setPins(report(p) ?? []);
      setBestSellers(report(b) ?? []);
      setHidden(new Set(report(h) ?? []));
      const w = report(await gateway.listWarehouses()) ?? [];
      setWarehouses(w);
      setSetup((cur) => (cur.warehouseId || w.length === 0 ? cur : { ...cur, warehouseId: w[0].id }));
    })();
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, [gateway, report]);

  // Vehicle cascade (Model → Generation → Engine). Make is never shown (owner decision D2).
  const generations: VehicleGeneration[] = useMemo(() => {
    const byChassis = new Map<string, VehicleVariant[]>();
    for (const v of variants) byChassis.set(v.chassisCode, [...(byChassis.get(v.chassisCode) ?? []), v]);
    return [...byChassis.entries()].map(([code, rows]) => {
      const years = [...new Set(rows.map((r) => r.yearLabel).filter(Boolean))].join(", ");
      return { chassisCode: code, label: years ? `${code} · ${years}` : code };
    });
  }, [variants]);

  const engines = useMemo(
    () => [...new Set(variants.filter((v) => v.chassisCode === chassisCode).map((v) => v.engineCode).filter((e): e is string => Boolean(e)))],
    [variants, chassisCode],
  );

  const vehicle: SelectedVehicle | null = useMemo(() => {
    const model = models.find((m) => m.slug === modelSlug);
    // A catalogue vehicle listed by chassis only (no engine codes) completes on the generation.
    if (!model || !chassisCode || (!engineCode && engines.length > 0)) return null;
    const gen = generations.find((g) => g.chassisCode === chassisCode);
    return {
      modelSlug: model.slug,
      modelName: model.name,
      generation: gen?.label ?? chassisCode,
      chassisCode,
      engineCode,
    };
  }, [models, modelSlug, chassisCode, engineCode, engines, generations]);

  const selectModel = useCallback(
    async (slug: string) => {
      setModelSlug(slug);
      setChassisCode("");
      setEngineCode("");
      setVariants([]);
      if (!slug) return;
      setVariants(report(await gateway.listVariants(slug)) ?? []);
    },
    [gateway, report],
  );

  const selectGeneration = useCallback(
    (code: string) => {
      setChassisCode(code);
      const only = [...new Set(variants.filter((v) => v.chassisCode === code).map((v) => v.engineCode).filter(Boolean))];
      setEngineCode(only.length === 1 ? (only[0] as string) : "");
    },
    [variants],
  );

  const clearVehicle = useCallback(() => {
    setModelSlug("");
    setChassisCode("");
    setEngineCode("");
    setVariants([]);
  }, []);

  // Keep the cart's vehicle context in step with the cascade (fitment travels with the sale).
  useEffect(() => {
    const c = cartRef.current;
    if (!c) return;
    void gateway.setCartVehicle(c.id, vehicle).then((res) => {
      const next = report(res);
      if (next) setCart(next);
    });
  }, [vehicle, gateway, report]);

  // Search — fitment-filtered when a vehicle is selected.
  const runSearch = useCallback(
    async (text: string) => {
      const q = text.trim();
      setQuery(text);
      if (!q && !vehicle) {
        setResults(null);
        return;
      }
      setBusy(true);
      const res = report(await gateway.searchParts(q, vehicle));
      setBusy(false);
      setResults(res ?? []);
      setDestination((d) => (d === "home" ? "search" : d));
      if (q) {
        const next = [q, ...recent.filter((r) => r.toLowerCase() !== q.toLowerCase())].slice(0, RECENT_MAX);
        setRecent(next);
        writeRecent(next);
      }
    },
    [gateway, vehicle, recent, report],
  );

  const clearRecent = useCallback(() => {
    setRecent([]);
    writeRecent([]);
  }, []);

  // Popular Items — best sellers + pins; every card removable, anything pinnable (owner decision D1).
  const popular: PopularRowItem[] = useMemo(() => buildPopularRow(pins, bestSellers, hidden), [pins, bestSellers, hidden]);

  // Last known live data per OEM (best sellers + search results) so a pinned part card shows its
  // real price/stock and can be added directly.
  const partCache = useMemo(() => {
    const m = new Map<string, PosPart>();
    for (const p of [...bestSellers, ...(results ?? [])]) m.set(p.oemPartNumber.trim().toUpperCase(), p);
    return m;
  }, [bestSellers, results]);

  const partForPin = useCallback(
    (pin: PopularPin): PosPart | null =>
      pin.kind === "part" && pin.oemPartNumber ? (partCache.get(pin.oemPartNumber.trim().toUpperCase()) ?? null) : null,
    [partCache],
  );

  const addPinLocal = useCallback((pin: PopularPin) => {
    haptic("select");
    setPins((cur) => [pin, ...cur.filter((p) => !(p.kind === pin.kind && p.key === pin.key))]);
  }, []);

  const isPinned = useCallback(
    (part: PosPart) => pins.some((p) => p.kind === "part" && p.key === pinForPart(part).key),
    [pins],
  );

  const pinPart = useCallback(
    async (part: PosPart) => {
      const pin = pinForPart(part);
      if (report(await gateway.pin(pin))) addPinLocal(pin);
      if (part.stockItemId && hidden.has(part.stockItemId)) {
        if (report(await gateway.unhideBestSeller(part.stockItemId))) {
          setHidden((cur) => {
            const next = new Set(cur);
            next.delete(part.stockItemId as string);
            return next;
          });
        }
      }
    },
    [gateway, hidden, report],
  );

  const removePopular = useCallback(
    async (item: PopularRowItem) => {
      if (item.source === "pin") {
        if (report(await gateway.unpin(item.pin))) {
          haptic("select");
          setPins((cur) => cur.filter((p) => !(p.kind === item.pin.kind && p.key === item.pin.key)));
        }
        return;
      }
      const id = item.part.stockItemId;
      if (!id) return;
      if (report(await gateway.hideBestSeller(id))) {
        haptic("select");
        setHidden((cur) => new Set(cur).add(id));
      }
    },
    [gateway, report],
  );

  // Current Sale
  const ensureCart = useCallback(async (): Promise<PosCart | null> => {
    if (cartRef.current) return cartRef.current;
    const t = tillRef.current;
    if (!t || t.status !== "open") {
      setError(t?.status === "variance_pending" ? "The till is waiting for a manager to approve its cash variance." : "Open the till before starting a sale.");
      setDestination("till");
      return null;
    }
    const opened = report(await gateway.openCart({ ...setup, warehouseId: t.warehouseId, currency: t.currency }));
    if (!opened) return null;
    let next = opened;
    if (report(await gateway.attachCartToTill(opened.id, t.id))) next = { ...next, tillSessionId: t.id };
    if (vehicle) next = report(await gateway.setCartVehicle(opened.id, vehicle)) ?? opened;
    setCart(next);
    return next;
  }, [gateway, report, vehicle, setup]);

  // Companion phone: pair a scanner phone to this sale. The phone scans; lines arrive live.
  const [companion, setCompanion] = useState<(ScanSession & { status: string }) | null>(null);
  const companionRef = useRef(companion);
  companionRef.current = companion;

  const pairCompanion = useCallback(async () => {
    const open = await ensureCart();
    if (!open) return;
    const session = report(await gateway.createScanSession(open.id));
    if (!session) return;
    haptic("success");
    setCompanion({ ...session, status: "open" });
  }, [ensureCart, gateway, report]);

  const unpairCompanion = useCallback(async () => {
    const current = companionRef.current;
    if (!current) return;
    if (current.status === "open" || current.status === "claimed") await gateway.revokeScanSession(current.sessionId);
    setCompanion(null);
  }, [gateway]);

  useEffect(() => {
    if (!companion || !cart) return;
    const cartId = cart.id;
    return gateway.watchCompanion(
      cartId,
      companion.sessionId,
      () => {
        void gateway.loadCart(cartId).then((r) => {
          if (r.ok && cartRef.current?.id === cartId) {
            haptic("tap");
            setCart(r.data);
          }
        });
      },
      (status) => setCompanion((c) => (c ? { ...c, status } : c)),
    );
    // Re-subscribe only when the paired session or the sale changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gateway, companion?.sessionId, cart?.id]);

  // A finished, parked or voided sale ends the pairing.
  useEffect(() => {
    if (!cart && companionRef.current) void unpairCompanion();
  }, [cart, unpairCompanion]);

  const guardOnline = useCallback(() => {
    if (online) return true;
    setError("Offline — the web POS cannot change a sale until the connection returns.");
    return false;
  }, [online]);

  /** Only an unreserved sale can change (§10.5): a reserved one is released first by cancelling payment. */
  const guardEditable = useCallback(() => {
    if (!checkoutRef.current) return true;
    setError("This sale is reserved for payment. Go back to the sale from the payment screen to change it.");
    return false;
  }, []);

  const addPart = useCallback(
    async (part: PosPart) => {
      if (!guardOnline() || !guardEditable()) return;
      if (!part.price) {
        setError(`${part.oemPartNumber} has no price — it cannot be sold until it is priced.`);
        return;
      }
      if (part.saleableQty != null && part.saleableQty <= 0) {
        setError(`${part.oemPartNumber} is out of stock — it cannot be sold until stock is received.`);
        return;
      }
      setBusy(true);
      const c = await ensureCart();
      if (c) {
        const next = report(await gateway.addPart(c.id, part, 1));
        if (next) {
          setCart(next);
          haptic("tap");
        }
      }
      setBusy(false);
    },
    [guardOnline, guardEditable, ensureCart, gateway, report],
  );

  const setLineQty = useCallback(
    async (lineId: string, qty: number) => {
      const c = cartRef.current;
      if (!c || !guardOnline() || !guardEditable()) return;
      haptic("tap");
      const next = report(await gateway.setLineQty(c.id, lineId, qty));
      if (next) setCart(next);
    },
    [gateway, guardOnline, guardEditable, report],
  );

  const removeLine = useCallback(
    async (lineId: string) => {
      const c = cartRef.current;
      if (!c || !guardOnline() || !guardEditable()) return;
      haptic("select");
      const next = report(await gateway.removeLine(c.id, lineId));
      if (next) setCart(next);
    },
    [gateway, guardOnline, guardEditable, report],
  );

  /** Quick Sale setup applies to the next sale; it is locked once parts are on the sale. */
  const changeSetup = useCallback(
    (next: Partial<SaleSetup>) => {
      const c = cartRef.current;
      if (c && c.lines.length > 0) {
        setError("Sale setup is locked once parts are on the sale. Park or finish this sale first.");
        return;
      }
      if (c) setCart(null);
      setSetup((cur) => ({ ...cur, ...next }));
    },
    [],
  );

  // Till refresh (used by manager approvals below)
  const refreshTill = useCallback(async () => {
    setTill(report(await gateway.getMyTill(deviceId())) ?? null);
  }, [gateway, report]);

  const refreshTillHistory = useCallback(async () => {
    setTillHistory(report(await gateway.listTillSessions(null)) ?? []);
  }, [gateway, report]);

  // Manager-gated actions
  const requestManager = useCallback(
    async (prompt: ManagerPrompt) => {
      if (!guardOnline()) return;
      if ((prompt.kind === "void" || prompt.kind === "discount" || prompt.kind === "override") && !guardEditable()) return;
      // A signed-in manager approves under their own name: no badge or password needed.
      setPromptNeedsManager(true);
      setManagerPrompt(prompt);
      if (selfApprover) {
        setPromptNeedsManager(false);
        return;
      }
      // Governed sale actions ask the policy first; drawer actions always need a manager on the server.
      const action = reasonActionFor(prompt);
      if (!action || action === "till_variance") return;
      let value = 0;
      if (prompt.kind === "discount") value = prompt.percent;
      if (prompt.kind === "override") {
        const before = cartRef.current?.lines.find((l) => l.id === prompt.lineId)?.unitPrice ?? 0;
        value = before === 0 ? (prompt.unitPrice === 0 ? 0 : 100) : (Math.abs(prompt.unitPrice - before) / before) * 100;
      }
      const res = await gateway.requiresManager(action, value);
      setPromptNeedsManager(res.ok ? res.data : true);
    },
    [guardOnline, guardEditable, gateway, selfApprover],
  );

  /**
   * Approve the open prompt. [proof] is a scanned badge, a manager's password for this one action,
   * or "self" when the signed-in user is a manager; within policy no proof is needed at all.
   */
  const confirmManager = useCallback(
    async (proof: ManagerProof | null, notes: string | null, reasonCode: string | null = null): Promise<boolean> => {
      const prompt = managerPrompt;
      const c = cartRef.current;
      if (!prompt) return false;
      const governedKind = prompt.kind === "void" || prompt.kind === "discount" || prompt.kind === "override" || prompt.kind === "refund";
      if ((governedKind || prompt.kind === "tillVariance") && !reasonCode) {
        setError("Choose a reason.");
        return false;
      }
      if (promptNeedsManager && (!proof || (proof.kind === "self" && !selfApprover))) {
        setError("An approver must approve this: scan their badge or let them sign in.");
        return false;
      }
      const creds = proof?.kind === "password" ? proof.credentials : null;
      const t = tillRef.current;
      setBusy(true);
      try {
        let by = "";
        if (proof?.kind === "badge") {
          // One server call validates the badge, runs the action as approved by its holder, and audits it.
          const req = badgeRequestFor(prompt, c, t, reasonCode, notes);
          if (!req) return false;
          const res = await gateway.badgeApprove(proof.payload, req.action, req.args, deviceId());
          if (!res.ok) {
            setError(res.error);
            return false;
          }
          by = `, approved by ${res.data.managerName ?? "manager badge"}`;
          if ((prompt.kind === "discount" || prompt.kind === "override") && c) {
            const before = c.lines.reduce((sum, l) => sum + l.lineTotal, 0);
            const next = report(await gateway.loadCart(c.id));
            if (next) {
              setCart(next);
              if (prompt.kind === "discount") {
                const after = next.lines.reduce((sum, l) => sum + l.lineTotal, 0);
                setDiscount({ cartId: next.id, percent: prompt.percent, amount: Math.max(0, before - after) + (discount?.cartId === next.id ? discount.amount : 0) });
              }
            }
          }
        } else {
          const g: Governed = { reasonCode: reasonCode ?? "", notes, manager: creds };
          by = creds ? " with manager approval" : selfApprover && promptNeedsManager ? " under your manager sign-in" : "";
          if (prompt.kind === "cashOut") {
            if (!t || !report(await gateway.recordCashMovement(t.id, prompt.movement, prompt.amount, prompt.reasonCode, notes, creds))) return false;
          } else if (prompt.kind === "tillVariance") {
            if (!report(await gateway.approveTillVariance(prompt.sessionId, reasonCode ?? "", notes, creds))) return false;
          } else if (prompt.kind === "handover") {
            if (!report(await gateway.handoverTill(prompt.sessionId, prompt.userId, notes, creds))) return false;
          } else if (prompt.kind === "void") {
            if (!c) return false;
            const res = await gateway.voidCart(c.id, g);
            if (!res.ok) return Boolean(report(res));
          } else if (prompt.kind === "discount") {
            if (!c) return false;
            const before = c.lines.reduce((sum, l) => sum + l.lineTotal, 0);
            const next = report(await gateway.applyDiscount(c.id, prompt.percent, g));
            if (!next) return false;
            const after = next.lines.reduce((sum, l) => sum + l.lineTotal, 0);
            setCart(next);
            setDiscount({ cartId: next.id, percent: prompt.percent, amount: Math.max(0, before - after) + (discount?.cartId === next.id ? discount.amount : 0) });
          } else if (prompt.kind === "override") {
            if (!c) return false;
            const next = report(await gateway.overrideLinePrice(c.id, prompt.lineId, prompt.unitPrice, g));
            if (!next) return false;
            setCart(next);
          } else if (!report(await gateway.refundInvoice(prompt.invoiceId, g))) {
            return false;
          }
        }
        setError(null);
        // What the operator sees afterwards, whichever way it was approved.
        if (prompt.kind === "cashOut") setNotice(`${prompt.label} of ${prompt.amount.toFixed(2)} recorded${by}.`);
        else if (prompt.kind === "tillVariance") {
          setTill(null);
          setNotice(`Cash variance approved${by}. The till is closed.`);
          void refreshTillHistory();
        } else if (prompt.kind === "handover") {
          setTill(null);
          setCart(null);
          setNotice(`Till handed over to ${prompt.name}${by}. They continue on their own sign-in.`);
        } else if (prompt.kind === "void") {
          setCart(null);
          setDiscount(null);
          setNotice(`Sale voided${by}.`);
        } else if (prompt.kind === "discount") setNotice(`${prompt.percent}% discount applied${by}.`);
        else if (prompt.kind === "override") setNotice(`Price for ${prompt.lineName} overridden${by}.`);
        else setNotice(`Refund posted for ${prompt.documentNumber ?? "the sale"}${by}.`);
        setManagerPrompt(null);
        return true;
      } finally {
        setBusy(false);
      }
    },
    [managerPrompt, promptNeedsManager, selfApprover, gateway, report, discount, refreshTillHistory],
  );

  // Till (cash drawer)
  const openTill = useCallback(
    async (warehouseId: string, openingFloat: number, tillCurrency: PosCurrency): Promise<boolean> => {
      if (!guardOnline()) return false;
      setBusy(true);
      try {
        const opened = report(await gateway.openTill(warehouseId, deviceId(), openingFloat, tillCurrency));
        if (!opened) return false;
        setTill(opened);
        setSetup((cur) => ({ ...cur, warehouseId: opened.warehouseId, currency: opened.currency }));
        setNotice(`Till opened with a float of ${opened.openingFloat.toFixed(2)} ${opened.currency}.`);
        void refreshTillHistory();
        return true;
      } finally {
        setBusy(false);
      }
    },
    [gateway, guardOnline, report, refreshTillHistory],
  );

  /** Cash in is the operator's own; every cash-out kind goes to a manager first. */
  const cashMovement = useCallback(
    async (movement: CashMovementKind, amount: number, reasonCode: string, label: string, notes: string | null): Promise<boolean> => {
      const t = tillRef.current;
      if (!t || t.status !== "open" || !guardOnline()) return false;
      if (movement !== "cash_in") {
        setPromptNeedsManager(!selfApprover);
        setManagerPrompt({ kind: "cashOut", movement, amount, reasonCode, label });
        return true;
      }
      if (!report(await gateway.recordCashMovement(t.id, movement, amount, reasonCode, notes, null))) return false;
      setNotice(`Cash in of ${amount.toFixed(2)} recorded.`);
      return true;
    },
    [gateway, guardOnline, report, selfApprover],
  );

  /** Blind count: the operator never sees the expected cash before submitting. */
  const closeTill = useCallback(
    async (counts: DenominationCount[], varianceReasonCode: string | null, notes: string | null): Promise<TillCloseResult | "needs_reason" | null> => {
      const t = tillRef.current;
      if (!t || !guardOnline()) return null;
      if (cartRef.current && cartRef.current.lines.length > 0) {
        setError("Finish, park or void the current sale before closing the till.");
        return null;
      }
      setBusy(true);
      try {
        const res = await gateway.closeTill(t.id, counts, varianceReasonCode, notes);
        if (!res.ok && /variance reason required/i.test(res.error)) return "needs_reason";
        const result = report(res);
        if (!result) return null;
        if (result.status === "closed") {
          setTill(null);
          setCart(null);
        } else {
          setTill({ ...t, status: "variance_pending", countedCash: result.countedCash, expectedCash: result.expectedCash, variance: result.variance });
        }
        void refreshTillHistory();
        return result;
      } finally {
        setBusy(false);
      }
    },
    [gateway, guardOnline, report, refreshTillHistory],
  );

  const loadHandoverOperators = useCallback(async (): Promise<HandoverOperator[]> => report(await gateway.listHandoverOperators()) ?? [], [gateway, report]);

  // Customer + garage (0 → manual cascade, 1 → auto-select, many → chooser)
  const applyVehicle = useCallback(
    async (v: { modelSlug: string | null; chassisCode: string | null; engineCode: string | null }) => {
      if (!v.modelSlug || !v.chassisCode) return;
      setModelSlug(v.modelSlug);
      const list = report(await gateway.listVariants(v.modelSlug)) ?? [];
      setVariants(list);
      setChassisCode(v.chassisCode);
      const engines = [...new Set(list.filter((x) => x.chassisCode === v.chassisCode).map((x) => x.engineCode).filter(Boolean))];
      setEngineCode(v.engineCode ?? (engines.length === 1 ? (engines[0] as string) : ""));
    },
    [gateway, report],
  );

  const selectCustomer = useCallback(
    async (customer: PosCustomer | null) => {
      if (!guardOnline()) return;
      const c = await ensureCart();
      if (!c) return;
      const next = report(await gateway.setCartCustomer(c.id, customer?.id ?? null));
      if (!next) return;
      setCart(next);
      setGarageChoices(null);
      if (!customer) return;
      const garage = report(await gateway.listGarage(customer.id)) ?? [];
      if (garage.length === 1) {
        const g = garage[0];
        await applyVehicle({ modelSlug: g.modelSlug, chassisCode: g.chassisCode, engineCode: g.engine });
      } else if (garage.length > 1) {
        setGarageChoices(garage);
      }
    },
    [guardOnline, ensureCart, gateway, report, applyVehicle],
  );

  const chooseGarageVehicle = useCallback(
    async (g: GarageVehicle | null) => {
      setGarageChoices(null);
      if (g) await applyVehicle({ modelSlug: g.modelSlug, chassisCode: g.chassisCode, engineCode: g.engine });
    },
    [applyVehicle],
  );

  const saveCustomer = useCallback(
    async (input: CustomerInput, id: string | null): Promise<PosCustomer | null> => {
      const res = id ? await gateway.updateCustomer(id, input) : await gateway.createCustomer(input);
      return report(res);
    },
    [gateway, report],
  );

  const addVehicleToGarage = useCallback(async () => {
    const c = cartRef.current;
    if (!c?.customerId || !vehicle) return;
    if (report(await gateway.saveGarageVehicle(c.customerId, vehicle, false))) {
      setNotice(`${vehicle.modelName} ${vehicle.chassisCode} saved to ${c.customerName ?? "the customer"}'s garage.`);
    }
  }, [gateway, report, vehicle]);

  // Payment — reserve-first (Blueprint §10.5–10.7). Opening payment reserves the stock and locks the
  // sale; money is taken against that order; only `cancelCheckout` (or expiry) unlocks it.
  const finishSale = useCallback(
    async (invoiceId: string, orderId: string | null, tenders: TenderLine[]): Promise<ReceiptDocument | null> => {
      const receipt = report(await gateway.loadReceipt(invoiceId));
      const doc = receipt ? { ...receipt, tenders: receipt.tenders.length ? receipt.tenders : tenders, operator } : null;
      setLastReceipt(doc);
      setReceiptOrderId(orderId);
      setCheckout(null);
      setSplit(null);
      splitLegKey.current = null;
      checkoutKeys.current = null;
      if (doc) haptic("success");
      setCart(null);
      setDiscount(null);
      return doc;
    },
    [gateway, report, operator],
  );

  /** Reserve the sale for payment (idempotent per cart attempt) and read the server's order. */
  const beginCheckout = useCallback(
    async (contacts: ReceiptContacts): Promise<boolean> => {
      const c = cartRef.current;
      if (!c || c.lines.length === 0 || !guardOnline()) return false;
      if (checkoutRef.current && checkoutRef.current.cartId === c.id) return true;
      if (!checkoutKeys.current || checkoutKeys.current.cartId !== c.id) {
        checkoutKeys.current = { cartId: c.id, requestId: crypto.randomUUID(), paymentRequestId: crypto.randomUUID() };
      }
      setBusy(true);
      try {
        const orderId = report(await gateway.prepareCheckout(c.id, checkoutKeys.current.requestId, contacts));
        if (!orderId) return false;
        const status = report(await gateway.paymentStatus(orderId));
        if (!status) return false;
        setCheckout({ orderId, cartId: c.id, status, attempt: null, outcome: null, message: null });
        // A sale already part-paid on this order resumes where it stopped.
        const found = await gateway.findSplit(orderId);
        setSplit(found.ok ? found.data : null);
        void gateway.providerAvailability().then((r) => setProviders(r.ok ? r.data : { ecocash: "Unavailable", paynow: "Unavailable", contipay: "Unavailable" }));
        return true;
      } finally {
        setBusy(false);
      }
    },
    [gateway, guardOnline, report],
  );

  /** Cash, card/bank, store credit: one idempotent settlement; a dropped answer is retried with the same key. */
  const payManual = useCallback(
    async (tenders: ManualTenderLine[], contacts: ReceiptContacts): Promise<ReceiptDocument | null> => {
      const co = checkoutRef.current;
      const keys = checkoutKeys.current;
      if (!co || !keys || !guardOnline()) return null;
      setBusy(true);
      try {
        // Same request id: refreshes the receipt contacts on the reserved order, nothing else.
        if (!report(await gateway.prepareCheckout(co.cartId, keys.requestId, contacts))) return null;
        const res = await gateway.settleTenders(co.orderId, keys.paymentRequestId, tenders);
        if (res.ok) return await finishSale(res.data.invoiceId, co.orderId, tenders);
        // Business refusals are definite: a fresh key for the next attempt. A network failure is not.
        const network = /fetch|network|timeout|failed to fetch/i.test(res.error);
        if (!network) keys.paymentRequestId = crypto.randomUUID();
        setCheckout({ ...co, outcome: network ? "unknown" : "error", message: network ? "The payment answer did not arrive. Retry safely: it cannot be taken twice." : res.error });
        if (/reservation expired/i.test(res.error)) expireCheckout();
        return null;
      } finally {
        setBusy(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [gateway, guardOnline, report, finishSale],
  );

  /** Start a digital payment; the order is watched until the provider settles or fails it. */
  const payProvider = useCallback(
    async (provider: DigitalProvider, params: { msisdn?: string; method?: PaynowMethod | ContipayMethod }, contacts: ReceiptContacts) => {
      const co = checkoutRef.current;
      const keys = checkoutKeys.current;
      if (!co || !keys || !guardOnline()) return;
      setBusy(true);
      try {
        if (!report(await gateway.prepareCheckout(co.cartId, keys.requestId, contacts))) return;
        const returnUrl = `${window.location.origin}/staff/pos`;
        const res = await gateway.startProvider(co.orderId, provider, { ...params, returnUrl });
        if (!res.ok) {
          setCheckout({ ...co, outcome: "error", message: res.error });
          return;
        }
        setCheckout({ ...co, attempt: { provider, intentId: res.data.intentId, checkoutUrl: res.data.checkoutUrl, startedAt: Date.now() }, outcome: null, message: res.data.message });
      } finally {
        setBusy(false);
      }
    },
    [gateway, guardOnline, report],
  );

  /** Re-read the order: maps the provider's answer to exactly one outcome (§10.7). */
  const refreshCheckout = useCallback(async () => {
    const co = checkoutRef.current;
    if (!co) return;
    const res = await gateway.paymentStatus(co.orderId);
    if (!res.ok) return;
    const st = res.data;
    if (st.salesInvoiceId && (st.state === "paid" || st.state === "allocation_pending" || st.state === "dispatch_ready")) {
      setCheckout({ ...co, status: st, outcome: "approved" });
      await finishSale(st.salesInvoiceId, co.orderId, [{ tender: st.settledProvider === "ecocash" ? "ecocash" : "bank", amount: st.total }]);
      return;
    }
    if (st.state === "allocation_pending" && !st.salesInvoiceId) {
      // Money captured, sale not finalised: never charge again — resolve from recovery.
      setCheckout({ ...co, status: st, outcome: "unknown", message: "The money arrived but the sale did not finish. Resolve it from Payments to resolve." });
      return;
    }
    if (st.state === "payment_failed" && co.attempt) {
      const cancelled = /cancel/i.test(`${st.providerStatus} ${st.providerFailure}`);
      setCheckout({ ...co, status: st, attempt: null, outcome: cancelled ? "cancelled" : "declined", message: st.providerFailure ?? "The payment was declined." });
      return;
    }
    if (st.state === "payment_expired" || st.state === "cancelled") {
      expireCheckout();
      return;
    }
    // Still waiting: past the window the outcome is Unknown — block a second charge and go to recovery.
    if (co.attempt && Date.now() - co.attempt.startedAt > PROVIDER_WAIT_MS) {
      setCheckout({ ...co, status: st, outcome: "unknown", message: "No answer from the provider. Do not take this payment again until it is resolved." });
      return;
    }
    setCheckout({ ...co, status: st });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gateway, finishSale]);

  // Watch a digital attempt every 3 s while the payment screen is open.
  useEffect(() => {
    if (!checkout?.attempt || checkout.outcome) return;
    const t = window.setInterval(() => void refreshCheckout(), 3_000);
    return () => window.clearInterval(t);
  }, [checkout?.attempt, checkout?.outcome, refreshCheckout]);

  /** The reservation ran out: the sale is unlocked with its lines intact and re-reserves next time. */
  const expireCheckout = useCallback(() => {
    setCheckout(null);
    setSplit(null);
    checkoutKeys.current = null;
    setNotice("The payment reservation expired. The sale is unchanged; continue to payment to reserve it again.");
  }, []);

  useEffect(() => {
    const at = checkout?.status.reservationExpiresAt ? Date.parse(checkout.status.reservationExpiresAt) : NaN;
    if (!Number.isFinite(at) || checkout?.attempt) return;
    const t = window.setTimeout(() => void refreshCheckout().then(() => {
      if (checkoutRef.current && Date.parse(checkoutRef.current.status.reservationExpiresAt ?? "") <= Date.now()) expireCheckout();
    }), Math.max(0, at - Date.now()) + 500);
    return () => window.clearTimeout(t);
  }, [checkout?.status.reservationExpiresAt, checkout?.attempt, refreshCheckout, expireCheckout]);

  /** Back to the sale: releases the stock and unlocks editing (refused while money is in flight). */
  const cancelCheckout = useCallback(
    async (reason = "Operator returned to the sale"): Promise<boolean> => {
      const co = checkoutRef.current;
      if (!co) return true;
      setBusy(true);
      try {
        if (!report(await gateway.cancelCheckout(co.orderId, reason))) return false;
        setCheckout(null);
        checkoutKeys.current = null;
        return true;
      } finally {
        setBusy(false);
      }
    },
    [gateway, report],
  );

  /** On account: the server's credit checks decide; the reservation is released first. */
  const payOnAccount = useCallback(
    async (contacts: ReceiptContacts): Promise<ReceiptDocument | null> => {
      const c = cartRef.current;
      if (!c || !guardOnline()) return null;
      if (checkoutRef.current && !(await cancelCheckout("Switched to account credit"))) return null;
      setBusy(true);
      try {
        const invoiceId = report(await gateway.checkoutOnAccount(c.id, contacts));
        return invoiceId ? await finishSale(invoiceId, null, []) : null;
      } finally {
        setBusy(false);
      }
    },
    [gateway, guardOnline, report, cancelCheckout, finishSale],
  );

  // Part payments (staged split, Blueprint §10.5 / §10.8). Each part is its own idempotent step; the
  // remaining balance is always the server's. The sale posts on its own when the parts cover it.
  const applySplit = useCallback(
    async (session: SplitSession): Promise<ReceiptDocument | null> => {
      setSplit(session);
      const co = checkoutRef.current;
      if (session.finalInvoiceId && ["settled", "refund_review", "refund_pending"].includes(session.status)) {
        const tenders = session.legs
          .filter((l) => (l.appliedAmount ?? l.amount) > 0 && ["captured", "allocated", "refund_review", "refund_pending"].includes(l.status))
          .map((l) => ({ tender: l.tender as TenderLine["tender"], amount: roundMoney(l.appliedAmount ?? l.amount) }));
        const doc = await finishSale(session.finalInvoiceId, session.orderId, tenders);
        const owed = session.refunds.filter((r) => r.status !== "settled" && r.status !== "cancelled").reduce((a, r) => a + r.grossAmount, 0);
        if (owed > 0) setNotice(`${formatMoney(roundMoney(owed), session.currency)} received over the new total is owed back to the customer. A manager approves the refund in Payments to resolve.`);
        return doc;
      }
      if (session.status === "finalization_failed" && co) {
        setCheckout({
          ...co,
          outcome: "unknown",
          message: `Paid in full but the sale did not post${session.finalizationError ? `: ${session.finalizationError}` : ""}. Do not take any more money; resolve it from Payments to resolve.`,
        });
      }
      return null;
    },
    [finishSale],
  );

  const startSplit = useCallback(async (): Promise<boolean> => {
    const co = checkoutRef.current;
    if (!co || !guardOnline()) return false;
    setBusy(true);
    try {
      const session = report(await gateway.startSplit(co.orderId));
      if (!session) return false;
      await applySplit(session);
      return true;
    } finally {
      setBusy(false);
    }
  }, [gateway, guardOnline, report, applySplit]);

  const addSplitPart = useCallback(
    async (tender: SplitTender, amount: number, reference: string | null): Promise<ReceiptDocument | null> => {
      const session = split;
      if (!session || !guardOnline()) return null;
      splitLegKey.current ??= crypto.randomUUID();
      setBusy(true);
      try {
        const res = await gateway.addSplitLeg(session.sessionId, tender, roundMoney(amount), splitLegKey.current, reference?.trim() || null);
        if (!res.ok) {
          // A dropped answer keeps its key (the retry returns the same part); a refusal gets a fresh one.
          if (!/fetch|network|timeout/i.test(res.error)) splitLegKey.current = null;
          setError(res.error);
          return null;
        }
        splitLegKey.current = null;
        haptic("success");
        return await applySplit(res.data);
      } finally {
        setBusy(false);
      }
    },
    [gateway, guardOnline, split, applySplit],
  );

  /** The customer keeps only what is paid for: the server works out the reduced basket and posts it. */
  const reduceBasket = useCallback(
    async (items: Array<{ cartLineId: string; qty: number }>, notes: string | null): Promise<ReceiptDocument | null> => {
      const session = split;
      if (!session || !guardOnline()) return null;
      setBusy(true);
      try {
        const next = report(await gateway.acceptReducedBasket(session.sessionId, items, notes));
        if (!next) return null;
        const c = cartRef.current;
        if (c && !next.finalInvoiceId) {
          const reloaded = report(await gateway.loadCart(c.id));
          if (reloaded) setCart(reloaded);
        }
        return await applySplit(next);
      } finally {
        setBusy(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [gateway, guardOnline, report, split, applySplit],
  );

  /** Stop a part-paid sale: the stock is released; money already taken becomes a refund for a manager. */
  const cancelSplit = useCallback(
    async (reason: string, feePolicy: RefundFeePolicy = "manual_review"): Promise<boolean> => {
      const session = split;
      if (!session) return true;
      setBusy(true);
      try {
        const next = report(await gateway.cancelSplit(session.sessionId, reason, feePolicy));
        if (!next) return false;
        setSplit(null);
        setCheckout(null);
        checkoutKeys.current = null;
        splitLegKey.current = null;
        setNotice(
          next.refunds.length
            ? "Part-paid sale cancelled. The money taken is waiting for a manager to refund it in Payments to resolve."
            : "Part payments cancelled. The sale can be edited or paid again.",
        );
        return true;
      } finally {
        setBusy(false);
      }
    },
    [gateway, report, split],
  );

  const retrySplitFinalization = useCallback(
    async (sessionId: string): Promise<SplitSession | null> => {
      const next = report(await gateway.retrySplitFinalization(sessionId));
      if (!next) return null;
      if (split?.sessionId === sessionId) await applySplit(next);
      else if (next.status === "settled") setNotice("The sale is posted. It is now in Ready for pickup.");
      return next;
    },
    [gateway, report, split, applySplit],
  );

  const refreshSplitRecovery = useCallback(async () => {
    setSplitRecovery(report(await gateway.listSplitRecovery()) ?? []);
  }, [gateway, report]);

  /** Refund steps for captured parts: manager or finance, by badge, password, or as the signed-in approver. */
  const splitRefundStep = useCallback(
    async (refundId: string, step: SplitRefundStep, proof: ManagerProof): Promise<boolean> => {
      if (proof.kind === "badge") {
        const [action, args]: [BadgeAction, Record<string, unknown>] =
          step.kind === "approve"
            ? ["split_refund_approve", { refund_id: refundId, fee_policy: step.feePolicy, customer_fee: step.customerFee, notes: step.notes }]
            : step.kind === "complete"
              ? ["split_refund_complete", { refund_id: refundId, provider_ref: step.providerRef, notes: step.notes }]
              : ["split_refund_fail", { refund_id: refundId, reason: step.reason }];
        const res = await gateway.badgeApprove(proof.payload, action, args, deviceId());
        if (!res.ok) {
          setError(res.error);
          return false;
        }
      } else if (!report(await gateway.splitRefundStep(refundId, step, proof.kind === "password" ? proof.credentials : null))) return false;
      setNotice(step.kind === "approve" ? "Refund approved." : step.kind === "complete" ? "Refund recorded as paid to the customer." : "Refund marked as failed.");
      void refreshSplitRecovery();
      return true;
    },
    [gateway, report, refreshSplitRecovery],
  );

  /** An Unknown outcome goes to its dedicated screen (§10.4): it cannot be dismissed by a tap. */
  const openRecovery = useCallback((orderId: string | null) => {
    setRecoveryOrderId(orderId);
    setDestination("recovery");
  }, []);

  const refreshRecovery = useCallback(async () => {
    setRecoveryItems(report(await gateway.listPaymentRecovery()) ?? []);
  }, [gateway, report]);

  const refreshPickups = useCallback(async (q = "") => {
    setPickups(report(await gateway.listPickupOrders(q)) ?? []);
  }, [gateway, report]);

  const collectOrder = useCallback(
    async (orderId: string, notes: string | null = null): Promise<boolean> => {
      if (!guardOnline()) return false;
      if (!report(await gateway.collectOrder(orderId, notes))) return false;
      setNotice("Marked as collected.");
      setReceiptOrderId((cur) => (cur === orderId ? null : cur));
      void refreshPickups();
      return true;
    },
    [gateway, guardOnline, report, refreshPickups],
  );

  const repairPaidOrder = useCallback(
    async (orderId: string, notes: string | null, proof: ManagerProof): Promise<boolean> => {
      if (proof.kind === "badge") {
        const res = await gateway.badgeApprove(proof.payload, "repair_paid_order", { order_id: orderId, notes }, deviceId());
        if (!res.ok) {
          setError(res.error);
          return false;
        }
      } else if (!report(await gateway.repairPaidOrder(orderId, notes, proof.kind === "password" ? proof.credentials : null))) return false;
      setNotice("Paid order repaired: the sale is finalised and the payment applied.");
      void refreshRecovery();
      return true;
    },
    [gateway, report, refreshRecovery],
  );

  /** Cancel a stuck order from recovery: allowed only while no money is in flight (server-checked). */
  const releaseOrder = useCallback(
    async (orderId: string, reason: string): Promise<boolean> => {
      if (!report(await gateway.cancelCheckout(orderId, reason))) return false;
      if (checkoutRef.current?.orderId === orderId) {
        setCheckout(null);
        checkoutKeys.current = null;
      }
      setNotice("Reservation released. The sale can be edited or paid again.");
      void refreshRecovery();
      return true;
    },
    [gateway, report, refreshRecovery],
  );

  // Orders
  const parkCurrent = useCallback(async () => {
    const c = cartRef.current;
    if (!c || c.lines.length === 0 || !guardOnline() || !guardEditable()) return;
    if (report(await gateway.parkCart(c.id))) {
      setCart(null);
      setDiscount(null);
      setNotice("Sale parked. Resume it from Orders.");
    }
  }, [gateway, guardOnline, guardEditable, report]);

  const resume = useCallback(
    async (cartId: string) => {
      if (cartRef.current && cartRef.current.lines.length > 0) {
        setError("Park or finish the current sale before resuming another.");
        return;
      }
      const next = report(await gateway.resumeCart(cartId));
      if (next) {
        setCart(next);
        setDestination("home");
      }
    },
    [gateway, report],
  );

  const quoteCurrent = useCallback(
    async (validUntil: string | null, notes: string | null) => {
      const c = cartRef.current;
      if (!c || c.lines.length === 0) return null;
      const id = report(await gateway.createQuotation(c.id, validUntil, notes));
      if (id) setNotice("Quotation created. Send or convert it from Orders.");
      return id;
    },
    [gateway, report],
  );

  const convertQuote = useCallback(
    async (quotationId: string) => {
      if (cartRef.current && cartRef.current.lines.length > 0) {
        setError("Park or finish the current sale before converting a quotation.");
        return;
      }
      const next = report(await gateway.convertQuotation(quotationId));
      if (next) {
        setCart(next);
        setDestination("home");
      }
    },
    [gateway, report],
  );

  // Pins beyond parts: vehicle (model) and category, both from anywhere they render (blueprint §7.2)
  const pinVehicle = useCallback(async () => {
    if (!vehicle) return;
    const pin: PopularPin = {
      kind: "model",
      key: `${vehicle.modelSlug}|${vehicle.chassisCode}|${vehicle.engineCode}`,
      label: `${vehicle.modelName} ${vehicle.chassisCode}`,
      subtitle: vehicle.engineCode,
      searchQuery: `${vehicle.modelName} ${vehicle.chassisCode}`,
      oemPartNumber: null,
      imageUrl: null,
    };
    if (report(await gateway.pin(pin))) addPinLocal(pin);
  }, [gateway, report, vehicle]);

  const pinCategory = useCallback(
    async (label: string, query: string, kind: "category" | "subcategory" = "category") => {
      const pin: PopularPin = { kind, key: label.toLowerCase(), label, subtitle: kind === "category" ? "Category" : "Subcategory", searchQuery: query, oemPartNumber: null, imageUrl: null };
      if (report(await gateway.pin(pin))) addPinLocal(pin);
    },
    [gateway, report],
  );

  const activatePin = useCallback(
    async (pin: PopularPin) => {
      if (pin.kind === "model") {
        const [slug, chassis, engine] = pin.key.split("|");
        await applyVehicle({ modelSlug: slug ?? null, chassisCode: chassis ?? null, engineCode: engine ?? null });
        return;
      }
      await runSearch(pin.searchQuery);
    },
    [applyVehicle, runSearch],
  );

  const subtotal = useMemo(() => (cart?.lines ?? []).reduce((sum, l) => sum + l.lineTotal, 0), [cart]);
  const discountAmount = discount && cart && discount.cartId === cart.id ? discount.amount : 0;

  return {
    gateway,
    isPreview: gateway.isPreview,
    companion,
    pairCompanion,
    unpairCompanion,
    destination,
    setDestination,
    operator,
    online,
    error,
    dismissError: () => setError(null),
    showError: (message: string) => setError(message),
    reportError: (message: string) => setError(message),
    notice,
    dismissNotice: () => setNotice(null),
    busy,
    // vehicle
    models,
    generations,
    engines,
    modelSlug,
    chassisCode,
    engineCode,
    vehicle,
    selectModel,
    selectGeneration,
    selectEngine: setEngineCode,
    clearVehicle,
    applyVehicle,
    // search
    query,
    setQuery,
    results,
    runSearch,
    recent,
    clearRecent,
    // popular
    popular,
    partForPin,
    isPinned,
    pinPart,
    pinVehicle,
    pinCategory,
    activatePin,
    removePopular,
    // sale
    cart,
    currency,
    subtotal,
    discountAmount,
    addPart,
    setLineQty,
    removeLine,
    warehouses,
    setup,
    changeSetup,
    // manager
    managerPrompt,
    promptNeedsManager,
    selfApprover,
    requestManager,
    cancelManager: () => setManagerPrompt(null),
    confirmManager,
    // customer
    selectCustomer,
    saveCustomer,
    garageChoices,
    chooseGarageVehicle,
    addVehicleToGarage,
    // payment
    checkout,
    beginCheckout,
    payManual,
    payProvider,
    refreshCheckout,
    cancelCheckout,
    payOnAccount,
    providers,
    receiptOrderId,
    openRecovery,
    recoveryOrderId,
    recoveryItems,
    refreshRecovery,
    repairPaidOrder,
    releaseOrder,
    pickups,
    refreshPickups,
    collectOrder,
    split,
    startSplit,
    addSplitPart,
    reduceBasket,
    cancelSplit,
    retrySplitFinalization,
    splitRecovery,
    refreshSplitRecovery,
    splitRefundStep,
    lastReceipt,
    clearReceipt: () => {
      setLastReceipt(null);
      setReceiptOrderId(null);
    },
    // till
    till,
    tillLoaded,
    tillHistory,
    refreshTill,
    refreshTillHistory,
    openTill,
    cashMovement,
    closeTill,
    loadHandoverOperators,
    // orders
    parkCurrent,
    resume,
    quoteCurrent,
    convertQuote,
  };
}

export type PosStore = ReturnType<typeof usePos>;
