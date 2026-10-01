"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { PosGateway } from "@/lib/pos/gateway";
import { buildPopularRow, pinForPart } from "@/lib/pos/popular";
import type {
  PopularPin,
  PopularRowItem,
  PosCart,
  PosCurrency,
  PosPart,
  SelectedVehicle,
  VehicleGeneration,
  VehicleModel,
  VehicleVariant,
} from "@/lib/pos/types";

const RECENT_KEY = "gtr.pos.recentSearches";
const RECENT_MAX = 8;

export type PosDestination =
  | "home"
  | "search"
  | "quickSale"
  | "customer"
  | "orders"
  | "returns"
  | "epc"
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
  const currency: PosCurrency = cart?.currency ?? "USD";
  const cartRef = useRef<PosCart | null>(null);
  cartRef.current = cart;

  const report = useCallback(<T,>(res: { ok: true; data: T } | { ok: false; error: string }): T | null => {
    if (res.ok) return res.data;
    setError(res.error);
    return null;
  }, []);

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
    if (!model || !chassisCode || !engineCode) return null;
    const gen = generations.find((g) => g.chassisCode === chassisCode);
    return {
      modelSlug: model.slug,
      modelName: model.name,
      generation: gen?.label ?? chassisCode,
      chassisCode,
      engineCode,
    };
  }, [models, modelSlug, chassisCode, engineCode, generations]);

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

  const isPinned = useCallback(
    (part: PosPart) => pins.some((p) => p.kind === "part" && p.key === pinForPart(part).key),
    [pins],
  );

  const pinPart = useCallback(
    async (part: PosPart) => {
      const pin = pinForPart(part);
      if (report(await gateway.pin(pin))) setPins((cur) => [pin, ...cur.filter((p) => !(p.kind === pin.kind && p.key === pin.key))]);
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
          setPins((cur) => cur.filter((p) => !(p.kind === item.pin.kind && p.key === item.pin.key)));
        }
        return;
      }
      const id = item.part.stockItemId;
      if (!id) return;
      if (report(await gateway.hideBestSeller(id))) setHidden((cur) => new Set(cur).add(id));
    },
    [gateway, report],
  );

  // Current Sale
  const ensureCart = useCallback(async (): Promise<PosCart | null> => {
    if (cartRef.current) return cartRef.current;
    const opened = report(await gateway.openCart("USD"));
    if (!opened) return null;
    let next = opened;
    if (vehicle) next = report(await gateway.setCartVehicle(opened.id, vehicle)) ?? opened;
    setCart(next);
    return next;
  }, [gateway, report, vehicle]);

  const addPart = useCallback(
    async (part: PosPart) => {
      if (!online) {
        setError("Offline — the web POS cannot change a sale until the connection returns.");
        return;
      }
      if (!part.price) {
        setError(`${part.oemPartNumber} has no price — it cannot be sold until it is priced.`);
        return;
      }
      setBusy(true);
      const c = await ensureCart();
      if (c) {
        const next = report(await gateway.addPart(c.id, part, 1));
        if (next) setCart(next);
      }
      setBusy(false);
    },
    [online, ensureCart, gateway, report],
  );

  const setLineQty = useCallback(
    async (lineId: string, qty: number) => {
      const c = cartRef.current;
      if (!c || !online) return;
      const next = report(await gateway.setLineQty(c.id, lineId, qty));
      if (next) setCart(next);
    },
    [gateway, online, report],
  );

  const removeLine = useCallback(
    async (lineId: string) => {
      const c = cartRef.current;
      if (!c || !online) return;
      const next = report(await gateway.removeLine(c.id, lineId));
      if (next) setCart(next);
    },
    [gateway, online, report],
  );

  const clearSale = useCallback(async () => {
    const c = cartRef.current;
    if (!c || !online) return;
    if (report(await gateway.voidCart(c.id))) setCart(null);
  }, [gateway, online, report]);

  const subtotal = useMemo(() => (cart?.lines ?? []).reduce((sum, l) => sum + l.lineTotal, 0), [cart]);

  return {
    isPreview: gateway.isPreview,
    destination,
    setDestination,
    operator,
    online,
    error,
    dismissError: () => setError(null),
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
    removePopular,
    // sale
    cart,
    currency,
    subtotal,
    addPart,
    setLineQty,
    removeLine,
    clearSale,
  };
}

export type PosStore = ReturnType<typeof usePos>;
