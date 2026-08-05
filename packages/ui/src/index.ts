/**
 * Design tokens — AutoDoc-inspired spare-parts shop (nissangtrauto.co.zw).
 * Fonts: Titillium Web (chrome) + Source Sans 3 (body). Not Inter/Roboto/purple.
 * Cross-platform mirror: brand-tokens.json + BRAND_TOKENS.md
 */
export const tokens = {
  color: {
    brand: {
      primary: "#C8102E",
      primaryHover: "#E01234",
      primaryInk: "#FFFFFF",
      steel: "#12151C",
      steelLift: "#1E2430",
      silver: "#C0C5CE",
      silverDim: "#8B929E",
      mist: "#E8ECF1",
      chalk: "#F4F5F7",
      white: "#FFFFFF",
      accent: "#0B6E4F",
      warning: "#B45309",
      danger: "#C8102E",
    },
    stock: {
      inStock: "#0B6E4F",
      low: "#B45309",
      backorder: "#6B7280",
      counterOnly: "#1E2430",
    },
    money: {
      usd: "#0B6E4F",
      zig: "#B45309",
    },
  },
  font: {
    display: 'var(--font-display-loaded), "Arial Narrow", "Helvetica Neue", sans-serif',
    body: 'var(--font-body-loaded), "Segoe UI", "Helvetica Neue", sans-serif',
    mono: '"IBM Plex Mono", ui-monospace, monospace',
  },
  space: {
    xs: "0.25rem",
    sm: "0.5rem",
    md: "1rem",
    lg: "1.5rem",
    xl: "2.5rem",
    "2xl": "4rem",
    shopMax: "1120px",
  },
  radius: {
    sharp: "2px",
    control: "8px",
    staff: "10px",
    staffSm: "6px",
  },
  motion: {
    entrance: "320ms cubic-bezier(0.22, 1, 0.36, 1)",
    hover: "150ms ease-out",
  },
} as const;

export type DesignTokens = typeof tokens;

export function tokensToCssVars(t: DesignTokens = tokens): Record<string, string> {
  return {
    "--gtr-red": t.color.brand.primary,
    "--gtr-red-hover": t.color.brand.primaryHover,
    "--gtr-red-ink": t.color.brand.primaryInk,
    "--gtr-steel": t.color.brand.steel,
    "--gtr-steel-lift": t.color.brand.steelLift,
    "--gtr-silver": t.color.brand.silver,
    "--gtr-silver-dim": t.color.brand.silverDim,
    "--gtr-mist": t.color.brand.mist,
    "--gtr-chalk": t.color.brand.chalk,
    "--gtr-white": t.color.brand.white,
    "--gtr-accent": t.color.brand.accent,
    "--gtr-stock-in": t.color.stock.inStock,
    "--gtr-stock-low": t.color.stock.low,
    "--gtr-stock-bo": t.color.stock.backorder,
    "--gtr-usd": t.color.money.usd,
    "--gtr-zig": t.color.money.zig,
    "--gtr-hover": t.motion.hover,
    "--gtr-radius-sharp": t.radius.sharp,
    "--gtr-radius-control": t.radius.control,
    "--gtr-radius-staff": t.radius.staff,
    "--gtr-radius-staff-sm": t.radius.staffSm,
    "--font-display": t.font.display,
    "--font-body": t.font.body,
    "--font-mono": t.font.mono,
    "--gtr-shop-max": t.space.shopMax,
  };
}
