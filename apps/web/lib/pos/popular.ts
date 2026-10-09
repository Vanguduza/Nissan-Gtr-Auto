import type { PopularPin, PopularRowItem, PosPart } from "@/lib/pos/types";

/**
 * Popular Items row (owner decision D1): operator pins first, then server best sellers.
 * Best sellers already pinned as parts are de-duplicated, and best sellers the operator removed
 * (server-persisted hide list) are left out. Mirrors `buildPopularRowItems` in the tablet
 * `PosPopularItems.kt` so both runtimes show the same row.
 */
export function buildPopularRow(
  pins: PopularPin[],
  bestSellers: PosPart[],
  hiddenStockItemIds: ReadonlySet<string>,
): PopularRowItem[] {
  const pinnedOems = new Set(
    pins
      .filter((p) => p.kind === "part" && p.oemPartNumber)
      .map((p) => (p.oemPartNumber ?? "").trim().toUpperCase()),
  );
  const pinItems: PopularRowItem[] = pins.map((pin) => ({
    source: "pin",
    key: `pin:${pin.kind}:${pin.key}`,
    pin,
  }));
  const sellerItems: PopularRowItem[] = bestSellers
    .filter((part) => !pinnedOems.has(part.oemPartNumber.trim().toUpperCase()))
    .filter((part) => !(part.stockItemId && hiddenStockItemIds.has(part.stockItemId)))
    .map((part) => ({
      source: "bestseller",
      key: `seller:${part.stockItemId ?? part.oemPartNumber}`,
      part,
    }));
  return [...pinItems, ...sellerItems];
}

export function pinForPart(part: PosPart): PopularPin {
  return {
    kind: "part",
    key: part.oemPartNumber.trim().toUpperCase(),
    label: part.name,
    subtitle: part.oemPartNumber,
    searchQuery: part.oemPartNumber,
    oemPartNumber: part.oemPartNumber,
    imageUrl: part.imageUrl,
  };
}
