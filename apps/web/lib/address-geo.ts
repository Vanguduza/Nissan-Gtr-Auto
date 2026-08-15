/**
 * Encode/decode map coordinates in `customer_addresses.line2` until a geo migration lands.
 * Mirrors Android `AddressGeo` (`#gtr_geo:lat,lng`).
 */

const GEO = /#gtr_geo:(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)/;

export function parseAddressGeo(
  line2: string | null | undefined,
): { lat: number; lng: number } | null {
  if (!line2) return null;
  const m = line2.match(GEO);
  if (!m) return null;
  const lat = Number(m[1]);
  const lng = Number(m[2]);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
  return { lat, lng };
}

export function stripAddressGeo(line2: string | null | undefined): string {
  if (!line2) return "";
  return line2
    .replace(GEO, "")
    .replace(/\n{2,}/g, "\n")
    .trim();
}

export function embedAddressGeo(
  line2: string | null | undefined,
  lat: number | null | undefined,
  lng: number | null | undefined,
): string | null {
  const base = stripAddressGeo(line2);
  if (lat == null || lng == null) return base || null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    throw new Error("latitude/longitude out of range");
  }
  const tag = `#gtr_geo:${lat},${lng}`;
  return base ? `${base}\n${tag}` : tag;
}
