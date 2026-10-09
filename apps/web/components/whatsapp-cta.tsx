import styles from "./whatsapp-cta.module.css";

const wa = process.env.NEXT_PUBLIC_WHATSAPP_E164?.replace(/\D/g, "") ?? ""; // nosemgrep: dial.no-client-secrets -- public wa.me phone number, not a token

export function WhatsAppCta({
  product,
  compact,
}: {
  /** Customer-facing product name (never a part number). */
  product?: string;
  compact?: boolean;
}) {
  if (!wa) return null;

  const text = product
    ? `Hi GTR Auto — please confirm fitment for ${product}`
    : "Hi GTR Auto — I need a parts counter check";
  const href = `https://wa.me/${wa}?text=${encodeURIComponent(text)}`;

  return (
    <a
      className={compact ? styles.compact : styles.btn}
      href={href}
      target="_blank"
      rel="noopener noreferrer"
    >
      Ask counter on WhatsApp
    </a>
  );
}
