import { StaffNav } from "@/components/staff-nav";
import { StaffDriverCashPanel } from "@/components/staff-driver-cash-panel";
import { Banknote, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Driver cash" };

export default function StaffLogisticsDriverCashPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics/driver-cash" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Banknote size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Driver cash
          </h1>
          <p className={styles.pageSubtitle}>
            Cash drivers collected on delivery: who still holds it, hand-ins to count, and differences for a manager to sign off.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffDriverCashPanel />
        </div>
      </div>
    </div>
  );
}
