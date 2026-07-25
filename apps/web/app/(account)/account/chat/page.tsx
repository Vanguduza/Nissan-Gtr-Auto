import { AccountNav } from "@/components/account-nav";
import { CustomerChatPanel } from "@/components/customer-chat-panel";
import { WhatsAppCta } from "@/components/whatsapp-cta";
import styles from "@/components/account.module.css";

export const metadata = { title: "Live chat · My Account" };

export default function AccountChatPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/chat" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Live chat</h1>
        <p className={styles.lede}>
          Message the counter for support or parts fitment. WhatsApp remains
          available if you prefer that channel.
        </p>
        <p style={{ marginBottom: "1rem" }}>
          <WhatsAppCta compact />
        </p>
        <CustomerChatPanel />
      </div>
    </div>
  );
}
