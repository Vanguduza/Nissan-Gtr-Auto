import { StaffNav } from "@/components/staff-nav";
import { StaffWarrantyPanel } from "@/components/staff-warranty-panel";
import { iconSizeMd, iconStroke, ShieldCheck } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Warranty" };

export default function StaffWarrantyPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warranty" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <ShieldCheck size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Warranty &amp; returns
          </h1>
          <p className={styles.pageSubtitle}>
            Claims, faulty returns, and quarantine stock routing.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffWarrantyPanel />
        </div>
      </div>
    </div>
  );
}
