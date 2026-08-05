import { AccountNav } from "@/components/account-nav";
import { NotificationsPanel } from "@/components/notifications-panel";
import { Bell, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Notifications" };

export default function NotificationsPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/notifications" />
      <div className={styles.panel}>
        <h1 className={styles.title}>
          <span className={styles.titleIcon} aria-hidden>
            <Bell size={iconSizeMd} strokeWidth={iconStroke} />
          </span>
          Notifications
        </h1>
        <p className={styles.lede}>
          Order updates, back-in-stock, and chat alerts will appear here when
          the inbox is connected. Empty for now — not a fake feed.
        </p>
        <NotificationsPanel />
      </div>
    </div>
  );
}
