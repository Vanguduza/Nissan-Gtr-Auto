import Link from "next/link";
import styles from "@/components/chat.module.css";

/** Secondary in-app chat entry — keep WhatsApp CTA alongside. */
export function ChatEntryLink({
  href = "/account/chat",
  product,
  compact,
  label = "Live chat",
}: {
  href?: string;
  /** Customer-facing product name (never a part number). */
  product?: string;
  compact?: boolean;
  label?: string;
}) {
  const qs =
    product != null && product !== ""
      ? `?kind=parts&subject=${encodeURIComponent(`Fitment: ${product}`)}`
      : "";
  return (
    <Link
      href={`${href}${qs}`}
      className={compact ? styles.chatLinkCompact : styles.chatLink}
    >
      {label}
    </Link>
  );
}
