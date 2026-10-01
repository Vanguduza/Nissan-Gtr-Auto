import type { PosGateway } from "@/lib/pos/gateway";
import { roundMoney } from "@/lib/pos/money";
import type { PopularPin, PosCart, PosPart, SelectedVehicle, VehicleModel, VehicleVariant } from "@/lib/pos/types";

/**
 * DEVELOPMENT-ONLY preview data so the benchmark layout can be built and screenshot without a
 * Supabase backend. Enabled only by `NEXT_PUBLIC_POS_PREVIEW=1` outside production; `next.config.ts`
 * refuses a production build with the flag set, and the UI labels the screen "Preview data".
 * Equivalent of the tablet's `FakeRpcClient`. Never a source of business truth.
 */
const MODELS: VehicleModel[] = [
  { slug: "gt-r", name: "GT-R", yearStart: 2007, yearEnd: null },
  { slug: "navara", name: "Navara", yearStart: 2005, yearEnd: null },
  { slug: "x-trail", name: "X-Trail", yearStart: 2001, yearEnd: null },
];
const VARIANTS: Record<string, VehicleVariant[]> = {
  "gt-r": [
    { slug: "r35-vr38", chassisCode: "R35", engineCode: "VR38DETT", yearLabel: "2007–" },
  ],
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

export function createPreviewPosGateway(): PosGateway {
  let pins: PopularPin[] = [];
  const hidden = new Set<string>();
  let cart: PosCart = { id: "preview-cart", currency: "USD", lines: [], customerName: null, vehicle: null };
  const ok = <T,>(data: T) => Promise.resolve({ ok: true as const, data });

  return {
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
    openCart: (currency) => {
      cart = { ...cart, currency };
      return ok(cart);
    },
    loadCart: () => ok(cart),
    addPart: (_cartId, part, qty) => {
      const price = part.price?.amount ?? 0;
      const existing = cart.lines.find((l) => l.oemPartNumber === part.oemPartNumber);
      const lines = existing
        ? cart.lines.map((l) => (l === existing ? { ...l, qty: l.qty + qty, lineTotal: roundMoney(l.unitPrice * (l.qty + qty)) } : l))
        : [
            ...cart.lines,
            {
              id: `line-${part.oemPartNumber}`,
              stockItemId: part.stockItemId ?? part.oemPartNumber,
              oemPartNumber: part.oemPartNumber,
              name: part.name,
              qty,
              unitPrice: price,
              lineTotal: roundMoney(price * qty),
              imageUrl: part.imageUrl,
            },
          ];
      cart = { ...cart, lines };
      return ok(cart);
    },
    setLineQty: (_cartId, lineId, qty) => {
      cart = {
        ...cart,
        lines:
          qty > 0
            ? cart.lines.map((l) => (l.id === lineId ? { ...l, qty, lineTotal: roundMoney(l.unitPrice * qty) } : l))
            : cart.lines.filter((l) => l.id !== lineId),
      };
      return ok(cart);
    },
    removeLine: (_cartId, lineId) => {
      cart = { ...cart, lines: cart.lines.filter((l) => l.id !== lineId) };
      return ok(cart);
    },
    setCartVehicle: (_cartId, vehicle: SelectedVehicle | null) => {
      cart = { ...cart, vehicle };
      return ok(cart);
    },
    voidCart: () => {
      cart = { ...cart, lines: [], customerName: null };
      return ok(true as const);
    },
    operatorLabel: () => Promise.resolve("Preview operator"),
    reauthenticate: (password) =>
      Promise.resolve(password ? { ok: true as const, data: true as const } : { ok: false as const, error: "Password required." }),
  };
}

/** Preview is never available in production builds, whatever the env says. */
export function isPosPreviewEnabled(): boolean {
  return process.env.NODE_ENV !== "production" && process.env.NEXT_PUBLIC_POS_PREVIEW === "1";
}
