/** Keep post-login navigation on this origin, including URL-parser edge cases. */
export function safeAuthNextPath(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return null;
  if (/[\\\u0000-\u0020\u007f]/.test(raw) || /%(?:2f|5c|0[0-9a-f]|1[0-9a-f]|7f)/i.test(raw)) return null;
  try {
    const origin = "https://navigation.invalid";
    return new URL(raw, origin).origin === origin ? raw : null;
  } catch {
    return null;
  }
}
