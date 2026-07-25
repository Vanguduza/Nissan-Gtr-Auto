import { StaffNav } from "@/components/staff-nav";
import { StaffCreditPanel } from "@/components/staff-credit-panel";
import { iconSizeMd, iconStroke, Users } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Customer credit" };

export default function StaffCustomerCreditPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/crm/credit" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Users size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Customer credit
          </h1>
          <p className={styles.pageSubtitle}>
            Credit limits, balances, and holds for B2B accounts.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffCreditPanel />
        </div>
      </div>
    </div>
  );
}
