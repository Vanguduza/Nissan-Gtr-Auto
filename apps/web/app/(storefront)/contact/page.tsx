import Link from "next/link";
import {
  iconSizeMd,
  iconStroke,
  MapPin,
  MessageCircle,
  Smartphone,
} from "@/components/icons";
import { WhatsAppCta } from "@/components/whatsapp-cta";
import styles from "@/app/(storefront)/page.module.css";
import contactStyles from "./contact.module.css";

export const metadata = { title: "Contact us" };

export default function ContactPage() {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>
        <span className={styles.titleIcon} aria-hidden>
          <MessageCircle size={iconSizeMd} strokeWidth={iconStroke} />
        </span>
        Contact us
      </h1>
      <p className={styles.lede}>
        Reach the Nissan GTR Auto parts counter in Harare — live chat, WhatsApp,
        or trade enquiries.
      </p>

      <ul className={contactStyles.list}>
        <li className={contactStyles.item}>
          <span className={contactStyles.icon} aria-hidden>
            <MessageCircle size={iconSizeMd} strokeWidth={iconStroke} />
          </span>
          <div>
            <h2 className={contactStyles.itemTitle}>Live chat</h2>
            <p className={contactStyles.itemBody}>
              Ask about fitment, stock, or an open order. Sign in required so
              threads stay on your account.
            </p>
            <Link href="/account/chat" className={styles.button}>
              Open live chat
            </Link>
          </div>
        </li>
        <li className={contactStyles.item}>
          <span className={contactStyles.icon} aria-hidden>
            <Smartphone size={iconSizeMd} strokeWidth={iconStroke} />
          </span>
          <div>
            <h2 className={contactStyles.itemTitle}>WhatsApp counter</h2>
            <p className={contactStyles.itemBody}>
              Message the counter on WhatsApp when you prefer an outside chat
              app.
            </p>
            <WhatsAppCta />
          </div>
        </li>
        <li className={contactStyles.item}>
          <span className={contactStyles.icon} aria-hidden>
            <MapPin size={iconSizeMd} strokeWidth={iconStroke} />
          </span>
          <div>
            <h2 className={contactStyles.itemTitle}>Counter & trade</h2>
            <p className={contactStyles.itemBody}>
              Nationwide dispatch from Harare counter stock. Trade accounts and
              RFQs use the B2B portal.
            </p>
            <Link href="/b2b" className={contactStyles.textLink}>
              Trade account
            </Link>
          </div>
        </li>
      </ul>
    </div>
  );
}
