import { StaffHrPayrollPanel } from "@/components/staff-hr-payroll-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export default function PayrollPage() {
  return <div className={styles.shell}><StaffNav current="/staff/hr/payroll" />
    <section className={styles.panel}><header className={styles.pageHeader}>
      <h1 className={styles.title}>Payroll &amp; payslips</h1>
      <p className={styles.pageSubtitle}>Gross pay, manual deductions and recorded cash funding.</p>
    </header><div className={styles.pageBody}><StaffHrPayrollPanel /></div></section></div>;
}
