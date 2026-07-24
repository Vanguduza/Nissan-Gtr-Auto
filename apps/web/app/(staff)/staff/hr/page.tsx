import { StaffHrPanel } from "@/components/staff-hr-panel";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · HR" };

export default function StaffHrPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/hr" />
      <div className={styles.panel}>
        <h1 className={styles.title}>HR attendance</h1>
        <p className={styles.lede}>
          Clock in/out and look up hours in a period. Gross payroll only —
          no PAYE, NSSA, or tax UI.
        </p>
        <StaffHrPanel />
      </div>
    </div>
  );
}
