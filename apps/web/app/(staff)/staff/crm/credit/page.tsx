import { StaffNav } from "@/components/staff-nav";
import { StaffCreditPanel } from "@/components/staff-credit-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Customer credit" };

export default function StaffCustomerCreditPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/crm/credit" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Customer credit</h1>
        <StaffCreditPanel />
      </div>
    </div>
  );
}
