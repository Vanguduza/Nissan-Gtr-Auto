import { StaffNav } from "@/components/staff-nav";
import { StaffChatPanel } from "@/components/staff-chat-panel";
import { iconSizeMd, iconStroke, MessageCircle } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Chat" };

export default function StaffChatPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/chat" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <MessageCircle size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Live chat inbox
          </h1>
          <p className={styles.pageSubtitle}>
            Open customer threads and reply from the staff queue.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffChatPanel />
        </div>
      </div>
    </div>
  );
}
