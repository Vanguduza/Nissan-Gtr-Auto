import { StaffNav } from "@/components/staff-nav";
import { StaffPanicInboxPanel } from "@/components/staff-panic-inbox-panel";
import { iconSizeMd, iconStroke, Siren } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Panic inbox" };

export default function StaffLogisticsPanicPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics/panic" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Siren size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Panic inbox
          </h1>
          <p className={styles.pageSubtitle}>
            Driver panic alerts — acknowledge and coordinate a response.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffPanicInboxPanel />
        </div>
      </div>
    </div>
  );
}
