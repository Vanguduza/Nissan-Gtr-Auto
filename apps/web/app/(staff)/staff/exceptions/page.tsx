import { StaffNav } from "@/components/staff-nav";
import { StaffExceptionsPanel } from "@/components/staff-exceptions-panel";
import { ShieldAlert, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Exceptions" };

export default function StaffExceptionsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/exceptions" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <ShieldAlert size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Exceptions
          </h1>
          <p className={styles.pageSubtitle}>
            Unusual activity to look into: voids, discounts, price cuts, returns, cash differences, card trouble, sales below cost or after hours, failed deliveries and stock count differences.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffExceptionsPanel />
        </div>
      </div>
    </div>
  );
}
