import { StaffNav } from "@/components/staff-nav";
import { StaffWarrantyPanel } from "@/components/staff-warranty-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Warranty" };

export default function StaffWarrantyPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warranty" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Warranty &amp; returns</h1>
        <p className={styles.lede}>
          Open / approve / reject / close claims and post returns to quarantine.
          Sales/warehouse/admin. Faulty returns never go straight to saleable
          stock.
        </p>
        <StaffWarrantyPanel />
      </div>
    </div>
  );
}
