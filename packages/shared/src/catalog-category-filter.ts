/**
 * Merchandising / shop category filter ↔ EPC `pnc_categories.category_name`.
 *
 * Storefront shortcuts use short slugs (`brakes`, `filters`) while PartSouq /
 * FAST / Megazip rows store assembly groups (`BRAKE PIPING & CONTROL`,
 * `Accelerator linkage for 1997–2000 Nissan FRONTIER…`). Exact equality
 * therefore returns empty results even when catalog data exists.
 *
 * FILTERS → CATEGORY on the shop PLP must list **merchandising** parents or
 * their curated subcategories — never raw EPC assembly dump strings.
 */

export type MerchSubcategory = {
  slug: string;
  label: string;
  /** Extra stems beyond label tokens for EPC / PNC matching. */
  stems?: readonly string[];
};

export type MerchParent = {
  slug: string;
  label: string;
  subcategories: readonly MerchSubcategory[];
};

/**
 * Curated shop taxonomy — aligned with web header + Android hamburger IA.
 * Parent slugs match `/shop?cat=` nav links.
 */
export const MERCHANDISING_TAXONOMY: readonly MerchParent[] = [
  {
    slug: "brakes",
    label: "Brakes",
    subcategories: [
      { slug: "brake-pads", label: "Brake pads", stems: ["pad", "lining"] },
      { slug: "brake-discs", label: "Brake discs", stems: ["disc", "rotor"] },
      { slug: "calipers", label: "Calipers", stems: ["caliper"] },
      {
        slug: "brake-hoses",
        label: "Brake hoses & lines",
        stems: ["hose", "piping", "brake line", "brake tube"],
      },
      { slug: "brake-fluid", label: "Brake fluid", stems: ["brake fluid"] },
    ],
  },
  {
    slug: "filters",
    label: "Filters",
    subcategories: [
      { slug: "oil-filters", label: "Oil filters", stems: ["oil filter"] },
      {
        slug: "air-filters",
        label: "Air filters",
        stems: ["air filter", "air cleaner", "cleaner"],
      },
      {
        slug: "cabin-filters",
        label: "Cabin filters",
        stems: ["cabin", "pollen", "cabin filter"],
      },
      { slug: "fuel-filters", label: "Fuel filters", stems: ["fuel filter"] },
    ],
  },
  {
    slug: "engine",
    label: "Engine",
    subcategories: [
      { slug: "gaskets", label: "Gaskets", stems: ["gasket", "seal"] },
      {
        slug: "timing",
        label: "Timing belts & chains",
        stems: ["timing", "cam belt", "timing chain"],
      },
      { slug: "pulleys", label: "Pulleys", stems: ["pulley", "idler"] },
      {
        slug: "engine-sensors",
        label: "Engine sensors",
        stems: ["sensor", "o2", "oxygen", "knock"],
      },
      {
        slug: "spark-plugs",
        label: "Spark plugs",
        stems: ["spark", "glow plug"],
      },
    ],
  },
  {
    slug: "suspension",
    label: "Suspension",
    subcategories: [
      {
        slug: "shock-absorbers",
        label: "Shock absorbers",
        stems: ["shock", "strut", "damper", "absorber"],
      },
      {
        slug: "coil-springs",
        label: "Coil springs",
        stems: ["coil spring", "spring"],
      },
      {
        slug: "control-arms",
        label: "Control arms",
        stems: ["control arm", "wishbone", "lateral link", "trailing arm"],
      },
      {
        slug: "ball-joints",
        label: "Ball joints",
        stems: ["ball joint", "ball-joint"],
      },
      {
        slug: "tie-rod-ends",
        label: "Tie rod ends",
        stems: ["tie rod", "tie-rod", "outer socket", "inner socket"],
      },
      {
        slug: "bushings",
        label: "Bushings",
        stems: ["bushing", "bush ", " arm bush"],
      },
    ],
  },
  {
    slug: "electrical",
    label: "Electrical",
    subcategories: [
      { slug: "batteries", label: "Batteries", stems: ["battery"] },
      { slug: "alternators", label: "Alternators", stems: ["alternator"] },
      { slug: "starters", label: "Starters", stems: ["starter"] },
      {
        slug: "ignition",
        label: "Ignition",
        stems: ["ignition", "coil", "distributor"],
      },
      { slug: "wiring", label: "Wiring", stems: ["wiring", "harness"] },
    ],
  },
  {
    slug: "cooling",
    label: "Cooling",
    subcategories: [
      { slug: "radiators", label: "Radiators", stems: ["radiator"] },
      { slug: "water-pumps", label: "Water pumps", stems: ["water pump"] },
      { slug: "thermostats", label: "Thermostats", stems: ["thermostat"] },
      {
        slug: "cooling-hoses",
        label: "Cooling hoses",
        stems: ["radiator hose", "coolant hose", "heater hose"],
      },
      {
        slug: "heater",
        label: "Heater & A/C",
        stems: ["heater", "evaporator", "condenser", "a/c", "ac "],
      },
    ],
  },
  {
    slug: "body",
    label: "Body",
    subcategories: [
      {
        slug: "body-panels",
        label: "Body panels",
        stems: ["fender", "bonnet", "hood", "door panel", "quarter"],
      },
      { slug: "bumpers", label: "Bumpers", stems: ["bumper"] },
      { slug: "mirrors", label: "Mirrors", stems: ["mirror"] },
      {
        slug: "exhaust",
        label: "Exhaust",
        stems: ["exhaust", "muffler", "silencer", "catalytic"],
      },
    ],
  },
  {
    slug: "transmission",
    label: "Drivetrain",
    subcategories: [
      { slug: "clutch", label: "Clutch kits", stems: ["clutch"] },
      { slug: "flywheels", label: "Flywheels", stems: ["flywheel"] },
      {
        slug: "gearbox-mounts",
        label: "Gearbox mounts",
        stems: ["mount", "transmission mount"],
      },
      {
        slug: "driveshaft",
        label: "Driveshaft & CV",
        stems: ["driveshaft", "drive shaft", "cv joint", "axle"],
      },
      {
        slug: "transfer",
        label: "Transfer & differential",
        stems: ["transfer", "differential", "diff "],
      },
    ],
  },
];

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

export type ShopFacetOption = {
  slug: string;
  label: string;
};

export type MerchResolveResult = {
  parent: MerchParent;
  /** Set when the filter resolved to a leaf subcategory. */
  sub?: MerchSubcategory;
};

function normKey(raw: string): string {
  return raw.trim().toLowerCase().replace(/[_]+/g, " ").replace(/\s+/g, " ");
}

function slugKey(raw: string): string {
  return raw
    .trim()
    .toLowerCase()
    .replace(/&/g, " and ")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

/**
 * Strip Megazip / PartSouq vehicle suffixes so assembly stems match.
 * Example: `Accelerator linkage for 1997–2000 Nissan FRONTIER…` → `Accelerator linkage`.
 */
export function stripEpcVehicleSuffix(name: string): string {
  let s = name.trim();
  if (!s) return s;
  const forMatch = s.match(/^(.+?)\s+for\s+\d/i);
  if (forMatch) return forMatch[1].trim();
  const forVehicle = s.match(
    /^(.+?)\s+for\s+(?:the\s+)?(?:\d{4}|nissan|toyota|honda|suzuki|subaru|mitsubishi|lexus)\b/i,
  );
  if (forVehicle) return forVehicle[1].trim();
  // Uppercase Megazip style: `WIRING FOR 2004 - 2011 NISSAN ALTIMA`
  const upperFor = s.match(/^(.+?)\s+FOR\s+/);
  if (upperFor && /[A-Z]/.test(upperFor[1])) return upperFor[1].trim();
  return s;
}

function stemToken(raw: string): string {
  const t = raw.trim().toLowerCase();
  if (t.length < 4) return t;
  if (t.endsWith("ies") && t.length > 4) return `${t.slice(0, -3)}y`;
  if (t.endsWith("ses") && t.length > 4) return t.slice(0, -2);
  if (t.endsWith("s") && !t.endsWith("ss")) return t.slice(0, -1);
  return t;
}

function collectNeedlesFromParts(
  raw: string,
  extra: readonly string[] | undefined,
  out: Set<string>,
): void {
  out.add(raw);
  const stem = stemToken(raw);
  if (stem.length >= 3) out.add(stem);

  const aliased = CATEGORY_FILTER_ALIASES[raw];
  if (aliased) {
    for (const a of aliased) out.add(a);
  }

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

  if (extra) {
    for (const e of extra) {
      const n = e.trim().toLowerCase();
      if (!n) continue;
      out.add(n);
      const es = stemToken(n);
      if (es.length >= 3) out.add(es);
    }
  }
}

/** Resolve a shop filter slug/label to a merchandising parent (and optional leaf). */
export function resolveMerchandisingNode(
  filter: string,
): MerchResolveResult | null {
  const raw = filter.trim();
  if (!raw) return null;
  const key = normKey(raw);
  const slug = slugKey(raw);

  for (const parent of MERCHANDISING_TAXONOMY) {
    if (
      parent.slug === slug ||
      normKey(parent.label) === key ||
      slugKey(parent.label) === slug
    ) {
      return { parent };
    }
    // Alias keys that map onto this parent slug (braking → brakes, etc.)
    const aliasNeedles = CATEGORY_FILTER_ALIASES[key];
    if (
      aliasNeedles &&
      (parent.slug === key ||
        aliasNeedles.some(
          (a) => a === parent.slug || parent.slug.startsWith(a),
        ))
    ) {
      // Only treat as parent when the alias row is a known parent synonym.
      if (
        key === "braking" ||
        key === "drivetrain" ||
        key === "engine parts" ||
        key === "cooling & heating" ||
        key === "steering & suspension" ||
        key === "body & exhaust"
      ) {
        // Map known synonyms onto taxonomy parents
        const mapped =
          key === "braking"
            ? "brakes"
            : key === "drivetrain"
              ? "transmission"
              : key === "engine parts"
                ? "engine"
                : key === "cooling & heating"
                  ? "cooling"
                  : key === "steering & suspension"
                    ? "suspension"
                    : key === "body & exhaust"
                      ? "body"
                      : null;
        if (mapped && parent.slug === mapped) return { parent };
      }
    }

    for (const sub of parent.subcategories) {
      if (
        sub.slug === slug ||
        normKey(sub.label) === key ||
        slugKey(sub.label) === slug
      ) {
        return { parent, sub };
      }
    }
  }

  // Synonym map for parent-only aliases not caught above
  const synonymParent: Record<string, string> = {
    braking: "brakes",
    drivetrain: "transmission",
    "engine parts": "engine",
    "cooling & heating": "cooling",
    "steering & suspension": "suspension",
    "body & exhaust": "body",
  };
  const mappedSlug = synonymParent[key] ?? synonymParent[slug];
  if (mappedSlug) {
    const parent = MERCHANDISING_TAXONOMY.find((p) => p.slug === mappedSlug);
    if (parent) return { parent };
  }

  return null;
}

/**
 * Labels (with slugs) for the FILTERS → CATEGORY pane.
 * - No active filter → top merchandising parents
 * - Parent or leaf selected → that parent's curated subcategories only
 * - Unknown filter → empty (honest; never dump raw EPC names)
 */
export function shopCategoryFacetOptions(
  activeFilter: string | null | undefined,
  activeSubfilter?: string | null,
): ShopFacetOption[] {
  const combined = activeSubfilter?.trim() || activeFilter?.trim() || "";
  if (!combined && !activeFilter?.trim()) {
    return MERCHANDISING_TAXONOMY.map((p) => ({
      slug: p.slug,
      label: p.label,
    }));
  }

  // Prefer resolving parent from `cat` when `sub` is also set
  const parentFilter = activeFilter?.trim() || combined;
  const resolved =
    resolveMerchandisingNode(parentFilter) ??
    (activeSubfilter?.trim()
      ? resolveMerchandisingNode(activeSubfilter)
      : null);

  if (!resolved) return [];

  return resolved.parent.subcategories.map((s) => ({
    slug: s.slug,
    label: s.label,
  }));
}

/** Expand a UI filter into lowercase needles for substring / ILIKE matching. */
export function categoryFilterNeedles(filter: string): string[] {
  const raw = filter.trim().toLowerCase();
  if (!raw) return [];

  const out = new Set<string>();
  const resolved = resolveMerchandisingNode(filter);

  if (resolved?.sub) {
    collectNeedlesFromParts(
      normKey(resolved.sub.label),
      resolved.sub.stems,
      out,
    );
    collectNeedlesFromParts(resolved.sub.slug.replace(/-/g, " "), undefined, out);
  } else if (resolved) {
    collectNeedlesFromParts(normKey(resolved.parent.label), undefined, out);
    collectNeedlesFromParts(resolved.parent.slug, undefined, out);
    const aliased = CATEGORY_FILTER_ALIASES[resolved.parent.slug];
    if (aliased) for (const a of aliased) out.add(a);
    const rawAlias = CATEGORY_FILTER_ALIASES[raw];
    if (rawAlias) for (const a of rawAlias) out.add(a);
  } else {
    collectNeedlesFromParts(raw, undefined, out);
  }

  return [...out].sort((a, b) => b.length - a.length);
}

/**
 * Effective shop filter when parent + leaf are both present.
 * Leaf wins for product matching; parent still drives facet siblings.
 */
export function effectiveCategoryFilter(
  category?: string | null,
  subcategory?: string | null,
): string | null {
  const sub = subcategory?.trim();
  if (sub) return sub;
  const cat = category?.trim();
  return cat || null;
}

/** True when any category/subcategory field matches the shop filter. */
export function categoryMatchesFilter(
  filter: string,
  ...fields: (string | null | undefined)[]
): boolean {
  const needles = categoryFilterNeedles(filter);
  if (!needles.length) return false;

  const haystacks = fields
    .map((f) => {
      if (!f?.trim()) return null;
      return stripEpcVehicleSuffix(f).trim().toLowerCase();
    })
    .filter((f): f is string => Boolean(f));
  if (!haystacks.length) return false;

  for (const hay of haystacks) {
    for (const needle of needles) {
      if (hay === needle || hay.includes(needle)) return true;
    }
  }
  return false;
}
