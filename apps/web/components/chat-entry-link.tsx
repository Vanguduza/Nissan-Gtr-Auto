import Link from "next/link";
import styles from "@/components/chat.module.css";

/** Secondary in-app chat entry — keep WhatsApp CTA alongside. */
export function ChatEntryLink({
  href = "/account/chat",
  oem,
  compact,
  label = "Live chat",
}: {
  href?: string;
  oem?: string;
  compact?: boolean;
  label?: string;
}) {
  const qs =
    oem != null && oem !== ""
      ? `?kind=parts&subject=${encodeURIComponent(`Fitment: ${oem}`)}`
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
