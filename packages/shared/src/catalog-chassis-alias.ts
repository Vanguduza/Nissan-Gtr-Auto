/**
 * When flat `vehicle_master` / fixture chassis codes lack a `catalog_variants`
 * row (Megazip gaps), map to the closest published EPC chassis for browse.
 *
 * Keep in sync with `data-pipeline/config/megazip_chassis_map.json` proxies and
 * Android `CatalogChassisAlias` / iOS `CatalogChassisAlias`.
 */

/** Direct chassis → published Megazip/EPC chassis. */
export const EPC_CHASSIS_ALIASES: Readonly<Record<string, string>> = {
  D40: "D22", // Navara — Megazip Frontier/Navara pack is D22-coded
  T32: "T31", // X-Trail — Megazip lists T30/T31 only
};

/**
 * Ordered unique chassis codes to try for EPC variant lookup / fitment browse.
 * Always starts with the requested code, then known aliases.
 */
export function epcChassisLookupCodes(chassis: string): string[] {
  const primary = chassis.trim().toUpperCase();
  if (!primary) return [];
  const out: string[] = [primary];
  const alias = EPC_CHASSIS_ALIASES[primary];
  if (alias) {
    const a = alias.trim().toUpperCase();
    if (a && a !== primary) out.push(a);
  }
  return out;
}
