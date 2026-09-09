import type { SupabaseClient } from "@gtr/supabase-client";

/**
 * Customer selector row published from the approved catalog_v2 release.
 * `id` is the canonical vehicle key used for EPC-backed fitment validation.
 */
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
  /** Canonical catalog_v2 customer vehicle key. */
  vehicleMasterId?: string | null;
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

const BRAND_PREFIXES = ["Nissan", "Datsun", "Infiniti"].sort(
  (a, b) => b.length - a.length,
);

/** Build cascading option lists from the published master only — never invent options. */
export const VehicleCascade = {
  deriveMaker(row: VehicleMasterRow): string | null {
    const variant = row.modelVariant.trim();
    const upper = variant.toUpperCase();
    for (const brand of BRAND_PREFIXES) {
      if (upper.startsWith(brand.toUpperCase())) return brand;
    }
    const vp = (row.vinPrefix ?? "").trim().toUpperCase();
    if (vp.startsWith("JNK") || vp.startsWith("5N3")) return "Infiniti";
    if (vp.startsWith("JN") || vp.startsWith("SJN") || vp.startsWith("MNT")) {
      return "Nissan";
    }
    return null;
  },

  makers(rows: VehicleMasterRow[]): string[] {
    return [...new Set(rows.map((r) => this.deriveMaker(r)).filter(Boolean) as string[])].sort();
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

  generations(rows: VehicleMasterRow[], maker: string, model: string): string[] {
    return [
      ...new Set(
        rows
          .filter(
            (r) =>
              this.deriveMaker(r) === maker && r.modelVariant.trim() === model,
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
      return vp.length > 0 && needle.startsWith(vp);
    });
    if (!match) return null;
    return {
      vehicleMasterId: match.id,
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
      vehicleMasterId: row.id,
      make: maker,
      model,
      generation,
      engine: engine || row.engineCode?.trim() || null,
      vinPrefix: row.vinPrefix,
    };
  },
};

type VehicleMasterRpcRow = {
  id: string;
  make: string;
  model_family: string;
  model_variant: string;
  chassis_code: string;
  engine_code: string | null;
  production_year: number | null;
  vin_prefix: string | null;
};

/**
 * Load the complete customer selector master derived from the approved catalog_v2 release.
 * This deliberately does not read the old four-row smoke `vehicle_master` table.
 */
export async function listVehicleMaster(
  client: SupabaseClient,
): Promise<
  | { ok: true; data: VehicleMasterRow[] }
  | { ok: false; error: string }
> {
  const { data, error } = await client.rpc("list_customer_vehicle_master", {
    p_maker: "nissan",
    p_limit: 10000,
    p_offset: 0,
  });

  if (error) return { ok: false, error: error.message };

  const rows = (data ?? []) as VehicleMasterRpcRow[];
  return {
    ok: true,
    data: rows.map((row) => ({
      id: row.id,
      vinPrefix: row.vin_prefix ?? null,
      chassisCode: row.chassis_code,
      engineCode: row.engine_code ?? null,
      productionYear: row.production_year ?? null,
      modelVariant: row.model_variant,
    })),
  };
}
