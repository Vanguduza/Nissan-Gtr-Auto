/**
 * Merchandising / shop category filter ↔ EPC `pnc_categories.category_name`.
 *
 * Storefront shortcuts use short slugs (`brakes`, `filters`) while PartSouq /
 * FAST rows store assembly groups (`BRAKE PIPING & CONTROL`, `AIR CLEANER`).
 * Exact equality therefore returns empty results even when catalog data exists.
 */

/** Extra stems for nav / home tile labels that differ from EPC wording. */
const CATEGORY_FILTER_ALIASES: Record<string, readonly string[]> = {
  brakes: ["brake"],
  braking: ["brake"],
  filters: ["filter", "cleaner"],
  engine: ["engine"],
  "engine parts": ["engine"],
  cooling: ["cool", "radiator", "thermostat"],
  "cooling & heating": ["cool", "radiator", "heater", "heating"],
  suspension: ["suspension", "strut"],
  "steering & suspension": ["steering", "suspension", "strut"],
  electrical: ["electric", "wiring"],
  body: ["body", "bumper"],
  "body & exhaust": ["body", "exhaust", "bumper"],
  transmission: ["transmission", "clutch", "transfer"],
  drivetrain: ["transmission", "drivetrain", "transfer", "power train"],
  "fuel system": ["fuel"],
  lighting: ["lamp", "light", "headlamp"],
  "service parts": ["filter", "oil", "spark", "service"],
};

function stemToken(raw: string): string {
  const t = raw.trim().toLowerCase();
  if (t.length < 4) return t;
  if (t.endsWith("ies") && t.length > 4) return `${t.slice(0, -3)}y`;
  if (t.endsWith("ses") && t.length > 4) return t.slice(0, -2);
  if (t.endsWith("s") && !t.endsWith("ss")) return t.slice(0, -1);
  return t;
}

/**
 * Expand a UI filter (`brakes`, `BRAKE PIPING & CONTROL`, `Braking`) into
 * lowercase needles for substring / ILIKE matching.
 */
export function categoryFilterNeedles(filter: string): string[] {
  const raw = filter.trim().toLowerCase();
  if (!raw) return [];

  const out = new Set<string>();
  out.add(raw);
  const stem = stemToken(raw);
  if (stem.length >= 3) out.add(stem);

  const aliased = CATEGORY_FILTER_ALIASES[raw];
  if (aliased) {
    for (const a of aliased) out.add(a);
  }

  // Multi-word labels: also try each significant token + its stem.
  for (const part of raw.split(/[^a-z0-9]+/g)) {
    if (part.length < 3) continue;
    out.add(part);
    const ps = stemToken(part);
    if (ps.length >= 3) out.add(ps);
    const partAlias = CATEGORY_FILTER_ALIASES[part];
    if (partAlias) {
      for (const a of partAlias) out.add(a);
    }
  }

  return [...out].sort((a, b) => b.length - a.length);
}

/** True when any category/subcategory field matches the shop filter. */
export function categoryMatchesFilter(
  filter: string,
  ...fields: (string | null | undefined)[]
): boolean {
  const needles = categoryFilterNeedles(filter);
  if (!needles.length) return false;

  const haystacks = fields
    .map((f) => f?.trim().toLowerCase())
    .filter((f): f is string => Boolean(f));
  if (!haystacks.length) return false;

  for (const hay of haystacks) {
    for (const needle of needles) {
      if (hay === needle || hay.includes(needle)) return true;
    }
  }
  return false;
}
