"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { PosGateway, ScanSession } from "@/lib/pos/gateway";
import { haptic } from "@/lib/pos/haptics";
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
  const [discount, setDiscount] = useState<{ cartId: string; percent: number; amount: number } | null>(null);
  const [garageChoices, setGarageChoices] = useState<GarageVehicle[] | null>(null);
  const [lastReceipt, setLastReceipt] = useState<ReceiptDocument | null>(null);
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

  const addPart = useCallback(
    async (part: PosPart) => {
      if (!guardOnline()) return;
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
    [guardOnline, ensureCart, gateway, report],
  );

  const setLineQty = useCallback(
    async (lineId: string, qty: number) => {
      const c = cartRef.current;
      if (!c || !guardOnline()) return;
      haptic("tap");
      const next = report(await gateway.setLineQty(c.id, lineId, qty));
      if (next) setCart(next);
    },
    [gateway, guardOnline, report],
  );

  const removeLine = useCallback(
    async (lineId: string) => {
      const c = cartRef.current;
      if (!c || !guardOnline()) return;
      haptic("select");
      const next = report(await gateway.removeLine(c.id, lineId));
      if (next) setCart(next);
    },
    [gateway, guardOnline, report],
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
      // Governed sale actions ask the policy first; drawer actions always need a manager on the server.
      setPromptNeedsManager(true);
      setManagerPrompt(prompt);
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
    [guardOnline, gateway],
  );

  const confirmManager = useCallback(
    async (manager: ManagerCredentials | null, notes: string | null, reasonCode: string | null = null): Promise<boolean> => {
      const prompt = managerPrompt;
      const c = cartRef.current;
      if (!prompt) return false;
      const governedKind = prompt.kind === "void" || prompt.kind === "discount" || prompt.kind === "override" || prompt.kind === "refund";
      if (governedKind && !reasonCode) {
        setError("Choose a reason.");
        return false;
      }
      if (!manager && (promptNeedsManager || !governedKind)) {
        setError("A manager must sign in to approve this.");
        return false;
      }
      const g: Governed = { reasonCode: reasonCode ?? "", notes, manager: promptNeedsManager ? manager : null };
      const by = g.manager ? " with manager approval" : "";
      setBusy(true);
      try {
        if (prompt.kind === "cashOut") {
          const t = tillRef.current;
          if (!t) return false;
          if (!manager || !report(await gateway.recordCashMovement(t.id, prompt.movement, prompt.amount, prompt.reasonCode, notes, manager))) return false;
          setNotice(`${prompt.label} of ${prompt.amount.toFixed(2)} recorded with manager approval.`);
        } else if (prompt.kind === "tillVariance") {
          if (!reasonCode) {
            setError("Choose a reason for the variance.");
            return false;
          }
          if (!manager || !report(await gateway.approveTillVariance(prompt.sessionId, reasonCode, notes, manager))) return false;
          setTill(null);
          setNotice("Cash variance approved. The till is closed.");
          void refreshTillHistory();
        } else if (prompt.kind === "handover") {
          if (!manager || !report(await gateway.handoverTill(prompt.sessionId, prompt.userId, notes, manager))) return false;
          setTill(null);
          setCart(null);
          setNotice(`Till handed over to ${prompt.name}. They continue on their own sign-in.`);
        } else if (prompt.kind === "void") {
          if (!c) return false;
          const res = await gateway.voidCart(c.id, g);
          if (!res.ok) return Boolean(report(res));
          setCart(null);
          setDiscount(null);
          setNotice(`Sale voided${by}.`);
        } else if (prompt.kind === "discount") {
          if (!c) return false;
          const before = c.lines.reduce((s, l) => s + l.lineTotal, 0);
          const next = report(await gateway.applyDiscount(c.id, prompt.percent, g));
          if (!next) return false;
          const after = next.lines.reduce((s, l) => s + l.lineTotal, 0);
          setCart(next);
          setDiscount({ cartId: next.id, percent: prompt.percent, amount: Math.max(0, before - after) + (discount?.cartId === next.id ? discount.amount : 0) });
          setNotice(`${prompt.percent}% discount applied${by}.`);
        } else if (prompt.kind === "override") {
          if (!c) return false;
          const next = report(await gateway.overrideLinePrice(c.id, prompt.lineId, prompt.unitPrice, g));
          if (!next) return false;
          setCart(next);
          setNotice(`Price for ${prompt.lineName} overridden${by}.`);
        } else {
          const refundId = report(await gateway.refundInvoice(prompt.invoiceId, g));
          if (!refundId) return false;
          setNotice(`Refund posted for ${prompt.documentNumber ?? "the sale"} (${refundId}).`);
        }
        setManagerPrompt(null);
        return true;
      } finally {
        setBusy(false);
      }
    },
    [managerPrompt, promptNeedsManager, gateway, report, discount, refreshTillHistory],
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
        setPromptNeedsManager(true);
        setManagerPrompt({ kind: "cashOut", movement, amount, reasonCode, label });
        return true;
      }
      if (!report(await gateway.recordCashMovement(t.id, movement, amount, reasonCode, notes, null))) return false;
      setNotice(`Cash in of ${amount.toFixed(2)} recorded.`);
      return true;
    },
    [gateway, guardOnline, report],
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

  // Payment
  const checkout = useCallback(
    async (tenders: TenderLine[], contacts: ReceiptContacts): Promise<ReceiptDocument | null> => {
      const c = cartRef.current;
      if (!c || !guardOnline()) return null;
      setBusy(true);
      try {
        const invoiceId = report(await gateway.checkout(c.id, tenders, contacts));
        if (!invoiceId) return null;
        const receipt = report(await gateway.loadReceipt(invoiceId));
        const doc = receipt ? { ...receipt, tenders: receipt.tenders.length ? receipt.tenders : tenders, operator } : null;
        setLastReceipt(doc);
        if (doc) haptic("success");
        setCart(null);
        setDiscount(null);
        return doc;
      } finally {
        setBusy(false);
      }
    },
    [gateway, guardOnline, report, operator],
  );

  const requestEcocash = useCallback(
    async (receipt: ReceiptDocument, msisdn: string) => {
      const id = report(await gateway.requestEcocash(receipt.invoiceId, msisdn, receipt.total, receipt.currency, cartRef.current?.customerId ?? null));
      if (id) setNotice(`EcoCash request sent to ${msisdn}. The customer approves it with their PIN.`);
    },
    [gateway, report],
  );

  // Orders
  const parkCurrent = useCallback(async () => {
    const c = cartRef.current;
    if (!c || c.lines.length === 0 || !guardOnline()) return;
    if (report(await gateway.parkCart(c.id))) {
      setCart(null);
      setDiscount(null);
      setNotice("Sale parked. Resume it from Orders.");
    }
  }, [gateway, guardOnline, report]);

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
    requestEcocash,
    lastReceipt,
    clearReceipt: () => setLastReceipt(null),
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
