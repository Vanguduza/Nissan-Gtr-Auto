import Link from "next/link";
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
        <p className={styles.lede}>
          Set credit limit, set/clear credit hold, and view open balance with
          explicit currency via <code>set_customer_credit</code>. Sales /
          finance / admin. Storefront cannot self-set. Also linked from{" "}
          <Link href="/staff/finance">Finance ledger</Link>.
        </p>
        <StaffCreditPanel />
      </div>
    </div>
  );
}
