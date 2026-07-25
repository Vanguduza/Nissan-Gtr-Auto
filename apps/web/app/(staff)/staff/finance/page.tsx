import { StaffFinancePanel } from "@/components/staff-finance-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Finance" };

export default function StaffFinancePage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/finance" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Finance</h1>
        <p className={styles.lede}>
          Draft/post journals, run P&amp;L / balance sheet / cash flow, and
          create · allocate · post payments. Finance/admin roles enforced by
          RPCs. Amounts always show explicit USD | ZIG.
        </p>
        <StaffFinancePanel />
      </div>
    </div>
  );
}
