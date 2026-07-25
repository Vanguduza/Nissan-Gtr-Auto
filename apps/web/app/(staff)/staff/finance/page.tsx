import { StaffFinancePanel } from "@/components/staff-finance-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Finance" };

export default function StaffFinancePage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/finance" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Finance ledger</h1>
        <StaffFinancePanel />
      </div>
    </div>
  );
}
