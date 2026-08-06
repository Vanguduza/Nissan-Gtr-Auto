import type { SupabaseClient } from "@gtr/supabase-client";

/** Live `vehicle_master` row — cascade source for maker → model → generation → engine. */
export type VehicleMasterRow = {
  id: string | null;
  vinPrefix: string | null;
  chassisCode: string;
  engineCode: string | null;
  productionYear: number | null;
  modelVariant: string;
};

/** Session fitment after Select vehicle confirm (cascade or VIN). */
export type SelectedFitmentVehicle = {
  make: string | null;
  model: string;
  generation: string;
  engine: string | null;
  vin?: string | null;
  vinPrefix?: string | null;
};

export function searchQueryForVehicle(v: SelectedFitmentVehicle): string {
  return [v.model, v.generation, v.engine].filter(Boolean).join(" ").trim();
}

/**
 * Brand labels matched at the start of `model_variant` (longest first).
 * Mirrors display names in data-pipeline/config/chassis_catalogs.json.
 */
const VARIANT_BRAND_PREFIXES = [
  "Mercedes-Benz",
  "Land Rover",
  "Alfa Romeo",
  "Volkswagen",
  "Infiniti",
  "Datsun",
  "Nissan",
  "Toyota",
  "Lexus",
  "Honda",
  "Acura",
  "Mazda",
  "Mitsubishi",
  "Subaru",
  "Suzuki",
  "Daihatsu",
  "Isuzu",
  "Hino",
  "Hyundai",
  "Kia",
  "Genesis",
  "BMW",
  "Mini",
  "Smart",
  "Audi",
  "Skoda",
  "Seat",
  "Porsche",
  "Ford",
  "Lincoln",
  "Chevrolet",
  "Cadillac",
  "Buick",
  "GMC",
  "Jeep",
  "Dodge",
  "Chrysler",
  "Ram",
  "Volvo",
  "Jaguar",
  "Peugeot",
  "Citroen",
  "Renault",
  "Opel",
  "Fiat",
].sort((a, b) => b.length - a.length);

/**
 * VIN WMI → maker (longest prefix first).
 * Nissan WMIs include SJN / MNT / VSK / MDH / ADN (not only JN*) — required for
 * SA / Thai / EU plants present in live vehicle_master.
 */
const VIN_WMI_MAKERS: [string, string][] = (
  [
    // Infiniti-specific before shared JN*
    ["JNK", "Infiniti"],
    ["5N3", "Infiniti"],
    // Nissan (incl. regional plants)
    ["SJN", "Nissan"],
    ["MNT", "Nissan"],
    ["MDH", "Nissan"],
    ["VSK", "Nissan"],
    ["ADN", "Nissan"],
    ["3N1", "Nissan"],
    ["5N1", "Nissan"],
    ["1N4", "Nissan"],
    ["1N6", "Nissan"],
    ["JN1", "Nissan"],
    ["JN", "Nissan"],
    // Other makers from chassis_catalogs.json
    ["JTD", "Toyota"],
    ["JT2", "Toyota"],
    ["JTE", "Toyota"],
    ["JTM", "Toyota"],
    ["4T1", "Toyota"],
    ["5TD", "Toyota"],
    ["2T1", "Toyota"],
    ["MR0", "Toyota"],
    ["JTJ", "Lexus"],
    ["JTH", "Lexus"],
    ["2T2", "Lexus"],
    ["58A", "Lexus"],
    ["JHM", "Honda"],
    ["1HG", "Honda"],
    ["2HG", "Honda"],
    ["3CZ", "Honda"],
    ["SHH", "Honda"],
    ["JH4", "Acura"],
    ["19U", "Acura"],
    ["2HN", "Acura"],
    ["JM1", "Mazda"],
    ["JM3", "Mazda"],
    ["1YV", "Mazda"],
    ["3MZ", "Mazda"],
    ["JA3", "Mitsubishi"],
    ["JA4", "Mitsubishi"],
    ["4A3", "Mitsubishi"],
    ["6MM", "Mitsubishi"],
    ["JF1", "Subaru"],
    ["JF2", "Subaru"],
    ["4S3", "Subaru"],
    ["4S4", "Subaru"],
    ["JS2", "Suzuki"],
    ["JS3", "Suzuki"],
    ["JSA", "Suzuki"],
    ["TSM", "Suzuki"],
    ["KMH", "Hyundai"],
    ["KM8", "Hyundai"],
    ["5NP", "Hyundai"],
    ["5NM", "Hyundai"],
    ["KNA", "Kia"],
    ["KND", "Kia"],
    ["5XY", "Kia"],
    ["3KP", "Kia"],
    ["WBA", "BMW"],
    ["WBS", "BMW"],
    ["WBY", "BMW"],
    ["4US", "BMW"],
    ["5UX", "BMW"],
    ["WDD", "Mercedes-Benz"],
    ["WDB", "Mercedes-Benz"],
    ["4JG", "Mercedes-Benz"],
    ["WAU", "Audi"],
    ["WA1", "Audi"],
    ["WVW", "Volkswagen"],
    ["WV1", "Volkswagen"],
    ["WV2", "Volkswagen"],
    ["3VW", "Volkswagen"],
    ["1VW", "Volkswagen"],
    ["1FA", "Ford"],
    ["1FT", "Ford"],
    ["1FM", "Ford"],
    ["WF0", "Ford"],
    ["SAL", "Land Rover"],
    ["SAJ", "Jaguar"],
  ] as [string, string][]
).sort((a, b) => b[0].length - a[0].length);

/** Bare model names common in vehicle_master without a brand prefix → Nissan. */
const NISSAN_MODEL_TOKEN =
  /^(MICRA|QASHQAI\+?\d*|JUKE|NAVARA|X-?TRAIL|PULSAR|PATROL|ALTIMA|SENTRA|MAXIMA|LEAF|370Z|350Z|GT-?R|SKYLINE|ALMERA|TIIDA|TEANA|PATHFINDER|MURANO|NP300|HARDBODY|CARAVAN|SYLPHY|PRIMERA|NOTE|CUBE)\b/i;

/** Build cascading option lists from live rows only — never invent options. */
export const VehicleCascade = {
  deriveMaker(row: VehicleMasterRow): string | null {
    const variant = row.modelVariant.trim();
    const upper = variant.toUpperCase();
    for (const brand of VARIANT_BRAND_PREFIXES) {
      if (upper.startsWith(brand.toUpperCase())) return brand;
    }
    const vp = (row.vinPrefix ?? "").trim().toUpperCase();
    if (vp) {
      for (const [prefix, maker] of VIN_WMI_MAKERS) {
        if (vp.startsWith(prefix)) return maker;
      }
    }
    // Bare Nissan model tokens (e.g. "NAVARA", "X-TRAIL") when WMI absent
    if (NISSAN_MODEL_TOKEN.test(variant)) return "Nissan";
    return null;
  },

  makers(rows: VehicleMasterRow[]): string[] {
    return [
      ...new Set(
        rows
          .map((r) => this.deriveMaker(r))
          .filter((m): m is string => Boolean(m)),
      ),
    ].sort();
  },

  models(rows: VehicleMasterRow[], maker: string): string[] {
    return [
      ...new Set(
        rows
          .filter((r) => this.deriveMaker(r) === maker)
          .map((r) => r.modelVariant.trim())
          .filter(Boolean),
      ),
    ].sort();
  },

  generations(
    rows: VehicleMasterRow[],
    maker: string,
    model: string,
  ): string[] {
    return [
      ...new Set(
        rows
          .filter(
            (r) =>
              this.deriveMaker(r) === maker &&
              r.modelVariant.trim() === model,
          )
          .map((r) => r.chassisCode.trim())
          .filter(Boolean),
      ),
    ].sort();
  },

  engines(
    rows: VehicleMasterRow[],
    maker: string,
    model: string,
    generation: string,
  ): string[] {
    return [
      ...new Set(
        rows
          .filter(
            (r) =>
              this.deriveMaker(r) === maker &&
              r.modelVariant.trim() === model &&
              r.chassisCode.trim() === generation,
          )
          .map((r) => r.engineCode?.trim())
          .filter((e): e is string => Boolean(e)),
      ),
    ].sort();
  },

  resolveVin(
    rows: VehicleMasterRow[],
    vinRaw: string,
  ): SelectedFitmentVehicle | null {
    const vin = vinRaw.trim().toUpperCase();
    if (vin.length < 11) return null;
    const needle = vin.slice(0, 11);
    const match = rows.find((row) => {
      const vp = (row.vinPrefix ?? "").trim().toUpperCase();
      if (!vp) return false;
      return (
        needle.startsWith(vp) ||
        vp.startsWith(needle.slice(0, Math.min(vp.length, needle.length)))
      );
    });
    if (!match) return null;
    return {
      make: this.deriveMaker(match),
      model: match.modelVariant.trim(),
      generation: match.chassisCode.trim(),
      engine: match.engineCode?.trim() || null,
      vin,
      vinPrefix: match.vinPrefix,
    };
  },

  fromCascade(
    maker: string,
    model: string,
    generation: string,
    engine: string | null,
    rows: VehicleMasterRow[],
  ): SelectedFitmentVehicle | null {
    const row = rows.find(
      (r) =>
        this.deriveMaker(r) === maker &&
        r.modelVariant.trim() === model &&
        r.chassisCode.trim() === generation &&
        (!engine || r.engineCode?.trim() === engine),
    );
    if (!row) return null;
    return {
      make: maker,
      model,
      generation,
      engine: engine || row.engineCode?.trim() || null,
      vinPrefix: row.vinPrefix,
    };
  },
};

/**
 * Load live vehicle_master rows (authenticated — RLS). Cap matches mobile clients.
 */
export async function listVehicleMaster(
  client: SupabaseClient,
): Promise<
  | { ok: true; data: VehicleMasterRow[] }
  | { ok: false; error: string }
> {
  const { data, error } = await client
    .from("vehicle_master")
    .select(
      "id, vin_prefix, chassis_code, engine_code, production_year, model_variant",
    )
    .order("model_variant", { ascending: true })
    .limit(500);

  if (error) return { ok: false, error: error.message };

  return {
    ok: true,
    data: (data ?? []).map((row) => ({
      id: row.id ?? null,
      vinPrefix: row.vin_prefix ?? null,
      chassisCode: row.chassis_code,
      engineCode: row.engine_code ?? null,
      productionYear: row.production_year ?? null,
      modelVariant: row.model_variant,
    })),
  };
}
