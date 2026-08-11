"use client";

import Image from "next/image";
import type { CSSProperties } from "react";

/** Typical Megazip EPC raster (~850×450). Used only for aspect ratio / next/image. */
const DEFAULT_W = 850;
const DEFAULT_H = 450;

/** High quality — line art / callouts must stay crisp (not soft PLP thumbs). */
const DIAGRAM_QUALITY = 90;
const THUMB_QUALITY = 82;

function isSupabasePublicStorage(src: string): boolean {
  try {
    const u = new URL(src);
    return (
      u.hostname.endsWith(".supabase.co") &&
      u.pathname.includes("/storage/v1/object/public/")
    );
  } catch {
    return false;
  }
}

type Props = {
  src: string;
  alt: string;
  className?: string;
  style?: CSSProperties;
  /** LCP hero (diagram canvas). Section grids should stay lazy. */
  priority?: boolean;
  /** Compact section-grid tile vs full diagram canvas. */
  variant?: "diagram" | "thumb";
  width?: number;
  height?: number;
  sizes?: string;
};

/**
 * Catalog / EPC Storage image via next/image (AVIF/WebP + edge cache on Vercel).
 * Falls back to &lt;img&gt; for non-Supabase URLs so remotePatterns never break the page.
 */
export function CatalogStorageImage({
  src,
  alt,
  className,
  style,
  priority = false,
  variant = "diagram",
  width = DEFAULT_W,
  height = DEFAULT_H,
  sizes,
}: Props) {
  const quality = variant === "thumb" ? THUMB_QUALITY : DIAGRAM_QUALITY;
  const resolvedSizes =
    sizes ??
    (variant === "thumb"
      ? "5rem"
      : "(min-width: 900px) 60vw, 100vw");

  if (!isSupabasePublicStorage(src)) {
    // eslint-disable-next-line @next/next/no-img-element
    return (
      <img
        className={className}
        style={style}
        src={src}
        alt={alt}
        loading={priority ? "eager" : "lazy"}
        decoding="async"
        fetchPriority={priority ? "high" : "auto"}
        width={width}
        height={height}
      />
    );
  }

  return (
    <Image
      className={className}
      style={style}
      src={src}
      alt={alt}
      width={width}
      height={height}
      sizes={resolvedSizes}
      quality={quality}
      priority={priority}
      {...(priority ? {} : { loading: "lazy" as const })}
    />
  );
}
