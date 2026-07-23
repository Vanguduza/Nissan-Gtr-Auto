import styles from "./whatsapp-cta.module.css";

const wa =
  process.env.NEXT_PUBLIC_WHATSAPP_E164?.replace(/\D/g, "") || "263770000000";

export function WhatsAppCta({
  oem,
  compact,
}: {
  oem?: string;
  compact?: boolean;
}) {
  const text = oem
    ? `Hi GTR Auto — please confirm fitment for ${oem}`
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
