import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const REPO_ROOT = path.resolve(__dirname, '../../..');

const TOKENS_JSON_PATH = path.join(__dirname, '../brand-tokens.json');
const rawData = fs.readFileSync(TOKENS_JSON_PATH, 'utf-8');
const tokens = JSON.parse(rawData);

// Helpers
function hexToArgbHex(hex) {
  const clean = hex.replace('#', '');
  if (clean.length === 6) {
    return `0xFF${clean.toUpperCase()}`;
  }
  if (clean.length === 8) {
    return `0x${clean.toUpperCase()}`;
  }
  return `0xFF${clean.toUpperCase()}`;
}

// 1. Generate Kotlin Compose Tokens
function generateKotlinTokens() {
  const colorLines = [];
  
  // Brand colors
  for (const [k, v] of Object.entries(tokens.color.brand)) {
    colorLines.push(`        val Brand_${k} = Color(${hexToArgbHex(v)})`);
  }
  // Status colors
  for (const [k, v] of Object.entries(tokens.color.status)) {
    colorLines.push(`        val Status_${k} = Color(${hexToArgbHex(v)})`);
  }
  // Neutral colors
  colorLines.push(`        val Neutral_canvas = Color(${hexToArgbHex(tokens.color.neutral.canvas)})`);
  colorLines.push(`        val Neutral_surface = Color(${hexToArgbHex(tokens.color.neutral.surface)})`);
  colorLines.push(`        val Neutral_surfaceElevated = Color(${hexToArgbHex(tokens.color.neutral.surfaceElevated)})`);
  colorLines.push(`        val Neutral_heroBackdrop = Color(${hexToArgbHex(tokens.color.neutral.heroBackdrop)})`);
  colorLines.push(`        val Neutral_borderSubtle = Color(${hexToArgbHex(tokens.color.neutral.borderSubtle)})`);
  colorLines.push(`        val Neutral_borderStrong = Color(${hexToArgbHex(tokens.color.neutral.borderStrong)})`);
  colorLines.push(`        val Neutral_borderFocus = Color(${hexToArgbHex(tokens.color.neutral.borderFocus)})`);
  for (const [k, v] of Object.entries(tokens.color.neutral.ink)) {
    colorLines.push(`        val Neutral_ink_${k} = Color(${hexToArgbHex(v)})`);
  }

  const spaceLines = [];
  for (const [k, v] of Object.entries(tokens.space)) {
    if (typeof v === 'number') {
      const sanitizedKey = k.replace('.', '_');
      spaceLines.push(`        val Space_${sanitizedKey} = ${v}.dp`);
    }
  }

  const radiusLines = [];
  for (const [k, v] of Object.entries(tokens.radius)) {
    if (typeof v === 'number') {
      radiusLines.push(`        val Radius_${k} = ${v}.dp`);
    }
  }

  return `// AUTO-GENERATED from brand-tokens.json — DO NOT EDIT DIRECTLY
package co.zw.nissangtr.pos.design.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object PosTokens {
    object ColorTokens {
${colorLines.join('\n')}
    }

    object SpaceTokens {
${spaceLines.join('\n')}
    }

    object RadiusTokens {
${radiusLines.join('\n')}
    }
}
`;
}

// 2. Generate CSS Variables
function generateCssVariables() {
  const lines = [':root {'];
  for (const [k, v] of Object.entries(tokens.color.brand)) {
    lines.push(`  --gtr-color-brand-${k}: ${v};`);
  }
  for (const [k, v] of Object.entries(tokens.color.status)) {
    lines.push(`  --gtr-color-status-${k}: ${v};`);
  }
  lines.push(`  --gtr-color-neutral-canvas: ${tokens.color.neutral.canvas};`);
  lines.push(`  --gtr-color-neutral-surface: ${tokens.color.neutral.surface};`);
  lines.push(`  --gtr-color-neutral-hero-backdrop: ${tokens.color.neutral.heroBackdrop};`);
  for (const [k, v] of Object.entries(tokens.color.neutral.ink)) {
    lines.push(`  --gtr-color-neutral-ink-${k}: ${v};`);
  }
  for (const [k, v] of Object.entries(tokens.space)) {
    if (typeof v === 'number') {
      lines.push(`  --gtr-space-${k.replace('_', '-')}: ${v}px;`);
    }
  }
  for (const [k, v] of Object.entries(tokens.radius)) {
    if (typeof v === 'number') {
      lines.push(`  --gtr-radius-${k}: ${v}px;`);
    }
  }
  lines.push('}');
  return lines.join('\n') + '\n';
}

// 3. Generate TypeScript constants
function generateTypeScriptTokens() {
  return `// AUTO-GENERATED from brand-tokens.json — DO NOT EDIT DIRECTLY
export const brandTokens = ${JSON.stringify(tokens, null, 2)} as const;

export type BrandTokens = typeof brandTokens;
`;
}

// 4. Generate Swift Tokens
function generateSwiftTokens() {
  const colorCases = [];
  for (const [k, v] of Object.entries(tokens.color.brand)) {
    colorCases.push(`    public static let brand_${k} = "${v}"`);
  }
  for (const [k, v] of Object.entries(tokens.color.status)) {
    colorCases.push(`    public static let status_${k} = "${v}"`);
  }
  colorCases.push(`    public static let neutral_canvas = "${tokens.color.neutral.canvas}"`);
  colorCases.push(`    public static let neutral_surface = "${tokens.color.neutral.surface}"`);

  return `// AUTO-GENERATED from brand-tokens.json — DO NOT EDIT DIRECTLY
import Foundation

public enum GtrTokens {
    public enum ColorHex {
${colorCases.join('\n')}
    }
}
`;
}

// Write outputs
function run() {
  // Kotlin output
  const kotlinOutDir = path.join(REPO_ROOT, 'packages/pos-design/src/main/java/co/zw/nissangtr/pos/design/tokens');
  fs.mkdirSync(kotlinOutDir, { recursive: true });
  fs.writeFileSync(path.join(kotlinOutDir, 'PosTokens.kt'), generateKotlinTokens(), 'utf-8');
  console.log('Generated: packages/pos-design/.../PosTokens.kt');

  // CSS and TS outputs
  const uiSrcDir = path.join(__dirname, '../src');
  fs.mkdirSync(uiSrcDir, { recursive: true });
  fs.writeFileSync(path.join(uiSrcDir, 'tokens.css'), generateCssVariables(), 'utf-8');
  fs.writeFileSync(path.join(uiSrcDir, 'tokens.ts'), generateTypeScriptTokens(), 'utf-8');
  console.log('Generated: packages/ui/src/tokens.css & tokens.ts');

  // Swift output
  const swiftOutDir = path.join(__dirname, '../dist');
  fs.mkdirSync(swiftOutDir, { recursive: true });
  fs.writeFileSync(path.join(swiftOutDir, 'Tokens.swift'), generateSwiftTokens(), 'utf-8');
  console.log('Generated: packages/ui/dist/Tokens.swift');
}

run();
