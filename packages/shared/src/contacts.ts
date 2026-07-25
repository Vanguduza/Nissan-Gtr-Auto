/**
 * Normalize till / receipt contact fields (email, E.164 phone/WhatsApp).
 * Shared with POS checkout clients; SQL mirrors live in
 * public._normalize_receipt_email / public._normalize_e164.
 */

export function normalizeReceiptEmail(email: string | null | undefined): string | null {
  if (email == null) return null;
  const v = email.trim().toLowerCase();
  return v.length > 0 ? v : null;
}

/** Returns +E.164 or null when invalid. */
export function normalizeE164(phone: string | null | undefined): string | null {
  if (phone == null) return null;
  let v = phone.trim().replace(/[\s\-()]/g, "");
  if (!v) return null;
  if (!/^\+?[0-9]{8,15}$/.test(v)) return null;
  if (!v.startsWith("+")) v = `+${v}`;
  return v;
}

export function receiptContactsForCheckout(input: {
  email?: string | null;
  whatsappE164?: string | null;
  phoneE164?: string | null;
}): {
  p_receipt_email: string | null;
  p_receipt_whatsapp_e164: string | null;
  p_receipt_phone_e164: string | null;
} {
  return {
    p_receipt_email: normalizeReceiptEmail(input.email),
    p_receipt_whatsapp_e164: normalizeE164(input.whatsappE164),
    p_receipt_phone_e164: normalizeE164(input.phoneE164),
  };
}
