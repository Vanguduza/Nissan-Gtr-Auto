/**
 * Design tokens for Nissan GTR Auto surfaces.
 * Invoke `/ui-ux-pro-max` before major visual redesigns — do not default to Inter + purple.
 */
export const tokens = {
  color: {
    brand: {
      primary: "#C8102E",
      primaryInk: "#FFFFFF",
      steel: "#1A1F2C",
      mist: "#F3F5F7",
      accent: "#0B6E4F",
    },
    money: {
      usd: "#0B6E4F",
      zig: "#B45309",
    },
  },
  font: {
    display: '"DM Sans", "Segoe UI", sans-serif',
    body: '"Source Sans 3", "Segoe UI", sans-serif',
    mono: '"IBM Plex Mono", ui-monospace, monospace',
  },
  space: {
    xs: "0.25rem",
    sm: "0.5rem",
    md: "1rem",
    lg: "1.5rem",
    xl: "2.5rem",
  },
} as const;

export type DesignTokens = typeof tokens;
