/** Fractions (0–1) of the drawn diagram image. */
export type EpcBox = { left: number; top: number; width: number; height: number };

/**
 * Resolve a callout to fractions of the image. `part_fitment.bbox_*` arrive either as 0–1
 * fractions or as pixels of the source image; pixel boxes need the image size (stored on the
 * diagram, else the loaded image's natural size). Boxes wholly outside the image are dropped and
 * overhangs clipped, so a bad callout never sits over the wrong part. Mirrors the tablet's
 * `EpcHotspot.normalizedIn`.
 */
export function epcBox(h: { x: number; y: number; w: number; h: number }, imageWidth: number | null | undefined, imageHeight: number | null | undefined): EpcBox | null {
  if (!(h.w > 0) || !(h.h > 0) || h.x < 0 || h.y < 0) return null;
  const fractions = h.x <= 1 && h.y <= 1 && h.w <= 1 && h.h <= 1;
  let sw = 1;
  let sh = 1;
  if (!fractions) {
    if (!imageWidth || !imageHeight || imageWidth <= 0 || imageHeight <= 0) return null;
    sw = imageWidth;
    sh = imageHeight;
  }
  const left = h.x / sw;
  const top = h.y / sh;
  if (left >= 1 || top >= 1) return null;
  return { left, top, width: Math.min(h.w / sw, 1 - left), height: Math.min(h.h / sh, 1 - top) };
}
