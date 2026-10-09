import { StaffNav } from "@/components/staff-nav";
import { StaffApprovalsPanel } from "@/components/staff-approvals-panel";
import { StaffPhoneAlerts } from "@/components/staff-phone-alerts";
import { Inbox, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Approvals" };

export default function StaffApprovalsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/approvals" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Inbox size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Approvals
          </h1>
          <p className={styles.pageSubtitle}>
            Everything waiting for a decision you can take, most urgent first. Open an item to decide it where it lives.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffApprovalsPanel />
          <StaffPhoneAlerts />
        </div>
      </div>
    </div>
  );
}
