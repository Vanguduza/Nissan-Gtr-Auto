import Link from "next/link";
import { SupplierNav } from "@/components/supplier-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Supplier portal" };

export default function SupplierHomePage() {
  return (
    <div className={styles.shell}>
      <SupplierNav current="/supplier" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Supplier portal</h1>
        <p className={styles.lede}>
          View RFQs you were invited to, save a draft quotation, then submit.
          Peer quotes are not visible (RLS).
        </p>
        <div className={styles.cardGrid}>
          <Link href="/supplier/rfqs" className={styles.card}>
            <span className={styles.cardLabel}>Invited RFQs</span>
            <span className={styles.cardBlurb}>Quote &amp; submit</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
