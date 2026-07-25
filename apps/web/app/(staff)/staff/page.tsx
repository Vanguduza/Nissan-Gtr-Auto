import Link from "next/link";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff" };

export default function StaffHubPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Staff</h1>
        <p className={styles.lede}>
          Thin ops surfaces for POS, warehouse, attendance, and dispatch pick →
          delivery note. Sign in required; roles enforced by Supabase RPCs.
        </p>
        <div className={styles.cardGrid}>
          <Link href="/staff/pos" className={styles.card}>
            <span className={styles.cardLabel}>POS</span>
            <span className={styles.cardBlurb}>Cart · lines · checkout</span>
          </Link>
          <Link href="/staff/warehouse" className={styles.card}>
            <span className={styles.cardLabel}>Warehouse</span>
            <span className={styles.cardBlurb}>Receive · transfer · count</span>
          </Link>
          <Link href="/staff/hr" className={styles.card}>
            <span className={styles.cardLabel}>HR</span>
            <span className={styles.cardBlurb}>Clock + hours</span>
          </Link>
          <Link href="/staff/logistics" className={styles.card}>
            <span className={styles.cardLabel}>Logistics</span>
            <span className={styles.cardBlurb}>Pick · DN · job</span>
          </Link>
          <Link href="/procurement" className={styles.card}>
            <span className={styles.cardLabel}>Procurement</span>
            <span className={styles.cardBlurb}>RFQs</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
