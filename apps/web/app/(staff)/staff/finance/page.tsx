import Link from "next/link";
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
        <p className={styles.lede}>
          Petty cash, cash sales, and online clearing registers with quick
          journal templates; multi-invoice payment allocate; trial balance + CSV;
          period-close wizard. Finance/admin roles enforced by RPCs and RLS.
          Amounts always show explicit USD | ZIG with visible ZiG rate when
          applicable.{" "}
          <Link href="/staff/crm/credit">Customer credit desk</Link> for B2B
          limit / hold (AR aging snapshot on Payments).
        </p>
        <StaffFinancePanel />
      </div>
    </div>
  );
}
