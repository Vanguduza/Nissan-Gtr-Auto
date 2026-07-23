/**
 * Design tokens for Nissan GTR Auto surfaces.
 * Domain: nissangtrauto.co.zw — invoke `/ui-ux-pro-max` before major redesigns.
 * Do not default to Inter + purple / cream-serif / broadsheet.
 */
export const tokens = {
  color: {
    brand: {
      primary: "#C8102E",
      primaryInk: "#FFFFFF",
      steel: "#12151C",
      steelLift: "#1E2430",
      mist: "#E8ECF1",
      chalk: "#F7F8FA",
      accent: "#0B6E4F",
    },
    money: {
      usd: "#0B6E4F",
      zig: "#B45309",
    },
  },
  font: {
    display: '"Barlow Condensed", "Arial Narrow", sans-serif',
    body: '"Source Sans 3", "Segoe UI", sans-serif',
    mono: '"IBM Plex Mono", ui-monospace, monospace',
  },
  space: {
    xs: "0.25rem",
    sm: "0.5rem",
    md: "1rem",
    lg: "1.5rem",
    xl: "2.5rem",
    "2xl": "4rem",
  },
  motion: {
    entrance: "420ms cubic-bezier(0.22, 1, 0.36, 1)",
    hover: "180ms ease-out",
  },
} as const;

export type DesignTokens = typeof tokens;

/** CSS custom properties for web surfaces */
export function tokensToCssVars(t: DesignTokens = tokens): Record<string, string> {
  return {
    "--gtr-red": t.color.brand.primary,
    "--gtr-red-ink": t.color.brand.primaryInk,
    "--gtr-steel": t.color.brand.steel,
    "--gtr-steel-lift": t.color.brand.steelLift,
    "--gtr-mist": t.color.brand.mist,
    "--gtr-chalk": t.color.brand.chalk,
    "--gtr-accent": t.color.brand.accent,
    "--gtr-usd": t.color.money.usd,
    "--gtr-zig": t.color.money.zig,
    "--font-display": t.font.display,
    "--font-body": t.font.body,
    "--font-mono": t.font.mono,
  };
}
