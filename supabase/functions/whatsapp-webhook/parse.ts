/**
 * Parse inbound WhatsApp text for parts-finder modes + handoff keywords.
 * Modes: part | vin | model | pnc
 */

export type SearchMode = "part" | "vin" | "model" | "pnc";

export type ParseResult =
  | { kind: "help" }
  | { kind: "handoff"; reason: "keyword" }
  | { kind: "search"; mode: SearchMode; query: string }
  | { kind: "invalid"; message: string };

const MODES = new Set<string>(["part", "vin", "model", "pnc"]);

const HANDOFF_KEYWORDS = new Set([
  "agent",
  "human",
  "help",
  "handoff",
  "counter",
  "sales",
  "staff",
  "person",
]);

export const HELP_TEXT = `Nissan GTR Auto — parts finder

Search (send as: mode query):
• part <OEM or OE number>
• vin <VIN or prefix>
• model <model / chassis>
• pnc <PNC or category>

Examples:
part 16546-EA00A
vin JN1TANT31U0
model Navara D40
pnc 21456

Need a person? Reply: agent / human / help`;

export function isSearchMode(value: string): value is SearchMode {
  return MODES.has(value);
}

/** Normalize and classify inbound text body. */
export function parseInboundText(raw: string): ParseResult {
  const text = raw.replace(/\s+/g, " ").trim();
  if (!text) {
    return { kind: "help" };
  }

  const lower = text.toLowerCase();
  if (HANDOFF_KEYWORDS.has(lower)) {
    return { kind: "handoff", reason: "keyword" };
  }

  // "mode:query" or "mode query"
  const colon = text.match(/^(part|vin|model|pnc)\s*[:\-]\s*(.+)$/i);
  if (colon) {
    const mode = colon[1]!.toLowerCase() as SearchMode;
    const query = colon[2]!.trim();
    if (!query) {
      return {
        kind: "invalid",
        message: `Add a query after ${mode}. Example: ${mode} <value>`,
      };
    }
    return { kind: "search", mode, query };
  }

  const space = text.match(/^(part|vin|model|pnc)\s+(.+)$/i);
  if (space) {
    const mode = space[1]!.toLowerCase() as SearchMode;
    const query = space[2]!.trim();
    if (!query) {
      return {
        kind: "invalid",
        message: `Add a query after ${mode}. Example: ${mode} <value>`,
      };
    }
    return { kind: "search", mode, query };
  }

  // Bare mode word → help for that mode
  if (isSearchMode(lower)) {
    return {
      kind: "invalid",
      message: `Send: ${lower} <query>\n\n${HELP_TEXT}`,
    };
  }

  // Default: treat as part search (OEM / OE habit)
  return { kind: "search", mode: "part", query: text };
}
