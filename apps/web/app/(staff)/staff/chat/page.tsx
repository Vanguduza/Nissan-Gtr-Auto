import { StaffNav } from "@/components/staff-nav";
import { StaffChatPanel } from "@/components/staff-chat-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Chat" };

export default function StaffChatPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/chat" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Live chat inbox</h1>
        <StaffChatPanel />
      </div>
    </div>
  );
}
