// AUTO-GENERATED from brand-tokens.json — DO NOT EDIT DIRECTLY
export const brandTokens = {
  "$schema": "./brand-tokens.schema.json",
  "meta": {
    "name": "Nissan GTR Auto",
    "source": "apps/web + docs/decisions/2026-07-23-storefront-autodoc-logo.md + docs/design/pos/POS_FRONTEND_BLUEPRINT_REV_1_5.md",
    "version": "1.5.0",
    "note": "Canonical brand tokens. Generated via build-tokens script into Kotlin, CSS/TS, and Swift."
  },
  "color": {
    "brand": {
      "primary": "#C8102E",
      "primaryHover": "#E01234",
      "primaryInk": "#FFFFFF",
      "red": "#C8102E",
      "redPressed": "#E01234",
      "steel": "#12151C",
      "steelLift": "#1E2430",
      "silver": "#C0C5CE",
      "silverDim": "#8B929E",
      "mist": "#E8ECF1",
      "chalk": "#F4F5F7",
      "white": "#FFFFFF",
      "accent": "#0B6E4F",
      "warning": "#B45309",
      "danger": "#C8102E"
    },
    "status": {
      "error": "#8E0F22",
      "unknown": "#5B4A2E",
      "warning": "#B45309",
      "success": "#0B6E4F",
      "offline": "#B45309"
    },
    "neutral": {
      "canvas": "#F4F5F7",
      "surface": "#FFFFFF",
      "surfaceElevated": "#FFFFFF",
      "heroBackdrop": "#0A0C0E",
      "ink": {
        "absolute": "#0A0C0E",
        "deep": "#12151C",
        "base": "#1E2430",
        "muted": "#8B929E"
      },
      "borderSubtle": "#E8ECF1",
      "borderStrong": "#C0C5CE",
      "borderFocus": "#C8102E",
      "scrim": "#000000"
    },
    "stock": {
      "inStock": "#0B6E4F",
      "low": "#B45309",
      "backorder": "#6B7280",
      "counterOnly": "#1E2430"
    },
    "money": {
      "usd": "#0B6E4F",
      "zig": "#B45309"
    }
  },
  "font": {
    "web": {
      "display": "Titillium Web",
      "body": "Source Sans 3",
      "mono": "IBM Plex Mono"
    },
    "pos": {
      "ui": "Inter",
      "display": "Titillium Web",
      "mono": "JetBrains Mono"
    },
    "android": {
      "display": "Titillium Web (OFL res/font)",
      "body": "Source Sans 3 (OFL res/font)",
      "mono": "monospace"
    },
    "ios": {
      "display": "Titillium Web (OFL UIAppFonts)",
      "body": "Source Sans 3 (OFL UIAppFonts)",
      "mono": "SF Mono"
    }
  },
  "space": {
    "0": 0,
    "1": 4,
    "2": 8,
    "3": 12,
    "4": 16,
    "5": 20,
    "6": 24,
    "8": 32,
    "10": 40,
    "12": 48,
    "0_5": 2,
    "xs": 4,
    "sm": 8,
    "md": 16,
    "lg": 24,
    "xl": 40,
    "xxl": 64,
    "shopMaxPx": 1120
  },
  "radius": {
    "xs": 6,
    "sm": 10,
    "md": 14,
    "lg": 20,
    "pill": 999,
    "sharp": 2,
    "control": 8,
    "staff": 10,
    "staffSm": 6
  },
  "elevation": {
    "0": {
      "y": 0,
      "blur": 0,
      "spread": 0,
      "opacity": 0
    },
    "1": {
      "y": 1,
      "blur": 2,
      "spread": 0,
      "opacity": 0.06
    },
    "2": {
      "y": 2,
      "blur": 8,
      "spread": 0,
      "opacity": 0.08
    },
    "3": {
      "y": 8,
      "blur": 24,
      "spread": 0,
      "opacity": 0.12
    }
  },
  "motion": {
    "entranceMs": 320,
    "hoverMs": 150
  },
  "avoid": [
    "Inter + purple gradients",
    "cream-serif terracotta",
    "broadsheet hairline newspaper layouts",
    "generic Material purple seed"
  ]
} as const;

export type BrandTokens = typeof brandTokens;
