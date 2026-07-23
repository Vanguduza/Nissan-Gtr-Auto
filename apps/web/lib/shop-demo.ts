export type StockState = "in_stock" | "low" | "backorder" | "counter_only";

export const DEMO_VEHICLE = {
  id: "demo-navara",
  label: "Nissan Navara D40 · YD25",
  vin: null as string | null,
};

export const DEMO_PRODUCTS = [
  {
    oem: "15208-65F0C",
    name: "Oil filter",
    brand: "Nissan OE",
    category: "filters",
    usd: 12.4,
    zig: null as number | null,
    stock: "in_stock" as StockState,
    coreCharge: 0,
    replaces: ["15208-65F0A", "AY100-NS004"],
    specs: ["Thread M20×1.5", "Height 86 mm"],
  },
  {
    oem: "40206-EA00A",
    name: "Front brake disc",
    brand: "Nissan OE",
    category: "brakes",
    usd: 84,
    zig: null,
    stock: "low" as StockState,
    coreCharge: 0,
    replaces: ["40206-EB300"],
    specs: ["Ventilated", "280 mm"],
  },
  {
    oem: "21410-JF00A",
    name: "Water pump assembly",
    brand: "Nissan OE",
    category: "cooling",
    usd: 142,
    zig: null,
    stock: "counter_only" as StockState,
    coreCharge: 25,
    replaces: ["21010-JF00A"],
    specs: ["With gasket"],
  },
  {
    oem: "16546-00Q0A",
    name: "Air filter element",
    brand: "Nissan OE",
    category: "filters",
    usd: 18.75,
    zig: null,
    stock: "in_stock" as StockState,
    coreCharge: 0,
    replaces: [],
    specs: ["Panel type"],
  },
];

export function findProduct(oem: string) {
  return DEMO_PRODUCTS.find(
    (p) => p.oem.toLowerCase() === decodeURIComponent(oem).toLowerCase(),
  );
}
