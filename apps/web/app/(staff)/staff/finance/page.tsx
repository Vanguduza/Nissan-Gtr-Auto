import { StaffFinancePanel } from "@/components/staff-finance-panel";
import { StaffNav } from "@/components/staff-nav";
import { Banknote, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Finance" };

export default function StaffFinancePage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/finance" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Banknote size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Finance ledger
          </h1>
          <p className={styles.pageSubtitle}>
            Journals, payments, periods, and reports — pick a view from the
            sidebar.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffFinancePanel />
        </div>
      </div>
    </div>
  );
}
